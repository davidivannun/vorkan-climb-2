package com.vorkan.climb2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Experimental CPU-only YOLOv8 box inference. Not connected to the UI.
 * A model file must be supplied explicitly; no weights are bundled or downloaded.
 * Call from a background thread, never from the Android main thread.
 */
internal class OfflineHoldDetector(modelFile: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
    }
    private val session = env.createSession(modelFile.absolutePath, options)
    private val inputShape = (session.inputInfo.values.first().info as TensorInfo).shape
    private val inputHeight: Int
    private val inputWidth: Int

    init {
        require(inputShape.size == 4 &&
            inputShape[0] in -1L..1L && inputShape[1] == 3L) {
            "Modelo ONNX incompatible: se espera [1,3,alto,ancho]"
        }
        // -1 denotes a dynamic dimension. Use a standard YOLO stride-aligned size.
        inputHeight = if (inputShape[2] <= 0L) 640 else inputShape[2].toInt()
        inputWidth = if (inputShape[3] <= 0L) 640 else inputShape[3].toInt()
        require(inputHeight in 32..2560 && inputWidth in 32..2560 &&
            inputHeight % 32 == 0 && inputWidth % 32 == 0) {
            "Dimensiones ONNX no soportadas: ${inputShape.contentToString()}"
        }
    }

    fun detect(bitmap: Bitmap, minConfidence: Float = 0.25f): List<HoldBoxes.Box> {
        require(minConfidence in 0f..1f)
        val scale = min(inputWidth.toFloat() / bitmap.width, inputHeight.toFloat() / bitmap.height)
        val scaledW = max(1, (bitmap.width * scale).toInt())
        val scaledH = max(1, (bitmap.height * scale).toInt())
        val padX = (inputWidth - scaledW) / 2
        val padY = (inputHeight - scaledH) / 2
        val inputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(bitmap, null,
            android.graphics.Rect(padX, padY, padX + scaledW, padY + scaledH),
            Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(inputWidth * inputHeight)
        inputBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        inputBitmap.recycle()
        val plane = inputWidth * inputHeight
        val data = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = Color.red(p) / 255f
            data[plane + i] = Color.green(p) / 255f
            data[2 * plane + i] = Color.blue(p) / 255f
        }
        val inputName = session.inputNames.first()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data),
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = result[0].value as Array<Array<FloatArray>>
                val channels = output[0]
                require(channels.size >= 5) { "Expected YOLOv8 [1,4+classes,N] output" }
                val count = channels[0].size
                val candidates = ArrayList<HoldBoxes.Box>()
                for (i in 0 until count) {
                    var confidence = 0f
                    for (c in 4 until channels.size) {
                        confidence = max(confidence, channels[c][i])
                    }
                    if (confidence < minConfidence) continue
                    val cx = channels[0][i]
                    val cy = channels[1][i]
                    val w = channels[2][i]
                    val h = channels[3][i]
                    val x1 = ((cx - w / 2f - padX) / scale / bitmap.width).coerceIn(0f, 1f)
                    val y1 = ((cy - h / 2f - padY) / scale / bitmap.height).coerceIn(0f, 1f)
                    val x2 = ((cx + w / 2f - padX) / scale / bitmap.width).coerceIn(0f, 1f)
                    val y2 = ((cy + h / 2f - padY) / scale / bitmap.height).coerceIn(0f, 1f)
                    if (x2 > x1 && y2 > y1) {
                        candidates.add(HoldBoxes.Box(x1, y1, x2, y2, confidence))
                    }
                }
                return HoldBoxes.deduplicate(candidates)
            }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }
}

package com.vorkan.climb2

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke

/** Runs offline MagicTouch inference. Call all methods on the same background thread. */
internal class AIHoldSegmenter(context: Context) : AutoCloseable {
    private val segmenter = InteractiveSegmenter.createFromOptions(
        context,
        InteractiveSegmenterOptions.builder().setBaseOptions(
            BaseOptions.builder().setModelAssetPath("interactive_segmentation.task").build()
        ).build()
    )

    fun select(bitmap: Bitmap, x: Float, y: Float, cropFraction: Float = 0.36f): List<Pair<Float, Float>> {
        // Crop around the tap so small holds occupy a useful fraction of model input.
        // The crop is centered as far as image boundaries allow.
        val cropW = (bitmap.width * cropFraction.coerceIn(0.10f, 0.36f)).toInt().coerceIn(96, bitmap.width)
        val cropH = (bitmap.height * cropFraction.coerceIn(0.10f, 0.36f)).toInt().coerceIn(96, bitmap.height)
        val px = (x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val py = (y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val left = (px - cropW / 2).coerceIn(0, bitmap.width - cropW)
        val top = (py - cropH / 2).coerceIn(0, bitmap.height - cropH)
        val crop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
        val prepared = Bitmap.createScaledBitmap(crop, 512, 512, true)
        if (prepared !== crop) crop.recycle()
        val localX = ((px - left).toFloat() / cropW).coerceIn(0f, 1f)
        val localY = ((py - top).toFloat() / cropH).coerceIn(0f, 1f)
        val mask = try {
            segmenter.setImage(BitmapImageBuilder(prepared).build())
            val stroke = Stroke.builder()
                .setBrushMode(Stroke.BrushMode.POSITIVE)
                .setPoints(listOf(NormalizedKeypoint.create(localX, localY)))
                .setCompleted(true).build()
            segmenter.segment(listOf(stroke))
        } finally {
            prepared.recycle()
        }
        val w = mask.width
        val h = mask.height
        val buffer = ByteBufferExtractor.extract(mask).asFloatBuffer()
        val confidence = FloatArray(w * h)
        buffer.rewind()
        buffer.get(confidence)
        val sx = (localX * w).toInt().coerceIn(0, w - 1)
        val sy = (localY * h).toInt().coerceIn(0, h - 1)
        // Try several confidence cutoffs. Some holds have soft model masks;
        // a single hard threshold was discarding most of them.
        val candidates = floatArrayOf(0.55f, 0.40f, 0.28f, 0.18f)
        var inside = BooleanArray(w * h)
        var queue = IntArray(w * h)
        var tail = 0
        var accepted = false
        for (threshold in candidates) {
            var start = -1
            var best = threshold
            val radius = (minOf(w, h) * 0.025f).toInt().coerceIn(4, 22)
            for (dy in -radius..radius) for (dx in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius) continue
                val px = sx + dx
                val py = sy + dy
                if (px !in 0 until w || py !in 0 until h) continue
                val index = py * w + px
                val score = confidence[index] - 0.10f * (dx * dx + dy * dy) / (radius * radius).toFloat()
                if (score > best) { best = score; start = index }
            }
            if (start < 0) continue
            inside = BooleanArray(w * h)
            queue = IntArray(w * h)
            var head = 0
            tail = 1
            queue[0] = start
            inside[start] = true
            val maxPixels = w * h / 4
            while (head < tail && tail < maxPixels) {
                val p = queue[head++]
                val px = p % w
                val py = p / w
                val neighbors = intArrayOf(if (px > 0) p - 1 else -1, if (px + 1 < w) p + 1 else -1,
                    if (py > 0) p - w else -1, if (py + 1 < h) p + w else -1)
                for (n in neighbors) if (n >= 0 && !inside[n] && confidence[n] >= threshold) {
                    inside[n] = true
                    queue[tail++] = n
                }
            }
            if (tail in 8 until maxPixels) { accepted = true; break }
        }
        if (!accepted) return emptyList()
        // Conservative refinement: a very narrow connection can join a second
        // hold to the touched hold. Erode one pixel, keep only the component
        // nearest the touch, then dilate back inside the original mask.
        // Only apply when the eroded mask has multiple substantial components;
        // otherwise preserve the proven baseline result unchanged.
        val originalInside = inside
        val originalTail = tail
        val eroded = BooleanArray(w * h)
        for (i in 0 until originalTail) {
            val p = queue[i]
            val xx = p % w
            val yy = p / w
            if (xx > 0 && xx < w - 1 && yy > 0 && yy < h - 1 &&
                originalInside[p - 1] && originalInside[p + 1] &&
                originalInside[p - w] && originalInside[p + w]) eroded[p] = true
        }
        val visited = BooleanArray(w * h)
        val parts = mutableListOf<IntArray>()
        for (i in 0 until originalTail) {
            val seed = queue[i]
            if (!eroded[seed] || visited[seed]) continue
            val component = IntArray(originalTail)
            var read = 0
            var count = 1
            component[0] = seed
            visited[seed] = true
            while (read < count) {
                val p = component[read++]
                val xx = p % w
                val yy = p / w
                val adjacent = intArrayOf(
                    if (xx > 0) p - 1 else -1,
                    if (xx + 1 < w) p + 1 else -1,
                    if (yy > 0) p - w else -1,
                    if (yy + 1 < h) p + w else -1)
                for (n in adjacent) if (n >= 0 && eroded[n] && !visited[n]) {
                    visited[n] = true
                    component[count++] = n
                }
            }
            if (count >= 12) parts.add(component.copyOf(count))
        }
        if (parts.size >= 2) {
            val sorted = parts.sortedByDescending { it.size }
            val largest = sorted[0]
            val second = sorted[1]
            // Ignore small holes, specks and fragmented edges.
            if (second.size >= largest.size * 0.18f) {
                val nearest = sorted.minByOrNull { component ->
                    component.minOf { p ->
                        val dx = p % w - sx
                        val dy = p / w - sy
                        dx * dx + dy * dy
                    }
                }
                if (nearest != null && nearest.size >= 12) {
                    val refined = BooleanArray(w * h)
                    val refinedQueue = IntArray(w * h)
                    var refinedTail = 0
                    for (p in nearest) {
                        if (!refined[p]) {
                            refined[p] = true
                            refinedQueue[refinedTail++] = p
                        }
                    }
                    // Restore the one-pixel border, but do not traverse across
                    // it: traversing could reconnect the two separate holds.
                    val coreSize = refinedTail
                    for (i in 0 until coreSize) {
                        val p = refinedQueue[i]
                        val xx = p % w
                        val yy = p / w
                        val adjacent = intArrayOf(
                            if (xx > 0) p - 1 else -1,
                            if (xx + 1 < w) p + 1 else -1,
                            if (yy > 0) p - w else -1,
                            if (yy + 1 < h) p + w else -1)
                        for (n in adjacent) if (n >= 0 && originalInside[n] && !refined[n]) {
                            refined[n] = true
                            refinedQueue[refinedTail++] = n
                        }
                    }
                    if (refinedTail >= originalTail * 0.12f &&
                        refinedTail < originalTail * 0.90f) {
                        inside = refined
                        queue = refinedQueue
                        tail = refinedTail
                    }
                }
            }
        }
        // Extract the exterior contour as a chain of oriented pixel-grid edges.
        fun key(px: Int, py: Int) = py * (w + 1) + px
        val edges = HashMap<Int, MutableList<Int>>()
        fun edge(ax: Int, ay: Int, bx: Int, by: Int) {
            edges.getOrPut(key(ax, ay)) { mutableListOf() }.add(key(bx, by))
        }
        for (i in 0 until tail) {
            val p = queue[i]
            val px = p % w
            val py = p / w
            if (py == 0 || !inside[p - w]) edge(px, py, px + 1, py)
            if (px == w - 1 || !inside[p + 1]) edge(px + 1, py, px + 1, py + 1)
            if (py == h - 1 || !inside[p + w]) edge(px + 1, py + 1, px, py + 1)
            if (px == 0 || !inside[p - 1]) edge(px, py + 1, px, py)
        }
        if (edges.isEmpty()) return emptyList()
        val first = edges.keys.minOrNull() ?: return emptyList()
        val contour = ArrayList<Pair<Float, Float>>()
        var current = first
        var steps = 0
        do {
            if (steps % 3 == 0) contour.add(Pair((left + (current % (w + 1)).toFloat() / w * cropW) / bitmap.width,
                (top + (current / (w + 1)).toFloat() / h * cropH) / bitmap.height))
            val next = edges[current]?.removeAt(0) ?: break
            current = next
            steps++
        } while (current != first && steps < w * h)
        return if (current == first && contour.size in 4..8000) contour else emptyList()
    }

    override fun close() = segmenter.close()
}

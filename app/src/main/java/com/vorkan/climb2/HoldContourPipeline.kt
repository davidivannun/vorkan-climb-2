package com.vorkan.climb2

import android.graphics.Bitmap

/**
 * Converts detector boxes into editable MagicTouch contours.
 * Execute from the existing single-threaded AI worker, not the UI thread.
 */
internal object HoldContourPipeline {
    data class Result(
        val box: HoldBoxes.Box,
        val outline: List<Pair<Float, Float>>,
        val successful: Boolean
    )

    fun segment(
        bitmap: Bitmap,
        boxes: List<HoldBoxes.Box>,
        segmenter: AIHoldSegmenter,
        isCancelled: () -> Boolean = { false },
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Result> {
        val results = ArrayList<Result>()
        val unique = HoldBoxes.deduplicate(boxes)
        for ((index, box) in unique.withIndex()) {
            if (isCancelled()) break
            // The detector supplies the candidate center; MagicTouch decides the contour.
            // A failure is retained for manual correction rather than silently discarded.
            val outline = runCatching {
                segmenter.select(bitmap, box.centerX, box.centerY)
            }.getOrDefault(emptyList())
            results.add(Result(box, outline, outline.size >= 4))
            onProgress(index + 1, unique.size)
        }
        return results
    }
}

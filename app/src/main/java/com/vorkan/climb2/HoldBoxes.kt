package com.vorkan.climb2

import kotlin.math.max
import kotlin.math.min

/** Bounding-box coordinates for an eventual offline on-device detector. */
internal object HoldBoxes {
    data class Box(val left: Float, val top: Float, val right: Float,
                   val bottom: Float, val confidence: Float) {
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    fun iou(a: Box, b: Box): Float {
        val w = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val h = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val areaA = max(0f, a.right - a.left) * max(0f, a.bottom - a.top)
        val areaB = max(0f, b.right - b.left) * max(0f, b.bottom - b.top)
        val union = areaA + areaB - w * h
        return if (union > 0f) w * h / union else 0f
    }

    fun deduplicate(boxes: List<Box>, overlap: Float = 0.45f): List<Box> {
        val accepted = ArrayList<Box>()
        for (box in boxes.sortedByDescending { it.confidence }) {
            if (accepted.none { iou(it, box) >= overlap }) accepted.add(box)
        }
        return accepted
    }
}

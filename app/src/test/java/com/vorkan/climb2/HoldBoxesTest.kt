package com.vorkan.climb2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldBoxesTest {
    @Test fun overlappingBoxesAreDeduplicated() {
        val a = HoldBoxes.Box(0.10f, 0.10f, 0.30f, 0.30f, 0.90f)
        val b = HoldBoxes.Box(0.11f, 0.11f, 0.31f, 0.31f, 0.70f)
        val c = HoldBoxes.Box(0.60f, 0.60f, 0.70f, 0.70f, 0.60f)
        val output = HoldBoxes.deduplicate(listOf(b, c, a))
        assertEquals(2, output.size)
        assertTrue(output.contains(a))
        assertTrue(output.contains(c))
    }

    @Test fun adjacentSmallHoldsAreNotMerged() {
        val a = HoldBoxes.Box(0.10f, 0.10f, 0.20f, 0.20f, 0.8f)
        val b = HoldBoxes.Box(0.21f, 0.10f, 0.30f, 0.20f, 0.7f)
        assertEquals(2, HoldBoxes.deduplicate(listOf(a, b)).size)
        assertEquals(0f, HoldBoxes.iou(a, b), 0.0001f)
    }
}

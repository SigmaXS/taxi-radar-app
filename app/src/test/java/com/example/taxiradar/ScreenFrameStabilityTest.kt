package com.example.taxiradar

import org.junit.Assert.*
import org.junit.Test

class ScreenFrameStabilityTest {
    @Test fun aStaticScreenshotRemainsUnchangedAcrossManyReads() {
        val gate = ScreenFrameStability(); val pixels = IntArray(1000) { 0xff203040.toInt() }; val mask = BooleanArray(1000)
        assertTrue(gate.changed(pixels, mask))
        repeat(30) { assertFalse(gate.changed(pixels, mask)) }
    }
    @Test fun expandingAndCollapsingOurPanelIsNotANewOffer() {
        val gate = ScreenFrameStability(); val base = IntArray(1000) { 0xff000000.toInt() }
        val small = BooleanArray(1000) { it < 50 }; val large = BooleanArray(1000) { it < 500 }
        gate.changed(base, small)
        val expanded = base.copyOf().apply { for (i in 0..499) this[i] = 0xffffffff.toInt() }
        assertFalse(gate.changed(expanded, large))
        assertFalse(gate.changed(base, small))
        assertFalse(gate.changed(base, small))
    }
    @Test fun changingTheUnderlyingCardRequiresRecognition() {
        val gate = ScreenFrameStability(); val mask = BooleanArray(1000) { it < 100 }
        gate.changed(IntArray(1000), mask)
        assertTrue(gate.changed(IntArray(1000) { if (it > 500) 0xffffffff.toInt() else 0 }, mask))
    }
    @Test fun TinyCompressionNoiseDoesNotInvalidateTheCard() {
        val gate = ScreenFrameStability(); val mask = BooleanArray(1000)
        gate.changed(IntArray(1000) { 0xff808080.toInt() }, mask)
        assertFalse(gate.changed(IntArray(1000) { 0xff828181.toInt() }, mask))
    }
}

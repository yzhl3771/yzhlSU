package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelTapCounterTest {
    @Test
    fun restoresOnlyAfterConsecutiveTapsAndResets() {
        val taps = KernelTapCounter(3)
        assertFalse(taps.tap(0))
        assertFalse(taps.tap(100))
        assertTrue(taps.tap(200))
        assertFalse(taps.tap(300))
        assertFalse(taps.tap(2_301))
        assertFalse(taps.tap(2_400))
        assertTrue(taps.tap(2_500))
        assertFalse(taps.tap(3_000))
        assertFalse(taps.tap(2_000))
        assertFalse(taps.tap(2_100))
        assertTrue(taps.tap(2_200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTapCount() {
        KernelTapCounter(0)
    }
}

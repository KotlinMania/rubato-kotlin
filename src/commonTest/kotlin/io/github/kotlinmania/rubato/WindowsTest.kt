// port-lint: tests rubato/src/windows.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class WindowsTest {
    @Test
    fun testBlackman2() {
        val wnd = makeWindow(16, WindowFunction.Blackman, F64Sample)
        val wnd2 = makeWindow(16, WindowFunction.Blackman2, F64Sample)
        assertTrue(abs(wnd[1] * wnd[1] - wnd2[1]) < 0.000001)
        assertTrue(abs(wnd[4] * wnd[4] - wnd2[4]) < 0.000001)
        assertTrue(abs(wnd[7] * wnd[7] - wnd2[7]) < 0.000001)
        assertTrue(wnd2[1] > 0.000001)
        assertTrue(wnd2[4] > 0.000001)
        assertTrue(wnd2[7] > 0.000001)
    }

    @Test
    fun testCutoff() {
        val c1 = calculateCutoff(128, WindowFunction.Blackman, F64Sample)
        assertTrue(abs(c1 - 0.953) < 0.001)
        val c2 = calculateCutoff(256, WindowFunction.Blackman, F64Sample)
        assertTrue(abs(c2 - 0.976) < 0.001)
        val c3 = calculateCutoff(128, WindowFunction.Blackman2, F64Sample)
        assertTrue(abs(c3 - 0.926) < 0.001)
        val c4 = calculateCutoff(256, WindowFunction.Blackman2, F64Sample)
        assertTrue(abs(c4 - 0.963) < 0.001)
        val c5 = calculateCutoff(128, WindowFunction.BlackmanHarris, F64Sample)
        assertTrue(abs(c5 - 0.937) < 0.001)
        val c6 = calculateCutoff(256, WindowFunction.BlackmanHarris, F64Sample)
        assertTrue(abs(c6 - 0.969) < 0.001)
        val c7 = calculateCutoff(128, WindowFunction.BlackmanHarris2, F64Sample)
        assertTrue(abs(c7 - 0.894) < 0.001)
        val c8 = calculateCutoff(256, WindowFunction.BlackmanHarris2, F64Sample)
        assertTrue(abs(c8 - 0.947) < 0.001)
        val c9 = calculateCutoff(128, WindowFunction.Hann, F64Sample)
        assertTrue(abs(c9 - 0.974) < 0.001)
        val c10 = calculateCutoff(256, WindowFunction.Hann, F64Sample)
        assertTrue(abs(c10 - 0.987) < 0.001)
        val c11 = calculateCutoff(128, WindowFunction.Hann2, F64Sample)
        assertTrue(abs(c11 - 0.958) < 0.001)
        val c12 = calculateCutoff(256, WindowFunction.Hann2, F64Sample)
        assertTrue(abs(c12 - 0.979) < 0.001)
    }
}

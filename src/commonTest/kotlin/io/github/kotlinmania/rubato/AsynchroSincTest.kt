// port-lint: tests rubato/src/asynchro_sinc.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsynchroSincTest {
    @Test
    fun intCubic() {
        val yvals = listOf(0.0, 2.0, 4.0, 6.0)
        val interp = interpCubic(0.5, yvals, F64Sample)
        assertEquals(3.0, interp)
    }

    @Test
    fun intLin32() {
        val yvals = listOf(1.0f, 5.0f)
        val interp = interpLin(0.25f, yvals, F32Sample)
        assertEquals(2.0f, interp)
    }

    @Test
    fun intCubic32() {
        val yvals = listOf(0.0f, 2.0f, 4.0f, 6.0f)
        val interp = interpCubic(0.5f, yvals, F32Sample)
        assertEquals(3.0f, interp)
    }

    @Test
    fun cubicWeightsMatchCubic() {
        val yvals = listOf(1.3, -0.7, 2.1, 0.4)
        for (xInt in 0..10) {
            val x = xInt.toDouble() / 10.0
            val direct = interpCubic(x, yvals, F64Sample)
            val w = interpCubicWeights(x, F64Sample)
            val blended = w[0] * yvals[0] + w[1] * yvals[1] + w[2] * yvals[2] + w[3] * yvals[3]
            assertTrue(abs(direct - blended) < 1.0e-12, "mismatch at x=$x")
        }
    }

    @Test
    fun quadWeightsMatchQuad() {
        val yvals = listOf(1.3, -0.7, 2.1)
        for (xInt in 0..10) {
            val x = xInt.toDouble() / 10.0
            val direct = interpQuad(x, yvals, F64Sample)
            val w = interpQuadWeights(x, F64Sample)
            val blended = w[0] * yvals[0] + w[1] * yvals[1] + w[2] * yvals[2]
            assertTrue(abs(direct - blended) < 1.0e-12, "mismatch at x=$x")
        }
    }

    @Test
    fun linWeightsMatchLin() {
        val yvals = listOf(1.3, -0.7)
        for (xInt in 0..10) {
            val x = xInt.toDouble() / 10.0
            val direct = interpLin(x, yvals, F64Sample)
            val w = interpLinWeights(x, F64Sample)
            val blended = w[0] * yvals[0] + w[1] * yvals[1]
            assertTrue(abs(direct - blended) < 1.0e-12, "mismatch at x=$x")
        }
    }
}

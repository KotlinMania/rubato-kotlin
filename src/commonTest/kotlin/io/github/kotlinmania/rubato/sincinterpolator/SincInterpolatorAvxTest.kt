// port-lint: tests rubato/src/sinc_interpolator/sinc_interpolator_avx.rs
package io.github.kotlinmania.rubato.sincinterpolator

import io.github.kotlinmania.rubato.F32Sample
import io.github.kotlinmania.rubato.F64Sample
import io.github.kotlinmania.rubato.WindowFunction
import io.github.kotlinmania.rubato.makeSincs
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class SincInterpolatorAvxTest {
    private fun getSincInterpolated64(
        wave: List<Double>,
        index: Int,
        sinc: List<Double>,
    ): Double {
        var acc = 0.0
        for (i in sinc.indices) {
            acc += wave[index + i] * sinc[i]
        }
        return acc
    }

    private fun getSincInterpolated32(
        wave: List<Float>,
        index: Int,
        sinc: List<Float>,
    ): Float {
        var acc = 0.0f
        for (i in sinc.indices) {
            acc += wave[index + i] * sinc[i]
        }
        return acc
    }

    @Test
    fun testAvxInterpolator64() {
        val rng = Random(42)
        val wave = List(2048) { rng.nextDouble() }
        val sincLen = 256
        val fCutoff = 0.94733715f
        val oversamplingFactor = 256
        val window = WindowFunction.BlackmanHarris2
        val sincs = makeSincs(sincLen, oversamplingFactor, fCutoff, window, F64Sample)

        val interpolator = AvxInterpolator.new(sincLen, oversamplingFactor, fCutoff, window, F64Sample).unwrap()
        val value = interpolator.getSincInterpolated(wave, 333, 123)
        val check = getSincInterpolated64(wave, 333, sincs[123])
        assertTrue(abs(value - check) < 1.0e-9)
    }

    @Test
    fun testAvxInterpolator32() {
        val rng = Random(42)
        val wave = List(2048) { rng.nextFloat() }
        val sincLen = 256
        val fCutoff = 0.94733715f
        val oversamplingFactor = 256
        val window = WindowFunction.BlackmanHarris2
        val sincs = makeSincs(sincLen, oversamplingFactor, fCutoff, window, F32Sample)

        val interpolator = AvxInterpolator.new(sincLen, oversamplingFactor, fCutoff, window, F32Sample).unwrap()
        val value = interpolator.getSincInterpolated(wave, 333, 123)
        val check = getSincInterpolated32(wave, 333, sincs[123])
        assertTrue(abs(value - check) < 1.0e-6f)
    }
}

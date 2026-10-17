// port-lint: tests rubato/src/synchro.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynchroTest {
    @Test
    fun resampleUnit() {
        val resampler = FftResampler(F64Sample, 147, 1000, WindowFunction.BlackmanHarris2)
        val waveIn = MutableList(147) { 0.0 }
        waveIn[0] = 0.3
        waveIn[1] = 0.7
        waveIn[2] = 1.0
        waveIn[3] = 1.0
        waveIn[4] = 0.7
        waveIn[5] = 0.3

        val waveOut = MutableList(1000) { 0.0 }
        val overlap = MutableList(1000) { 0.0 }
        resampler.resampleUnit(waveIn, 0, waveOut, 0, overlap)
        var vecsum = 0.0
        var maxval = Double.NEGATIVE_INFINITY
        for (v in waveOut) {
            vecsum += v
            maxval = max(maxval, v)
        }
        assertTrue(abs(vecsum - 4.0 * 1000.0 / 147.0) < 1.0e-6)
        assertTrue(abs(maxval - 1.0) < 0.1)
    }

    @Test
    fun fftMoreSubChunksThanFrames() {
        for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
            val resampler =
                Fft
                    .newCustom(
                        F64Sample,
                        44100,
                        48000,
                        100,
                        1000,
                        2,
                        WindowFunction.BlackmanHarris2,
                        fixed,
                    ).unwrap()
            assertEquals(147, resampler.fftSizeIn())
            assertEquals(160, resampler.fftSizeOut())
        }
    }

    @Test
    fun fftSizesAndCutoff() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
                Pair(44100, 192000),
                Pair(192000, 44100),
            )
        for (chunksize in listOf(512, 1024, 4096)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    assertTrue(
                        abs(
                            resampler.fftSizeOut().toDouble() / resampler.fftSizeIn().toDouble() -
                                outputRate.toDouble() / inputRate.toDouble(),
                        ) < 1e-9,
                    )
                    assertEquals(resampler.fftSizeOut() / 2, resampler.outputDelay())

                    val limit = min(outputRate.toFloat() / inputRate.toFloat(), 1.0f)
                    assertTrue(resampler.cutoff() > 0.5f * limit)
                    assertTrue(resampler.cutoff() < limit)
                }
            }
        }
    }

    @Test
    fun fftOutput() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
                Pair(44100, 192000),
                Pair(192000, 44100),
            )
        for (chunksize in listOf(512, 1024)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    checkOutput(resampler)
                }
            }
        }
    }

    @Test
    fun fftRatio() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
            )
        for (chunksize in listOf(512, 1024)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    checkRatio(resampler, 20000 / chunksize, 0.05)
                }
            }
        }
    }

    @Test
    fun fftLen() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
                Pair(44100, 192000),
                Pair(192000, 44100),
                Pair(44100, 44110),
            )
        for (chunksize in listOf(512, 1024, 4096)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    when (fixed) {
                        FixedSync.Input -> assertFiLen(resampler, chunksize)
                        FixedSync.Output -> assertFoLen(resampler, chunksize)
                        FixedSync.Both -> assertFbLen(resampler)
                    }
                }
            }
        }
    }

    @Test
    fun fftReset() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
            )
        for (chunksize in listOf(512, 1024)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    checkReset(resampler)
                }
            }
        }
    }

    @Test
    fun fftInputOffset() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
            )
        for (chunksize in listOf(512, 1024)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    checkInputOffset(resampler)
                }
            }
        }
    }

    @Test
    fun fftOutputOffset() {
        val ratesList =
            listOf(
                Pair(44100, 48000),
                Pair(48000, 44100),
                Pair(44100, 88200),
                Pair(88200, 44100),
            )
        for (chunksize in listOf(512, 1024)) {
            for ((inputRate, outputRate) in ratesList) {
                for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
                    val resampler = Fft.new(F64Sample, inputRate, outputRate, chunksize, 2, fixed).unwrap()
                    checkOutputOffset(resampler)
                }
            }
        }
    }

    @Test
    fun fftMasked() {
        for (fixed in listOf(FixedSync.Input, FixedSync.Output, FixedSync.Both)) {
            val resampler =
                Fft
                    .newCustom(
                        F64Sample,
                        44100,
                        48000,
                        1024,
                        2,
                        2,
                        WindowFunction.Hann,
                        fixed,
                    ).unwrap()
            checkMasked(resampler)
        }
    }
}

// port-lint: tests rubato/src/asynchro.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsynchroTest {
    private fun basicParams(): SincInterpolationParameters =
        SincInterpolationParameters(
            sincLen = 64,
            fCutoff = 0.95f,
            interpolation = SincInterpolationType.Cubic,
            oversamplingFactor = 16,
            window = WindowFunction.BlackmanHarris2,
        )

    @Test
    fun polyCutoffIsNone() {
        for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
            val resampler =
                Async
                    .newPoly(
                        F64Sample,
                        ratio,
                        1.0,
                        PolynomialDegree.Cubic,
                        1024,
                        2,
                        FixedAsync.Input,
                    ).unwrap()
            assertNull(resampler.cutoff())
        }
    }

    @Test
    fun sincCutoffGiven() {
        for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
            val resampler =
                Async
                    .newSinc(
                        F64Sample,
                        ratio,
                        1.0,
                        basicParams(),
                        1024,
                        2,
                        FixedAsync.Input,
                    ).unwrap()
            val expected = 0.95f * min(ratio, 1.0).toFloat()
            assertTrue(abs(resampler.cutoff()!! - expected) < 1e-6f)
        }
    }

    @Test
    fun sincCutoffAutomatic() {
        for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
            for (sincLen in listOf(32, 256)) {
                val params =
                    SincInterpolationParameters(
                        sincLen = sincLen,
                        fCutoff = null,
                        interpolation = SincInterpolationType.Cubic,
                        oversamplingFactor = 16,
                        window = WindowFunction.BlackmanHarris2,
                    )
                val resampler =
                    Async
                        .newSinc(
                            F64Sample,
                            ratio,
                            1.0,
                            params,
                            1024,
                            2,
                            FixedAsync.Input,
                        ).unwrap()
                val limit = min(ratio, 1.0).toFloat()
                val cutoff = resampler.cutoff()!!
                assertTrue(cutoff > 0.5f * limit)
                assertTrue(cutoff < limit)
            }
        }
    }

    @Test
    fun polyOutput() {
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newPoly(
                                F64Sample,
                                ratio,
                                1.0,
                                PolynomialDegree.Cubic,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkOutput(resampler)
                }
            }
        }
    }

    @Test
    fun polyRatio() {
        for (chunksize in listOf(100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newPoly(
                                F64Sample,
                                ratio,
                                1.0,
                                PolynomialDegree.Cubic,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkRatio(resampler, 100000 / chunksize, 0.001)
                }
            }
        }
    }

    @Test
    fun polyLen() {
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newPoly(
                                F64Sample,
                                ratio,
                                1.0,
                                PolynomialDegree.Cubic,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    when (fixed) {
                        FixedAsync.Input -> assertFiLen(resampler, chunksize)
                        FixedAsync.Output -> assertFoLen(resampler, chunksize)
                    }
                }
            }
        }
    }

    @Test
    fun polyMasked() {
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler =
                Async
                    .newPoly(
                        F64Sample,
                        0.75,
                        1.0,
                        PolynomialDegree.Cubic,
                        1024,
                        2,
                        fixed,
                    ).unwrap()
            checkMasked(resampler)
        }
    }

    @Test
    fun polyReset() {
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newPoly(
                                F64Sample,
                                ratio,
                                1.0,
                                PolynomialDegree.Cubic,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkReset(resampler)
                }
            }
        }
    }

    @Test
    fun sincOutput() {
        val params = basicParams()
        for (chunksize in listOf(100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newSinc(
                                F64Sample,
                                ratio,
                                1.0,
                                params,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkOutput(resampler)
                }
            }
        }
    }

    @Test
    fun sincRatio() {
        val params = basicParams()
        for (chunksize in listOf(100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newSinc(
                                F64Sample,
                                ratio,
                                1.0,
                                params,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkRatio(resampler, 100000 / chunksize, 0.001)
                }
            }
        }
    }

    @Test
    fun sincLen() {
        val params = basicParams()
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newSinc(
                                F64Sample,
                                ratio,
                                1.0,
                                params,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    when (fixed) {
                        FixedAsync.Input -> assertFiLen(resampler, chunksize)
                        FixedAsync.Output -> assertFoLen(resampler, chunksize)
                    }
                }
            }
        }
    }

    @Test
    fun sincReset() {
        val params = basicParams()
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newSinc(
                                F64Sample,
                                ratio,
                                1.0,
                                params,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkReset(resampler)
                }
            }
        }
    }

    @Test
    fun asyncInputOffset() {
        val params = basicParams()
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newSinc(
                                F64Sample,
                                ratio,
                                1.0,
                                params,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkInputOffset(resampler)
                }
            }
        }
    }

    @Test
    fun asyncOutputOffset() {
        val params = basicParams()
        for (chunksize in listOf(1, 100, 1024)) {
            for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler =
                        Async
                            .newSinc(
                                F64Sample,
                                ratio,
                                1.0,
                                params,
                                chunksize,
                                2,
                                fixed,
                            ).unwrap()
                    checkOutputOffset(resampler)
                }
            }
        }
    }

    @Test
    fun sincMasked() {
        val params = basicParams()
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler =
                Async
                    .newSinc(
                        F64Sample,
                        0.75,
                        1.0,
                        params,
                        1024,
                        2,
                        fixed,
                    ).unwrap()
            checkMasked(resampler)
        }
    }

    @Test
    fun asyncResize() {
        val params = basicParams()
        for (ratio in listOf(0.8, 1.2, 0.125, 8.0)) {
            for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                val resampler =
                    Async
                        .newSinc(
                            F64Sample,
                            ratio,
                            1.0,
                            params,
                            1024,
                            2,
                            fixed,
                        ).unwrap()
                resampler.setChunkSize(600).unwrap()
                checkOutput(resampler)
            }
        }
    }

    @Test
    fun asyncRelativeRatioExactBounds() {
        val params = basicParams()
        val originalRatio = 44100.0 / 48000.0
        val max = 1.0161
        val resampler =
            Async
                .newSinc(
                    F64Sample,
                    originalRatio,
                    max,
                    params,
                    1024,
                    2,
                    FixedAsync.Input,
                ).unwrap()
        resampler.setResampleRatioRelative(max, false).unwrap()
        resampler.setResampleRatioRelative(1.0 / max, false).unwrap()
        assertIs<ResampleError.RatioOutOfBounds>(
            resampler.setResampleRatioRelative(max * 1.0001, false).errorOrNull(),
        )
        assertIs<ResampleError.RatioOutOfBounds>(
            resampler.setResampleRatioRelative(1.0 / max * 0.9999, false).errorOrNull(),
        )
    }

    private fun processAndGetFrameCounts(resampler: Async<Double>): Pair<Int, Int> {
        val inputFrames = resampler.inputFramesNext()
        val outputFramesMax = resampler.outputFramesMax()
        val inputData = List(2) { MutableList(inputFrames) { 0.25 } }
        val input = SequentialSliceOfVecs.new(inputData, 2, inputFrames)!!
        val outputData = List(2) { MutableList(outputFramesMax) { 0.0 } }
        val output = SequentialSliceOfVecs.newMut(outputData, 2, outputFramesMax)!!
        val fc = resampler.processIntoBuffer(input, output, null).unwrap()
        return Pair(fc.inputFrames, fc.outputFrames)
    }

    private fun processChunksAndGetTotalFrameCounts(
        resampler: Async<Double>,
        nbrChunks: Int,
    ): Pair<Int, Int> {
        var sumIn = 0
        var sumOut = 0
        repeat(nbrChunks) {
            val (framesIn, framesOut) = processAndGetFrameCounts(resampler)
            sumIn += framesIn
            sumOut += framesOut
        }
        return Pair(sumIn, sumOut)
    }

    private fun assertRatioWithinTolerance(
        totalIn: Int,
        totalOut: Int,
        expectedRatio: Double,
        tolerance: Double,
        label: String,
    ) {
        val measuredRatio = totalOut.toDouble() / totalIn.toDouble()
        val lower = expectedRatio - tolerance
        val upper = expectedRatio + tolerance
        assertTrue(
            measuredRatio in lower..upper,
            "$label ratio out of range: got $measuredRatio, expected $expectedRatio +/- $tolerance",
        )
    }

    private fun checkRelativeRatioChangesFrameRatio(resampler: Async<Double>) {
        val nbrChunks = 8
        val tolerance = 0.01

        val (baselineIn, baselineOut) = processChunksAndGetTotalFrameCounts(resampler, nbrChunks)
        assertRatioWithinTolerance(baselineIn, baselineOut, 1.0, tolerance, "Baseline")

        resampler.setResampleRatioRelative(0.75, false).unwrap()
        val (lowerIn, lowerOut) = processChunksAndGetTotalFrameCounts(resampler, nbrChunks)
        assertRatioWithinTolerance(lowerIn, lowerOut, 0.75, tolerance, "0.75x")

        resampler.setResampleRatioRelative(1.25, false).unwrap()
        val (higherIn, higherOut) = processChunksAndGetTotalFrameCounts(resampler, nbrChunks)
        assertRatioWithinTolerance(higherIn, higherOut, 1.25, tolerance, "1.25x")
    }

    @Test
    fun polyRelativeRatioChangesFrameRatio() {
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler =
                Async
                    .newPoly(
                        F64Sample,
                        1.0,
                        4.0,
                        PolynomialDegree.Cubic,
                        1024,
                        2,
                        fixed,
                    ).unwrap()
            checkRelativeRatioChangesFrameRatio(resampler)
        }
    }

    @Test
    fun sincRelativeRatioChangesFrameRatio() {
        val params = basicParams()
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler =
                Async
                    .newSinc(
                        F64Sample,
                        1.0,
                        4.0,
                        params,
                        1024,
                        2,
                        fixed,
                    ).unwrap()
            checkRelativeRatioChangesFrameRatio(resampler)
        }
    }

    private fun compare1ch4chSincOutput(
        interpolation: SincInterpolationType,
        ratio: Double,
        fixed: FixedAsync,
    ) {
        val params =
            SincInterpolationParameters(
                sincLen = 64,
                fCutoff = 0.95f,
                interpolation = interpolation,
                oversamplingFactor = 16,
                window = WindowFunction.BlackmanHarris2,
            )
        val chunk = 256
        val r1 = Async.newSinc(F64Sample, ratio, 1.0, params, chunk, 1, fixed).unwrap()
        val r4 = Async.newSinc(F64Sample, ratio, 1.0, params, chunk, 4, fixed).unwrap()

        var phase = 0.0
        repeat(20) {
            val framesIn = r1.inputFramesNext()
            val framesOut = r1.outputFramesNext()
            assertEquals(framesIn, r4.inputFramesNext())
            assertEquals(framesOut, r4.outputFramesNext())

            val wave = MutableList(framesIn) { i -> sin(phase + i.toDouble() * 0.1) }
            phase += framesIn.toDouble() * 0.1

            val in1Data = listOf(wave.toMutableList())
            val in4Data = List(4) { wave.toMutableList() }
            val input1ch = SequentialSliceOfVecs.new(in1Data, 1, framesIn)!!
            val input4ch = SequentialSliceOfVecs.new(in4Data, 4, framesIn)!!

            val out1Data = listOf(MutableList(framesOut) { 0.0 })
            val out4Data = List(4) { MutableList(framesOut) { 0.0 } }
            val out1 = SequentialSliceOfVecs.newMut(out1Data, 1, framesOut)!!
            val out4 = SequentialSliceOfVecs.newMut(out4Data, 4, framesOut)!!

            r1.processIntoBuffer(input1ch, out1, null).unwrap()
            r4.processIntoBuffer(input4ch, out4, null).unwrap()

            for (frame in 0 until framesOut) {
                val expected = out1Data[0][frame]
                for (ch in 1 until 4) {
                    assertEquals(
                        out4Data[0][frame],
                        out4Data[ch][frame],
                        "interp=$interpolation ratio=$ratio frame=$frame: ch$ch differs from ch0",
                    )
                }
                for (ch in 0 until 4) {
                    val diff = abs(out4Data[ch][frame] - expected)
                    assertTrue(
                        diff < 1e-10,
                        "interp=$interpolation ratio=$ratio ch=$ch frame=$frame: 4ch=${out4Data[ch][frame]} vs 1ch=$expected",
                    )
                }
            }
        }
    }

    @Test
    fun sinc4chMatches1ch() {
        for (interp in listOf(
            SincInterpolationType.Cubic,
            SincInterpolationType.Quadratic,
            SincInterpolationType.Linear,
            SincInterpolationType.Nearest,
        )) {
            for (ratio in listOf(0.8, 1.2)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    compare1ch4chSincOutput(interp, ratio, fixed)
                }
            }
        }
    }

    @Test
    fun avgTRatioEqualRatios() {
        for (r in listOf(0.1, 0.5, 1.0, 2.0, 10.0)) {
            val got = Async.avgTRatio(r, r)
            val expected = 1.0 / r
            assertTrue(abs(got - expected) < 1e-12)
        }
    }

    @Test
    fun avgTRatioSymmetric() {
        for ((r1, r2) in listOf(
            Pair(1.0, 0.2),
            Pair(2.0, 3.0),
            Pair(0.3, 0.5),
            Pair(0.125, 8.0),
            Pair(1.0, 1.0),
        )) {
            val forward = Async.avgTRatio(r1, r2)
            val backward = Async.avgTRatio(r2, r1)
            assertTrue(abs(forward - backward) < 1e-12)
        }
    }

    @Test
    fun avgTRatioKnownValue() {
        val got = Async.avgTRatio(1.0, 0.2)
        assertTrue(abs(got - 3.0) < 1e-12)
        val got2 = Async.avgTRatio(2.0, 3.0)
        val expected2 = 5.0 / 12.0
        assertTrue(abs(got2 - expected2) < 1e-12)
    }

    private fun processOneBlock(
        resampler: Async<Double>,
        channels: Int,
    ): Pair<Int, Int> {
        val framesIn = resampler.inputFramesNext()
        val framesOutMax = resampler.outputFramesMax()
        val inData = List(channels) { MutableList(framesIn) { 0.0 } }
        val input = SequentialSliceOfVecs.new(inData, channels, framesIn)!!
        val outData = List(channels) { MutableList(framesOutMax) { 0.0 } }
        val out = SequentialSliceOfVecs.newMut(outData, channels, framesOutMax)!!
        val fc = resampler.processIntoBuffer(input, out, null).unwrap()
        return Pair(fc.inputFrames, fc.outputFrames)
    }

    @Test
    fun polyRampLargeRatioChangeDoesNotPanic() {
        for (degree in listOf(PolynomialDegree.Cubic, PolynomialDegree.Linear)) {
            for (targetRel in listOf(0.2, 5.0)) {
                val chunkSize = 1024
                val channels = 1
                val resampler =
                    Async
                        .newPoly(
                            F64Sample,
                            1.0,
                            6.0,
                            degree,
                            chunkSize,
                            channels,
                            FixedAsync.Input,
                        ).unwrap()
                val (framesIn, framesOut) = processOneBlock(resampler, channels)
                assertEquals(chunkSize, framesIn)
                assertTrue(framesOut > 0)

                resampler.setResampleRatioRelative(targetRel, true).unwrap()
                val framesIn2 = resampler.inputFramesNext()
                val framesOut2 = resampler.outputFramesNext()
                assertEquals(chunkSize, framesIn2)
                assertTrue(framesOut2 > 0)

                val (consumed2, produced2) = processOneBlock(resampler, channels)
                assertEquals(framesIn2, consumed2)
                assertEquals(framesOut2, produced2)
            }
        }
    }

    @Test
    fun sincRampLargeRatioChangeDoesNotPanic() {
        for (targetRel in listOf(0.2, 5.0)) {
            val chunkSize = 1024
            val channels = 1
            val params = basicParams()
            val resampler =
                Async
                    .newSinc(
                        F64Sample,
                        1.0,
                        6.0,
                        params,
                        chunkSize,
                        channels,
                        FixedAsync.Input,
                    ).unwrap()
            val (framesIn, framesOut) = processOneBlock(resampler, channels)
            assertEquals(chunkSize, framesIn)
            assertTrue(framesOut > 0)

            resampler.setResampleRatioRelative(targetRel, true).unwrap()
            val framesIn2 = resampler.inputFramesNext()
            val framesOut2 = resampler.outputFramesNext()
            assertEquals(chunkSize, framesIn2)
            assertTrue(framesOut2 > 0)

            val (consumed2, produced2) = processOneBlock(resampler, channels)
            assertEquals(framesIn2, consumed2)
            assertEquals(framesOut2, produced2)
        }
    }

    @Test
    fun polyOutputFixedRampLargeRatioChangeDoesNotPanic() {
        for (degree in listOf(PolynomialDegree.Cubic, PolynomialDegree.Linear)) {
            for (targetRel in listOf(0.2, 5.0)) {
                val chunkSize = 1024
                val channels = 1
                val resampler =
                    Async
                        .newPoly(
                            F64Sample,
                            1.0,
                            6.0,
                            degree,
                            chunkSize,
                            channels,
                            FixedAsync.Output,
                        ).unwrap()
                val (_, framesOut) = processOneBlock(resampler, channels)
                assertEquals(chunkSize, framesOut)

                resampler.setResampleRatioRelative(targetRel, true).unwrap()
                val framesIn2 = resampler.inputFramesNext()
                val framesOut2 = resampler.outputFramesNext()
                assertTrue(framesIn2 > 0)
                assertEquals(chunkSize, framesOut2)

                val (consumed2, produced2) = processOneBlock(resampler, channels)
                assertEquals(framesIn2, consumed2)
                assertEquals(framesOut2, produced2)
            }
        }
    }

    @Test
    fun sincOutputFixedRampLargeRatioChangeDoesNotPanic() {
        for (targetRel in listOf(0.2, 5.0)) {
            val chunkSize = 1024
            val channels = 1
            val params = basicParams()
            val resampler =
                Async
                    .newSinc(
                        F64Sample,
                        1.0,
                        6.0,
                        params,
                        chunkSize,
                        channels,
                        FixedAsync.Output,
                    ).unwrap()
            val (_, framesOut) = processOneBlock(resampler, channels)
            assertEquals(chunkSize, framesOut)

            resampler.setResampleRatioRelative(targetRel, true).unwrap()
            val framesIn2 = resampler.inputFramesNext()
            val framesOut2 = resampler.outputFramesNext()
            assertTrue(framesIn2 > 0)
            assertEquals(chunkSize, framesOut2)

            val (consumed2, produced2) = processOneBlock(resampler, channels)
            assertEquals(framesIn2, consumed2)
            assertEquals(framesOut2, produced2)
        }
    }

    @Test
    fun tRatioIncrementReachesTarget() {
        val cases =
            listOf(
                Triple(1.0, 0.2, 100),
                Triple(1.0, 5.0, 1024),
                Triple(2.0, 3.0, 512),
                Triple(0.5, 0.5, 256),
                Triple(0.125, 8.0, 64),
            )
        for ((r1, r2, n) in cases) {
            val inc = Async.computeTRatioIncrement(r1, r2, n)
            val tRatioEnd = 1.0 / r1 + n.toDouble() * inc
            val expectedEnd = 1.0 / r2
            assertTrue(abs(tRatioEnd - expectedEnd) < 1e-10)
        }
    }

    @Test
    fun tRatioIncrementEqualRatiosIsZero() {
        for (r in listOf(0.1, 0.5, 1.0, 2.0, 10.0)) {
            for (n in listOf(1, 100, 1024)) {
                val inc = Async.computeTRatioIncrement(r, r, n)
                assertTrue(abs(inc) < 1e-15)
            }
        }
    }

    @Test
    fun advanceIndexMatchesAnalyticalFormula() {
        val cases =
            listOf(
                Triple(1.0, 0.2, 100),
                Triple(1.0, 5.0, 1024),
                Triple(2.0, 3.0, 512),
                Triple(0.5, 0.8, 256),
                Triple(0.125, 8.0, 64),
                Triple(1.0, 1.0, 200),
            )
        for ((r1, r2, n) in cases) {
            val startIdx = 0.0
            val inc = Async.computeTRatioIncrement(r1, r2, n)
            val finalIdx = Async.advanceIndex(startIdx, 1.0 / r1, inc, n)
            val avg = Async.avgTRatio(r1, r2)
            val rampOvershoot = 0.5 * (1.0 / r2 - 1.0 / r1)
            val analytical = startIdx + n.toDouble() * avg + rampOvershoot
            assertTrue(abs(finalIdx - analytical) <= 1e-6)
        }
    }

    @Test
    fun advanceIndexStaysWithinBufferBounds() {
        val chunkSize = 1024
        val interpolatorLen = 4
        for (lastIndex in listOf(0.0, 0.5, 2.0)) {
            for ((r1, r2) in listOf(
                Pair(1.0, 0.2),
                Pair(1.0, 5.0),
                Pair(0.5, 2.0),
                Pair(2.0, 0.5),
                Pair(1.0, 1.0),
                Pair(0.3, 0.3),
                Pair(0.125, 8.0),
                Pair(8.0, 0.125),
            )) {
                val n =
                    Async.calculateOutputSize(
                        chunkSize,
                        r1,
                        r2,
                        lastIndex,
                        interpolatorLen,
                        FixedAsync.Input,
                    )
                if (n == 0) continue
                val inc = Async.computeTRatioIncrement(r1, r2, n)
                val finalIdx = Async.advanceIndex(lastIndex, 1.0 / r1, inc, n)
                val bound = chunkSize - interpolatorLen - 1
                assertTrue(floor(finalIdx).toInt() <= bound)
            }
        }
    }

    @Test
    fun rejectNonFiniteRatios() {
        for (ratio in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)) {
            val res =
                Async.newPoly(
                    F64Sample,
                    ratio,
                    1.0,
                    PolynomialDegree.Cubic,
                    1024,
                    2,
                    FixedAsync.Input,
                )
            assertIs<ResamplerConstructionError.InvalidRatio>(res.errorOrNull())
        }
        for (rel in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.5)) {
            val res =
                Async.newPoly(
                    F64Sample,
                    1.0,
                    rel,
                    PolynomialDegree.Cubic,
                    1024,
                    2,
                    FixedAsync.Input,
                )
            assertIs<ResamplerConstructionError.InvalidRelativeRatio>(res.errorOrNull())
        }
    }

    @Test
    fun hugeRatioPanicsOnSizeCalculation() {
        val err =
            assertFailsWith<IllegalArgumentException> {
                Async.newPoly(
                    F64Sample,
                    1e19,
                    1.0,
                    PolynomialDegree.Cubic,
                    1024,
                    2,
                    FixedAsync.Input,
                )
            }
        assertTrue(err.message!!.contains("does not fit in a usize"))
    }

    @Test
    fun hugeRelativeRatioPanicsOnBufferLength() {
        val err =
            assertFailsWith<IllegalArgumentException> {
                Async.newPoly(
                    F64Sample,
                    1.0,
                    ULong.MAX_VALUE.toDouble(),
                    PolynomialDegree.Cubic,
                    1024,
                    2,
                    FixedAsync.Output,
                )
            }
        assertTrue(err.message!!.contains("does not fit in a usize"))
    }

    @Test
    fun relativeRatioCannotInstallANonFiniteRatio() {
        val resampler =
            Async
                .newPoly(
                    F64Sample,
                    1e308,
                    10.0,
                    PolynomialDegree.Cubic,
                    1024,
                    2,
                    FixedAsync.Output,
                ).unwrap()
        assertIs<ResampleError.RatioOutOfBounds>(
            resampler.setResampleRatioRelative(10.0, false).errorOrNull(),
        )
        assertEquals(1e308, resampler.resampleRatio())

        val resampler2 =
            Async
                .newPoly(
                    F64Sample,
                    2.0,
                    10.0,
                    PolynomialDegree.Cubic,
                    1024,
                    2,
                    FixedAsync.Output,
                ).unwrap()
        resampler2.setResampleRatioRelative(10.0, false).unwrap()
        assertEquals(20.0, resampler2.resampleRatio())
    }

    @Test
    fun acceptRealisticSizes() {
        val cases =
            listOf(
                Triple(192000.0 / 44100.0, 10.0, FixedAsync.Input),
                Triple(192000.0 / 44100.0, 10.0, FixedAsync.Output),
                Triple(44100.0 / 192000.0, 10.0, FixedAsync.Input),
                Triple(44100.0 / 192000.0, 10.0, FixedAsync.Output),
                Triple(1.0, 1.0, FixedAsync.Input),
                Triple(1.0, 1000.0, FixedAsync.Output),
            )
        for ((ratio, rel, fixed) in cases) {
            val resampler =
                Async
                    .newPoly(
                        F64Sample,
                        ratio,
                        rel,
                        PolynomialDegree.Cubic,
                        8192,
                        2,
                        fixed,
                    ).unwrap()
            assertEquals(2, resampler.nbrChannels())
        }
    }
}

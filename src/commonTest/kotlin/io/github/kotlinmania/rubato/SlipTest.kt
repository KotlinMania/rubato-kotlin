// port-lint: tests rubato/src/slip.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SlipTest {
    @Test
    fun crossfadeLenScalesWithChunk() {
        assertEquals(128, MAX_CROSSFADE_LEN)
        assertEquals(258, 2 * MAX_CROSSFADE_LEN + 2)
        assertEquals(MAX_CROSSFADE_LEN, crossfadeLenFor(4096))
        assertEquals(MAX_CROSSFADE_LEN, crossfadeLenFor(2 * MAX_CROSSFADE_LEN + 2))
        assertTrue(crossfadeLenFor(100) < MAX_CROSSFADE_LEN)
        for (chunk in listOf(4, 5, 17, 50, 100, 257)) {
            val l = crossfadeLenFor(chunk)
            assertTrue(l >= 1 && 2 * l + 2 <= chunk, "chunk $chunk -> len $l")
        }
        assertEquals(0, crossfadeLenFor(3))
    }

    @Test
    fun driftOutput() {
        for (chunksize in listOf(50, 1024)) {
            for (ratio in listOf(1.0, 1.0005, 0.9995)) {
                for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                    val resampler = Slip.new(F64Sample, chunksize, 2, fixed).unwrap()
                    resampler.setResampleRatio(ratio, false).unwrap()
                    checkOutput(resampler)
                }
            }
        }
    }

    @Test
    fun driftReset() {
        for (chunksize in listOf(50, 1024)) {
            for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                val resampler = Slip.new(F64Sample, chunksize, 2, fixed).unwrap()
                checkReset(resampler)
            }
        }
    }

    @Test
    fun driftMasked() {
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler = Slip.new(F64Sample, 1024, 2, fixed).unwrap()
            checkMasked(resampler)
        }
    }

    @Test
    fun driftInputOffset() {
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler = Slip.new(F64Sample, 1024, 2, fixed).unwrap()
            checkInputOffset(resampler)
        }
    }

    @Test
    fun driftOutputOffset() {
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler = Slip.new(F64Sample, 1024, 2, fixed).unwrap()
            checkOutputOffset(resampler)
        }
    }

    @Test
    fun rejectsShortChunk() {
        val res = Slip.new(F64Sample, 3, 1, FixedAsync.Input)
        assertIs<ResamplerConstructionError.InvalidChunkSize>(res.errorOrNull())
        val ok = Slip.new(F64Sample, 4, 1, FixedAsync.Input).unwrap()
        assertEquals(1, ok.nbrChannels())
    }

    @Test
    fun unitRatioIsIdentity() {
        val resampler = Slip.new(F64Sample, 64, 1, FixedAsync.Input).unwrap()
        val inputData = listOf(MutableList(64) { i -> sin(i.toDouble() * 0.3) })
        val input = SequentialSliceOfVecs.new(inputData, 1, 64)!!
        val outputData = listOf(MutableList(65) { 0.0 })
        val output = SequentialSliceOfVecs.newMut(outputData, 1, 65)!!
        val fc = resampler.processIntoBuffer(input, output, null).unwrap()
        assertEquals(64, fc.inputFrames)
        assertEquals(64, fc.outputFrames)
        assertEquals(inputData[0], outputData[0].subList(0, 64))
    }

    @Test
    fun dcStaysFlatAcrossCorrection() {
        val chunk = 64
        val resampler = Slip.new(F64Sample, chunk, 1, FixedAsync.Input).unwrap()
        resampler.setResampleRatio(1.01, false).unwrap()
        var corrected = false
        repeat(10) {
            val outLen = resampler.outputFramesNext()
            if (outLen != chunk) {
                corrected = true
            }
            val inputData = listOf(MutableList(chunk) { 0.5 })
            val input = SequentialSliceOfVecs.new(inputData, 1, chunk)!!
            val outputData = listOf(MutableList(outLen) { 0.0 })
            val output = SequentialSliceOfVecs.newMut(outputData, 1, outLen)!!
            resampler.processIntoBuffer(input, output, null).unwrap()
            for (v in outputData[0]) {
                assertTrue(abs(v - 0.5) < 1e-12, "DC not preserved: $v")
            }
        }
        assertTrue(corrected, "expected at least one correction in the run")
    }

    @Test
    fun multipleCorrectionsPerChunk() {
        for (ratio in listOf(1.005, 0.995)) {
            for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                val chunk = 1024
                val resampler = Slip.new(F64Sample, chunk, 1, fixed).unwrap()
                resampler.setResampleRatio(ratio, false).unwrap()
                var totalIn = 0
                var totalOut = 0
                var maxDelta = 0
                var ramp = 0.0
                repeat(500) {
                    val framesIn = resampler.inputFramesNext()
                    val framesOut = resampler.outputFramesNext()
                    maxDelta = max(maxDelta, abs(framesIn - framesOut))
                    val inputData = listOf(MutableList(framesIn) { i -> ramp + i.toDouble() })
                    ramp += framesIn.toDouble()
                    val input = SequentialSliceOfVecs.new(inputData, 1, framesIn)!!
                    val outputData = listOf(MutableList(framesOut) { 0.0 })
                    val output = SequentialSliceOfVecs.newMut(outputData, 1, framesOut)!!
                    val fc = resampler.processIntoBuffer(input, output, null).unwrap()
                    totalIn += fc.inputFrames
                    totalOut += fc.outputFrames
                    val outCh = outputData[0]
                    for (i in 0 until outCh.size - 1) {
                        assertTrue(
                            outCh[i + 1] >= outCh[i] - 1e-9,
                            "ramp not monotonic: ${outCh[i]} -> ${outCh[i + 1]}",
                        )
                    }
                }
                assertTrue(
                    maxDelta > 1,
                    "expected more than one correction in a chunk, got max delta $maxDelta",
                )
                val measured = totalOut.toDouble() / totalIn.toDouble()
                assertTrue(
                    abs(measured - ratio) < 1e-4,
                    "measured ratio $measured too far from target $ratio",
                )
            }
        }
    }

    @Test
    fun realizedRatioTracksTarget() {
        for (ratio in listOf(1.0003, 0.9997)) {
            for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
                val resampler = Slip.new(F64Sample, 1024, 2, fixed).unwrap()
                resampler.setResampleRatio(ratio, false).unwrap()
                var totalIn = 0
                var totalOut = 0
                repeat(2000) {
                    val framesIn = resampler.inputFramesNext()
                    val framesOut = resampler.outputFramesNext()
                    val inputData = List(2) { MutableList(framesIn) { 0.0 } }
                    val input = SequentialSliceOfVecs.new(inputData, 2, framesIn)!!
                    val outputData = List(2) { MutableList(framesOut) { 0.0 } }
                    val output = SequentialSliceOfVecs.newMut(outputData, 2, framesOut)!!
                    val fc = resampler.processIntoBuffer(input, output, null).unwrap()
                    totalIn += fc.inputFrames
                    totalOut += fc.outputFrames
                }
                val measured = totalOut.toDouble() / totalIn.toDouble()
                assertTrue(
                    abs(measured - ratio) < 1e-4,
                    "measured ratio $measured too far from target $ratio",
                )
            }
        }
    }

    @Test
    fun driftResize() {
        for (fixed in listOf(FixedAsync.Input, FixedAsync.Output)) {
            val resampler = Slip.new(F64Sample, 1024, 2, fixed).unwrap()
            resampler.setResampleRatio(1.0005, false).unwrap()
            resampler.setChunkSize(600).unwrap()
            checkOutput(resampler)
        }
    }

    @Test
    fun setRatioRespectsRange() {
        val resampler = Slip.new(F64Sample, 1024, 2, FixedAsync.Input).unwrap()
        val (min, max) = resampler.currentRatioRange()
        val inRange = 1.0 + (max - 1.0) * 0.5
        resampler.setResampleRatio(inRange, false).unwrap()
        assertEquals(inRange, resampler.resampleRatio())
        assertIs<ResampleError.RatioOutsideRange>(
            resampler.setResampleRatio(2.0, false).errorOrNull(),
        )
        assertEquals(max, resampler.resampleRatio())
        assertIs<ResampleError.RatioOutsideRange>(
            resampler.setResampleRatio(0.5, false).errorOrNull(),
        )
        assertEquals(min, resampler.resampleRatio())
        assertIs<ResampleError.RatioOutsideRange>(
            resampler.setResampleRatio(-1.0, false).errorOrNull(),
        )
        assertEquals(min, resampler.resampleRatio())
    }
}

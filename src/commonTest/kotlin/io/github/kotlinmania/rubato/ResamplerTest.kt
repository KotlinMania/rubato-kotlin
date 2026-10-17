package io.github.kotlinmania.rubato

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun expectedOutputValue(
    idx: Int,
    delay: Int,
    ratio: Double,
): Double {
    if (idx <= delay) {
        return 0.0
    }
    return (idx - delay).toDouble() * 0.1 / ratio
}

internal fun checkOutput(resampler: Resampler<Double>) {
    var rampValue = 0.0
    val maxInputLen = resampler.inputFramesMax()
    val maxOutputLen = resampler.outputFramesMax()
    val ratio = resampler.resampleRatio()
    val delay = resampler.outputDelay()
    var outputIndex = 0
    val outIncr = 0.1 / ratio

    val nbrIterations = 100000 / (resampler.outputFramesNext() + resampler.inputFramesNext())
    repeat(nbrIterations) {
        val expectedFramesIn = resampler.inputFramesNext()
        val expectedFramesOut = resampler.outputFramesNext()
        assertTrue(expectedFramesIn <= maxInputLen)
        assertTrue(expectedFramesOut <= maxOutputLen)
        val inputData = List(2) { MutableList(expectedFramesIn) { 0.0 } }
        for (m in 0 until expectedFramesIn) {
            for (ch in 0 until 2) {
                inputData[ch][m] = rampValue
            }
            rampValue += 0.1
        }
        val input = SequentialSliceOfVecs.new(inputData, 2, expectedFramesIn)!!
        val outputData = List(2) { MutableList(expectedFramesOut) { 0.0 } }
        val output = SequentialSliceOfVecs.newMut(outputData, 2, expectedFramesOut)!!

        val fc = resampler.processIntoBuffer(input, output, null).unwrap()
        assertEquals(expectedFramesIn, fc.inputFrames)
        assertEquals(expectedFramesOut, fc.outputFrames)
        for (idx in 0 until fc.outputFrames) {
            val expected = expectedOutputValue(outputIndex + idx, delay, ratio)
            for (ch in 0 until 2) {
                val value = outputData[ch][idx]
                val margin = 3.0 * outIncr
                assertTrue(
                    value > expected - margin,
                    "Value at frame ${outputIndex + idx} is too small, $value < $expected - $margin",
                )
                assertTrue(
                    value < expected + margin,
                    "Value at frame ${outputIndex + idx} is too large, $value > $expected + $margin",
                )
            }
        }
        outputIndex += fc.outputFrames
    }
    assertTrue(outputIndex > 1000, "Too few frames checked!")
}

internal fun checkRatio(
    resampler: Resampler<Double>,
    repetitions: Int,
    margin: Double,
) {
    val ratio = resampler.resampleRatio()
    val maxInputLen = resampler.inputFramesMax()
    val maxOutputLen = resampler.outputFramesMax()
    val wavesIn = List(2) { MutableList(maxInputLen) { 0.0 } }
    val input = SequentialSliceOfVecs.new(wavesIn, 2, maxInputLen)!!
    val wavesOut = List(2) { MutableList(maxOutputLen) { 0.0 } }
    val output = SequentialSliceOfVecs.newMut(wavesOut, 2, maxOutputLen)!!
    var totalIn = 0
    var totalOut = 0
    repeat(repetitions) {
        val out = resampler.processIntoBuffer(input, output, null).unwrap()
        totalIn += out.inputFrames
        totalOut += out.outputFrames
    }
    val measuredRatio = totalOut.toDouble() / totalIn.toDouble()
    assertTrue(
        measuredRatio / ratio > (1.0 - margin),
        "Measured ratio is too small, measured / expected = ${measuredRatio / ratio}",
    )
    assertTrue(
        measuredRatio / ratio < (1.0 + margin),
        "Measured ratio is too large, measured / expected = ${measuredRatio / ratio}",
    )
}

internal fun assertFiLen(
    resampler: Resampler<Double>,
    chunksize: Int,
) {
    val nbrFramesInNext = resampler.inputFramesNext()
    val nbrFramesInMax = resampler.inputFramesMax()
    assertEquals(
        chunksize,
        nbrFramesInNext,
        "expected $chunksize for next input samples, got $nbrFramesInNext",
    )
    assertEquals(
        chunksize,
        nbrFramesInMax,
        "expected $chunksize for max input samples, got $nbrFramesInMax",
    )
}

internal fun assertFoLen(
    resampler: Resampler<Double>,
    chunksize: Int,
) {
    val nbrFramesOutNext = resampler.outputFramesNext()
    val nbrFramesOutMax = resampler.outputFramesMax()
    assertEquals(
        chunksize,
        nbrFramesOutNext,
        "expected $chunksize for next output samples, got $nbrFramesOutNext",
    )
    assertEquals(
        chunksize,
        nbrFramesOutMax,
        "expected $chunksize for max output samples, got $nbrFramesOutMax",
    )
}

internal fun assertFbLen(resampler: Resampler<Double>) {
    val nbrFramesOutNext = resampler.outputFramesNext()
    val nbrFramesOutMax = resampler.outputFramesMax()
    val nbrFramesInNext = resampler.inputFramesNext()
    val nbrFramesInMax = resampler.inputFramesMax()
    val ratio = resampler.resampleRatio()
    assertEquals(
        nbrFramesOutMax,
        nbrFramesOutNext,
        "next output frames, $nbrFramesOutNext, is different than max, $nbrFramesOutMax",
    )
    assertEquals(
        nbrFramesInMax,
        nbrFramesInNext,
        "next input frames, $nbrFramesInNext, is different than max, $nbrFramesInMax",
    )
    val framesRatio = nbrFramesOutNext.toDouble() / nbrFramesInNext.toDouble()
    assertTrue(abs(framesRatio - ratio) < 0.000001)
}

internal fun checkReset(resampler: Resampler<Double>) {
    val rng = Random(1234)
    val framesIn = resampler.inputFramesNext()
    val inputData = List(2) { MutableList(framesIn) { rng.nextDouble() } }
    val input = SequentialSliceOfVecs.new(inputData, 2, framesIn)!!

    val framesOut = resampler.outputFramesNext()
    val outputData1 = List(2) { MutableList(framesOut) { 0.0 } }
    val output1 = SequentialSliceOfVecs.newMut(outputData1, 2, framesOut)!!
    resampler.processIntoBuffer(input, output1, null).unwrap()
    resampler.reset()
    assertEquals(
        framesIn,
        resampler.inputFramesNext(),
        "Resampler requires different number of frames when new and after a reset.",
    )
    val outputData2 = List(2) { MutableList(framesOut) { 0.0 } }
    val output2 = SequentialSliceOfVecs.newMut(outputData2, 2, framesOut)!!
    resampler.processIntoBuffer(input, output2, null).unwrap()
    assertEquals(
        outputData1,
        outputData2,
        "Resampler gives different output when new and after a reset.",
    )
}

internal fun checkInputOffset(resampler: Resampler<Double>) {
    val rng = Random(1234)
    val framesIn = resampler.inputFramesNext()
    val inputData1 = List(2) { MutableList(framesIn) { rng.nextDouble() } }
    val offset = 123
    val inputData2 =
        List(2) { ch ->
            MutableList(framesIn + offset) { idx ->
                if (idx >= offset) inputData1[ch][idx - offset] else 0.0
            }
        }

    val input1 = SequentialSliceOfVecs.new(inputData1, 2, framesIn)!!
    val input2 = SequentialSliceOfVecs.new(inputData2, 2, framesIn + offset)!!

    val framesOut = resampler.outputFramesNext()
    val outputData1 = List(2) { MutableList(framesOut) { 0.0 } }
    val output1 = SequentialSliceOfVecs.newMut(outputData1, 2, framesOut)!!
    resampler.processIntoBuffer(input1, output1, null).unwrap()
    resampler.reset()
    assertEquals(
        framesIn,
        resampler.inputFramesNext(),
        "Resampler requires different number of frames when new and after a reset.",
    )
    val outputData2 = List(2) { MutableList(framesOut) { 0.0 } }
    val output2 = SequentialSliceOfVecs.newMut(outputData2, 2, framesOut)!!

    val indexing =
        Indexing(
            inputOffset = offset,
            outputOffset = 0,
            activeChannelsMask = null,
            partialLen = null,
        )
    resampler.processIntoBuffer(input2, output2, indexing).unwrap()
    assertEquals(
        outputData1,
        outputData2,
        "Resampler gives different output when new and after a reset.",
    )
}

internal fun checkOutputOffset(resampler: Resampler<Double>) {
    val rng = Random(1234)
    val framesIn = resampler.inputFramesNext()
    val inputData = List(2) { MutableList(framesIn) { rng.nextDouble() } }
    val input = SequentialSliceOfVecs.new(inputData, 2, framesIn)!!

    val framesOut = resampler.outputFramesNext()
    val outputData1 = List(2) { MutableList(framesOut) { 0.0 } }
    val output1 = SequentialSliceOfVecs.newMut(outputData1, 2, framesOut)!!
    resampler.processIntoBuffer(input, output1, null).unwrap()
    resampler.reset()
    assertEquals(
        framesIn,
        resampler.inputFramesNext(),
        "Resampler requires different number of frames when new and after a reset.",
    )
    val offset = 123
    val outputData2 = List(2) { MutableList(framesOut + offset) { 0.0 } }
    val output2 = SequentialSliceOfVecs.newMut(outputData2, 2, framesOut + offset)!!
    val indexing =
        Indexing(
            inputOffset = 0,
            outputOffset = offset,
            activeChannelsMask = null,
            partialLen = null,
        )
    resampler.processIntoBuffer(input, output2, indexing).unwrap()
    assertEquals(
        outputData1[0],
        outputData2[0].subList(offset, offset + framesOut),
        "Resampler gives different output when new and after a reset.",
    )
    assertEquals(
        outputData1[1],
        outputData2[1].subList(offset, offset + framesOut),
        "Resampler gives different output when new and after a reset.",
    )
}

internal fun checkMasked(resampler: Resampler<Double>) {
    val rng = Random(1234)
    val framesIn = resampler.inputFramesNext()
    val inputData = List(2) { MutableList(framesIn) { rng.nextDouble() + 0.1 } }
    val input = SequentialSliceOfVecs.new(inputData, 2, framesIn)!!

    val framesOut = resampler.outputFramesNext()
    val outputData = List(2) { MutableList(framesOut) { 0.0 } }
    val output = SequentialSliceOfVecs.newMut(outputData, 2, framesOut)!!

    val indexing =
        Indexing(
            inputOffset = 0,
            outputOffset = 0,
            activeChannelsMask = listOf(false, true),
            partialLen = null,
        )
    resampler.processIntoBuffer(input, output, indexing).unwrap()

    val nonZeroChan0 = outputData[0].count { it != 0.0 }
    val nonZeroChan1 = outputData[1].count { it != 0.0 }
    assertEquals(
        0,
        nonZeroChan0,
        "Some sample in the non-active channel has a non-zero value",
    )
    assertTrue(
        nonZeroChan1 > 0,
        "No sample in the active channel has a non-zero value",
    )
}

class ResamplerTest {
    private fun testSincResampler(): Async<Double> =
        Async
            .newSinc(
                F64Sample,
                88200.0 / 44100.0,
                1.1,
                SincInterpolationParameters(
                    sincLen = 64,
                    fCutoff = 0.95f,
                    interpolation = SincInterpolationType.Cubic,
                    oversamplingFactor = 16,
                    window = WindowFunction.BlackmanHarris2,
                ),
                1024,
                2,
                FixedAsync.Input,
            ).unwrap()

    @Test
    fun processSingleChunk() {
        val resampler = testSincResampler()
        val inLen = resampler.inputFramesNext()
        val samples = MutableList(inLen) { it.toDouble() / 10.0 }
        val inputData = listOf(samples.toMutableList(), samples.toMutableList())
        val input = SequentialSliceOfVecs.new(inputData, 2, inLen)!!

        val expected = resampler.outputFramesNext()
        val out = resampler.process(input, null).unwrap()
        assertEquals(2, out.channels())
        assertEquals(expected, out.frames())

        val expected2 = resampler.outputFramesNext()
        val out2 = resampler.process(input, Indexing.new().withPartialLen(inLen / 2)).unwrap()
        assertEquals(expected2, out2.frames())
    }

    @Test
    fun wrongLengthMaskReturnsError() {
        val resampler = testSincResampler()
        val inLen = resampler.inputFramesNext()
        val inputData = List(2) { MutableList(inLen) { 0.0 } }
        val input = SequentialSliceOfVecs.new(inputData, 2, inLen)!!

        val indexing = Indexing.new().withActiveChannelsMask(listOf(true, true, true))
        val res = resampler.process(input, indexing)
        val err = assertIs<ResampleError.WrongNumberOfMaskChannels>(res.errorOrNull())
        assertEquals(2, err.expected)
        assertEquals(3, err.actual)
    }

    @Test
    fun processAll() {
        val resampler = testSincResampler()
        val inputLen = 12345
        val samples = MutableList(inputLen) { it.toDouble() / 10.0 }
        val inputData = listOf(samples.toMutableList(), samples.toMutableList())
        val input = SequentialSliceOfVecs.new(inputData, 2, inputLen)!!
        val outputLen = resampler.processAllNeededOutputLen(inputLen)
        val outputData = List(2) { MutableList(outputLen) { 0.0 } }
        val output = SequentialSliceOfVecs.newMut(outputData, 2, outputLen)!!
        val fc = resampler.processAllIntoBuffer(input, output, inputLen, null).unwrap()
        assertEquals(inputLen, fc.inputFrames)
        assertEquals(2 * fc.inputFrames, fc.outputFrames)

        val increment = 0.1 / resampler.resampleRatio()
        val delay = resampler.outputDelay()
        val margin = (delay.toDouble() * resampler.resampleRatio()).toInt()
        var expected = margin.toDouble() * increment
        for (frame in margin until (fc.outputFrames - margin)) {
            for (chan in 0 until 2) {
                val value = output.readSample(chan, frame)!!
                assertTrue(
                    abs(value - expected) < 100.0 * increment,
                    "frame: $frame, value: $value, expected: $expected",
                )
            }
            expected += increment
        }
    }

    @Test
    fun processAllAllocating() {
        val resampler = testSincResampler()
        val inputLen = 12345
        val samples = MutableList(inputLen) { it.toDouble() / 10.0 }
        val inputData = listOf(samples.toMutableList(), samples.toMutableList())
        val input = SequentialSliceOfVecs.new(inputData, 2, inputLen)!!

        val output = resampler.processAll(input, inputLen, null).unwrap()
        val expectedLen = 2 * inputLen
        assertEquals(2, output.channels())
        assertEquals(expectedLen, output.frames())

        val increment = 0.1 / resampler.resampleRatio()
        val margin = (resampler.outputDelay().toDouble() * resampler.resampleRatio()).toInt()
        for (frame in margin until (expectedLen - margin)) {
            val expected = frame.toDouble() * increment
            for (chan in 0 until 2) {
                val value = output.readSample(chan, frame)!!
                assertTrue(
                    abs(value - expected) < 100.0 * increment,
                    "frame: $frame, value: $value, expected: $expected",
                )
            }
        }

        val output2 = resampler.processAll(input, inputLen, null).unwrap()
        assertEquals(expectedLen, output2.frames())
        for (frame in 0 until expectedLen) {
            for (chan in 0 until 2) {
                assertEquals(output.readSample(chan, frame), output2.readSample(chan, frame))
            }
        }
    }

    @Test
    fun processAllTrimsWithoutLeavingStaleFrames() {
        val fsIn = 8000
        val fsOut = 16000
        val freq = 440.0
        val inputLen = fsIn
        val samples =
            MutableList(inputLen) { i ->
                sin(2.0 * PI * freq * i.toDouble() / fsIn.toDouble())
            }
        val inputData = listOf(samples.toMutableList(), samples.toMutableList())
        val input = SequentialSliceOfVecs.new(inputData, 2, inputLen)!!

        val resampler = Fft.new(F64Sample, fsIn, fsOut, 1024, 2, FixedSync.Input).unwrap()
        val delay = resampler.outputDelay()
        val output = resampler.processAll(input, inputLen, null).unwrap()
        assertEquals(2 * inputLen, output.frames())

        for (frame in delay until (output.frames() - 512)) {
            val expected = sin(2.0 * PI * freq * frame.toDouble() / fsOut.toDouble())
            for (chan in 0 until 2) {
                val value = output.readSample(chan, frame)!!
                assertTrue(
                    abs(value - expected) < 1e-6,
                    "frame: $frame, channel: $chan, value: $value, expected: $expected",
                )
            }
        }
    }

    @Test
    fun processAllTrimsDelayForShortClip() {
        val fsIn = 8000
        val fsOut = 16000
        val freq = 440.0
        val inputLen = 300
        val samples =
            MutableList(inputLen) { i ->
                sin(2.0 * PI * freq * i.toDouble() / fsIn.toDouble())
            }
        val inputData = listOf(samples.toMutableList(), samples.toMutableList())
        val input = SequentialSliceOfVecs.new(inputData, 2, inputLen)!!

        val resampler = Fft.new(F64Sample, fsIn, fsOut, 1024, 2, FixedSync.Input).unwrap()
        assertTrue(inputLen < resampler.inputFramesNext())
        val output = resampler.processAll(input, inputLen, null).unwrap()
        assertEquals(2 * inputLen, output.frames())

        for (frame in 64 until (output.frames() - 64)) {
            val expected = sin(2.0 * PI * freq * frame.toDouble() / fsOut.toDouble())
            for (chan in 0 until 2) {
                val value = output.readSample(chan, frame)!!
                assertTrue(
                    abs(value - expected) < 1e-2,
                    "frame: $frame, channel: $chan, value: $value, expected: $expected",
                )
            }
        }
    }

    @Test
    fun processAllTrimsDelayLongerThanTheClip() {
        val resampler =
            Async
                .newSinc(
                    F64Sample,
                    1.0,
                    1.1,
                    SincInterpolationParameters(
                        sincLen = 256,
                        fCutoff = 0.95f,
                        interpolation = SincInterpolationType.Cubic,
                        oversamplingFactor = 16,
                        window = WindowFunction.BlackmanHarris2,
                    ),
                    64,
                    1,
                    FixedAsync.Input,
                ).unwrap()
        val inputLen = 10
        assertTrue(resampler.outputDelay() > inputLen)

        val inputData = listOf(MutableList(inputLen) { 1.0 })
        val input = SequentialSliceOfVecs.new(inputData, 1, inputLen)!!
        val output = resampler.processAll(input, inputLen, null).unwrap()
        assertEquals(inputLen, output.frames())

        for (frame in 0 until (inputLen - 1)) {
            val value = output.readSample(0, frame)!!
            assertTrue(abs(value - 1.0) < 0.1, "frame: $frame, value: $value")
        }
    }

    @Test
    fun capabilityQueries() {
        val resampler = testSincResampler()
        assertTrue(resampler.isAdjustable())
        assertTrue(resampler.isResizable())
        resampler.asAdjustable()!!.setResampleRatioRelative(1.05, false).unwrap()
        assertNotNull(resampler.asResizable())

        val boxed: Resampler<Double> = testSincResampler()
        assertTrue(boxed.isAdjustable())
        assertTrue(boxed.isResizable())
        assertNotNull(boxed.asAdjustable())
        assertNotNull(boxed.asResizable())

        val slip = Slip.new(F64Sample, 1024, 2, FixedAsync.Output).unwrap()
        assertTrue(slip.isAdjustable())
        assertTrue(slip.isResizable())
        assertNotNull(slip.asAdjustable())
        assertNotNull(slip.asResizable())

        val fft = Fft.new(F64Sample, 44100, 48000, 1024, 2, FixedSync.Both).unwrap()
        assertFalse(fft.isAdjustable())
        assertFalse(fft.isResizable())
        assertNull(fft.asAdjustable())
        assertNull(fft.asResizable())
    }

    @Test
    fun boxedResampler() {
        val boxed: Resampler<Double> = testSincResampler()
        val maxFramesOut = boxed.outputFramesMax()
        val nbrFramesInNext = boxed.inputFramesNext()
        val waves = List(2) { MutableList(nbrFramesInNext) { 0.0 } }
        val wavesOut = List(2) { MutableList(maxFramesOut) { 0.0 } }
        val input = SequentialSliceOfVecs.new(waves, 2, nbrFramesInNext)!!
        val output = SequentialSliceOfVecs.newMut(wavesOut, 2, maxFramesOut)!!
        boxed.processIntoBuffer(input, output, null).unwrap()
    }
}

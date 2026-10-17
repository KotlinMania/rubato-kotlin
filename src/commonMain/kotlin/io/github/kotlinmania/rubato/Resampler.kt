package io.github.kotlinmania.rubato

import kotlin.math.ceil

/**
 * Number of input frames consumed and output frames produced in a single processing call.
 */
public data class FrameCount(
    public val inputFrames: Int,
    public val outputFrames: Int,
)

/**
 * A resampler that is used to resample a chunk of audio to a new sample rate.
 * For asynchronous resamplers, the rate can be adjusted as required.
 */
internal interface Resampler<T> {
    val sample: Sample<T>

    /**
     * Convenience wrapper for [processIntoBuffer] that allocates the output buffer with each call.
     */
    fun process(
        bufferIn: Adapter<T>,
        indexing: Indexing? = null,
    ): ResampleResult<InterleavedOwned<T>> {
        val frames = outputFramesNext()
        val channels = nbrChannels()
        val bufferOut = InterleavedOwned.new(sample.zero, channels, frames)
        val offsets = getOffsets(indexing)
        val effectiveIndexing =
            Indexing(
                inputOffset = offsets.inputOffset,
                outputOffset = 0,
                partialLen = getPartialLen(indexing),
                activeChannelsMask = indexing?.activeChannelsMask?.toList(),
            )
        val res = processIntoBuffer(bufferIn, bufferOut, effectiveIndexing)
        if (res is ResampleResult.Err) {
            return res
        }
        return ResampleResult.Ok(bufferOut)
    }

    /**
     * Resample a buffer of audio to a pre-allocated output buffer.
     */
    fun processIntoBuffer(
        bufferIn: Adapter<T>,
        bufferOut: AdapterMut<T>,
        indexing: Indexing? = null,
    ): ResampleResult<FrameCount>

    /**
     * Convenience method for processing audio clips of arbitrary length from and to buffers in memory.
     */
    fun processAllIntoBuffer(
        bufferIn: Adapter<T>,
        bufferOut: AdapterMut<T>,
        inputLen: Int,
        activeChannelsMask: List<Boolean>? = null,
    ): ResampleResult<FrameCount> {
        val expectedOutputLen = ceil(resampleRatio() * inputLen.toDouble()).toInt()

        val indexing =
            Indexing(
                inputOffset = 0,
                outputOffset = 0,
                activeChannelsMask = activeChannelsMask?.toList(),
                partialLen = null,
            )

        var framesLeft = inputLen
        var outputLen = 0
        var framesToTrim = outputDelay()

        val nextNbrInputFrames = inputFramesNext()
        while (framesLeft > nextNbrInputFrames) {
            val res = processIntoBuffer(bufferIn, bufferOut, indexing)
            if (res is ResampleResult.Err) {
                return res
            }
            val counts = (res as ResampleResult.Ok).value
            framesLeft -= counts.inputFrames
            outputLen += counts.outputFrames
            indexing.inputOffset += counts.inputFrames
            indexing.outputOffset += counts.outputFrames
            val trimmed = trimStartupDelay(bufferOut, indexing, outputLen, framesToTrim)
            outputLen = trimmed.outputLen
            framesToTrim = trimmed.framesToTrim
        }
        if (framesLeft > 0) {
            indexing.partialLen = framesLeft
            val res = processIntoBuffer(bufferIn, bufferOut, indexing)
            if (res is ResampleResult.Err) {
                return res
            }
            val counts = (res as ResampleResult.Ok).value
            outputLen += counts.outputFrames
            indexing.outputOffset += counts.outputFrames
            val trimmed = trimStartupDelay(bufferOut, indexing, outputLen, framesToTrim)
            outputLen = trimmed.outputLen
            framesToTrim = trimmed.framesToTrim
        }
        indexing.partialLen = 0
        while (outputLen < expectedOutputLen + framesToTrim) {
            val res = processIntoBuffer(bufferIn, bufferOut, indexing)
            if (res is ResampleResult.Err) {
                return res
            }
            val counts = (res as ResampleResult.Ok).value
            outputLen += counts.outputFrames
            indexing.outputOffset += counts.outputFrames
            val trimmed = trimStartupDelay(bufferOut, indexing, outputLen, framesToTrim)
            outputLen = trimmed.outputLen
            framesToTrim = trimmed.framesToTrim
        }
        return ResampleResult.Ok(FrameCount(inputLen, expectedOutputLen))
    }

    /**
     * Resample a whole audio clip in a single call, returning the result in a freshly allocated buffer.
     */
    fun processAll(
        bufferIn: Adapter<T>,
        inputLen: Int,
        activeChannelsMask: List<Boolean>? = null,
    ): ResampleResult<InterleavedOwned<T>> {
        reset()
        val channels = nbrChannels()
        val neededLen = processAllNeededOutputLen(inputLen)
        val bufferOut = InterleavedOwned.new(sample.zero, channels, neededLen)
        val res = processAllIntoBuffer(bufferIn, bufferOut, inputLen, activeChannelsMask)
        if (res is ResampleResult.Err) {
            return res
        }
        val outputLen = (res as ResampleResult.Ok).value.outputFrames
        val data = bufferOut.takeData()
        val trimmedData = data.subList(0, outputLen * channels).toMutableList()
        val owned =
            InterleavedOwned.newFrom(trimmedData, channels, outputLen)
                ?: error("trimmed length is consistent with the channel count")
        return ResampleResult.Ok(owned)
    }

    /**
     * Calculate the minimal length of the output buffer needed to process a clip of length `inputLen`.
     */
    fun processAllNeededOutputLen(inputLen: Int): Int {
        val delayFrames = outputDelay()
        val outputFramesMax = outputFramesMax()
        val expectedOutputLen = ceil(resampleRatio() * inputLen.toDouble()).toInt()
        return delayFrames + outputFramesMax + expectedOutputLen
    }

    fun inputFramesMax(): Int

    fun inputFramesNext(): Int

    fun nbrChannels(): Int

    fun outputFramesMax(): Int

    fun outputFramesNext(): Int

    fun outputDelay(): Int

    fun resampleRatio(): Double

    fun reset()

    fun asAdjustable(): Adjustable<T>?

    fun isAdjustable(): Boolean = false

    fun asResizable(): Resizable<T>?

    fun isResizable(): Boolean = false
}

/**
 * A [Resampler] whose resample ratio can be changed after construction.
 */
internal interface Adjustable<T> : Resampler<T> {
    fun setResampleRatio(
        newRatio: Double,
        ramp: Boolean,
    ): ResampleResult<Unit>

    fun setResampleRatioRelative(
        relRatio: Double,
        ramp: Boolean,
    ): ResampleResult<Unit>
}

/**
 * A [Resampler] whose chunk size can be changed after construction.
 */
internal interface Resizable<T> : Resampler<T> {
    fun setChunkSize(chunksize: Int): ResampleResult<Unit>
}

private data class TrimState(
    val outputLen: Int,
    val framesToTrim: Int,
)

private fun <T> trimStartupDelay(
    bufferOut: AdapterMut<T>,
    indexing: Indexing,
    outputLen: Int,
    framesToTrim: Int,
): TrimState {
    if (framesToTrim == 0 || outputLen <= framesToTrim) {
        return TrimState(outputLen, framesToTrim)
    }
    bufferOut.copyFramesWithin(framesToTrim, 0, outputLen - framesToTrim)
    val newOutputLen = outputLen - framesToTrim
    indexing.outputOffset -= framesToTrim
    return TrimState(newOutputLen, 0)
}

internal fun <T> validateBuffers(
    waveIn: Adapter<T>,
    waveOut: AdapterMut<T>,
    channels: Int,
    minInputLen: Int,
    minOutputLen: Int,
): ResampleResult<Unit> {
    if (waveIn.channels() != channels) {
        return ResampleResult.Err(
            ResampleError.WrongNumberOfInputChannels(
                expected = channels,
                actual = waveIn.channels(),
            ),
        )
    }
    if (waveIn.frames() < minInputLen) {
        return ResampleResult.Err(
            ResampleError.InsufficientInputBufferSize(
                expected = minInputLen,
                actual = waveIn.frames(),
            ),
        )
    }
    if (waveOut.channels() != channels) {
        return ResampleResult.Err(
            ResampleError.WrongNumberOfOutputChannels(
                expected = channels,
                actual = waveOut.channels(),
            ),
        )
    }
    if (waveOut.frames() < minOutputLen) {
        return ResampleResult.Err(
            ResampleError.InsufficientOutputBufferSize(
                expected = minOutputLen,
                actual = waveOut.frames(),
            ),
        )
    }
    return ResampleResult.Ok(Unit)
}

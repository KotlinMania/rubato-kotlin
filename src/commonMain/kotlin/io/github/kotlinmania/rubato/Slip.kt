// port-lint: source rubato/src/slip.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.truncate

/**
 * Target length in frames of the crossfade that hides each slip.
 */
internal const val MAX_CROSSFADE_LEN: Int = 128

/**
 * Crossfade length actually used for a given chunk size.
 */
internal fun crossfadeLenFor(chunkSize: Int): Int =
    min((chunkSize - 2).coerceAtLeast(0) / 2, MAX_CROSSFADE_LEN)

/**
 * Largest number of corrections that fit in one chunk without the crossfades overlapping.
 */
internal fun maxCorrections(
    chunkSize: Int,
    crossfadeLen: Int,
): Int = (chunkSize - 1).coerceAtLeast(0) / (crossfadeLen + 2)

/**
 * The timing error, in frames, that one chunk adds to the accumulator.
 */
internal fun driftPerChunk(
    ratio: Double,
    chunkSize: Int,
    fixed: FixedAsync,
): Double =
    when (fixed) {
        FixedAsync.Input -> (ratio - 1.0) * chunkSize.toDouble()
        FixedAsync.Output -> (1.0 - 1.0 / ratio) * chunkSize.toDouble()
    }

/**
 * The range of resample ratios the resampler can sustain for a given chunk size and correction
 * capacity.
 */
internal fun ratioRange(
    maxCorrection: Int,
    chunkSize: Int,
    fixed: FixedAsync,
): Pair<Double, Double> {
    val f = maxCorrection.toDouble() / chunkSize.toDouble()
    return when (fixed) {
        FixedAsync.Input -> Pair(1.0 - f, 1.0 + f)
        FixedAsync.Output -> Pair(1.0 / (1.0 + f), 1.0 / (1.0 - f))
    }
}

/**
 * Copy `input` to `output`, inserting or dropping frames along the way as requested.
 */
internal fun <T> placeCorrection(
    sample: Sample<T>,
    input: List<T>,
    inputLen: Int,
    output: MutableList<T>,
    outputLen: Int,
    correction: Int,
    crossfadeLen: Int,
) {
    val l = crossfadeLen
    val n = abs(correction)
    if (n == 0) {
        val copyLen = min(outputLen, inputLen)
        for (i in 0 until copyLen) {
            output[i] = input[i]
        }
        return
    }
    val step = if (correction > 0) -1 else 1

    val gapTotal = outputLen - n * l
    val baseGap = gapTotal / (n + 1)
    val extra = gapTotal % (n + 1)

    var offset = 0
    var pos = 0
    for (r in 0 until n) {
        val gap = baseGap + if (r < extra) 1 else 0
        val src = pos + offset
        for (k in 0 until gap) {
            output[pos + k] = input[src + k]
        }
        pos += gap
        for (j in 0 until l) {
            val w = sample.coerceFromDouble((j.toDouble() + 0.5) / l.toDouble())
            val i = pos + offset
            val a = input[i]
            val b = input[i + step]
            output[pos] = sample.add(a, sample.mul(w, sample.sub(b, a)))
            pos += 1
        }
        offset += step
    }
    val src = pos + offset
    val rem = outputLen - pos
    for (k in 0 until rem) {
        output[pos + k] = input[src + k]
    }
}

/**
 * A clutch for matching two almost-equal sample rates, slipping a frame when needed.
 */
internal class Slip<T> private constructor(
    override val sample: Sample<T>,
    private val nbrChannels: Int,
    private var chunkSize: Int,
    private val maxChunkSize: Int,
    private var crossfadeLen: Int,
    private val maxCorrection: Int,
    private var neededInputSize: Int,
    private var neededOutputSize: Int,
    private var correction: Int,
    private var driftAcc: Double,
    private var resampleRatio: Double,
    private val inputScratch: MutableList<T>,
    private val outputScratch: MutableList<T>,
    private val channelMask: BooleanArray,
    private val fixed: FixedAsync,
) : Resampler<T>,
    Adjustable<T>,
    Resizable<T> {
    override fun toString(): String =
        "Slip(nbrChannels=$nbrChannels, chunkSize=$chunkSize, maxChunkSize=$maxChunkSize, " +
            "crossfadeLen=$crossfadeLen, maxCorrection=$maxCorrection, " +
            "neededInputSize=$neededInputSize, neededOutputSize=$neededOutputSize, " +
            "correction=$correction, driftAcc=$driftAcc, resampleRatio=$resampleRatio, " +
            "channelMask=${channelMask.contentToString()}, fixed=$fixed)"

    internal fun currentRatioRange(): Pair<Double, Double> =
        ratioRange(
            maxCorrections(chunkSize, crossfadeLen),
            chunkSize,
            fixed,
        )

    private fun replan() {
        val projected = driftAcc + driftPerChunk(resampleRatio, chunkSize, fixed)
        val cap = maxCorrections(chunkSize, crossfadeLen)
        correction = truncate(projected).toInt().coerceIn(-cap, cap)
        when (fixed) {
            FixedAsync.Input -> {
                neededInputSize = chunkSize
                neededOutputSize = chunkSize + correction
            }

            FixedAsync.Output -> {
                neededOutputSize = chunkSize
                neededInputSize = chunkSize - correction
            }
        }
    }

    override fun processIntoBuffer(
        bufferIn: Adapter<T>,
        bufferOut: AdapterMut<T>,
        indexing: Indexing?,
    ): ResampleResult<FrameCount> {
        val maskRes = updateMask(indexing, channelMask)
        if (maskRes is ResampleResult.Err) {
            return maskRes
        }
        val (inputOffset, outputOffset) = getOffsets(indexing)

        val partialInputLen = getPartialLen(indexing)
        val framesToRead =
            if (partialInputLen != null) {
                min(partialInputLen, neededInputSize)
            } else {
                neededInputSize
            }

        val valRes =
            validateBuffers(
                bufferIn,
                bufferOut,
                nbrChannels,
                framesToRead + inputOffset,
                neededOutputSize + outputOffset,
            )
        if (valRes is ResampleResult.Err) {
            return valRes
        }

        val inputLen = neededInputSize
        val outputLen = neededOutputSize
        val zero = sample.zero

        for (chan in 0 until nbrChannels) {
            if (!channelMask[chan]) {
                continue
            }
            if (correction == 0) {
                bufferIn.copyFromChannelToSlice(
                    chan,
                    inputOffset,
                    outputScratch,
                    framesToRead,
                )
                if (framesToRead < outputLen) {
                    for (i in framesToRead until outputLen) {
                        outputScratch[i] = zero
                    }
                }
            } else {
                bufferIn.copyFromChannelToSlice(
                    chan,
                    inputOffset,
                    inputScratch,
                    framesToRead,
                )
                if (framesToRead < inputLen) {
                    for (i in framesToRead until inputLen) {
                        inputScratch[i] = zero
                    }
                }
                placeCorrection(
                    sample = sample,
                    input = inputScratch,
                    inputLen = inputLen,
                    output = outputScratch,
                    outputLen = outputLen,
                    correction = correction,
                    crossfadeLen = crossfadeLen,
                )
            }
            bufferOut.copyFromSliceToChannel(
                chan,
                outputOffset,
                outputScratch,
                outputLen,
            )
        }

        driftAcc += driftPerChunk(resampleRatio, chunkSize, fixed)
        driftAcc -= correction.toDouble()
        replan()

        return ResampleResult.Ok(FrameCount(inputFrames = inputLen, outputFrames = outputLen))
    }

    override fun outputFramesMax(): Int =
        when (fixed) {
            FixedAsync.Input -> maxChunkSize + maxCorrection
            FixedAsync.Output -> maxChunkSize
        }

    override fun outputFramesNext(): Int = neededOutputSize

    override fun outputDelay(): Int = 0

    override fun nbrChannels(): Int = nbrChannels

    override fun inputFramesMax(): Int =
        when (fixed) {
            FixedAsync.Input -> maxChunkSize
            FixedAsync.Output -> maxChunkSize + maxCorrection
        }

    override fun inputFramesNext(): Int = neededInputSize

    override fun resampleRatio(): Double = resampleRatio

    override fun reset() {
        channelMask.fill(true)
        driftAcc = 0.0
        resampleRatio = 1.0
        chunkSize = maxChunkSize
        crossfadeLen = crossfadeLenFor(maxChunkSize)
        replan()
    }

    override fun asAdjustable(): Adjustable<T>? = this

    override fun isAdjustable(): Boolean = true

    override fun asResizable(): Resizable<T>? = this

    override fun isResizable(): Boolean = true

    override fun setResampleRatio(
        newRatio: Double,
        ramp: Boolean,
    ): ResampleResult<Unit> {
        val (min, max) = currentRatioRange()
        val inRange = (ramp || !ramp) && newRatio >= min && newRatio <= max
        resampleRatio =
            if (newRatio > max) {
                max
            } else if (newRatio < min || newRatio.isNaN()) {
                min
            } else {
                newRatio
            }
        replan()
        return if (!inRange) {
            ResampleResult.Err(
                ResampleError.RatioOutsideRange(
                    provided = newRatio,
                    min = min,
                    max = max,
                ),
            )
        } else {
            ResampleResult.Ok(Unit)
        }
    }

    override fun setResampleRatioRelative(
        relRatio: Double,
        ramp: Boolean,
    ): ResampleResult<Unit> = setResampleRatio(relRatio, ramp)

    override fun setChunkSize(chunksize: Int): ResampleResult<Unit> {
        val newCrossfadeLen = crossfadeLenFor(chunksize)
        if (chunksize > maxChunkSize || newCrossfadeLen == 0) {
            return ResampleResult.Err(
                ResampleError.InvalidChunkSize(
                    max = maxChunkSize,
                    requested = chunksize,
                ),
            )
        }
        chunkSize = chunksize
        crossfadeLen = newCrossfadeLen
        replan()
        return ResampleResult.Ok(Unit)
    }

    companion object {
        fun <T> new(
            sample: Sample<T>,
            chunkSize: Int,
            nbrChannels: Int,
            fixed: FixedAsync,
        ): ConstructionResult<Slip<T>> {
            val crossfadeLen = crossfadeLenFor(chunkSize)
            if (crossfadeLen == 0) {
                return ConstructionResult.Err(ResamplerConstructionError.InvalidChunkSize(chunkSize))
            }
            val maxCorrection = maxCorrections(chunkSize, crossfadeLen)
            val scratchLen = chunkSize + maxCorrection
            val zero = sample.zero
            val resampler =
                Slip(
                    sample = sample,
                    nbrChannels = nbrChannels,
                    chunkSize = chunkSize,
                    maxChunkSize = chunkSize,
                    crossfadeLen = crossfadeLen,
                    maxCorrection = maxCorrection,
                    neededInputSize = chunkSize,
                    neededOutputSize = chunkSize,
                    correction = 0,
                    driftAcc = 0.0,
                    resampleRatio = 1.0,
                    inputScratch = MutableList(scratchLen) { zero },
                    outputScratch = MutableList(scratchLen) { zero },
                    channelMask = BooleanArray(nbrChannels) { true },
                    fixed = fixed,
                )
            resampler.replan()
            return ConstructionResult.Ok(resampler)
        }
    }
}

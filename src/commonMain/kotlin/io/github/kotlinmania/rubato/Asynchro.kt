// port-lint: source rubato/src/asynchro.rs
package io.github.kotlinmania.rubato

import io.github.kotlinmania.rubato.sincinterpolator.AnyInterpolator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * An enum for specifying which side of an asynchronous resampler should be fixed size.
 * This is similar to [FixedSync] that is used for the synchronous resamplers.
 * The difference is asynchronous resamplers must allow one side to vary,
 * and can therefore not support the `Both` option.
 */
public enum class FixedAsync {
    /** Input size is fixed, output size varies. */
    Input,

    /** Output size is fixed, input size varies. */
    Output,
}

/**
 * Functions for making the scalar product with a sinc.
 */
internal interface InnerResampler<T> {
    /**
     * Make the scalar product between the waveform starting at `index` and the sinc of `subindex`.
     */
    fun process(
        index: Double,
        nbrFrames: Int,
        channelMask: BooleanArray,
        tRatio: Double,
        tRatioIncrement: Double,
        waveIn: List<MutableList<T>>,
        waveOut: AdapterMut<T>,
        outputOffset: Int,
    ): Double

    /**
     * Get interpolator length.
     */
    fun nbrPoints(): Int

    /**
     * Get initial value for lastIndex.
     */
    fun initLastIndex(): Double
}

internal data class SteppedIndex(
    val idx: Double,
    val tRatio: Double,
)

/**
 * Advance the interpolation index by one output frame.
 *
 * Returns `(newIdx, newTRatio)`.
 */
internal fun stepIndex(
    idx: Double,
    tRatio: Double,
    tRatioIncrement: Double,
): SteppedIndex {
    val newTRatio = tRatio + tRatioIncrement
    return SteppedIndex(idx + newTRatio, newTRatio)
}

private fun validateRatios(
    resampleRatio: Double,
    maxResampleRatioRelative: Double,
): ConstructionResult<Unit> {
    if (!resampleRatio.isFinite() || resampleRatio <= 0.0) {
        return ConstructionResult.Err(ResamplerConstructionError.InvalidRatio(resampleRatio))
    }
    if (!maxResampleRatioRelative.isFinite() || maxResampleRatioRelative < 1.0) {
        return ConstructionResult.Err(ResamplerConstructionError.InvalidRelativeRatio(maxResampleRatioRelative))
    }
    return ConstructionResult.Ok(Unit)
}

private fun sizeAsUsize(size: Double): Int {
    require(!size.isNaN() && size <= Int.MAX_VALUE.toDouble()) {
        "The combination of chunkSize, resampleRatio and maxResampleRatioRelative gives " +
            "a size of $size frames, which does not fit in a usize"
    }
    return size.toInt().coerceAtLeast(0)
}

/**
 * An asynchronous resampler that uses either polynomial or sinc interpolation.
 */
internal class Async<T> private constructor(
    override val sample: Sample<T>,
    private val nbrChannels: Int,
    private var chunkSize: Int,
    private val maxChunkSize: Int,
    private var neededInputSize: Int,
    private var neededOutputSize: Int,
    private var lastIndex: Double,
    private var currentBufferFill: Int,
    private var resampleRatio: Double,
    private val resampleRatioOriginal: Double,
    private var targetRatio: Double,
    private val maxRelativeRatio: Double,
    private val buffer: List<MutableList<T>>,
    private val innerResampler: InnerResampler<T>,
    private val channelMask: BooleanArray,
    private val fixed: FixedAsync,
    private val sincCutoff: Float?,
) : Resampler<T>,
    Adjustable<T>,
    Resizable<T> {
    override fun toString(): String =
        "Async(nbrChannels=$nbrChannels, chunkSize=$chunkSize, maxChunkSize=$maxChunkSize, " +
            "neededInputSize=$neededInputSize, neededOutputSize=$neededOutputSize, " +
            "lastIndex=$lastIndex, currentBufferFill=$currentBufferFill, " +
            "resampleRatio=$resampleRatio, resampleRatioOriginal=$resampleRatioOriginal, " +
            "targetRatio=$targetRatio, maxRelativeRatio=$maxRelativeRatio, " +
            "bufferLen=${buffer.firstOrNull()?.size ?: 0}, " +
            "channelMask=${channelMask.contentToString()}, fixed=$fixed)"

    /**
     * The relative cutoff frequency of the sinc anti-aliasing filter,
     * or `null` if this resampler uses polynomial interpolation.
     */
    fun cutoff(): Float? = sincCutoff

    private fun updateLengths() {
        neededInputSize =
            calculateInputSize(
                chunkSize,
                resampleRatio,
                targetRatio,
                lastIndex,
                innerResampler.nbrPoints(),
                fixed,
            )
        neededOutputSize =
            calculateOutputSize(
                chunkSize,
                resampleRatio,
                targetRatio,
                lastIndex,
                innerResampler.nbrPoints(),
                fixed,
            )
    }

    private fun relativeRatioInBounds(relRatio: Double): Boolean =
        relRatio >= 1.0 / maxRelativeRatio && relRatio <= maxRelativeRatio

    private fun applyRatio(
        newRatio: Double,
        ramp: Boolean,
    ) {
        if (!ramp) {
            resampleRatio = newRatio
        }
        targetRatio = newRatio
        updateLengths()
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

        val interpolatorLen = innerResampler.nbrPoints()
        val tRatio = 1.0 / resampleRatio
        val tRatioIncrement =
            computeTRatioIncrement(
                resampleRatio,
                targetRatio,
                neededOutputSize,
            )

        val overlapLen = 2 * interpolatorLen
        for (buf in buffer) {
            for (i in 0 until overlapLen) {
                buf[i] = buf[currentBufferFill + i]
            }
        }

        for (chan in 0 until nbrChannels) {
            if (channelMask[chan]) {
                bufferIn.copyFromChannelToSlice(
                    chan,
                    inputOffset,
                    buffer[chan].subList(overlapLen, overlapLen + framesToRead),
                    framesToRead,
                )
                if (framesToRead < neededInputSize) {
                    val zero = sample.zero
                    val chBuf = buffer[chan]
                    for (i in (overlapLen + framesToRead) until (overlapLen + neededInputSize)) {
                        chBuf[i] = zero
                    }
                }
            }
        }

        currentBufferFill = neededInputSize

        var idx = lastIndex
        idx =
            innerResampler.process(
                idx,
                neededOutputSize,
                channelMask,
                tRatio,
                tRatioIncrement,
                buffer,
                bufferOut,
                outputOffset,
            )

        lastIndex = idx - neededInputSize.toDouble()
        resampleRatio = targetRatio
        val inputSize = neededInputSize
        val outputSize = neededOutputSize
        updateLengths()
        return ResampleResult.Ok(FrameCount(inputFrames = inputSize, outputFrames = outputSize))
    }

    override fun outputFramesMax(): Int =
        calculateMaxOutputSize(
            maxChunkSize,
            resampleRatioOriginal,
            maxRelativeRatio,
            fixed,
        )

    override fun outputFramesNext(): Int = neededOutputSize

    override fun outputDelay(): Int =
        (innerResampler.nbrPoints().toDouble() * resampleRatio / 2.0).toInt()

    override fun nbrChannels(): Int = nbrChannels

    override fun inputFramesMax(): Int =
        calculateMaxInputSize(
            maxChunkSize,
            resampleRatioOriginal,
            maxRelativeRatio,
            innerResampler.nbrPoints(),
            fixed,
        )

    override fun inputFramesNext(): Int = neededInputSize

    override fun resampleRatio(): Double = resampleRatio

    override fun reset() {
        val zero = sample.zero
        for (ch in buffer) {
            for (i in ch.indices) {
                ch[i] = zero
            }
        }
        channelMask.fill(true)
        lastIndex = innerResampler.initLastIndex()
        resampleRatio = resampleRatioOriginal
        targetRatio = resampleRatioOriginal
        chunkSize = maxChunkSize
        updateLengths()
    }

    override fun asAdjustable(): Adjustable<T>? = this

    override fun isAdjustable(): Boolean = true

    override fun asResizable(): Resizable<T>? = this

    override fun isResizable(): Boolean = true

    override fun setResampleRatio(
        newRatio: Double,
        ramp: Boolean,
    ): ResampleResult<Unit> =
        if (relativeRatioInBounds(newRatio / resampleRatioOriginal)) {
            applyRatio(newRatio, ramp)
            ResampleResult.Ok(Unit)
        } else {
            ResampleResult.Err(
                ResampleError.RatioOutOfBounds(
                    provided = newRatio,
                    original = resampleRatioOriginal,
                    maxRelativeRatio = maxRelativeRatio,
                ),
            )
        }

    override fun setResampleRatioRelative(
        relRatio: Double,
        ramp: Boolean,
    ): ResampleResult<Unit> {
        val newRatio = resampleRatioOriginal * relRatio
        return if (newRatio.isFinite() && newRatio > 0.0 && relativeRatioInBounds(relRatio)) {
            applyRatio(newRatio, ramp)
            ResampleResult.Ok(Unit)
        } else {
            ResampleResult.Err(
                ResampleError.RatioOutOfBounds(
                    provided = newRatio,
                    original = resampleRatioOriginal,
                    maxRelativeRatio = maxRelativeRatio,
                ),
            )
        }
    }

    override fun setChunkSize(chunksize: Int): ResampleResult<Unit> {
        if (chunksize > maxChunkSize || chunksize <= 0) {
            return ResampleResult.Err(
                ResampleError.InvalidChunkSize(
                    max = maxChunkSize,
                    requested = chunksize,
                ),
            )
        }
        chunkSize = chunksize
        updateLengths()
        return ResampleResult.Ok(Unit)
    }

    companion object {
        fun <T> newPoly(
            sample: Sample<T>,
            resampleRatio: Double,
            maxResampleRatioRelative: Double,
            interpolationType: PolynomialDegree,
            chunkSize: Int,
            nbrChannels: Int,
            fixed: FixedAsync,
        ): ConstructionResult<Async<T>> {
            val ratioCheck = validateRatios(resampleRatio, maxResampleRatioRelative)
            if (ratioCheck is ConstructionResult.Err) {
                return ratioCheck
            }
            if (chunkSize <= 0) {
                return ConstructionResult.Err(ResamplerConstructionError.InvalidChunkSize(chunkSize))
            }

            val channelMask = BooleanArray(nbrChannels) { true }
            val interpolatorLen = interpolationType.nbrPoints()
            val innerResampler = InnerPoly(interpolationType, sample)
            val lastIndex = innerResampler.initLastIndex()
            val neededInputSize =
                calculateInputSize(
                    chunkSize,
                    resampleRatio,
                    resampleRatio,
                    lastIndex,
                    interpolatorLen,
                    fixed,
                )
            val neededOutputSize =
                calculateOutputSize(
                    chunkSize,
                    resampleRatio,
                    resampleRatio,
                    lastIndex,
                    interpolatorLen,
                    fixed,
                )
            val maxInputSize =
                calculateMaxInputSize(
                    chunkSize,
                    resampleRatio,
                    maxResampleRatioRelative,
                    interpolatorLen,
                    fixed,
                )
            val bufferLen =
                (maxInputSize.toLong() + 2L * interpolatorLen.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            val zero = sample.zero
            val buffer = List(nbrChannels) { MutableList(bufferLen) { zero } }

            return ConstructionResult.Ok(
                Async(
                    sample = sample,
                    nbrChannels = nbrChannels,
                    chunkSize = chunkSize,
                    maxChunkSize = chunkSize,
                    neededInputSize = neededInputSize,
                    neededOutputSize = neededOutputSize,
                    lastIndex = lastIndex,
                    currentBufferFill = neededInputSize,
                    resampleRatio = resampleRatio,
                    resampleRatioOriginal = resampleRatio,
                    targetRatio = resampleRatio,
                    maxRelativeRatio = maxResampleRatioRelative,
                    buffer = buffer,
                    innerResampler = innerResampler,
                    channelMask = channelMask,
                    fixed = fixed,
                    sincCutoff = null,
                ),
            )
        }

        fun <T> newSinc(
            sample: Sample<T>,
            resampleRatio: Double,
            maxResampleRatioRelative: Double,
            parameters: SincInterpolationParameters,
            chunkSize: Int,
            nbrChannels: Int,
            fixed: FixedAsync,
        ): ConstructionResult<Async<T>> {
            val interpolator =
                makeInterpolator(
                    parameters.sincLen,
                    resampleRatio,
                    parameters.fCutoff,
                    parameters.oversamplingFactor,
                    parameters.window,
                    sample,
                )
            return newWithSincInterpolator(
                sample = sample,
                resampleRatio = resampleRatio,
                maxResampleRatioRelative = maxResampleRatioRelative,
                interpolationType = parameters.interpolation,
                interpolator = interpolator,
                fCutoff =
                    resolveCutoff(
                        parameters.sincLen,
                        resampleRatio,
                        parameters.fCutoff,
                        parameters.window,
                    ),
                chunkSize = chunkSize,
                nbrChannels = nbrChannels,
                fixed = fixed,
            )
        }

        fun <T> newWithSincInterpolator(
            sample: Sample<T>,
            resampleRatio: Double,
            maxResampleRatioRelative: Double,
            interpolationType: SincInterpolationType,
            interpolator: AnyInterpolator<T>,
            fCutoff: Float,
            chunkSize: Int,
            nbrChannels: Int,
            fixed: FixedAsync,
        ): ConstructionResult<Async<T>> {
            val ratioCheck = validateRatios(resampleRatio, maxResampleRatioRelative)
            if (ratioCheck is ConstructionResult.Err) {
                return ratioCheck
            }
            if (chunkSize <= 0) {
                return ConstructionResult.Err(ResamplerConstructionError.InvalidChunkSize(chunkSize))
            }

            val interpolatorLen = interpolator.nbrPoints()
            val innerResampler = InnerSinc(interpolator, interpolationType)
            val lastIndex = innerResampler.initLastIndex()
            val neededInputSize =
                calculateInputSize(
                    chunkSize,
                    resampleRatio,
                    resampleRatio,
                    lastIndex,
                    interpolatorLen,
                    fixed,
                )
            val neededOutputSize =
                calculateOutputSize(
                    chunkSize,
                    resampleRatio,
                    resampleRatio,
                    lastIndex,
                    interpolatorLen,
                    fixed,
                )
            val maxInputSize =
                calculateMaxInputSize(
                    chunkSize,
                    resampleRatio,
                    maxResampleRatioRelative,
                    interpolatorLen,
                    fixed,
                )
            val bufferLen =
                (maxInputSize.toLong() + 2L * interpolatorLen.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            val zero = sample.zero
            val buffer = List(nbrChannels) { MutableList(bufferLen) { zero } }
            val channelMask = BooleanArray(nbrChannels) { true }

            return ConstructionResult.Ok(
                Async(
                    sample = sample,
                    nbrChannels = nbrChannels,
                    chunkSize = chunkSize,
                    maxChunkSize = chunkSize,
                    neededInputSize = neededInputSize,
                    neededOutputSize = neededOutputSize,
                    lastIndex = lastIndex,
                    currentBufferFill = neededInputSize,
                    resampleRatio = resampleRatio,
                    resampleRatioOriginal = resampleRatio,
                    targetRatio = resampleRatio,
                    maxRelativeRatio = maxResampleRatioRelative,
                    buffer = buffer,
                    innerResampler = innerResampler,
                    channelMask = channelMask,
                    fixed = fixed,
                    sincCutoff = fCutoff,
                ),
            )
        }

        internal fun avgTRatio(
            resampleRatio: Double,
            targetRatio: Double,
        ): Double = 0.5 * (1.0 / resampleRatio + 1.0 / targetRatio)

        internal fun computeTRatioIncrement(
            resampleRatio: Double,
            targetRatio: Double,
            nbrFrames: Int,
        ): Double = (1.0 / targetRatio - 1.0 / resampleRatio) / nbrFrames.toDouble()

        internal fun advanceIndex(
            startIdx: Double,
            startTRatio: Double,
            tRatioIncrement: Double,
            nbrFrames: Int,
        ): Double {
            var idx = startIdx
            var tRatio = startTRatio
            repeat(nbrFrames) {
                val next = stepIndex(idx, tRatio, tRatioIncrement)
                idx = next.idx
                tRatio = next.tRatio
            }
            return idx
        }

        internal fun calculateInputSize(
            chunkSize: Int,
            resampleRatio: Double,
            targetRatio: Double,
            lastIndex: Double,
            interpolatorLen: Int,
            fixed: FixedAsync,
        ): Int =
            when (fixed) {
                FixedAsync.Input -> {
                    chunkSize
                }

                FixedAsync.Output -> {
                    val rampOvershoot = 0.5 * (1.0 / targetRatio - 1.0 / resampleRatio)
                    sizeAsUsize(
                        ceil(
                            lastIndex +
                                chunkSize.toDouble() * avgTRatio(resampleRatio, targetRatio) +
                                rampOvershoot +
                                interpolatorLen.toDouble(),
                        ),
                    )
                }
            }

        internal fun calculateOutputSize(
            chunkSize: Int,
            resampleRatio: Double,
            targetRatio: Double,
            lastIndex: Double,
            interpolatorLen: Int,
            fixed: FixedAsync,
        ): Int =
            when (fixed) {
                FixedAsync.Output -> {
                    chunkSize
                }

                FixedAsync.Input -> {
                    val space = chunkSize.toDouble() - (interpolatorLen + 1).toDouble() - lastIndex
                    val rampOvershoot = 0.5 * (1.0 / targetRatio - 1.0 / resampleRatio)
                    sizeAsUsize(
                        floor((space - rampOvershoot) / avgTRatio(resampleRatio, targetRatio)),
                    )
                }
            }

        internal fun calculateMaxInputSize(
            chunkSize: Int,
            resampleRatioOriginal: Double,
            maxRelativeRatio: Double,
            interpolatorLen: Int,
            fixed: FixedAsync,
        ): Int {
            val size =
                when (fixed) {
                    FixedAsync.Input -> {
                        chunkSize.toDouble()
                    }

                    FixedAsync.Output -> {
                        ceil(chunkSize.toDouble() / resampleRatioOriginal * maxRelativeRatio) +
                            2.0 +
                            (interpolatorLen / 2).toDouble()
                    }
                }
            return sizeAsUsize(size)
        }

        internal fun calculateMaxOutputSize(
            chunkSize: Int,
            resampleRatioOriginal: Double,
            maxRelativeRatio: Double,
            fixed: FixedAsync,
        ): Int {
            val size =
                when (fixed) {
                    FixedAsync.Output -> {
                        chunkSize.toDouble()
                    }

                    FixedAsync.Input -> {
                        chunkSize.toDouble() * resampleRatioOriginal * maxRelativeRatio + 10.0
                    }
                }
            return sizeAsUsize(size)
        }
    }
}

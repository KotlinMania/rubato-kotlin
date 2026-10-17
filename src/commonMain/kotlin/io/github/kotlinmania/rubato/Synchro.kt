// port-lint: source rubato/src/synchro.rs
package io.github.kotlinmania.rubato

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * An enum for specifying which side of a synchronous resampler should be fixed size.
 * This is similar to [FixedAsync] that is used for the asynchronous resamplers.
 * The difference is asynchronous resamplers must allow one side to vary,
 * and can therefore not support the `Both` option.
 */
public enum class FixedSync {
    /** Input size is fixed, output size varies. */
    Input,

    /** Output size is fixed, input size varies. */
    Output,

    /** Both input and output sizes are fixed. */
    Both,
}

private fun gcd(
    a: Int,
    b: Int,
): Int {
    var x = a
    var y = b
    while (y != 0) {
        val t = y
        y = x % y
        x = t
    }
    return x
}

private fun divCeil(
    a: Int,
    b: Int,
): Int = (a + b - 1) / b

private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

private fun nextPowerOfTwo(n: Int): Int {
    var p = 1
    while (p < n) {
        p = p shl 1
    }
    return p
}

/**
 * Precomputed complex DFT plan for arbitrary length `n`, using radix-2 Cooley-Tukey
 * when `n` is a power of two and Bluestein's chirp-Z algorithm otherwise.
 */
internal class ComplexFftPlan(
    val n: Int,
) {
    private val isPow2: Boolean = isPowerOfTwo(n)
    private val pow2Len: Int = if (isPow2) n else nextPowerOfTwo(2 * n - 1)
    private val bitRev: IntArray = computeBitReversal(pow2Len)
    private val twiddleRe: DoubleArray
    private val twiddleIm: DoubleArray

    // Bluestein precomputed chirp and transformed kernel (for non-power-of-two n)
    private val chirpRe: DoubleArray
    private val chirpIm: DoubleArray
    private val kernelFftRe: DoubleArray
    private val kernelFftIm: DoubleArray
    private val workARe: DoubleArray
    private val workAIm: DoubleArray

    init {
        twiddleRe = DoubleArray(pow2Len)
        twiddleIm = DoubleArray(pow2Len)
        var half = 1
        while (half < pow2Len) {
            val angleStep = -PI / half.toDouble()
            for (k in 0 until half) {
                val angle = angleStep * k.toDouble()
                twiddleRe[half + k] = cos(angle)
                twiddleIm[half + k] = sin(angle)
            }
            half = half shl 1
        }

        if (!isPow2 && n > 1) {
            chirpRe = DoubleArray(n)
            chirpIm = DoubleArray(n)
            kernelFftRe = DoubleArray(pow2Len)
            kernelFftIm = DoubleArray(pow2Len)
            workARe = DoubleArray(pow2Len)
            workAIm = DoubleArray(pow2Len)

            for (k in 0 until n) {
                val rem = ((k.toLong() * k.toLong()) % (2L * n.toLong())).toDouble()
                val angle = -PI * rem / n.toDouble()
                val c = cos(angle)
                val s = sin(angle)
                chirpRe[k] = c
                chirpIm[k] = s
                kernelFftRe[k] = c
                kernelFftIm[k] = -s
                if (k > 0) {
                    kernelFftRe[pow2Len - k] = c
                    kernelFftIm[pow2Len - k] = -s
                }
            }
            radix2Forward(kernelFftRe, kernelFftIm)
        } else {
            chirpRe = DoubleArray(0)
            chirpIm = DoubleArray(0)
            kernelFftRe = DoubleArray(0)
            kernelFftIm = DoubleArray(0)
            workARe = DoubleArray(0)
            workAIm = DoubleArray(0)
        }
    }

    private fun computeBitReversal(size: Int): IntArray {
        val rev = IntArray(size)
        var j = 0
        for (i in 0 until size) {
            rev[i] = j
            var bit = size shr 1
            while (bit > 0 && (j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
        }
        return rev
    }

    private fun radix2Forward(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        val m = pow2Len
        for (i in 0 until m) {
            val j = bitRev[i]
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }
        var half = 1
        while (half < m) {
            val len = half shl 1
            var i = 0
            while (i < m) {
                for (k in 0 until half) {
                    val wr = twiddleRe[half + k]
                    val wi = twiddleIm[half + k]
                    val uIdx = i + k
                    val vIdx = uIdx + half
                    val vr = re[vIdx] * wr - im[vIdx] * wi
                    val vi = re[vIdx] * wi + im[vIdx] * wr
                    re[vIdx] = re[uIdx] - vr
                    im[vIdx] = im[uIdx] - vi
                    re[uIdx] += vr
                    im[uIdx] += vi
                }
                i += len
            }
            half = len
        }
    }

    private fun radix2Inverse(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        for (i in 0 until pow2Len) {
            im[i] = -im[i]
        }
        radix2Forward(re, im)
        val scale = 1.0 / pow2Len.toDouble()
        for (i in 0 until pow2Len) {
            re[i] *= scale
            im[i] = -im[i] * scale
        }
    }

    /**
     * In-place unnormalized forward complex DFT of length `n`.
     */
    fun processForward(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        if (n <= 1) return
        if (isPow2) {
            radix2Forward(re, im)
            return
        }
        for (k in 0 until n) {
            val cr = chirpRe[k]
            val ci = chirpIm[k]
            workARe[k] = re[k] * cr - im[k] * ci
            workAIm[k] = re[k] * ci + im[k] * cr
        }
        for (k in n until pow2Len) {
            workARe[k] = 0.0
            workAIm[k] = 0.0
        }
        radix2Forward(workARe, workAIm)
        for (k in 0 until pow2Len) {
            val ar = workARe[k]
            val ai = workAIm[k]
            val br = kernelFftRe[k]
            val bi = kernelFftIm[k]
            workARe[k] = ar * br - ai * bi
            workAIm[k] = ar * bi + ai * br
        }
        radix2Inverse(workARe, workAIm)
        for (k in 0 until n) {
            val cr = chirpRe[k]
            val ci = chirpIm[k]
            val wr = workARe[k]
            val wi = workAIm[k]
            re[k] = wr * cr - wi * ci
            im[k] = wr * ci + wi * cr
        }
    }

    /**
     * In-place unnormalized inverse complex DFT of length `n`.
     */
    fun processInverse(
        re: DoubleArray,
        im: DoubleArray,
    ) {
        if (n <= 1) return
        for (k in 0 until n) {
            im[k] = -im[k]
        }
        processForward(re, im)
        for (k in 0 until n) {
            im[k] = -im[k]
        }
    }
}

/**
 * Real-to-complex and complex-to-real FFT plan for even length `2 * halfLen`,
 * matching Rust `realfft` unnormalized conventions.
 */
internal class EvenRealFftPlan(
    val halfLen: Int,
) {
    private val complexPlan = ComplexFftPlan(halfLen)
    private val zRe = DoubleArray(halfLen)
    private val zIm = DoubleArray(halfLen)
    private val unpackCos = DoubleArray(halfLen)
    private val unpackSin = DoubleArray(halfLen)

    init {
        for (k in 0 until halfLen) {
            val angle = -PI * k.toDouble() / halfLen.toDouble()
            unpackCos[k] = cos(angle)
            unpackSin[k] = sin(angle)
        }
    }

    /**
     * Unnormalized forward R2C DFT of `input` (length `2 * halfLen`) into
     * `(outRe, outIm)` (each of length `halfLen + 1`).
     */
    fun forward(
        input: DoubleArray,
        outRe: DoubleArray,
        outIm: DoubleArray,
    ) {
        val m = halfLen
        for (n in 0 until m) {
            zRe[n] = input[2 * n]
            zIm[n] = input[2 * n + 1]
        }
        complexPlan.processForward(zRe, zIm)

        val dcRe = zRe[0]
        val dcIm = zIm[0]
        outRe[0] = dcRe + dcIm
        outIm[0] = 0.0
        outRe[m] = dcRe - dcIm
        outIm[m] = 0.0

        for (k in 1 until m) {
            val mk = m - k
            val zkRe = zRe[k]
            val zkIm = zIm[k]
            val zmkRe = zRe[mk]
            val zmkIm = -zIm[mk]

            val eRe = 0.5 * (zkRe + zmkRe)
            val eIm = 0.5 * (zkIm + zmkIm)
            val oRe = 0.5 * (zkIm - zmkIm)
            val oIm = -0.5 * (zkRe - zmkRe)

            val wr = unpackCos[k]
            val wi = unpackSin[k]
            val woRe = oRe * wr - oIm * wi
            val woIm = oRe * wi + oIm * wr

            outRe[k] = eRe + woRe
            outIm[k] = eIm + woIm
        }
    }

    /**
     * Unnormalized inverse C2R DFT of `(inRe, inIm)` (length `halfLen + 1`) into
     * `output` (length `2 * halfLen`).
     */
    fun inverse(
        inRe: DoubleArray,
        inIm: DoubleArray,
        output: DoubleArray,
    ) {
        val m = halfLen
        val x0 = inRe[0]
        val xm = inRe[m]
        zRe[0] = x0 + xm
        zIm[0] = x0 - xm

        for (k in 1 until m) {
            val mk = m - k
            val xkRe = inRe[k]
            val xkIm = inIm[k]
            val xmkRe = inRe[mk]
            val xmkIm = -inIm[mk]

            val sumRe = xkRe + xmkRe
            val sumIm = xkIm + xmkIm
            val diffRe = xkRe - xmkRe
            val diffIm = xkIm - xmkIm

            val wr = unpackCos[k]
            val wi = -unpackSin[k]
            val rotRe = diffRe * wr - diffIm * wi
            val rotIm = diffRe * wi + diffIm * wr

            zRe[k] = sumRe - rotIm
            zIm[k] = sumIm + rotRe
        }

        complexPlan.processInverse(zRe, zIm)

        for (n in 0 until m) {
            output[2 * n] = zRe[n]
            output[2 * n + 1] = zIm[n]
        }
    }
}

/**
 * A helper for resampling a single chunk of data.
 */
internal class FftResampler<T>(
    private val sample: Sample<T>,
    val fftSizeIn: Int,
    val fftSizeOut: Int,
    window: WindowFunction,
) {
    val cutoff: Float
    private val filterFRe: DoubleArray
    private val filterFIm: DoubleArray
    private val fftPlan: EvenRealFftPlan
    private val ifftPlan: EvenRealFftPlan
    private val inputBuf: DoubleArray
    private val inputFRe: DoubleArray
    private val inputFIm: DoubleArray
    private val outputFRe: DoubleArray
    private val outputFIm: DoubleArray
    private val outputBuf: DoubleArray

    init {
        cutoff =
            if (fftSizeIn > fftSizeOut) {
                calculateCutoff(fftSizeOut, window, F32Sample) * fftSizeOut.toFloat() / fftSizeIn.toFloat()
            } else {
                calculateCutoff(fftSizeIn, window, F32Sample)
            }
        val sinc = makeSincs(fftSizeIn, 1, cutoff, window, sample)
        val filterT = DoubleArray(2 * fftSizeIn)
        filterFRe = DoubleArray(fftSizeIn + 1)
        filterFIm = DoubleArray(fftSizeIn + 1)
        val scale = 1.0 / (2 * fftSizeIn).toDouble()
        for (n in 0 until fftSizeIn) {
            filterT[n] = sample.toDouble(sinc[0][n]) * scale
        }

        inputFRe = DoubleArray(fftSizeIn + 1)
        inputFIm = DoubleArray(fftSizeIn + 1)
        inputBuf = DoubleArray(2 * fftSizeIn)
        outputFRe = DoubleArray(fftSizeOut + 1)
        outputFIm = DoubleArray(fftSizeOut + 1)
        outputBuf = DoubleArray(2 * fftSizeOut)

        fftPlan = EvenRealFftPlan(fftSizeIn)
        ifftPlan = EvenRealFftPlan(fftSizeOut)
        fftPlan.forward(filterT, filterFRe, filterFIm)
    }

    /**
     * Resample a small chunk.
     */
    fun resampleUnit(
        waveIn: List<T>,
        waveInOffset: Int,
        waveOut: MutableList<T>,
        waveOutOffset: Int,
        overlap: MutableList<T>,
    ) {
        for (i in 0 until fftSizeIn) {
            inputBuf[i] = sample.toDouble(waveIn[waveInOffset + i])
        }
        for (i in fftSizeIn until 2 * fftSizeIn) {
            inputBuf[i] = 0.0
        }

        fftPlan.forward(inputBuf, inputFRe, inputFIm)

        val newLen =
            if (fftSizeIn < fftSizeOut) {
                fftSizeIn + 1
            } else {
                fftSizeOut
            }

        for (i in 0 until newLen) {
            val sr = inputFRe[i]
            val si = inputFIm[i]
            val fr = filterFRe[i]
            val fi = filterFIm[i]
            outputFRe[i] = sr * fr - si * fi
            outputFIm[i] = sr * fi + si * fr
        }
        for (i in newLen..fftSizeOut) {
            outputFRe[i] = 0.0
            outputFIm[i] = 0.0
        }

        ifftPlan.inverse(outputFRe, outputFIm, outputBuf)

        for (n in 0 until fftSizeOut) {
            val outSample = sample.coerceFromDouble(outputBuf[n])
            waveOut[waveOutOffset + n] = sample.add(outSample, overlap[n])
            overlap[n] = sample.coerceFromDouble(outputBuf[fftSizeOut + n])
        }
    }
}

private fun validateSampleRates(
    input: Int,
    output: Int,
): ConstructionResult<Unit> {
    if (input <= 0 || output <= 0) {
        return ConstructionResult.Err(ResamplerConstructionError.InvalidSampleRate(input = input, output = output))
    }
    return ConstructionResult.Ok(Unit)
}

/**
 * A synchronous resampler that uses FFT.
 *
 * The resampling is done by FFT:ing the input data. The spectrum is then extended or
 * truncated as well as multiplied with an antialiasing filter
 * before it's inverse transformed to get the resampled waveforms.
 */
internal class Fft<T> private constructor(
    override val sample: Sample<T>,
    private val nbrChannels: Int,
    private var chunkSizeIn: Int,
    private var chunkSizeOut: Int,
    private val fftSizeInVal: Int,
    private val fftSizeOutVal: Int,
    private val overlaps: List<MutableList<T>>,
    private val inputScratch: List<MutableList<T>>,
    private val outputScratch: List<MutableList<T>>,
    private val channelMask: BooleanArray,
    private var savedFrames: Int,
    private val resampler: FftResampler<T>,
    private val fixed: FixedSync,
) : Resampler<T> {
    override fun toString(): String =
        "Fft(nbrChannels=$nbrChannels, chunkSizeIn=$chunkSizeIn, chunkSizeOut=$chunkSizeOut, " +
            "fftSizeIn=$fftSizeInVal, fftSizeOut=$fftSizeOutVal, " +
            "overlapsLen=${overlaps.firstOrNull()?.size ?: 0}, " +
            "inputScratchLen=${inputScratch.firstOrNull()?.size ?: 0}, " +
            "outputScratchLen=${outputScratch.firstOrNull()?.size ?: 0}, " +
            "channelMask=${channelMask.contentToString()}, savedFrames=$savedFrames, fixed=$fixed)"

    /**
     * The FFT block size on the input side, in frames.
     */
    fun fftSizeIn(): Int = fftSizeInVal

    /**
     * The FFT block size on the output side, in frames.
     */
    fun fftSizeOut(): Int = fftSizeOutVal

    /**
     * The relative cutoff frequency of the anti-aliasing filter.
     */
    fun cutoff(): Float = resampler.cutoff

    private fun updateChunkSizes() {
        when (fixed) {
            FixedSync.Input -> {
                val (inSz, outSz) =
                    calcChunkSizes(
                        fftSizeInVal,
                        fftSizeOutVal,
                        chunkSizeIn,
                        savedFrames,
                        fixed,
                    )
                chunkSizeIn = inSz
                chunkSizeOut = outSz
            }

            FixedSync.Output -> {
                val (inSz, outSz) =
                    calcChunkSizes(
                        fftSizeInVal,
                        fftSizeOutVal,
                        chunkSizeOut,
                        savedFrames,
                        fixed,
                    )
                chunkSizeIn = inSz
                chunkSizeOut = outSz
            }

            FixedSync.Both -> {}
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
                min(partialInputLen, chunkSizeIn)
            } else {
                chunkSizeIn
            }

        val valRes =
            validateBuffers(
                bufferIn,
                bufferOut,
                nbrChannels,
                framesToRead + inputOffset,
                chunkSizeOut + outputOffset,
            )
        if (valRes is ResampleResult.Err) {
            return valRes
        }

        val zero = sample.zero
        val (subchunksToProcess, outputScratchOffset) =
            when (fixed) {
                FixedSync.Input -> {
                    val availableInputFrames = savedFrames + chunkSizeIn
                    val nbrChunksReady = chunkSizeOut / fftSizeOutVal
                    val inputFramesToProcess = nbrChunksReady * fftSizeInVal

                    for (chan in 0 until nbrChannels) {
                        if (channelMask[chan]) {
                            bufferIn.copyFromChannelToSlice(
                                chan,
                                inputOffset,
                                inputScratch[chan].subList(savedFrames, savedFrames + framesToRead),
                                framesToRead,
                            )
                            if (framesToRead < chunkSizeIn) {
                                val chBuf = inputScratch[chan]
                                for (i in (savedFrames + framesToRead) until (savedFrames + chunkSizeIn)) {
                                    chBuf[i] = zero
                                }
                            }
                        }
                    }
                    savedFrames = availableInputFrames - inputFramesToProcess
                    Pair(nbrChunksReady, 0)
                }

                FixedSync.Output, FixedSync.Both -> {
                    for (chan in 0 until nbrChannels) {
                        if (channelMask[chan]) {
                            bufferIn.copyFromChannelToSlice(
                                chan,
                                inputOffset,
                                inputScratch[chan],
                                framesToRead,
                            )
                            if (framesToRead < chunkSizeIn) {
                                val chBuf = inputScratch[chan]
                                for (i in framesToRead until chunkSizeIn) {
                                    chBuf[i] = zero
                                }
                            }
                        }
                    }
                    Pair(chunkSizeIn / fftSizeInVal, savedFrames)
                }
            }

        for (chan in 0 until nbrChannels) {
            if (channelMask[chan]) {
                val inChan = inputScratch[chan]
                val outChan = outputScratch[chan]
                val overlapChan = overlaps[chan]
                for (sub in 0 until subchunksToProcess) {
                    resampler.resampleUnit(
                        waveIn = inChan,
                        waveInOffset = sub * fftSizeInVal,
                        waveOut = outChan,
                        waveOutOffset = outputScratchOffset + sub * fftSizeOutVal,
                        overlap = overlapChan,
                    )
                }
            }
        }

        for (chan in 0 until nbrChannels) {
            if (channelMask[chan]) {
                bufferOut.copyFromSliceToChannel(
                    chan,
                    outputOffset,
                    outputScratch[chan],
                    chunkSizeOut,
                )
            }
        }

        when (fixed) {
            FixedSync.Input -> {
                val nbrInputFramesUsed = subchunksToProcess * fftSizeInVal
                for (chan in 0 until nbrChannels) {
                    if (channelMask[chan]) {
                        val chBuf = inputScratch[chan]
                        for (i in 0 until savedFrames) {
                            chBuf[i] = chBuf[nbrInputFramesUsed + i]
                        }
                    }
                }
            }

            FixedSync.Output -> {
                val availableOutputFrames = savedFrames + fftSizeOutVal * subchunksToProcess
                savedFrames = availableOutputFrames - chunkSizeOut
                for (chan in 0 until nbrChannels) {
                    if (channelMask[chan]) {
                        val chBuf = outputScratch[chan]
                        for (i in 0 until savedFrames) {
                            chBuf[i] = chBuf[chunkSizeOut + i]
                        }
                    }
                }
            }

            FixedSync.Both -> {}
        }

        val inputSize = chunkSizeIn
        val outputSize = chunkSizeOut
        updateChunkSizes()
        return ResampleResult.Ok(FrameCount(inputFrames = inputSize, outputFrames = outputSize))
    }

    override fun inputFramesMax(): Int =
        calcInputFramesMax(
            fixed,
            chunkSizeIn,
            chunkSizeOut,
            fftSizeInVal,
            fftSizeOutVal,
        )

    override fun inputFramesNext(): Int = chunkSizeIn

    override fun nbrChannels(): Int = nbrChannels

    override fun outputFramesMax(): Int =
        calcOutputFramesMax(
            fixed,
            chunkSizeIn,
            chunkSizeOut,
            fftSizeInVal,
            fftSizeOutVal,
        )

    override fun outputFramesNext(): Int = chunkSizeOut

    override fun outputDelay(): Int = fftSizeOutVal / 2

    override fun resampleRatio(): Double = fftSizeOutVal.toDouble() / fftSizeInVal.toDouble()

    override fun reset() {
        val zero = sample.zero
        for (ch in overlaps) {
            for (i in ch.indices) ch[i] = zero
        }
        for (ch in inputScratch) {
            for (i in ch.indices) ch[i] = zero
        }
        for (ch in outputScratch) {
            for (i in ch.indices) ch[i] = zero
        }
        channelMask.fill(true)
        savedFrames = 0
        updateChunkSizes()
    }

    override fun asAdjustable(): Adjustable<T>? = null

    override fun asResizable(): Resizable<T>? = null

    companion object {
        fun <T> new(
            sample: Sample<T>,
            sampleRateInput: Int,
            sampleRateOutput: Int,
            chunkSize: Int,
            nbrChannels: Int,
            fixed: FixedSync,
        ): ConstructionResult<Fft<T>> {
            val subChunks = max(chunkSize / 256, 1)
            return newCustom(
                sample = sample,
                sampleRateInput = sampleRateInput,
                sampleRateOutput = sampleRateOutput,
                chunkSize = chunkSize,
                subChunks = subChunks,
                nbrChannels = nbrChannels,
                window = WindowFunction.BlackmanHarris2,
                fixed = fixed,
            )
        }

        fun <T> newCustom(
            sample: Sample<T>,
            sampleRateInput: Int,
            sampleRateOutput: Int,
            chunkSize: Int,
            subChunks: Int,
            nbrChannels: Int,
            window: WindowFunction,
            fixed: FixedSync,
        ): ConstructionResult<Fft<T>> {
            val rateCheck = validateSampleRates(sampleRateInput, sampleRateOutput)
            if (rateCheck is ConstructionResult.Err) {
                return rateCheck
            }
            if (chunkSize <= 0) {
                return ConstructionResult.Err(ResamplerConstructionError.InvalidChunkSize(chunkSize))
            }

            val actualSubChunks = max(subChunks, 1)
            val g = gcd(sampleRateInput, sampleRateOutput)

            val rawFftChunks =
                when (fixed) {
                    FixedSync.Input -> {
                        val minChunkIn = sampleRateInput / g
                        val wantedSubsize = chunkSize / actualSubChunks
                        divCeil(wantedSubsize, minChunkIn)
                    }

                    FixedSync.Output -> {
                        val minChunkOut = sampleRateOutput / g
                        val wantedSubsize = chunkSize / actualSubChunks
                        divCeil(wantedSubsize, minChunkOut)
                    }

                    FixedSync.Both -> {
                        val minChunkIn = sampleRateInput / g
                        divCeil(chunkSize, minChunkIn)
                    }
                }
            val fftChunks = max(rawFftChunks, 1)
            val fftSizeOut = fftChunks * sampleRateOutput / g
            val fftSizeIn = fftChunks * sampleRateInput / g

            val resampler = FftResampler(sample, fftSizeIn, fftSizeOut, window)
            val zero = sample.zero
            val overlaps = List(nbrChannels) { MutableList(fftSizeOut) { zero } }
            val savedFrames = 0

            val (chunkSizeIn, chunkSizeOut) =
                calcChunkSizes(
                    fftSizeIn,
                    fftSizeOut,
                    chunkSize,
                    savedFrames,
                    fixed,
                )

            val neededInputBufferSize =
                calcInputFramesMax(
                    fixed,
                    chunkSizeIn,
                    chunkSizeOut,
                    fftSizeIn,
                    fftSizeOut,
                ) + fftSizeIn
            val neededOutputBufferSize =
                calcOutputFramesMax(
                    fixed,
                    chunkSizeIn,
                    chunkSizeOut,
                    fftSizeIn,
                    fftSizeOut,
                ) + fftSizeOut

            val inputScratch = List(nbrChannels) { MutableList(neededInputBufferSize) { zero } }
            val outputScratch = List(nbrChannels) { MutableList(neededOutputBufferSize) { zero } }
            val channelMask = BooleanArray(nbrChannels) { true }

            return ConstructionResult.Ok(
                Fft(
                    sample = sample,
                    nbrChannels = nbrChannels,
                    chunkSizeIn = chunkSizeIn,
                    chunkSizeOut = chunkSizeOut,
                    fftSizeInVal = fftSizeIn,
                    fftSizeOutVal = fftSizeOut,
                    overlaps = overlaps,
                    inputScratch = inputScratch,
                    outputScratch = outputScratch,
                    channelMask = channelMask,
                    savedFrames = savedFrames,
                    resampler = resampler,
                    fixed = fixed,
                ),
            )
        }

        internal fun calcChunkSizes(
            fftSizeIn: Int,
            fftSizeOut: Int,
            chunkSize: Int,
            savedFrames: Int,
            fixed: FixedSync,
        ): Pair<Int, Int> =
            when (fixed) {
                FixedSync.Input -> {
                    val subchunksAvailable = (chunkSize + savedFrames) / fftSizeIn
                    val framesAvailable = subchunksAvailable * fftSizeOut
                    Pair(chunkSize, framesAvailable)
                }

                FixedSync.Output -> {
                    val subchunksNeeded = divCeil((chunkSize - savedFrames).coerceAtLeast(0), fftSizeOut)
                    val framesNeeded = subchunksNeeded * fftSizeIn
                    Pair(framesNeeded, chunkSize)
                }

                FixedSync.Both -> {
                    val subchunksNeeded = divCeil(chunkSize, fftSizeIn)
                    val framesNeededIn = subchunksNeeded * fftSizeIn
                    val framesNeededOut = subchunksNeeded * fftSizeOut
                    Pair(framesNeededIn, framesNeededOut)
                }
            }

        internal fun calcInputFramesMax(
            fixed: FixedSync,
            chunkSizeIn: Int,
            chunkSizeOut: Int,
            fftSizeIn: Int,
            fftSizeOut: Int,
        ): Int =
            when (fixed) {
                FixedSync.Both, FixedSync.Input -> chunkSizeIn
                FixedSync.Output -> divCeil(chunkSizeOut, fftSizeOut) * fftSizeIn
            }

        internal fun calcOutputFramesMax(
            fixed: FixedSync,
            chunkSizeIn: Int,
            chunkSizeOut: Int,
            fftSizeIn: Int,
            fftSizeOut: Int,
        ): Int =
            when (fixed) {
                FixedSync.Both, FixedSync.Output -> {
                    chunkSizeOut
                }

                FixedSync.Input -> {
                    val maxStoredFrames = fftSizeIn - 1
                    val maxAvailableFrames = maxStoredFrames + chunkSizeIn
                    val maxSubchunksToProcess = maxAvailableFrames / fftSizeIn
                    maxSubchunksToProcess * fftSizeOut
                }
            }
    }
}

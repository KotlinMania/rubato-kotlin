// port-lint: source rubato/src/asynchro_sinc.rs
package io.github.kotlinmania.rubato

import io.github.kotlinmania.rubato.sincinterpolator.AlignedBuf
import io.github.kotlinmania.rubato.sincinterpolator.AnyInterpolator
import io.github.kotlinmania.rubato.sincinterpolator.ScalarInterpolator
import kotlin.math.floor

/**
 * A struct holding the parameters for sinc interpolation.
 */
public data class SincInterpolationParameters(
    /**
     * Length of the windowed sinc interpolation filter.
     * The value will be rounded up to the nearest multiple of 8.
     */
    public var sincLen: Int = 256,
    /**
     * Relative cutoff frequency of the sinc interpolation filter
     * (relative to the lowest one of fsIn/2 or fsOut/2).
     */
    public var fCutoff: Float? = null,
    /**
     * The number of intermediate points to use for interpolation.
     */
    public var oversamplingFactor: Int = 128,
    /**
     * Interpolation type, see [SincInterpolationType].
     */
    public var interpolation: SincInterpolationType = SincInterpolationType.Cubic,
    /**
     * Window function to use.
     */
    public var window: WindowFunction = WindowFunction.BlackmanHarris2,
) {
    public fun withSincLen(sincLen: Int): SincInterpolationParameters = copy(sincLen = sincLen)

    public fun withWindow(window: WindowFunction): SincInterpolationParameters = copy(window = window)

    public fun withFCutoff(fCutoff: Float): SincInterpolationParameters = copy(fCutoff = fCutoff)

    public fun withOversamplingFactor(oversamplingFactor: Int): SincInterpolationParameters =
        copy(oversamplingFactor = oversamplingFactor)

    public fun withInterpolation(interpolation: SincInterpolationType): SincInterpolationParameters =
        copy(interpolation = interpolation)

    public companion object {
        public fun new(
            sincLen: Int,
            window: WindowFunction,
        ): SincInterpolationParameters =
            SincInterpolationParameters(
                sincLen = sincLen,
                fCutoff = null,
                oversamplingFactor = 128,
                interpolation = SincInterpolationType.Cubic,
                window = window,
            )
    }
}

/**
 * Interpolation methods that can be selected.
 */
public enum class SincInterpolationType {
    /**
     * Cubic interpolation using the four nearest intermediate sinc points.
     */
    Cubic,

    /**
     * Quadratic interpolation using the three nearest intermediate sinc points.
     */
    Quadratic,

    /**
     * Linear interpolation between the two nearest intermediate sinc points.
     */
    Linear,

    /**
     * No interpolation: the nearest intermediate sinc point is used directly.
     */
    Nearest,
}

/**
 * Round the sinc length up to the multiple of 8 that the interpolators use.
 */
internal fun roundSincLen(sincLen: Int): Int {
    val rem = sincLen % 8
    return if (rem == 0) {
        if (sincLen == 0) 8 else sincLen
    } else {
        sincLen + (8 - rem)
    }
}

/**
 * Resolve the relative cutoff frequency of the sinc filter.
 */
internal fun resolveCutoff(
    sincLen: Int,
    resampleRatio: Double,
    fCutoff: Float?,
    window: WindowFunction,
): Float {
    val resolved = fCutoff ?: calculateCutoff(roundSincLen(sincLen), window, F32Sample)
    return if (resampleRatio >= 1.0) {
        resolved
    } else {
        resolved * resampleRatio.toFloat()
    }
}

internal fun <T> makeInterpolator(
    sincLen: Int,
    resampleRatio: Double,
    fCutoff: Float?,
    oversamplingFactor: Int,
    window: WindowFunction,
    sample: Sample<T>,
): AnyInterpolator<T> {
    val cutoff = resolveCutoff(sincLen, resampleRatio, fCutoff, window)
    val roundedSincLen = roundSincLen(sincLen)
    return AnyInterpolator.Scalar(
        ScalarInterpolator.new(
            sincLen = roundedSincLen,
            oversamplingFactor = oversamplingFactor,
            fCutoff = cutoff,
            window = window,
            sample = sample,
        ),
    )
}

/**
 * Perform cubic polynomial interpolation to get value at `x`.
 * Input points are assumed to be at x = -1, 0, 1, 2.
 */
internal fun <T> interpCubic(
    x: T,
    yvals: List<T>,
    s: Sample<T>,
): T {
    val y0 = yvals[0]
    val y1 = yvals[1]
    val y2 = yvals[2]
    val y3 = yvals[3]
    val a0 = y1
    val cThird = s.coerceFromDouble(1.0 / 3.0)
    val cHalf = s.coerceFromDouble(0.5)
    val cSixth = s.coerceFromDouble(1.0 / 6.0)
    val a1 =
        s.sub(
            s.add(
                s.sub(s.neg(s.mul(cThird, y0)), s.mul(cHalf, y1)),
                y2,
            ),
            s.mul(cSixth, y3),
        )
    val a2 = s.sub(s.mul(cHalf, s.add(y0, y2)), y1)
    val a3 = s.add(s.mul(cHalf, s.sub(y1, y2)), s.mul(cSixth, s.sub(y3, y0)))
    val x2 = s.mul(x, x)
    val x3 = s.mul(x2, x)
    return s.add(
        s.add(a0, s.mul(a1, x)),
        s.add(s.mul(a2, x2), s.mul(a3, x3)),
    )
}

/**
 * Compute the four blending weights for cubic interpolation at fractional position `x`.
 */
internal fun <T> interpCubicWeights(
    x: T,
    s: Sample<T>,
): List<T> {
    val x2 = s.mul(x, x)
    val x3 = s.mul(x2, x)
    val cNegThird = s.coerceFromDouble(-1.0 / 3.0)
    val cHalf = s.coerceFromDouble(0.5)
    val cSixth = s.coerceFromDouble(1.0 / 6.0)
    val cOne = s.coerceFromDouble(1.0)
    val w0 = s.sub(s.add(s.mul(cNegThird, x), s.mul(cHalf, x2)), s.mul(cSixth, x3))
    val w1 = s.add(s.sub(s.sub(cOne, s.mul(cHalf, x)), x2), s.mul(cHalf, x3))
    val w2 = s.sub(s.add(x, s.mul(cHalf, x2)), s.mul(cHalf, x3))
    val w3 = s.add(s.neg(s.mul(cSixth, x)), s.mul(cSixth, x3))
    return listOf(w0, w1, w2, w3)
}

/**
 * Perform quadratic polynomial interpolation to get value at `x`.
 * Input points are assumed to be at x = 0, 1, 2.
 */
internal fun <T> interpQuad(
    x: T,
    yvals: List<T>,
    s: Sample<T>,
): T {
    val y0 = yvals[0]
    val y1 = yvals[1]
    val y2 = yvals[2]
    val c2 = s.coerceFromDouble(2.0)
    val c3 = s.coerceFromDouble(3.0)
    val c4 = s.coerceFromDouble(4.0)
    val cHalf = s.coerceFromDouble(0.5)
    val a2 = s.add(s.sub(y0, s.mul(c2, y1)), y2)
    val a1 = s.sub(s.add(s.neg(s.mul(c3, y0)), s.mul(c4, y1)), y2)
    val a0 = s.mul(c2, y0)
    val x2 = s.mul(x, x)
    return s.mul(cHalf, s.add(s.add(a0, s.mul(a1, x)), s.mul(a2, x2)))
}

/**
 * Compute the three blending weights for quadratic interpolation at fractional position `x`.
 */
internal fun <T> interpQuadWeights(
    x: T,
    s: Sample<T>,
): List<T> {
    val x2 = s.mul(x, x)
    val cHalf = s.coerceFromDouble(0.5)
    val c2 = s.coerceFromDouble(2.0)
    val c3 = s.coerceFromDouble(3.0)
    val c4 = s.coerceFromDouble(4.0)
    val w0 = s.mul(cHalf, s.add(s.sub(c2, s.mul(c3, x)), x2))
    val w1 = s.mul(cHalf, s.sub(s.mul(c4, x), s.mul(c2, x2)))
    val w2 = s.mul(cHalf, s.sub(x2, x))
    return listOf(w0, w1, w2)
}

/**
 * Perform linear interpolation between two points at x = 0 and x = 1.
 */
internal fun <T> interpLin(
    x: T,
    yvals: List<T>,
    s: Sample<T>,
): T {
    val y0 = yvals[0]
    val y1 = yvals[1]
    return s.add(y0, s.mul(x, s.sub(y1, y0)))
}

/**
 * Compute the two blending weights for linear interpolation at fractional position `x`.
 */
internal fun <T> interpLinWeights(
    x: T,
    s: Sample<T>,
): List<T> {
    val cOne = s.coerceFromDouble(1.0)
    return listOf(s.sub(cOne, x), x)
}

internal class InnerSinc<T>(
    internal val interpolator: AnyInterpolator<T>,
    internal val interpolation: SincInterpolationType,
) : InnerResampler<T> {
    private val sample: Sample<T> = interpolator.sample
    private val combined: AlignedBuf<T> = AlignedBuf.zeroed(interpolator.nbrPoints() + 1, sample)

    private fun processCombinedFrame(
        nearest: Array<IndexSubindex>,
        weights: List<T>,
        interpolatorLen: Int,
        channelMask: BooleanArray,
        waveIn: List<MutableList<T>>,
        waveOut: AdapterMut<T>,
        frame: Int,
        outputOffset: Int,
    ) {
        for (n in nearest) {
            interpolator.prefetchSinc(n.subindex)
        }
        val minIdx = interpolator.makeCombinedSinc(nearest, weights, combined.data)
        val base = minIdx + 2 * interpolatorLen
        val combSlice = combined.subList(0, interpolatorLen)
        val extraWeight = combined[interpolatorLen]
        for (chan in channelMask.indices) {
            if (channelMask[chan]) {
                val buf = waveIn[chan]
                val dot = interpolator.getSincDotProduct(buf, base, combSlice)
                val result = sample.add(dot, sample.mul(extraWeight, buf[base + interpolatorLen]))
                waveOut.writeSample(chan, frame + outputOffset, result)
            }
        }
    }

    private fun processDirectFrame(
        nearest: Array<IndexSubindex>,
        interpolatorLen: Int,
        channelMask: BooleanArray,
        waveIn: List<MutableList<T>>,
        waveOut: AdapterMut<T>,
        frame: Int,
        outputOffset: Int,
        interp: (List<T>) -> T,
    ) {
        val n = nearest.size
        val points = sample.newList(n)
        for (chan in channelMask.indices) {
            if (channelMask[chan]) {
                val buf = waveIn[chan]
                for (i in 0 until n) {
                    val ni = nearest[i]
                    points[i] =
                        interpolator.getSincInterpolated(
                            buf,
                            ni.index + 2 * interpolatorLen,
                            ni.subindex,
                        )
                }
                waveOut.writeSample(chan, frame + outputOffset, interp(points))
            }
        }
    }

    override fun process(
        index: Double,
        nbrFrames: Int,
        channelMask: BooleanArray,
        tRatio: Double,
        tRatioIncrement: Double,
        waveIn: List<MutableList<T>>,
        waveOut: AdapterMut<T>,
        outputOffset: Int,
    ): Double {
        var curTRatio = tRatio
        var idx = index
        val interpolatorLen = interpolator.nbrPoints()
        val oversamplingFactor = interpolator.nbrSincs()
        var activeCount = 0
        for (a in channelMask) {
            if (a) activeCount += 1
        }
        when (interpolation) {
            SincInterpolationType.Cubic -> {
                val useCombined = activeCount >= 2
                val nearest = Array(4) { IndexSubindex(0, 0) }
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    getNearestTimes4(idx, oversamplingFactor, nearest)
                    val scaled = idx * oversamplingFactor.toDouble()
                    val fracOffset = sample.coerceFromDouble(scaled - floor(scaled))
                    if (useCombined) {
                        val weights = interpCubicWeights(fracOffset, sample)
                        processCombinedFrame(
                            nearest = nearest,
                            weights = weights,
                            interpolatorLen = interpolatorLen,
                            channelMask = channelMask,
                            waveIn = waveIn,
                            waveOut = waveOut,
                            frame = frame,
                            outputOffset = outputOffset,
                        )
                    } else {
                        processDirectFrame(
                            nearest = nearest,
                            interpolatorLen = interpolatorLen,
                            channelMask = channelMask,
                            waveIn = waveIn,
                            waveOut = waveOut,
                            frame = frame,
                            outputOffset = outputOffset,
                        ) { pts -> interpCubic(fracOffset, pts, sample) }
                    }
                }
            }

            SincInterpolationType.Quadratic -> {
                val useCombined = activeCount > 2
                val nearest = Array(3) { IndexSubindex(0, 0) }
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    getNearestTimes3(idx, oversamplingFactor, nearest)
                    val scaled = idx * oversamplingFactor.toDouble()
                    val fracOffset = sample.coerceFromDouble(scaled - floor(scaled))
                    if (useCombined) {
                        val weights = interpQuadWeights(fracOffset, sample)
                        processCombinedFrame(
                            nearest = nearest,
                            weights = weights,
                            interpolatorLen = interpolatorLen,
                            channelMask = channelMask,
                            waveIn = waveIn,
                            waveOut = waveOut,
                            frame = frame,
                            outputOffset = outputOffset,
                        )
                    } else {
                        processDirectFrame(
                            nearest = nearest,
                            interpolatorLen = interpolatorLen,
                            channelMask = channelMask,
                            waveIn = waveIn,
                            waveOut = waveOut,
                            frame = frame,
                            outputOffset = outputOffset,
                        ) { pts -> interpQuad(fracOffset, pts, sample) }
                    }
                }
            }

            SincInterpolationType.Linear -> {
                val useCombined = activeCount > 2
                val nearest = Array(2) { IndexSubindex(0, 0) }
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    getNearestTimes2(idx, oversamplingFactor, nearest)
                    val scaled = idx * oversamplingFactor.toDouble()
                    val fracOffset = sample.coerceFromDouble(scaled - floor(scaled))
                    if (useCombined) {
                        val weights = interpLinWeights(fracOffset, sample)
                        processCombinedFrame(
                            nearest = nearest,
                            weights = weights,
                            interpolatorLen = interpolatorLen,
                            channelMask = channelMask,
                            waveIn = waveIn,
                            waveOut = waveOut,
                            frame = frame,
                            outputOffset = outputOffset,
                        )
                    } else {
                        processDirectFrame(
                            nearest = nearest,
                            interpolatorLen = interpolatorLen,
                            channelMask = channelMask,
                            waveIn = waveIn,
                            waveOut = waveOut,
                            frame = frame,
                            outputOffset = outputOffset,
                        ) { pts -> interpLin(fracOffset, pts, sample) }
                    }
                }
            }

            SincInterpolationType.Nearest -> {
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    val nearest = getNearestTime(idx, oversamplingFactor)
                    for (chan in channelMask.indices) {
                        if (channelMask[chan]) {
                            val buf = waveIn[chan]
                            val point =
                                interpolator.getSincInterpolated(
                                    buf,
                                    nearest.index + 2 * interpolatorLen,
                                    nearest.subindex,
                                )
                            waveOut.writeSample(chan, frame + outputOffset, point)
                        }
                    }
                }
            }
        }
        return idx
    }

    override fun nbrPoints(): Int = interpolator.nbrPoints()

    override fun initLastIndex(): Double = -(interpolator.nbrPoints().toDouble() - 1.0)
}

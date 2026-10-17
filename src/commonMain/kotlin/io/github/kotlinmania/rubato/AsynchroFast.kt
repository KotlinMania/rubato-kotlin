// port-lint: source rubato/src/asynchro_fast.rs
package io.github.kotlinmania.rubato

import kotlin.math.floor

/**
 * Degree of the polynomial used for interpolation.
 * A higher degree gives a higher quality result, while taking longer to compute.
 */
public enum class PolynomialDegree {
    /**
     * Septic polynomial, fitted using 8 sample points.
     */
    Septic,

    /**
     * Quintic polynomial, fitted using 6 sample points.
     */
    Quintic,

    /**
     * Cubic polynomial, fitted using 4 sample points.
     */
    Cubic,

    /**
     * Linear polynomial, fitted using 2 sample points.
     */
    Linear,

    /**
     * Nearest, uses the nearest sample point without any fitting.
     */
    Nearest,
    ;

    public fun nbrPoints(): Int =
        when (this) {
            Nearest -> 1
            Linear -> 2
            Cubic -> 4
            Quintic -> 6
            Septic -> 8
        }
}

/**
 * Perform septic polynomial interpolation to get value at `x`.
 * Input points are assumed to be at x = -3, -2, -1, 0, 1, 2, 3, 4.
 */
internal fun <T> interpSeptic(
    x: T,
    yvals: List<T>,
    offset: Int,
    s: Sample<T>,
): T {
    val xv = s.toDouble(x)
    val a = s.toDouble(yvals[offset])
    val b = s.toDouble(yvals[offset + 1])
    val c = s.toDouble(yvals[offset + 2])
    val d = s.toDouble(yvals[offset + 3])
    val e = s.toDouble(yvals[offset + 4])
    val f = s.toDouble(yvals[offset + 5])
    val g = s.toDouble(yvals[offset + 6])
    val h = s.toDouble(yvals[offset + 7])
    val k7 = -a + 7.0 * b - 21.0 * c + 35.0 * d - 35.0 * e + 21.0 * f - 7.0 * g + h
    val k6 = 7.0 * a - 42.0 * b + 105.0 * c - 140.0 * d + 105.0 * e - 42.0 * f + 7.0 * g
    val k5 = -7.0 * a - 14.0 * b + 189.0 * c - 490.0 * d + 595.0 * e - 378.0 * f + 119.0 * g - 14.0 * h
    val k4 = -35.0 * a + 420.0 * b - 1365.0 * c + 1960.0 * d - 1365.0 * e + 420.0 * f - 35.0 * g
    val k3 = 56.0 * a - 497.0 * b + 336.0 * c + 1715.0 * d - 3080.0 * e + 1869.0 * f - 448.0 * g + 49.0 * h
    val k2 = 28.0 * a - 378.0 * b + 3780.0 * c - 6860.0 * d + 3780.0 * e - 378.0 * f + 28.0 * g
    val k1 = -48.0 * a + 504.0 * b - 3024.0 * c - 1260.0 * d + 5040.0 * e - 1512.0 * f + 336.0 * g - 36.0 * h
    val k0 = 5040.0 * d
    val res = (1.0 / 5040.0) * (k0 + xv * (k1 + xv * (k2 + xv * (k3 + xv * (k4 + xv * (k5 + xv * (k6 + xv * k7)))))))
    return s.coerceFromDouble(res)
}

/**
 * Perform quintic polynomial interpolation to get value at `x`.
 * Input points are assumed to be at x = -2, -1, 0, 1, 2, 3.
 */
internal fun <T> interpQuintic(
    x: T,
    yvals: List<T>,
    offset: Int,
    s: Sample<T>,
): T {
    val xv = s.toDouble(x)
    val a = s.toDouble(yvals[offset])
    val b = s.toDouble(yvals[offset + 1])
    val c = s.toDouble(yvals[offset + 2])
    val d = s.toDouble(yvals[offset + 3])
    val e = s.toDouble(yvals[offset + 4])
    val f = s.toDouble(yvals[offset + 5])
    val k5 = -a + 5.0 * b - 10.0 * c + 10.0 * d - 5.0 * e + f
    val k4 = 5.0 * a - 20.0 * b + 30.0 * c - 20.0 * d + 5.0 * e
    val k3 = -5.0 * a - 5.0 * b + 50.0 * c - 70.0 * d + 35.0 * e - 5.0 * f
    val k2 = -5.0 * a + 80.0 * b - 150.0 * c + 80.0 * d - 5.0 * e
    val k1 = 6.0 * a - 60.0 * b - 40.0 * c + 120.0 * d - 30.0 * e + 4.0 * f
    val k0 = 120.0 * c
    val res = (1.0 / 120.0) * (k0 + xv * (k1 + xv * (k2 + xv * (k3 + xv * (k4 + xv * k5)))))
    return s.coerceFromDouble(res)
}

/**
 * Perform cubic polynomial interpolation to get value at `x`.
 * Input points are assumed to be at x = -1, 0, 1, 2.
 */
internal fun <T> interpCubicPoly(
    x: T,
    yvals: List<T>,
    offset: Int,
    s: Sample<T>,
): T {
    val xv = s.toDouble(x)
    val y0 = s.toDouble(yvals[offset])
    val y1 = s.toDouble(yvals[offset + 1])
    val y2 = s.toDouble(yvals[offset + 2])
    val y3 = s.toDouble(yvals[offset + 3])
    val a0 = y1
    val a1 = -(1.0 / 3.0) * y0 - 0.5 * y1 + y2 - (1.0 / 6.0) * y3
    val a2 = 0.5 * (y0 + y2) - y1
    val a3 = 0.5 * (y1 - y2) + (1.0 / 6.0) * (y3 - y0)
    return s.coerceFromDouble(a0 + xv * (a1 + xv * (a2 + xv * a3)))
}

/**
 * Linear interpolation between two points at x = 0 and x = 1.
 */
internal fun <T> interpLinPoly(
    x: T,
    yvals: List<T>,
    offset: Int,
    s: Sample<T>,
): T {
    val y0 = yvals[offset]
    val y1 = yvals[offset + 1]
    return s.add(y0, s.mul(x, s.sub(y1, y0)))
}

internal class InnerPoly<T>(
    internal val interpolation: PolynomialDegree,
    private val sample: Sample<T>,
) : InnerResampler<T> {
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
        val interpolatorLen = nbrPoints()
        var curTRatio = tRatio
        var idx = index
        when (interpolation) {
            PolynomialDegree.Septic -> {
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    val idxFloor = floor(idx)
                    val startIdx = idxFloor.toInt() - 3
                    val frac = idx - idxFloor
                    val fracOffset = sample.coerceFromDouble(frac)
                    val base = startIdx + 2 * interpolatorLen
                    for (chan in channelMask.indices) {
                        if (channelMask[chan]) {
                            val v = interpSeptic(fracOffset, waveIn[chan], base, sample)
                            waveOut.writeSample(chan, frame + outputOffset, v)
                        }
                    }
                }
            }

            PolynomialDegree.Quintic -> {
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    val idxFloor = floor(idx)
                    val startIdx = idxFloor.toInt() - 2
                    val frac = idx - idxFloor
                    val fracOffset = sample.coerceFromDouble(frac)
                    val base = startIdx + 2 * interpolatorLen
                    for (chan in channelMask.indices) {
                        if (channelMask[chan]) {
                            val v = interpQuintic(fracOffset, waveIn[chan], base, sample)
                            waveOut.writeSample(chan, frame + outputOffset, v)
                        }
                    }
                }
            }

            PolynomialDegree.Cubic -> {
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    val idxFloor = floor(idx)
                    val startIdx = idxFloor.toInt() - 1
                    val frac = idx - idxFloor
                    val fracOffset = sample.coerceFromDouble(frac)
                    val base = startIdx + 2 * interpolatorLen
                    for (chan in channelMask.indices) {
                        if (channelMask[chan]) {
                            val v = interpCubicPoly(fracOffset, waveIn[chan], base, sample)
                            waveOut.writeSample(chan, frame + outputOffset, v)
                        }
                    }
                }
            }

            PolynomialDegree.Linear -> {
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    val idxFloor = floor(idx)
                    val startIdx = idxFloor.toInt()
                    val frac = idx - idxFloor
                    val fracOffset = sample.coerceFromDouble(frac)
                    val base = startIdx + 2 * interpolatorLen
                    for (chan in channelMask.indices) {
                        if (channelMask[chan]) {
                            val v = interpLinPoly(fracOffset, waveIn[chan], base, sample)
                            waveOut.writeSample(chan, frame + outputOffset, v)
                        }
                    }
                }
            }

            PolynomialDegree.Nearest -> {
                for (frame in 0 until nbrFrames) {
                    val stepped = stepIndex(idx, curTRatio, tRatioIncrement)
                    idx = stepped.idx
                    curTRatio = stepped.tRatio
                    val startIdx = floor(idx).toInt()
                    val base = startIdx + 2 * interpolatorLen
                    for (chan in channelMask.indices) {
                        if (channelMask[chan]) {
                            waveOut.writeSample(chan, frame + outputOffset, waveIn[chan][base])
                        }
                    }
                }
            }
        }
        return idx
    }

    override fun nbrPoints(): Int = interpolation.nbrPoints()

    override fun initLastIndex(): Double = -(interpolation.nbrPoints().toDouble() / 2.0)
}

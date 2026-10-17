// port-lint: source rubato/src/windows.rs
package io.github.kotlinmania.rubato

import kotlin.math.PI
import kotlin.math.cos

/**
 * Different window functions that can be used to window the sinc function.
 */
public enum class WindowFunction {
    /**
     * Blackman. Intermediate rolloff and intermediate attenuation.
     */
    Blackman,

    /**
     * Squared Blackman. Slower rolloff but better attenuation than Blackman.
     */
    Blackman2,

    /**
     * Blackman-Harris. Slow rolloff but good attenuation.
     */
    BlackmanHarris,

    /**
     * Squared Blackman-Harris. Slower rolloff but better attenuation than Blackman-Harris.
     */
    BlackmanHarris2,

    /**
     * Hann. Fast rolloff but not very high attenuation.
     */
    Hann,

    /**
     * Squared Hann. Slower rolloff and higher attenuation than simple Hann.
     */
    Hann2,
}

/**
 * Helper function. Standard Blackman-Harris window (periodic).
 */
internal fun <T> blackmanHarris(npoints: Int, sample: Sample<T>): MutableList<T> {
    val result = ArrayList<T>(npoints)
    val n = npoints.toDouble()
    for (i in 0 until npoints) {
        val x = 2.0 * PI * i.toDouble() / n
        val v = 0.35875 - 0.48829 * cos(x) + 0.14128 * cos(2.0 * x) - 0.01168 * cos(3.0 * x)
        result.add(sample.coerceFromDouble(v))
    }
    return result
}

/**
 * Helper function. Standard Blackman window (periodic).
 */
internal fun <T> blackman(npoints: Int, sample: Sample<T>): MutableList<T> {
    val result = ArrayList<T>(npoints)
    val n = npoints.toDouble()
    for (i in 0 until npoints) {
        val x = 2.0 * PI * i.toDouble() / n
        val v = 0.42 - 0.5 * cos(x) + 0.08 * cos(2.0 * x)
        result.add(sample.coerceFromDouble(v))
    }
    return result
}

/**
 * Helper function. Standard Hann window (periodic).
 */
internal fun <T> hann(npoints: Int, sample: Sample<T>): MutableList<T> {
    val result = ArrayList<T>(npoints)
    val n = npoints.toDouble()
    for (i in 0 until npoints) {
        val x = 2.0 * PI * i.toDouble() / n
        val v = 0.5 - 0.5 * cos(x)
        result.add(sample.coerceFromDouble(v))
    }
    return result
}

/**
 * Make the selected window function.
 */
internal fun <T> makeWindow(
    npoints: Int,
    windowfunc: WindowFunction,
    sample: Sample<T>,
): MutableList<T> {
    val window =
        when (windowfunc) {
            WindowFunction.BlackmanHarris, WindowFunction.BlackmanHarris2 -> blackmanHarris(npoints, sample)
            WindowFunction.Blackman, WindowFunction.Blackman2 -> blackman(npoints, sample)
            WindowFunction.Hann, WindowFunction.Hann2 -> hann(npoints, sample)
        }
    when (windowfunc) {
        WindowFunction.Blackman2, WindowFunction.BlackmanHarris2, WindowFunction.Hann2 -> {
            for (i in window.indices) {
                val y = window[i]
                window[i] = sample.mul(y, y)
            }
        }

        else -> {}
    }
    return window
}

/**
 * Calculate a suitable relative cutoff frequency for the given sinc length using the given window function.
 * The result is based on an approximation, which gives good results for sinc lengths from 32 to 2048.
 */
internal fun <T> calculateCutoff(
    npoints: Int,
    windowfunc: WindowFunction,
    sample: Sample<T>,
): T {
    val k1: T
    val k2: T
    val k3: T
    when (windowfunc) {
        WindowFunction.BlackmanHarris -> {
            k1 = sample.coerceFromDouble(8.041443677716476)
            k2 = sample.coerceFromDouble(55.9506779343387)
            k3 = sample.coerceFromDouble(898.0287985384213)
        }

        WindowFunction.BlackmanHarris2 -> {
            k1 = sample.coerceFromDouble(13.745202940783823)
            k2 = sample.coerceFromDouble(121.73532586374934)
            k3 = sample.coerceFromDouble(5964.163279612051)
        }

        WindowFunction.Blackman -> {
            k1 = sample.coerceFromDouble(6.159598046201173)
            k2 = sample.coerceFromDouble(18.926415097606878)
            k3 = sample.coerceFromDouble(653.4247430458968)
        }

        WindowFunction.Blackman2 -> {
            k1 = sample.coerceFromDouble(9.506235102129398)
            k2 = sample.coerceFromDouble(79.13120634953742)
            k3 = sample.coerceFromDouble(1502.2316160588925)
        }

        WindowFunction.Hann -> {
            k1 = sample.coerceFromDouble(3.3481080887677166)
            k2 = sample.coerceFromDouble(10.106519434875038)
            k3 = sample.coerceFromDouble(78.96345249024414)
        }

        WindowFunction.Hann2 -> {
            k1 = sample.coerceFromDouble(5.38751148378734)
            k2 = sample.coerceFromDouble(29.69451915489501)
            k3 = sample.coerceFromDouble(184.82117462266237)
        }
    }
    val one = sample.one
    val n = sample.coerceFromInt(npoints)
    val n2 = sample.mul(n, n)
    val n3 = sample.mul(n2, n)
    val denom =
        sample.add(
            sample.add(
                sample.add(sample.div(k1, n), sample.div(k2, n2)),
                sample.div(k3, n3),
            ),
            one,
        )
    return sample.div(one, denom)
}

/**
 * Calculate a suitable relative cutoff frequency (`Double`) for the given sinc length and window function.
 */
public fun calculateCutoff(npoints: Int, windowfunc: WindowFunction): Double =
    calculateCutoff(npoints, windowfunc, F64Sample)

/**
 * Calculate a suitable relative cutoff frequency (`Float`) for the given sinc length and window function.
 */
public fun calculateCutoffF32(npoints: Int, windowfunc: WindowFunction): Float =
    calculateCutoff(npoints, windowfunc, F32Sample)

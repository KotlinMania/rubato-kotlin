// port-lint: source rubato/src/sinc.rs
package io.github.kotlinmania.rubato

/**
 * Helper function: sinc(x) = sin(pi * x) / (pi * x).
 */
internal fun <T> sinc(value: T, sample: Sample<T>): T =
    if (value == sample.zero) {
        sample.one
    } else {
        val piX = sample.mul(value, sample.pi)
        sample.div(sample.sinOf(piX), piX)
    }

/**
 * Helper function. Make a set of windowed sincs.
 */
internal fun <T> makeSincs(
    npoints: Int,
    factor: Int,
    fCutoff: Float,
    windowfunc: WindowFunction,
    sample: Sample<T>,
): MutableList<MutableList<T>> {
    val totpoints = npoints * factor
    val y = ArrayList<T>(totpoints)
    val window = makeWindow(totpoints, windowfunc, sample)
    var sum = sample.zero
    val halfTot = sample.coerceFromInt(totpoints / 2)
    val cutoffT = sample.coerceFromFloat(fCutoff)
    val factorT = sample.coerceFromInt(factor)
    for (x in 0 until totpoints) {
        val w = window[x]
        val arg =
            sample.div(
                sample.mul(sample.sub(sample.coerceFromInt(x), halfTot), cutoffT),
                factorT,
            )
        val value = sample.mul(w, sinc(arg, sample))
        sum = sample.add(sum, value)
        y.add(value)
    }
    sum = sample.div(sum, factorT)
    val sincs = MutableList(factor) { sample.newList(npoints) }
    for (p in 0 until npoints) {
        for (n in 0 until factor) {
            sincs[factor - n - 1][p] = sample.div(y[factor * p + n], sum)
        }
    }
    return sincs
}

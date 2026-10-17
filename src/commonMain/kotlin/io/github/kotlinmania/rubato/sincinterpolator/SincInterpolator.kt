package io.github.kotlinmania.rubato.sincinterpolator

import io.github.kotlinmania.rubato.IndexSubindex
import io.github.kotlinmania.rubato.Sample
import io.github.kotlinmania.rubato.WindowFunction
import io.github.kotlinmania.rubato.makeSincs

/**
 * Functions for making the scalar product with a sinc.
 */
internal interface SincInterpolator<T> {
    val sample: Sample<T>

    /**
     * Compute the dot product of the wave starting at `index` with the provided sinc slice.
     */
    fun getSincDotProduct(
        wave: List<T>,
        index: Int,
        sinc: List<T>,
    ): T

    /**
     * Expose the raw sinc table for use in combined-sinc building.
     */
    fun getSincs(): List<AlignedBuf<T>>

    /**
     * Get sinc length.
     */
    fun nbrPoints(): Int

    /**
     * Get number of sincs used for oversampling.
     */
    fun nbrSincs(): Int

    /**
     * Warm the CPU cache with the sinc row at `subindex`.
     */
    fun prefetchSinc(subindex: Int) {
        if (subindex >= 0 && subindex < nbrSincs()) {
            getSincs()[subindex]
        }
    }

    /**
     * Make the scalar product between the waveform starting at `index` and the sinc of `subindex`.
     */
    fun getSincInterpolated(
        wave: List<T>,
        index: Int,
        subindex: Int,
    ): T {
        require(index + nbrPoints() < wave.size) {
            "Tried to interpolate for index $index, max for the given input is ${wave.size - nbrPoints() - 1}"
        }
        require(subindex < nbrSincs()) {
            "Tried to use sinc subindex $subindex, max is ${nbrSincs() - 1}"
        }
        return getSincDotProduct(wave, index, getSincs()[subindex].data)
    }

    /**
     * Build a combined sinc by blending the sincs indicated by `nearest` with `weights`.
     */
    fun makeCombinedSinc(
        nearest: Array<IndexSubindex>,
        weights: List<T>,
        combined: MutableList<T>,
    ): Int {
        require(combined.size == nbrPoints() + 1) {
            "combined must be nbrPoints()+1: the extra element holds any spillover " +
                "from nearest points at the higher integer index"
        }
        var minIdx = nearest[0].index
        for (i in 1 until nearest.size) {
            if (nearest[i].index < minIdx) {
                minIdx = nearest[i].index
            }
        }
        val zero = sample.zero
        for (i in combined.indices) {
            combined[i] = zero
        }
        val sincs = getSincs()
        for (i in nearest.indices) {
            val n = nearest[i]
            val w = weights[i]
            val shift = n.index - minIdx
            val row = sincs[n.subindex].data
            for (k in row.indices) {
                val cur = combined[shift + k]
                combined[shift + k] = sample.add(cur, sample.mul(w, row[k]))
            }
        }
        return minIdx
    }
}

/**
 * A concrete sealed class over every sinc-interpolator implementation.
 */
internal sealed class AnyInterpolator<T> : SincInterpolator<T> {
    internal data class Avx<T>(
        val inner: AvxInterpolator<T>,
    ) : AnyInterpolator<T>() {
        override val sample: Sample<T>
            get() = inner.sample

        override fun getSincDotProduct(
            wave: List<T>,
            index: Int,
            sinc: List<T>,
        ): T = inner.getSincDotProduct(wave, index, sinc)

        override fun getSincs(): List<AlignedBuf<T>> = inner.getSincs()

        override fun nbrPoints(): Int = inner.nbrPoints()

        override fun nbrSincs(): Int = inner.nbrSincs()

        override fun prefetchSinc(subindex: Int) = inner.prefetchSinc(subindex)

        override fun makeCombinedSinc(
            nearest: Array<IndexSubindex>,
            weights: List<T>,
            combined: MutableList<T>,
        ): Int = inner.makeCombinedSinc(nearest, weights, combined)
    }

    internal data class Sse<T>(
        val inner: SseInterpolator<T>,
    ) : AnyInterpolator<T>() {
        override val sample: Sample<T>
            get() = inner.sample

        override fun getSincDotProduct(
            wave: List<T>,
            index: Int,
            sinc: List<T>,
        ): T = inner.getSincDotProduct(wave, index, sinc)

        override fun getSincs(): List<AlignedBuf<T>> = inner.getSincs()

        override fun nbrPoints(): Int = inner.nbrPoints()

        override fun nbrSincs(): Int = inner.nbrSincs()

        override fun prefetchSinc(subindex: Int) = inner.prefetchSinc(subindex)

        override fun makeCombinedSinc(
            nearest: Array<IndexSubindex>,
            weights: List<T>,
            combined: MutableList<T>,
        ): Int = inner.makeCombinedSinc(nearest, weights, combined)
    }

    internal data class Neon<T>(
        val inner: NeonInterpolator<T>,
    ) : AnyInterpolator<T>() {
        override val sample: Sample<T>
            get() = inner.sample

        override fun getSincDotProduct(
            wave: List<T>,
            index: Int,
            sinc: List<T>,
        ): T = inner.getSincDotProduct(wave, index, sinc)

        override fun getSincs(): List<AlignedBuf<T>> = inner.getSincs()

        override fun nbrPoints(): Int = inner.nbrPoints()

        override fun nbrSincs(): Int = inner.nbrSincs()

        override fun prefetchSinc(subindex: Int) = inner.prefetchSinc(subindex)

        override fun makeCombinedSinc(
            nearest: Array<IndexSubindex>,
            weights: List<T>,
            combined: MutableList<T>,
        ): Int = inner.makeCombinedSinc(nearest, weights, combined)
    }

    internal data class Scalar<T>(
        val inner: ScalarInterpolator<T>,
    ) : AnyInterpolator<T>() {
        override val sample: Sample<T>
            get() = inner.sample

        override fun getSincDotProduct(
            wave: List<T>,
            index: Int,
            sinc: List<T>,
        ): T = inner.getSincDotProduct(wave, index, sinc)

        override fun getSincs(): List<AlignedBuf<T>> = inner.getSincs()

        override fun nbrPoints(): Int = inner.nbrPoints()

        override fun nbrSincs(): Int = inner.nbrSincs()

        override fun prefetchSinc(subindex: Int) = inner.prefetchSinc(subindex)

        override fun makeCombinedSinc(
            nearest: Array<IndexSubindex>,
            weights: List<T>,
            combined: MutableList<T>,
        ): Int = inner.makeCombinedSinc(nearest, weights, combined)
    }
}

/**
 * A plain scalar interpolator.
 */
internal class ScalarInterpolator<T>(
    internal val sincs: List<AlignedBuf<T>>,
    private val length: Int,
    private val nbrSincsCount: Int,
    override val sample: Sample<T>,
) : SincInterpolator<T> {
    override fun getSincDotProduct(
        wave: List<T>,
        index: Int,
        sinc: List<T>,
    ): T {
        var acc0 = sample.zero
        var acc1 = sample.zero
        var acc2 = sample.zero
        var acc3 = sample.zero
        var acc4 = sample.zero
        var acc5 = sample.zero
        var acc6 = sample.zero
        var acc7 = sample.zero
        var idx = 0
        val len = sinc.size
        val blocks = len / 8
        repeat(blocks) {
            acc0 = sample.add(acc0, sample.mul(wave[index + idx], sinc[idx]))
            acc1 = sample.add(acc1, sample.mul(wave[index + idx + 1], sinc[idx + 1]))
            acc2 = sample.add(acc2, sample.mul(wave[index + idx + 2], sinc[idx + 2]))
            acc3 = sample.add(acc3, sample.mul(wave[index + idx + 3], sinc[idx + 3]))
            acc4 = sample.add(acc4, sample.mul(wave[index + idx + 4], sinc[idx + 4]))
            acc5 = sample.add(acc5, sample.mul(wave[index + idx + 5], sinc[idx + 5]))
            acc6 = sample.add(acc6, sample.mul(wave[index + idx + 6], sinc[idx + 6]))
            acc7 = sample.add(acc7, sample.mul(wave[index + idx + 7], sinc[idx + 7]))
            idx += 8
        }
        val s01 = sample.add(acc0, acc1)
        val s23 = sample.add(acc2, acc3)
        val s45 = sample.add(acc4, acc5)
        val s67 = sample.add(acc6, acc7)
        return sample.add(sample.add(s01, s23), sample.add(s45, s67))
    }

    override fun getSincs(): List<AlignedBuf<T>> = sincs

    override fun nbrPoints(): Int = length

    override fun nbrSincs(): Int = nbrSincsCount

    internal companion object {
        internal fun <T> new(
            sincLen: Int,
            oversamplingFactor: Int,
            fCutoff: Float,
            window: WindowFunction,
            sample: Sample<T>,
        ): ScalarInterpolator<T> {
            require(sincLen % 8 == 0) { "Sinc length must be a multiple of 8" }
            val rawSincs = makeSincs(sincLen, oversamplingFactor, fCutoff, window, sample)
            val sincs = rawSincs.map { row -> AlignedBuf.fromSlice(row) }
            return ScalarInterpolator(
                sincs = sincs,
                length = sincLen,
                nbrSincsCount = oversamplingFactor,
                sample = sample,
            )
        }
    }
}

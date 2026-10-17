// port-lint: source rubato/src/sinc_interpolator/sinc_interpolator_avx.rs
package io.github.kotlinmania.rubato.sincinterpolator

import io.github.kotlinmania.rubato.CpuFeature
import io.github.kotlinmania.rubato.IndexSubindex
import io.github.kotlinmania.rubato.MissingCpuFeature
import io.github.kotlinmania.rubato.Sample
import io.github.kotlinmania.rubato.WindowFunction
import io.github.kotlinmania.rubato.makeSincs

private val AVX_FEATURES: List<CpuFeature> = listOf(CpuFeature.Avx, CpuFeature.Fma)

/**
 * An AVX accelerated interpolator.
 */
internal class AvxInterpolator<T>(
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
        var idx = 0
        val blocks32 = length / 16
        repeat(blocks32) {
            for (k in 0 until 4) {
                acc0 = sample.add(acc0, sample.mul(wave[index + idx + k], sinc[idx + k]))
                acc1 = sample.add(acc1, sample.mul(wave[index + idx + 4 + k], sinc[idx + 4 + k]))
                acc2 = sample.add(acc2, sample.mul(wave[index + idx + 8 + k], sinc[idx + 8 + k]))
                acc3 = sample.add(acc3, sample.mul(wave[index + idx + 12 + k], sinc[idx + 12 + k]))
            }
            idx += 16
        }
        val rem4 = (length % 16) / 4
        repeat(rem4) {
            for (k in 0 until 4) {
                acc0 = sample.add(acc0, sample.mul(wave[index + idx + k], sinc[idx + k]))
            }
            idx += 4
        }
        return sample.add(sample.add(acc0, acc1), sample.add(acc2, acc3))
    }

    override fun getSincs(): List<AlignedBuf<T>> = sincs

    override fun nbrPoints(): Int = length

    override fun nbrSincs(): Int = nbrSincsCount

    override fun makeCombinedSinc(
        nearest: Array<IndexSubindex>,
        weights: List<T>,
        combined: MutableList<T>,
    ): Int {
        require(combined.size == length + 1) {
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
        for (i in nearest.indices) {
            val n = nearest[i]
            val w = weights[i]
            val shift = n.index - minIdx
            val row = sincs[n.subindex].data
            for (k in 0 until length) {
                combined[shift + k] = sample.add(combined[shift + k], sample.mul(w, row[k]))
            }
        }
        return minIdx
    }

    internal companion object {
        internal fun <T> new(
            sincLen: Int,
            oversamplingFactor: Int,
            fCutoff: Float,
            window: WindowFunction,
            sample: Sample<T>,
        ): AvxInterpolatorResult<T> {
            val missing = AVX_FEATURES.firstOrNull { !it.isDetected() }
            if (missing != null) {
                return AvxInterpolatorResult.Err(MissingCpuFeature(missing))
            }
            require(sincLen % 8 == 0) { "Sinc length must be a multiple of 8." }
            val rawSincs = makeSincs(sincLen, oversamplingFactor, fCutoff, window, sample)
            val sincs = rawSincs.map { row -> AlignedBuf.fromSlice(row) }
            return AvxInterpolatorResult.Ok(
                AvxInterpolator(
                    sincs = sincs,
                    length = sincLen,
                    nbrSincsCount = oversamplingFactor,
                    sample = sample,
                ),
            )
        }
    }
}

internal sealed class AvxInterpolatorResult<T> {
    internal data class Ok<T>(
        val value: AvxInterpolator<T>,
    ) : AvxInterpolatorResult<T>()

    internal data class Err<T>(
        val error: MissingCpuFeature,
    ) : AvxInterpolatorResult<T>()

    internal fun unwrap(): AvxInterpolator<T> =
        when (this) {
            is Ok -> value
            is Err -> throw IllegalStateException(error.toString())
        }
}

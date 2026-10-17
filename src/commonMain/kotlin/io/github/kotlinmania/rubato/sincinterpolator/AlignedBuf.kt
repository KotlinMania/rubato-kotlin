// port-lint: source rubato/src/sinc_interpolator/aligned_buf.rs
package io.github.kotlinmania.rubato.sincinterpolator

import io.github.kotlinmania.rubato.Sample

/**
 * Heap buffer of `T` for sinc tables and the combined-sinc scratch.
 */
internal class AlignedBuf<T>(
    internal val data: MutableList<T>,
) {
    internal val size: Int
        get() = data.size

    internal operator fun get(index: Int): T = data[index]

    internal operator fun set(index: Int, value: T) {
        data[index] = value
    }

    internal fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = data.subList(fromIndex, toIndex)

    internal companion object {
        internal fun <T> zeroed(len: Int, sample: Sample<T>): AlignedBuf<T> =
            AlignedBuf(sample.newList(len))

        internal fun <T> fromSlice(src: List<T>): AlignedBuf<T> =
            AlignedBuf(src.toMutableList())
    }
}

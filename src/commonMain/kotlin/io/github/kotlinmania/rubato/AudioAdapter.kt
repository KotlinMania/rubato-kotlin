package io.github.kotlinmania.rubato

/**
 * Read-only audio buffer adapter (`audioadapter::Adapter`).
 */
internal interface Adapter<T> {
    fun channels(): Int

    fun frames(): Int

    fun readSample(
        channel: Int,
        frame: Int,
    ): T?

    fun copyFromChannelToSlice(
        channel: Int,
        skip: Int,
        slice: MutableList<T>,
        count: Int = slice.size,
    ): Int {
        for (i in 0 until count) {
            val v = readSample(channel, skip + i)
            if (v != null) {
                slice[i] = v
            }
        }
        return count
    }
}

/**
 * Mutable audio buffer adapter (`audioadapter::AdapterMut`).
 */
internal interface AdapterMut<T> : Adapter<T> {
    fun writeSample(
        channel: Int,
        frame: Int,
        value: T,
    ): Boolean

    fun copyFromSliceToChannel(
        channel: Int,
        skip: Int,
        slice: List<T>,
        count: Int = slice.size,
    ): Int {
        for (i in 0 until count) {
            writeSample(channel, skip + i, slice[i])
        }
        return count
    }

    fun copyFramesWithin(
        src: Int,
        dest: Int,
        count: Int,
    ) {
        val chs = channels()
        if (dest < src) {
            for (f in 0 until count) {
                for (c in 0 until chs) {
                    val v = readSample(c, src + f)
                    if (v != null) {
                        writeSample(c, dest + f, v)
                    }
                }
            }
        } else if (dest > src) {
            for (f in (count - 1) downTo 0) {
                for (c in 0 until chs) {
                    val v = readSample(c, src + f)
                    if (v != null) {
                        writeSample(c, dest + f, v)
                    }
                }
            }
        }
    }
}

/**
 * Owned interleaved audio buffer (`audioadapter_buffers::owned::InterleavedOwned`).
 */
internal class InterleavedOwned<T>(
    private val data: MutableList<T>,
    private val channelCount: Int,
    private val frameCount: Int,
) : AdapterMut<T> {
    override fun channels(): Int = channelCount

    override fun frames(): Int = frameCount

    override fun readSample(
        channel: Int,
        frame: Int,
    ): T? {
        if (channel < 0 || channel >= channelCount || frame < 0 || frame >= frameCount) {
            return null
        }
        return data[frame * channelCount + channel]
    }

    override fun writeSample(
        channel: Int,
        frame: Int,
        value: T,
    ): Boolean {
        if (channel < 0 || channel >= channelCount || frame < 0 || frame >= frameCount) {
            return false
        }
        data[frame * channelCount + channel] = value
        return true
    }

    internal fun takeData(): MutableList<T> = data

    internal companion object {
        internal fun <T> new(
            initValue: T,
            channels: Int,
            frames: Int,
        ): InterleavedOwned<T> =
            InterleavedOwned(
                data = MutableList(channels * frames) { initValue },
                channelCount = channels,
                frameCount = frames,
            )

        internal fun <T> newFrom(
            data: MutableList<T>,
            channels: Int,
            frames: Int,
        ): InterleavedOwned<T>? =
            if (data.size >= channels * frames) {
                InterleavedOwned(data, channels, frames)
            } else {
                null
            }
    }
}

/**
 * Sequential slice of per-channel vectors (`audioadapter_buffers::direct::SequentialSliceOfVecs`).
 */
internal class SequentialSliceOfVecs<T>(
    internal val data: List<MutableList<T>>,
    private val channelCount: Int,
    private val frameCount: Int,
) : AdapterMut<T> {
    override fun channels(): Int = channelCount

    override fun frames(): Int = frameCount

    override fun readSample(
        channel: Int,
        frame: Int,
    ): T? {
        if (channel < 0 || channel >= channelCount || frame < 0 || frame >= frameCount) {
            return null
        }
        return data[channel][frame]
    }

    override fun writeSample(
        channel: Int,
        frame: Int,
        value: T,
    ): Boolean {
        if (channel < 0 || channel >= channelCount || frame < 0 || frame >= frameCount) {
            return false
        }
        data[channel][frame] = value
        return true
    }

    override fun copyFromChannelToSlice(
        channel: Int,
        skip: Int,
        slice: MutableList<T>,
        count: Int,
    ): Int {
        val ch = data[channel]
        for (i in 0 until count) {
            slice[i] = ch[skip + i]
        }
        return count
    }

    override fun copyFromSliceToChannel(
        channel: Int,
        skip: Int,
        slice: List<T>,
        count: Int,
    ): Int {
        val ch = data[channel]
        for (i in 0 until count) {
            ch[skip + i] = slice[i]
        }
        return count
    }

    internal companion object {
        internal fun <T> new(
            data: List<List<T>>,
            channels: Int,
            frames: Int,
        ): SequentialSliceOfVecs<T>? {
            if (data.size < channels) return null
            for (c in 0 until channels) {
                if (data[c].size < frames) return null
            }
            return SequentialSliceOfVecs(
                data = data.map { it.toMutableList() },
                channelCount = channels,
                frameCount = frames,
            )
        }

        internal fun <T> newMut(
            data: List<MutableList<T>>,
            channels: Int,
            frames: Int,
        ): SequentialSliceOfVecs<T>? {
            if (data.size < channels) return null
            for (c in 0 until channels) {
                if (data[c].size < frames) return null
            }
            return SequentialSliceOfVecs(
                data = data,
                channelCount = channels,
                frameCount = frames,
            )
        }
    }
}

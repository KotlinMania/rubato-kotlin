package io.github.kotlinmania.rubato

/**
 * A struct for providing optional parameters when calling `processIntoBuffer`.
 *
 * All fields have sensible defaults: zero offsets, no partial length, and all channels active.
 */
public data class Indexing(
    /**
     * Number of frames to skip at the beginning of the input buffer before reading.
     */
    public var inputOffset: Int = 0,
    /**
     * Number of frames to skip at the beginning of the output buffer before writing.
     */
    public var outputOffset: Int = 0,
    /**
     * Set when the input buffer contains fewer valid frames than `inputFramesNext` requires.
     */
    public var partialLen: Int? = null,
    /**
     * Optional per-channel processing mask.
     */
    public var activeChannelsMask: List<Boolean>? = null,
) {
    /**
     * Set the number of frames to skip at the start of the input buffer.
     */
    public fun withInputOffset(frames: Int): Indexing = copy(inputOffset = frames)

    /**
     * Set the number of frames to skip at the start of the output buffer.
     */
    public fun withOutputOffset(frames: Int): Indexing = copy(outputOffset = frames)

    /**
     * Set the number of valid input frames available for a partial (final) chunk.
     */
    public fun withPartialLen(frames: Int): Indexing = copy(partialLen = frames)

    /**
     * Set the per-channel processing mask.
     */
    public fun withActiveChannelsMask(mask: List<Boolean>): Indexing = copy(activeChannelsMask = mask.toList())

    public companion object {
        /**
         * Create an [Indexing] with all fields at their defaults.
         */
        public fun new(): Indexing = Indexing()
    }
}

internal data class BufferOffsets(
    val inputOffset: Int,
    val outputOffset: Int,
)

internal fun getOffsets(indexing: Indexing?): BufferOffsets =
    if (indexing != null) {
        BufferOffsets(indexing.inputOffset, indexing.outputOffset)
    } else {
        BufferOffsets(0, 0)
    }

internal fun getPartialLen(indexing: Indexing?): Int? = indexing?.partialLen

/**
 * Helper to update the mask from an optional [Indexing] struct.
 */
internal fun updateMask(
    indexing: Indexing?,
    mask: BooleanArray,
): ResampleResult<Unit> {
    if (indexing != null) {
        val newMask = indexing.activeChannelsMask
        if (newMask != null) {
            if (newMask.size != mask.size) {
                return ResampleResult.Err(
                    ResampleError.WrongNumberOfMaskChannels(
                        expected = mask.size,
                        actual = newMask.size,
                    ),
                )
            }
            for (i in mask.indices) {
                mask[i] = newMask[i]
            }
            return ResampleResult.Ok(Unit)
        }
    }
    for (i in mask.indices) {
        mask[i] = true
    }
    return ResampleResult.Ok(Unit)
}

// port-lint: source rubato/src/interpolation.rs
package io.github.kotlinmania.rubato

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Nearest time point in `(index, subindex)` format.
 */
internal data class IndexSubindex(
    val index: Int,
    val subindex: Int,
)

/**
 * Get the two nearest time points for time `t` in format `(index, subindex)`.
 */
internal fun getNearestTimes2(
    t: Double,
    factor: Int,
    points: Array<IndexSubindex>,
) {
    val tFloor = floor(t)
    var index = tFloor.toInt()
    var subindex = floor((t - tFloor) * factor.toDouble()).toInt()
    points[0] = IndexSubindex(index, subindex)
    subindex += 1
    if (subindex >= factor) {
        subindex -= factor
        index += 1
    }
    points[1] = IndexSubindex(index, subindex)
}

/**
 * Get the three nearest time points for time `t` in format `(index, subindex)`.
 */
internal fun getNearestTimes3(
    t: Double,
    factor: Int,
    points: Array<IndexSubindex>,
) {
    val tFloor = floor(t)
    val start = tFloor.toInt()
    val frac = floor((t - tFloor) * factor.toDouble()).toInt()
    for (sub in 0 until 3) {
        var index = start
        var subindex = frac + sub
        if (subindex < 0) {
            subindex += factor
            index -= 1
        } else if (subindex >= factor) {
            subindex -= factor
            index += 1
        }
        points[sub] = IndexSubindex(index, subindex)
    }
}

/**
 * Get the four nearest time points for time `t` in format `(index, subindex)`.
 */
internal fun getNearestTimes4(
    t: Double,
    factor: Int,
    points: Array<IndexSubindex>,
) {
    val tFloor = floor(t)
    val start = tFloor.toInt()
    val frac = floor((t - tFloor) * factor.toDouble()).toInt()
    for ((idx, sub) in (-1 until 3).withIndex()) {
        var index = start
        var subindex = frac + sub
        if (subindex < 0) {
            subindex += factor
            index -= 1
        } else if (subindex >= factor) {
            subindex -= factor
            index += 1
        }
        points[idx] = IndexSubindex(index, subindex)
    }
}

/**
 * Get the nearest time point for time `t` in format `(index, subindex)`.
 */
internal fun getNearestTime(
    t: Double,
    factor: Int,
): IndexSubindex {
    val tFloor = floor(t)
    var index = tFloor.toInt()
    var subindex = ((t - tFloor) * factor.toDouble()).roundToInt()
    if (subindex >= factor) {
        subindex -= factor
        index += 1
    }
    return IndexSubindex(index, subindex)
}

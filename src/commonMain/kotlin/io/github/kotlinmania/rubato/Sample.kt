// port-lint: source rubato/src/sample.rs
package io.github.kotlinmania.rubato

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Operations governing a single sample type (`Float` or `Double`).
 */
internal interface Sample<T> {
    val pi: T
    val zero: T
    val one: T

    fun sinOf(value: T): T

    fun cosOf(value: T): T

    fun add(a: T, b: T): T

    fun sub(a: T, b: T): T

    fun mul(a: T, b: T): T

    fun div(a: T, b: T): T

    fun neg(a: T): T

    fun absOf(a: T): T

    fun coerceFromInt(value: Int): T

    fun coerceFromDouble(value: Double): T

    fun coerceFromFloat(value: Float): T

    fun toDouble(value: T): Double

    fun newList(size: Int, init: T = zero): MutableList<T>
}

internal object F32Sample : Sample<Float> {
    override val pi: Float = PI.toFloat()
    override val zero: Float = 0.0f
    override val one: Float = 1.0f

    override fun sinOf(value: Float): Float = sin(value)

    override fun cosOf(value: Float): Float = cos(value)

    override fun add(a: Float, b: Float): Float = a + b

    override fun sub(a: Float, b: Float): Float = a - b

    override fun mul(a: Float, b: Float): Float = a * b

    override fun div(a: Float, b: Float): Float = a / b

    override fun neg(a: Float): Float = -a

    override fun absOf(a: Float): Float = abs(a)

    override fun coerceFromInt(value: Int): Float = value.toFloat()

    override fun coerceFromDouble(value: Double): Float = value.toFloat()

    override fun coerceFromFloat(value: Float): Float = value

    override fun toDouble(value: Float): Double = value.toDouble()

    override fun newList(size: Int, init: Float): MutableList<Float> = MutableList(size) { init }
}

internal object F64Sample : Sample<Double> {
    override val pi: Double = PI
    override val zero: Double = 0.0
    override val one: Double = 1.0

    override fun sinOf(value: Double): Double = sin(value)

    override fun cosOf(value: Double): Double = cos(value)

    override fun add(a: Double, b: Double): Double = a + b

    override fun sub(a: Double, b: Double): Double = a - b

    override fun mul(a: Double, b: Double): Double = a * b

    override fun div(a: Double, b: Double): Double = a / b

    override fun neg(a: Double): Double = -a

    override fun absOf(a: Double): Double = abs(a)

    override fun coerceFromInt(value: Int): Double = value.toDouble()

    override fun coerceFromDouble(value: Double): Double = value

    override fun coerceFromFloat(value: Float): Double = value.toDouble()

    override fun toDouble(value: Double): Double = value

    override fun newList(size: Int, init: Double): MutableList<Double> = MutableList(size) { init }
}

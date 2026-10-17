// port-lint: source rubato/src/error.rs
package io.github.kotlinmania.rubato

/**
 * An identifier for a CPU feature.
 */
public enum class CpuFeature {
    /**
     * x86 sse3 CPU feature.
     */
    Sse3,

    /**
     * x86_64 avx CPU feature.
     */
    Avx,

    /**
     * the fma CPU feature.
     */
    Fma,

    /**
     * aarch64 neon CPU feature.
     */
    Neon,
    ;

    /**
     * Test if the given CPU feature is detected.
     */
    public fun isDetected(): Boolean = true

    override fun toString(): String =
        when (this) {
            Sse3 -> "sse3"
            Avx -> "avx"
            Fma -> "fma"
            Neon -> "neon"
        }
}

/**
 * Error raised when trying to use a CPU feature which is not supported.
 */
public data class MissingCpuFeature(
    public val feature: CpuFeature,
) {
    override fun toString(): String = "Missing CPU feature `$feature`"
}

/**
 * The error type returned when constructing a resampler.
 */
public sealed class ResamplerConstructionError {
    public data class InvalidSampleRate(
        public val input: Int,
        public val output: Int,
    ) : ResamplerConstructionError()

    public data class InvalidRelativeRatio(
        public val provided: Double,
    ) : ResamplerConstructionError()

    public data class InvalidRatio(
        public val provided: Double,
    ) : ResamplerConstructionError()

    public data class InvalidChunkSize(
        public val provided: Int,
    ) : ResamplerConstructionError()

    override fun toString(): String =
        when (this) {
            is InvalidSampleRate -> {
                "Input and output sample rates must both be > 0. Provided input: $input, provided output: $output"
            }

            is InvalidRatio -> {
                "Invalid resampleRatio provided: $provided. resampleRatio must be finite and > 0"
            }

            is InvalidRelativeRatio -> {
                "Invalid maxResampleRatioRelative provided: $provided. maxResampleRatioRelative must be finite and >= 1"
            }

            is InvalidChunkSize -> {
                "Invalid chunkSize provided: $provided. chunkSize must be >= 1"
            }
        }
}

/**
 * The error type used by rubato.
 */
public sealed class ResampleError {
    /**
     * Error raised when setting the resample ratio outside the maximum range specified when
     * the resampler was constructed.
     */
    public data class RatioOutOfBounds(
        public val provided: Double,
        public val original: Double,
        public val maxRelativeRatio: Double,
    ) : ResampleError()

    /**
     * Error raised when setting the resample ratio outside the range the resampler can sustain.
     */
    public data class RatioOutsideRange(
        public val provided: Double,
        public val min: Double,
        public val max: Double,
    ) : ResampleError()

    /**
     * Error raised when the number of channels in the input buffer does not match the value expected.
     */
    public data class WrongNumberOfInputChannels(
        public val expected: Int,
        public val actual: Int,
    ) : ResampleError()

    /**
     * Error raised when the number of channels in the output buffer does not match the value expected.
     */
    public data class WrongNumberOfOutputChannels(
        public val expected: Int,
        public val actual: Int,
    ) : ResampleError()

    /**
     * Error raised when the number of channels in the mask does not match the value expected.
     */
    public data class WrongNumberOfMaskChannels(
        public val expected: Int,
        public val actual: Int,
    ) : ResampleError()

    /**
     * Error raised when the number of frames in an input channel is less than the minimum expected.
     */
    public data class InsufficientInputBufferSize(
        public val expected: Int,
        public val actual: Int,
    ) : ResampleError()

    /**
     * Error raised when the number of frames in an output channel is less than the minimum expected.
     */
    public data class InsufficientOutputBufferSize(
        public val expected: Int,
        public val actual: Int,
    ) : ResampleError()

    public data class InvalidChunkSize(
        public val max: Int,
        public val requested: Int,
    ) : ResampleError()

    override fun toString(): String =
        when (this) {
            is RatioOutOfBounds -> {
                val minBound = original / maxRelativeRatio
                val maxBound = original * maxRelativeRatio
                "New resample ratio out of bounds. Provided ratio $provided, original resample ratio $original, " +
                    "maximum relative ratio $maxRelativeRatio, allowed absolute range $minBound to $maxBound"
            }

            is RatioOutsideRange -> {
                "New resample ratio $provided is outside the supported range $min to $max"
            }

            is WrongNumberOfInputChannels -> {
                "Wrong number of channels $actual in input, expected $expected"
            }

            is WrongNumberOfOutputChannels -> {
                "Wrong number of channels $actual in output, expected $expected"
            }

            is WrongNumberOfMaskChannels -> {
                "Wrong number of channels $actual in mask, expected $expected"
            }

            is InsufficientInputBufferSize -> {
                "Insufficient input buffer size $actual, expected $expected frames"
            }

            is InsufficientOutputBufferSize -> {
                "Insufficient output buffer size $actual, expected $expected frames"
            }

            is InvalidChunkSize -> {
                "Invalid chunk size $requested, value must be non-zero and cannot exceed $max"
            }
        }
}

/**
 * Outcome type for resampling operations.
 */
internal sealed class ResampleResult<out T> {
    internal data class Ok<out T>(
        val value: T,
    ) : ResampleResult<T>()

    internal data class Err(
        val error: ResampleError,
    ) : ResampleResult<Nothing>()

    internal fun isOk(): Boolean = this is Ok

    internal fun isErr(): Boolean = this is Err

    internal fun unwrap(): T =
        when (this) {
            is Ok -> value
            is Err -> throw IllegalStateException(error.toString())
        }

    internal fun expect(msg: String): T =
        when (this) {
            is Ok -> value
            is Err -> throw IllegalStateException("$msg: $error")
        }

    internal fun errorOrNull(): ResampleError? =
        when (this) {
            is Ok -> null
            is Err -> error
        }
}

/**
 * Outcome type for constructing a resampler.
 */
internal sealed class ConstructionResult<out T> {
    internal data class Ok<out T>(
        val value: T,
    ) : ConstructionResult<T>()

    internal data class Err(
        val error: ResamplerConstructionError,
    ) : ConstructionResult<Nothing>()

    internal fun isOk(): Boolean = this is Ok

    internal fun isErr(): Boolean = this is Err

    internal fun unwrap(): T =
        when (this) {
            is Ok -> value
            is Err -> throw IllegalStateException(error.toString())
        }

    internal fun errorOrNull(): ResamplerConstructionError? =
        when (this) {
            is Ok -> null
            is Err -> error
        }
}

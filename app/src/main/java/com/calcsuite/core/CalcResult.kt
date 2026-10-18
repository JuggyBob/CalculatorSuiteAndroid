package com.calcsuite.core

/**
 * Uniform result type shared by every calculation engine.
 *
 * Engines never throw for invalid user input: they return [Err] with a short,
 * user-facing message and a coarse category so that screens can present a
 * consistent error treatment.
 */
enum class ErrorKind {
    /** Text could not be parsed as a number / value. */
    InvalidInput,

    /** Expression syntax is malformed. */
    Syntax,

    /** Mathematically undefined operation (log of a negative number, ...). */
    Domain,

    /** Division by zero. */
    DivisionByZero,

    /** Result cannot be represented. */
    Overflow,

    /** Operation is undefined for the given inputs. */
    Undefined,

    /** Input values contradict each other (e.g. inconsistent linear system). */
    Inconsistent,

    /** Not enough information to determine a unique answer. */
    Insufficient,

    /** External data (network) unavailable. */
    Unavailable,
}

data class CalcError(val kind: ErrorKind, val message: String)

sealed interface CalcResult<out T> {
    data class Ok<T>(val value: T) : CalcResult<T>
    data class Err(val error: CalcError) : CalcResult<Nothing>

    fun valueOrNull(): T? = (this as? Ok)?.value
}

fun <T> ok(value: T): CalcResult<T> = CalcResult.Ok(value)

fun <T> err(kind: ErrorKind, message: String): CalcResult<T> = CalcResult.Err(CalcError(kind, message))

inline fun <T, R> CalcResult<T>.map(transform: (T) -> R): CalcResult<R> = when (this) {
    is CalcResult.Ok -> CalcResult.Ok(transform(value))
    is CalcResult.Err -> this
}

inline fun <T, R> CalcResult<T>.flatMap(transform: (T) -> CalcResult<R>): CalcResult<R> = when (this) {
    is CalcResult.Ok -> transform(value)
    is CalcResult.Err -> this
}

/** Validates and collects field-level errors produced by UI layers. */
class FieldErrors(private val errors: Map<String, String>) {
    operator fun get(field: String): String? = errors[field]
    fun hasErrors(): Boolean = errors.isNotEmpty()
    companion object {
        val NONE = FieldErrors(emptyMap())
    }
}

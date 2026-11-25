package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err

/**
 * Shared limits for the recursive calculus code.
 *
 * A recursive-descent parser can be made to overflow the JVM stack with a few
 * thousand nested parentheses or signs. The parser counts its own nesting and
 * stops well before that; the guards below are a second line of defence for the
 * tree walks (printing, simplifying, differentiating) that a parsed tree can
 * still trigger.
 */
internal const val MAX_EXPRESSION_DEPTH = 200
internal const val MAX_EXPRESSION_LENGTH = 20_000
internal const val TOO_DEEP_MESSAGE = "The expression is nested too deeply"

/**
 * Runs a public engine entry and converts a [StackOverflowError] into the
 * ordinary error result the caller expects. Engines never throw for bad user
 * input; an over-deep expression is bad input, not a crash.
 */
internal inline fun <T> guarded(
    kind: ErrorKind = ErrorKind.Overflow,
    message: String = TOO_DEEP_MESSAGE,
    body: () -> CalcResult<T>,
): CalcResult<T> = try {
    body()
} catch (e: StackOverflowError) {
    err(kind, message)
}

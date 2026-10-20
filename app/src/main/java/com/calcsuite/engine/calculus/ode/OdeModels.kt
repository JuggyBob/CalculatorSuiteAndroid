package com.calcsuite.engine.calculus.ode

import com.calcsuite.engine.calculus.Node

/**
 * The solving strategy that produced a result.
 *
 * Each entry names a real method with real assumptions; nothing here is a
 * catch-all that pretends to have solved something.
 */
enum class OdeMethod(val label: String, val assumption: String) {
    SEPARABLE(
        "Separable equations",
        "The right-hand side splits into a factor of x alone and a factor of y alone.",
    ),
    FIRST_ORDER_LINEAR(
        "First-order linear equations",
        "The equation is y′ + P(x)·y = Q(x), so an integrating factor exists.",
    ),
    CONSTANT_COEFFICIENT(
        "Linear equations with constant coefficients",
        "The coefficients of the derivatives are constants, so the characteristic equation applies.",
    ),
    EXACT(
        "Exact equations",
        "M(x,y) + N(x,y)·y′ = 0 has an integrating factor of 1.",
    ),
    HOMOGENEOUS(
        "Homogeneous first-order equations",
        "The right-hand side depends only on the ratio y/x, so y = v·x substitutes.",
    ),
    NUMERICAL(
        "Numerical approximation",
        "No closed form was produced, so the solution is stepped forward numerically.",
    ),
}

/**
 * A differential equation after parsing, in the standard form
 *
 *     a_n·y⁽ⁿ⁾ + … + a_1·y′ + a_0·y = F(x, y, y′, …)
 *
 * The coefficients are kept as trees rather than numbers, so an equation such as
 * `y′ + x·y = 1` — whose coefficient of y depends on x — is still recognised
 * as a linear equation.
 */
data class ParsedOde(
    /** The text the user typed. */
    val original: String,
    /** The independent variable, for example `x`. */
    val independent: String,
    /** The dependent variable, for example `y`. */
    val dependent: String,
    /** The order of the equation. */
    val order: Int,
    /**
     * The coefficient of each derivative, lowest first: [0] multiplies y,
     * [1] multiplies y′, and so on. A coefficient is null when the equation
     * does not contain that derivative.
     */
    val coefficients: List<Node?>,
    /** The right-hand side, F. */
    val rhs: Node,
    /** The equation rewritten with the leading coefficient divided out. */
    val normalisedRhs: Node,
    /** A plain-text rendering of the equation. */
    val display: String,
) {
    /** The coefficient of the n-th derivative, or null. */
    fun coefficient(index: Int): Node? = coefficients.getOrNull(index)
}

/** What the engine could work out about an equation before solving it. */
data class OdeClassification(
    val order: Int,
    /** True when the equation is linear in y and its derivatives. */
    val isLinear: Boolean,
    /** True when there is no forcing term, so the equation is homogeneous. */
    val isHomogeneous: Boolean,
    /** True when the right-hand side is a function of y/x alone. */
    val isHomogeneousFirstOrder: Boolean,
    /** True when the equation is exact, in the sense of an integrating factor of 1. */
    val isExact: Boolean,
    /** The split of the right-hand side when the equation is separable. */
    val separable: SeparableForm?,
    /** True when the equation is first-order linear, y′ + P(x)y = Q(x). */
    val isFirstOrderLinear: Boolean,
    /** True when every derivative coefficient is a constant. */
    val hasConstantCoefficients: Boolean,
    /** A short description of the classification, for the explanation. */
    val summary: String,
)

/** The factorisation of a separable right-hand side. */
data class SeparableForm(
    /** The factor that depends on x alone. */
    val a: Node,
    /** The factor that depends on y alone. */
    val b: Node,
)

/** One condition on the solution. */
data class OdeCondition(
    /** The order of the derivative the condition applies to: 0 for y itself. */
    val order: Int,
    /** Where the condition applies. */
    val at: Double,
    /** The value the solution takes there. */
    val value: Double,
    /** The condition as it was written. */
    val display: String,
)

/** A check that the answer really is a solution. */
data class OdeVerification(
    /** True when the check was carried out and passed. */
    val verified: Boolean,
    /** What was checked, in words. */
    val what: String,
    /** The residual, when one could be computed. */
    val detail: String?,
)

/** One row of a numerical solution. */
data class NumericalPoint(val x: Double, val y: Double, val step: Int)

/** The outcome of a numerical integration. */
data class NumericalResult(
    val method: NumericalMethod,
    val from: Double,
    val to: Double,
    val step: Double,
    val points: List<NumericalPoint>,
    val steps: List<String>,
    /** The largest step-to-step change, a rough measure of the error. */
    val errorIndicator: String?,
    /** A comparison against a known solution, when one is available. */
    val comparison: String?,
)

/** The numerical methods the tool can run. */
enum class NumericalMethod(val label: String, val order: Int, val description: String) {
    EULER(
        "Euler's method",
        1,
        "Each step takes the slope at the current point and moves in a straight line.",
    ),
    HEUN(
        "Improved Euler (Heun)",
        2,
        "Predict with Euler, then correct using the slope at the predicted point.",
    ),
    RK4(
        "Runge–Kutta 4",
        4,
        "Combines four slope estimates per step, which is far more accurate for the same work.",
    ),
}

/**
 * The result of solving a differential equation.
 *
 * Every field is filled from the solver's own work: the steps are the
 * transformations that were actually performed, the constants are the ones
 * that were actually solved for, and the verification is the residual that was
 * actually computed.
 */
data class OdeSolution(
    val equation: String,
    val method: OdeMethod,
    val classification: OdeClassification,
    /** The solution with an arbitrary constant, when one was produced. */
    val generalSolution: String?,
    /** The solution after the conditions were applied. */
    val particularSolution: String?,
    /** The constants and the values they took. */
    val constants: List<Pair<String, String>>,
    /** The derivation, step by step. */
    val steps: List<String>,
    val verification: OdeVerification?,
    /** What the solution means. */
    val interpretation: String,
    /** The assumptions the method needed. */
    val assumptions: List<String>,
    /** The numerical solution, when one was produced. */
    val numerical: NumericalResult?,
    /** A note that the answer is only partly closed form. */
    val limitation: String?,
    /**
     * The particular solution as a tree, when there is one.
     *
     * The verification uses it to substitute the answer back into the equation,
     * rather than checking the text.
     */
    val solutionNode: com.calcsuite.engine.calculus.Node? = null,
)

/** Why an equation could not be solved. */
data class OdeFailure(
    val message: String,
    /** The method that would have applied, had the engine supported it. */
    val suggestedMethod: OdeMethod?,
    /** What the user could try instead. */
    val suggestion: String?,
)

/** The outcome of a solve request. */
sealed interface OdeResult {
    data class Solved(val solution: OdeSolution) : OdeResult
    data class Unsupported(val failure: OdeFailure) : OdeResult
    data class Invalid(val message: String) : OdeResult
}

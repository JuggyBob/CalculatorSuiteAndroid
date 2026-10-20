package com.calcsuite.ui.tools.calculus

/**
 * The operations the calculus calculator can perform.
 *
 * The list is unchanged from the single screen the calculus used to live in —
 * the refactor split that screen into separate tools, it did not remove
 * anything from it.
 */
enum class CalcOperation(val label: String, val shortLabel: String) {
    DERIVATIVE("Derivative", "d/dx"),
    INTEGRAL("Integral", "∫"),
    LIMIT("Limit", "lim"),
    SERIES("Series", "Σ∞"),
    TAYLOR("Taylor", "T"),
    SUM("Sum and product", "Σ Π"),
    EQUATION("Roots", "= 0"),
    EXTREMA("Extrema", "f′=0"),
    ODE("Differential equation", "y′"),
    GRAPH("Graph", "plot"),
    GRADIENT("Gradient", "∇"),
    FOURIER("Fourier series", "Σ"),
    COMPLEX("Complex number", "i"),
    PDE("Partial differential", "∂t"),
}

/**
 * The calculus tools.
 *
 * Every entry is a separate, discoverable tool in the Calculus category. They
 * all open the same calculator with a different operation preselected, a
 * different default expression, and their own title and description — so each
 * one lands on a purpose-specific interface rather than an empty screen.
 */
enum class CalculusTool(
    /** The registry id, and the route the tool is opened by. */
    val toolId: String,
    val title: String,
    val operation: CalcOperation,
    /** The expression the tool starts with, chosen to suit the operation. */
    val defaultExpression: String,
    val description: String,
) {
    DERIVATIVES(
        "calculus.derivatives",
        "Derivatives",
        CalcOperation.DERIVATIVE,
        "x^2 + 3x",
        "Symbolic and numerical derivatives, higher orders and partials",
    ),
    INTEGRALS(
        "calculus.integrals",
        "Integrals",
        CalcOperation.INTEGRAL,
        "x^2 + 3x",
        "Indefinite and definite integration, exact where it exists",
    ),
    LIMITS(
        "calculus.limits",
        "Limits",
        CalcOperation.LIMIT,
        "sin(x)/x",
        "Finite, one-sided and infinite limits",
    ),
    TAYLOR(
        "calculus.taylor",
        "Taylor & Maclaurin",
        CalcOperation.TAYLOR,
        "e^x",
        "Expansions around a centre, with every coefficient",
    ),
    SUMMATIONS(
        "calculus.summations",
        "Summations & Products",
        CalcOperation.SUM,
        "k",
        "Finite sums and products in sigma and pi notation",
    ),
    SERIES(
        "calculus.series",
        "Infinite Series",
        CalcOperation.SERIES,
        "0.5^k",
        "Convergence of an infinite series and its closed form",
    ),
    ROOTS(
        "calculus.roots",
        "Equation Solving & Roots",
        CalcOperation.EQUATION,
        "x^2 - 5x + 6",
        "Find the real roots of an equation",
    ),
    EXTREMA(
        "calculus.extrema",
        "Extrema & Optimization",
        CalcOperation.EXTREMA,
        "x^2 - 4x",
        "Stationary points, local extrema and critical points",
    ),
    GRAPH(
        "calculus.graph",
        "Function Graphing",
        CalcOperation.GRAPH,
        "x^2 + 3x",
        "Plot a function, its derivative and an area under the curve",
    ),
    DIFFERENTIAL_EQUATIONS(
        "calculus.differential_equations",
        "Differential Equations",
        CalcOperation.ODE,
        "2y",
        "Solve ODEs symbolically or numerically, and graph the solution",
    ),
    MULTIVARIABLE(
        "calculus.multivariable",
        "Multivariable Calculus",
        CalcOperation.GRADIENT,
        "x^2 + 3xy + y^2",
        "Partial derivatives, gradients, Hessians and multiple integrals",
    ),
    FOURIER(
        "calculus.fourier",
        "Fourier Analysis",
        CalcOperation.FOURIER,
        "sin(x)",
        "Fourier series, harmonics and the discrete transform",
    ),
    COMPLEX(
        "calculus.complex",
        "Complex Analysis",
        CalcOperation.COMPLEX,
        "z^2",
        "Complex numbers, functions, contours and residues",
    ),
    PDE(
        "calculus.pde",
        "Partial Differential Equations",
        CalcOperation.PDE,
        "sin(pi*x)",
        "Heat, wave, Laplace and Poisson equations, solved numerically",
    );

    companion object {
        /** The tool for a registry id, or null when the id is not a calculus tool. */
        fun forId(toolId: String): CalculusTool? = entries.firstOrNull { it.toolId == toolId }
    }
}

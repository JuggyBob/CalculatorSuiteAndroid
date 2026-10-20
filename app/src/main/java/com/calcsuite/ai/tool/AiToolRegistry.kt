package com.calcsuite.ai.tool

/**
 * The mathematical tools the AI may choose from.
 *
 * The list was made by reading the engines rather than by reading the tool
 * names, so only the tools that take a structured mathematical input and
 * answer it are here. The unit converters, the random generator, the finance
 * and health tools, the date and time tools and the geometry diagrams are
 * left out: they do not answer a mathematical problem that can be written
 * down and solved.
 *
 * The grammar of each tool is the grammar of the parser that already
 * exists, because the engines are called as they are.
 */
object AiToolRegistry {

    /** The grammar the calculus tools share. */
    private const val CALCULUS_GRAMMAR =
        "Numbers with a dot as the decimal mark; x as the variable; " +
            "^ for powers (right-associative); * or juxtaposition for products; " +
            "/ for division; + and - for sums and differences; parentheses for grouping; " +
            "sin, cos, tan, asin, acos, atan, ln, log, exp, sqrt, cbrt, abs, floor, " +
            "ceil, round, min, max, each followed by ( ); pi, e, tau and phi for constants; " +
            "a leading - for a negative."

    private const val ALGEBRA_GRAMMAR =
        "Numbers with a dot as the decimal mark; x and y as the variables; " +
            "^ for powers; * or juxtaposition for products; / for division; " +
            "+ and - for sums and differences; parentheses for grouping; " +
            "an equation is written with a single = sign."

    /** The calculus tools. */
    private val calculusTools = listOf(
        AiToolSpec(
            id = "calculus.derivative",
            title = "Derivative",
            domain = AiToolDomain.CALCULUS,
            description = "Differentiate an expression with respect to one variable.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The expression to differentiate.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The variable to differentiate with respect to.",
                    required = false,
                    defaultText = "x",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "Differentiates with respect to one variable only. " +
                "Higher orders are given by differentiating the result again.",
            examples = listOf("x^2 + 3x", "sin(x)*x", "x^3 - 2x + 1"),
        ),
        AiToolSpec(
            id = "calculus.integral",
            title = "Integral",
            domain = AiToolDomain.CALCULUS,
            description = "Integrate an expression, either over an interval or to find " +
                "an antiderivative.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The expression to integrate.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The variable of integration.",
                    required = false,
                    defaultText = "x",
                ),
                AiArgument(
                    name = "lowerBound",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The lower limit. Leave it out for an indefinite integral.",
                    required = false,
                ),
                AiArgument(
                    name = "upperBound",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The upper limit. Leave it out for an indefinite integral.",
                    required = false,
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The bounds are numbers, not expressions. " +
                "An indefinite integral is found where the parser can find one.",
            examples = listOf("x^2 from 0 to 1", "3x + 2", "x^2 - 4 from -2 to 2"),
        ),
        AiToolSpec(
            id = "calculus.limit",
            title = "Limit",
            domain = AiToolDomain.CALCULUS,
            description = "Find the limit of an expression as the variable approaches a value.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The expression whose limit is wanted.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The variable that approaches the value.",
                    required = false,
                    defaultText = "x",
                ),
                AiArgument(
                    name = "point",
                    kind = AiArgumentKind.NUMBER,
                    description = "The value the variable approaches.",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The limit is taken from the right and from the left.",
            examples = listOf("(x^2 - 1)/(x - 1) at 1", "sin(x)/x at 0"),
        ),
        AiToolSpec(
            id = "calculus.summation",
            title = "Summation",
            domain = AiToolDomain.CALCULUS,
            description = "Add up the terms of an expression over a range of whole numbers.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The term to add up.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The index of the sum.",
                    required = false,
                    defaultText = "n",
                ),
                AiArgument(
                    name = "from",
                    kind = AiArgumentKind.INTEGER,
                    description = "The first index.",
                ),
                AiArgument(
                    name = "to",
                    kind = AiArgumentKind.INTEGER,
                    description = "The last index.",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The index must be the variable of the term. " +
                "The range may not be longer than 100000 terms.",
            examples = listOf("n from 1 to 10", "n^2 from 1 to 100", "1/n from 1 to 50"),
        ),
        AiToolSpec(
            id = "calculus.taylor",
            title = "Taylor series",
            domain = AiToolDomain.CALCULUS,
            description = "Expand an expression as a power series about a centre.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The expression to expand.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The variable of the expansion.",
                    required = false,
                    defaultText = "x",
                ),
                AiArgument(
                    name = "centre",
                    kind = AiArgumentKind.NUMBER,
                    description = "The point the series is about.",
                    required = false,
                    defaultText = "0",
                ),
                AiArgument(
                    name = "order",
                    kind = AiArgumentKind.INTEGER,
                    description = "How many terms to keep.",
                    required = false,
                    defaultText = "5",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The order must be between 0 and 12.",
            examples = listOf("sin(x) about 0 order 6", "e^x about 1 order 4"),
        ),
        AiToolSpec(
            id = "calculus.series",
            title = "Series convergence",
            domain = AiToolDomain.CALCULUS,
            description = "Decide whether a series with general terms converges.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The general term of the series.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The index of the series.",
                    required = false,
                    defaultText = "n",
                ),
                AiArgument(
                    name = "firstTerm",
                    kind = AiArgumentKind.INTEGER,
                    description = "The first index.",
                ),
                AiArgument(
                    name = "lastTerm",
                    kind = AiArgumentKind.INTEGER,
                    description = "The last index.",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The index must be the variable of the term.",
            examples = listOf("1/n^2 from 1 to 1000", "(-1)^n from 1 to 100"),
        ),
        AiToolSpec(
            id = "calculus.roots",
            title = "Roots",
            domain = AiToolDomain.CALCULUS,
            description = "Find the real roots of an expression over an interval.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The expression whose roots are wanted.",
                ),
                AiArgument(
                    name = "from",
                    kind = AiArgumentKind.NUMBER,
                    description = "Where to start looking.",
                ),
                AiArgument(
                    name = "to",
                    kind = AiArgumentKind.NUMBER,
                    description = "Where to stop looking.",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The roots are found by sampling, so a root that touches zero " +
                "without crossing it may be missed.",
            examples = listOf("x^2 - 4 from -10 to 10", "x^3 - 6x^2 + 11x - 6 from 0 to 10"),
        ),
        AiToolSpec(
            id = "calculus.extrema",
            title = "Extrema",
            domain = AiToolDomain.CALCULUS,
            description = "Find the stationary points of an expression over an interval.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The expression to examine.",
                ),
                AiArgument(
                    name = "from",
                    kind = AiArgumentKind.NUMBER,
                    description = "The start of the interval.",
                ),
                AiArgument(
                    name = "to",
                    kind = AiArgumentKind.NUMBER,
                    description = "The end of the interval.",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The interval is sampled, so a very narrow feature may be missed.",
            examples = listOf("x^3 - 3x from -5 to 5", "sin(x) from 0 to 10"),
        ),
        AiToolSpec(
            id = "calculus.ode",
            title = "Differential equation",
            domain = AiToolDomain.CALCULUS,
            description = "Solve a first-order differential equation numerically.",
            arguments = listOf(
                AiArgument(
                    name = "equation",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The equation, written as dy/dx = ... or y' = ... .",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The independent variable.",
                    required = false,
                    defaultText = "x",
                ),
                AiArgument(
                    name = "initialPoint",
                    kind = AiArgumentKind.NUMBER,
                    description = "The x the solution starts at.",
                    required = false,
                    defaultText = "0",
                ),
                AiArgument(
                    name = "initialValue",
                    kind = AiArgumentKind.NUMBER,
                    description = "The y the solution starts at.",
                    required = false,
                    defaultText = "1",
                ),
            ),
            grammar = "The right-hand side may mention x and y: dy/dx = 2y, y' = x - y.",
            limitations = "The equation is solved numerically, so the answer is an approximation.",
            examples = listOf("dy/dx = 2y", "y' = x - y from 0 with 1"),
        ),
        AiToolSpec(
            id = "calculus.partial",
            title = "Partial derivative",
            domain = AiToolDomain.CALCULUS,
            description = "Differentiate an expression of two variables with respect to one of them.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The function of two variables.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The variable to differentiate with respect to.",
                    required = false,
                    defaultText = "x",
                ),
                AiArgument(
                    name = "secondVariable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The other variable, when there is one.",
                    required = false,
                ),
                AiArgument(
                    name = "order",
                    kind = AiArgumentKind.INTEGER,
                    description = "The order of the partial derivative.",
                    required = false,
                    defaultText = "1",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "Needs the second variable when the function has one.",
            examples = listOf("x^2 + y^2 with respect to x", "x*y with respect to y"),
        ),
    )

    /** The algebra tools. */
    private val algebraTools = listOf(
        AiToolSpec(
            id = "algebra.linear",
            title = "Linear equation",
            domain = AiToolDomain.ALGEBRA,
            description = "Solve a linear equation in one variable.",
            arguments = listOf(
                AiArgument(
                    name = "equation",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The equation, with one = sign.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The unknown.",
                    required = false,
                    defaultText = "x",
                ),
            ),
            grammar = ALGEBRA_GRAMMAR,
            limitations = "The unknown must appear to the first power.",
            examples = listOf("2x + 4 = 10", "3x - 5 = 7"),
        ),
        AiToolSpec(
            id = "algebra.quadratic",
            title = "Quadratic equation",
            domain = AiToolDomain.ALGEBRA,
            description = "Solve a quadratic equation in one variable.",
            arguments = listOf(
                AiArgument(
                    name = "equation",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The equation, with one = sign.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The unknown.",
                    required = false,
                    defaultText = "x",
                ),
            ),
            grammar = ALGEBRA_GRAMMAR,
            limitations = "The unknown must appear to the second power, or the equation " +
                "is solved as a linear one.",
            examples = listOf("x^2 - 5x + 6 = 0", "2x^2 + 7x + 3 = 0"),
        ),
        AiToolSpec(
            id = "algebra.system",
            title = "Two equations, two unknowns",
            domain = AiToolDomain.ALGEBRA,
            description = "Solve two linear equations in x and y at once.",
            arguments = listOf(
                AiArgument(
                    name = "a1",
                    kind = AiArgumentKind.NUMBER,
                    description = "The coefficient of x in the first equation.",
                ),
                AiArgument(
                    name = "b1",
                    kind = AiArgumentKind.NUMBER,
                    description = "The coefficient of y in the first equation.",
                ),
                AiArgument(
                    name = "c1",
                    kind = AiArgumentKind.NUMBER,
                    description = "The right-hand side of the first equation.",
                ),
                AiArgument(
                    name = "a2",
                    kind = AiArgumentKind.NUMBER,
                    description = "The coefficient of x in the second equation.",
                ),
                AiArgument(
                    name = "b2",
                    kind = AiArgumentKind.NUMBER,
                    description = "The coefficient of y in the second equation.",
                ),
                AiArgument(
                    name = "c2",
                    kind = AiArgumentKind.NUMBER,
                    description = "The right-hand side of the second equation.",
                ),
            ),
            grammar = "The equations are written as a1 x + b1 y = c1 and a2 x + b2 y = c2, " +
                "and the six numbers are given one by one. A number that is not " +
                "there is 0, and a term that is subtracted has a negative coefficient.",
            limitations = "Both equations must be linear. The answer is put back into " +
                "the two equations before it is shown, so a wrong set of " +
                "coefficients is refused.",
            examples = listOf(
                "2x + y = 5 gives a1 2, b1 1, c1 5; x - y = 1 gives a2 1, b2 -1, c2 1",
            ),
        ),
        AiToolSpec(
            id = "algebra.inequality",
            title = "Inequality",
            domain = AiToolDomain.ALGEBRA,
            description = "Solve an inequality in one variable.",
            arguments = listOf(
                AiArgument(
                    name = "inequality",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The inequality, with <, <=, > or >=.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The unknown.",
                    required = false,
                    defaultText = "x",
                ),
            ),
            grammar = ALGEBRA_GRAMMAR,
            limitations = "One inequality in one variable. A chain such as -2 < 3x + 1 <= 10 is " +
                "read as a compound inequality.",
            examples = listOf("2x + 3 < 11", "x^2 - 4 >= 0"),
        ),
        AiToolSpec(
            id = "algebra.gcf",
            title = "Highest common factor",
            domain = AiToolDomain.ALGEBRA,
            description = "Find the highest common factor of two whole numbers.",
            arguments = listOf(
                AiArgument("first", AiArgumentKind.INTEGER, "The first number."),
                AiArgument("second", AiArgumentKind.INTEGER, "The second number."),
            ),
            grammar = "Two whole numbers.",
            limitations = "Whole numbers only.",
            examples = listOf("12 and 18", "36 and 24"),
        ),
        AiToolSpec(
            id = "algebra.lcm",
            title = "Lowest common multiple",
            domain = AiToolDomain.ALGEBRA,
            description = "Find the lowest common multiple of two whole numbers.",
            arguments = listOf(
                AiArgument("first", AiArgumentKind.INTEGER, "The first number."),
                AiArgument("second", AiArgumentKind.INTEGER, "The second number."),
            ),
            grammar = "Two whole numbers.",
            limitations = "Whole numbers only.",
            examples = listOf("4 and 6", "12 and 15"),
        ),
        AiToolSpec(
            id = "algebra.prime",
            title = "Prime factorisation",
            domain = AiToolDomain.ALGEBRA,
            description = "Write a whole number as a product of primes.",
            arguments = listOf(
                AiArgument("number", AiArgumentKind.INTEGER, "The number."),
            ),
            grammar = "One whole number.",
            limitations = "Whole numbers only.",
            examples = listOf("60", "97"),
        ),
        AiToolSpec(
            id = "algebra.combinatorics",
            title = "Combinations and permutations",
            domain = AiToolDomain.ALGEBRA,
            description = "Count the ways of choosing or ordering items.",
            arguments = listOf(
                AiArgument("n", AiArgumentKind.INTEGER, "The number of items to choose from."),
                AiArgument("r", AiArgumentKind.INTEGER, "How many are chosen."),
            ),
            grammar = "Two whole numbers.",
            limitations = "Whole numbers only.",
            examples = listOf("5 and 2", "10 and 3"),
        ),
    )

    /** The advanced mathematics tools. */
    private val advancedTools = listOf(
        AiToolSpec(
            id = "advanced.linear_algebra",
            title = "Vectors and matrices",
            domain = AiToolDomain.ADVANCED,
            description = "Work with vectors: their lengths, their angle, a projection, " +
                "or the least-squares fit of a line through points.",
            arguments = listOf(
                AiArgument(
                    name = "operation",
                    kind = AiArgumentKind.VARIABLE,
                    description = "One of norm, distance, angle, projection, inner_product, " +
                        "gram_schmidt, least_squares.",
                ),
                AiArgument(
                    name = "firstVector",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The first vector, as numbers separated by commas, " +
                        "for example 3, 4.",
                ),
                AiArgument(
                    name = "secondVector",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The second vector, where the operation needs one.",
                    required = false,
                ),
            ),
            grammar = "A vector is written as its numbers separated by commas.",
            limitations = "The vectors must have the same number of components.",
            examples = listOf("norm of 3, 4", "angle between 1, 0 and 0, 1"),
        ),
        AiToolSpec(
            id = "advanced.measure",
            title = "Measure of a set",
            domain = AiToolDomain.ADVANCED,
            description = "Measure one interval, or the union or the overlap of two intervals.",
            arguments = listOf(
                AiArgument(
                    name = "firstSet",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The first set, as its bounds separated by a comma, " +
                        "for example 0, 2.",
                ),
                AiArgument(
                    name = "secondSet",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The second set, where the operation needs one.",
                    required = false,
                ),
            ),
            grammar = "An interval is written as its two bounds separated by a comma.",
            limitations = "Intervals of real numbers only.",
            examples = listOf("0, 2", "union of 0, 2 and 1, 3"),
        ),
        AiToolSpec(
            id = "advanced.complex",
            title = "Complex numbers",
            domain = AiToolDomain.ADVANCED,
            description = "Work with a complex function: its value at a point, or the " +
                "Cauchy-Riemann conditions.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The function of z, written with z as the variable.",
                ),
                AiArgument(
                    name = "point",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The point, as its real and imaginary parts separated by " +
                        "a comma, for example 1, 2.",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The variable must be z.",
            examples = listOf("z^2 at 1, 1", "z^3 + 1 at 0, 1"),
        ),
        AiToolSpec(
            id = "advanced.fourier",
            title = "Fourier series",
            domain = AiToolDomain.ADVANCED,
            description = "Find the Fourier series of an expression over an interval.",
            arguments = listOf(
                AiArgument(
                    name = "expression",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The function of one variable.",
                ),
                AiArgument(
                    name = "variable",
                    kind = AiArgumentKind.VARIABLE,
                    description = "The variable.",
                    required = false,
                    defaultText = "x",
                ),
                AiArgument(
                    name = "from",
                    kind = AiArgumentKind.NUMBER,
                    description = "The start of the interval.",
                    required = false,
                    defaultText = "0",
                ),
                AiArgument(
                    name = "to",
                    kind = AiArgumentKind.NUMBER,
                    description = "The end of the interval.",
                    required = false,
                    defaultText = "1",
                ),
            ),
            grammar = CALCULUS_GRAMMAR,
            limitations = "The function must be given over a whole number of periods.",
            examples = listOf("sin(x) from 0 to 1", "x from 0 to 1"),
        ),
        AiToolSpec(
            id = "advanced.pde",
            title = "Partial differential equation",
            domain = AiToolDomain.ADVANCED,
            description = "Solve the heat or the wave equation along a rod, or Laplace's " +
                "or Poisson's equation on a square.",
            arguments = listOf(
                AiArgument(
                    name = "equation",
                    kind = AiArgumentKind.VARIABLE,
                    description = "One of heat, wave, laplace, poisson.",
                ),
                AiArgument(
                    name = "initial",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The initial value along the rod, for the heat and " +
                        "the wave equations.",
                    required = false,
                    defaultText = "sin(pi*x)",
                ),
                AiArgument(
                    name = "boundary",
                    kind = AiArgumentKind.EXPRESSION,
                    description = "The value on the four sides of the square, for " +
                        "Laplace's and Poisson's equations. The same expression is " +
                        "used on all four sides, and a constant gives a flat field.",
                    required = false,
                    defaultText = "sin(pi*x)",
                ),
                AiArgument(
                    name = "length",
                    kind = AiArgumentKind.NUMBER,
                    description = "The length of the rod or the side of the square.",
                    required = false,
                    defaultText = "1",
                ),
            ),
            grammar = "The initial value and the boundary are expressions in x and y.",
            limitations = "The result is a numerical approximation. The heat scheme needs " +
                "enough steps in time to stay stable.",
            examples = listOf("heat with sin(pi*x)", "laplace on a square of side 1"),
        ),
    )

    /** Every tool the AI may choose from. */
    val all: List<AiToolSpec> = calculusTools + algebraTools + advancedTools

    /** The tool with that name, if there is one. */
    fun byId(id: String): AiToolSpec? = all.firstOrNull { it.id == id }

    /** The tools of one domain. */
    fun inDomain(domain: AiToolDomain): List<AiToolSpec> = all.filter { it.domain == domain }

    /** The tools whose name contains the text, for the search box. */
    fun search(query: String): List<AiToolSpec> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.id.contains(q) ||
                it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
        }
    }
}

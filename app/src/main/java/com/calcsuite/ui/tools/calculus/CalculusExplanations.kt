package com.calcsuite.ui.tools.calculus

import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.calculus.DerivativeResult
import com.calcsuite.engine.calculus.ExtremaResult
import com.calcsuite.engine.calculus.IntegrationResult
import com.calcsuite.engine.calculus.LimitResult
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import com.calcsuite.engine.calculus.RootResult
import com.calcsuite.engine.calculus.SeriesResult

/**
 * The prose behind the nine calculus panels.
 *
 * This file is pure Kotlin so it can be read and tested without Compose. Each
 * panel keeps its own result, so each panel asks for its own explanation and
 * passes in the result it already computed. The steps are the engine's own
 * steps, never a retelling written by hand, and the verification describes the
 * check that the tool really makes. Nothing here changes a calculation: it
 * only says what the calculation did.
 */

/** The one builder every panel's explanation goes through. */
private fun calculusExplanation(
    definition: String,
    formula: String,
    symbols: List<Pair<String, String>>,
    conditions: List<String>,
    method: String,
    steps: List<String>,
    interpretation: String,
    verification: String,
    limitations: String,
): Explanation = Explanation(
    definition = definition,
    formula = formula,
    symbols = symbols,
    conditions = conditions,
    method = method,
    steps = steps,
    interpretation = interpretation,
    verification = verification,
    limitations = limitations,
)

/** Short label for an expression the user may have left blank. */
private fun named(input: String): String = input.trim().ifBlank { "the expression" }

/** The explanation of a symbolic derivative, including the point check. */
fun derivativeExplanation(
    input: String,
    result: DerivativeResult?,
    variable: String = "x",
): Explanation = calculusExplanation(
    definition = "The derivative measures how fast a function changes. It is the slope " +
        "of the tangent to the curve at a point, found as the limit of the slope of a " +
        "chord as the two points come together.",
    formula = "f′(x) = lim(h→0) (f(x+h) − f(x)) ÷ h  =  d/dx f(x)",
    symbols = listOf(
        "f′(x)" to "the derivative of f with respect to x",
        "d/dx" to "differentiate the following expression with respect to x",
        "h" to "a small change in x, used by the central-difference check",
        "x" to "the point at which the slope is measured",
    ),
    conditions = listOf(
        "The expression must parse as a function of the chosen variable.",
        "The limit above must exist at the point for a tangent slope to be defined.",
        "A partial derivative holds every other variable fixed.",
    ),
    method = "The expression is parsed into a tree and differentiated with the sum, " +
        "product, quotient, power and chain rules. The printed derivative is simplified " +
        "before it is shown, and the working names the rule that was used.",
    steps = result?.steps ?: listOf(
        "No derivative was produced. Check that the expression parses and that it uses " +
            "a function the differentiator knows.",
    ),
    interpretation = "The answer gives the slope of ${named(input)} at any $variable where " +
        "it is defined. The Checks at a point card evaluates both f and f′ at the chosen value.",
    verification = "The Checks at a point card compares the printed f′ at the chosen $variable " +
        "with a central difference (f($variable+h) − f($variable−h)) ÷ 2h at h = 1e-6. " +
        "Agreement within 0.1 per cent confirms the symbolic answer at that point.",
    limitations = "Differentiation is symbolic for the functions the parser knows; an unknown " +
        "function cannot be differentiated here. The numeric check tests one point only, " +
        "and repeated differentiation belongs to the Taylor tool.",
)

/** The explanation of an indefinite or definite integral. */
fun integralExplanation(
    input: String,
    result: IntegrationResult?,
    lowerLimit: String = "a",
    upperLimit: String = "b",
): Explanation = calculusExplanation(
    definition = "An antiderivative reverses differentiation: F′(x) = f(x). A definite " +
        "integral adds signed areas between the curve and the horizontal axis, so area " +
        "below the axis counts as negative.",
    formula = "∫ f(x) dx = F(x) + C, where F′(x) = f(x)     " +
        "∫ from $lowerLimit to $upperLimit of f(x) dx = F($upperLimit) − F($lowerLimit)",
    symbols = listOf(
        "∫ f(x) dx" to "the indefinite integral, a family of antiderivatives",
        "F(x)" to "an antiderivative whose derivative is f(x)",
        "C" to "the constant of integration, the same for every antiderivative",
        "$lowerLimit, $upperLimit" to "the lower and upper limits of the definite integral",
        "f(x)" to "the function being integrated, called the integrand",
    ),
    conditions = listOf(
        "The indefinite panel says so when no elementary antiderivative is recognised.",
        "The definite integral needs f defined throughout the interval; a singularity " +
            "inside it is reported rather than given a value.",
        "Swapping the limits changes the sign of the definite answer.",
    ),
    method = "The indefinite result is found by matching known patterns and simplifying " +
        "the antiderivative. The definite result is integrated numerically with adaptive " +
        "Simpson refinement, so it is not substituted into an antiderivative.",
    steps = result?.steps ?: listOf(
        "No definite result was produced. Check the expression and the two limits.",
    ),
    interpretation = "The indefinite value is a family of functions differing by a constant. " +
        "For ${named(input)} the definite value is the signed area from $lowerLimit to " +
        "$upperLimit. A negative value means more of the curve lies below the axis.",
    verification = if (result?.exact == true && result.method.startsWith("Definite")) {
        "The two limits are equal, so the interval has zero width and the value 0 follows " +
            "without sampling the function."
    } else {
        "Adaptive Simpson compares the whole-interval estimate with the sum of the two " +
            "half-interval estimates. It subdivides while they differ by more than 1e-13 " +
            "relative, up to a depth of 24, and reports a singularity if a sample is undefined."
    },
    limitations = "The indefinite panel recognises polynomials, 1/x, e^x, sin, cos and sums " +
        "and constant multiples of those; other inputs are reported as having no elementary " +
        "antiderivative. Definite values are numerical even where an elementary antiderivative " +
        "exists. The shaded band on the graph is a drawing, not this computed value.",
)

/** The explanation of a finite, one-sided or infinite limit. */
fun limitExplanation(
    input: String,
    result: LimitResult?,
    variable: String = "x",
    point: String = "a",
): Explanation = calculusExplanation(
    definition = "A limit is the value a function approaches as its variable approaches a " +
        "point. The function need not be defined at that point for the limit to exist.",
    formula = "lim($variable→$point) f($variable) = L   when f($variable) can be made as " +
        "close to L as we wish by taking $variable close to $point",
    symbols = listOf(
        "lim" to "the limit operator",
        "$variable → $point" to "the variable approaches the point from the chosen side or sides",
        "f($variable)" to "the function whose behaviour is being studied",
        "L" to "the value that the function approaches",
    ),
    conditions = listOf(
        "The point must be a number; Infinity and -Infinity ask for a trend at infinity.",
        "Direct substitution gives the limit only where the function is continuous.",
        "For a two-sided limit the values on the left and the right must agree.",
    ),
    method = "The tool tries direct substitution first. If that is undefined it samples " +
        "the chosen sides a small distance from the point, or samples large values of the " +
        "variable for a limit at infinity.",
    steps = result?.steps ?: listOf(
        "No limit was produced. Check the expression and the point it approaches.",
    ),
    interpretation = "The value is what ${named(input)} tends to as $variable approaches " +
        "$point. The words 'does not exist' mean the sampled sides disagree, or no finite " +
        "trend was found.",
    verification = "The tool reports which method it used. For a two-sided numeric limit it " +
        "evaluates f at $point ± 1e-4 and accepts the answer only when the two values differ " +
        "by at most 1 per cent relative. At infinity it evaluates at ±10⁶ and then at ten " +
        "times that magnitude.",
    limitations = "The numeric limit is an estimate from a small offset, so a fast-changing " +
        "or oscillating function can mislead it. The tool does not cancel algebra by hand. " +
        "One-sided answers depend on the side selected.",
)

/** The explanation of convergence, or of a finite partial sum. */
fun seriesExplanation(
    input: String,
    result: SeriesResult?,
    variable: String = "k",
    start: String = "1",
    end: String = "∞",
): Explanation = calculusExplanation(
    definition = "An infinite series is the sum of the terms of a sequence. It converges " +
        "when its partial sums approach a finite value, and diverges otherwise.",
    formula = "∑ from $variable=$start to ∞ of r^$variable = r^$start ÷ (1 − r) when |r| < 1" +
        "     |     otherwise: ∑ from $variable=$start to $end of g($variable)",
    symbols = listOf(
        "∑" to "add the terms over the stated index range",
        "r" to "the fixed ratio of a geometric series",
        variable to "the index that runs from the first term to the last",
        "$start, $end" to "the first and last indices entered",
        "g($variable)" to "the value of a term at index $variable",
    ),
    conditions = listOf(
        "A closed form is used only for a term r^$variable with a numeric ratio r.",
        "The geometric closed form needs |r| < 1; otherwise the series diverges.",
        "A partial sum is limited to 5000 terms.",
    ),
    method = "The engine recognises r^$variable and applies the geometric sum, or checks " +
        "whether the terms shrink. For any other term it adds a finite number of terms and " +
        "reports that partial sum.",
    steps = result?.steps ?: listOf(
        "No series result was produced. Check the expression and the two indices.",
    ),
    interpretation = "For ${named(input)} the verdict says whether the series has a finite " +
        "sum and gives the closed form when one is known. A partial-sum verdict is only the " +
        "total over the range entered, not a statement about the infinite series.",
    verification = "For a geometric term the working prints the ratio and compares |r| with 1 " +
        "before using r^$start ÷ (1 − r). For other terms it compares the term at the first " +
        "index with the term 50 indices later to see whether the terms approach zero.",
    limitations = "Only geometric terms have a closed form here. Other verdicts come from a " +
        "finite check of term size, not a general convergence proof, and ranges over 5000 " +
        "terms are refused.",
)

/** The explanation of a Taylor or Maclaurin expansion. */
fun taylorExplanation(
    input: String,
    result: SeriesResult?,
    variable: String = "x",
    centre: String = "0",
    order: String = "0 to 12",
): Explanation = calculusExplanation(
    definition = "A Taylor series writes a function near a point as a sum of powers of " +
        "(x − a). Its coefficients come from the derivatives of the function at that point. " +
        "About a = 0 it is called a Maclaurin series.",
    formula = "Taylor: f($variable) = ∑ from k=0 to order of f⁽ᵏ⁾(a) ÷ k! · ($variable − a)^k",
    symbols = listOf(
        "a" to "the centre of the expansion, here $centre",
        "f⁽ᵏ⁾(a)" to "the k-th derivative evaluated at the centre",
        "k!" to "the factorial of k, which divides that derivative",
        "order" to "the highest power kept, from 0 to 12",
        variable to "the point near the centre where the series is used",
    ),
    conditions = listOf(
        "The function must be defined at the centre.",
        "Every derivative up to the chosen order must be differentiable symbolically.",
        "The order must be a whole number between 0 and 12.",
    ),
    method = "The tool differentiates the expression repeatedly, simplifies after each step, " +
        "evaluates each derivative at the centre and divides by k!. Every coefficient is " +
        "printed in the Terms card.",
    steps = result?.steps ?: listOf(
        "No expansion was produced. Check the expression, the centre and an order from 0 to 12.",
    ),
    interpretation = "For ${named(input)} the verdict names the order, and the Terms card " +
        "lists the constant term and each coefficient. Stopping at that order leaves a " +
        "remainder, so the series is an approximation away from the centre.",
    verification = "The Terms card prints f($centre) and every coefficient f⁽ᵏ⁾($centre) ÷ k!, " +
        "so each can be compared with the derivative of the entered function at the centre. " +
        "If a derivative is undefined there, the working says so.",
    limitations = "Coefficients are evaluated in double precision, not exactly. The order is " +
        "capped at 12, the series is reliable only near the centre, and the tool does not " +
        "estimate the remainder.",
)

/** The explanation of a finite sum and product in sigma and pi notation. */
fun sumProductExplanation(
    input: String,
    sum: SeriesResult?,
    product: SeriesResult?,
    variable: String = "k",
    start: String = "a",
    end: String = "b",
): Explanation = calculusExplanation(
    definition = "Summation adds the values of an expression at each integer index in a " +
        "range. Product notation multiplies the same values instead.",
    formula = "∑ from $variable=$start to $end of g($variable) = g($start) + … + g($end)" +
        "     ∏ from $variable=$start to $end of g($variable) = g($start) · … · g($end)",
    symbols = listOf(
        "∑" to "add the terms over the index range",
        "∏" to "multiply the factors over the index range",
        "g($variable)" to "the term or factor evaluated at index $variable",
        "$start, $end" to "the first and last integer indices",
        variable to "the index that increases by one each time",
    ),
    conditions = listOf(
        "Both limits must be whole numbers and the upper limit must not be below the lower one.",
        "Every term or factor in the range must be defined.",
        "A sum is limited to 100000 terms and a product to 5000 factors.",
    ),
    method = "The engine evaluates the expression at each integer index and accumulates a " +
        "sum or a product in double precision. The working states how many values were used.",
    steps = (sum?.steps ?: listOf(
        "No sum was produced. Check the expression and the two indices.",
    )) + (product?.steps ?: emptyList()),
    interpretation = "The answer adds or multiplies the values of ${named(input)} over the " +
        "exact range entered. Changing either limit changes the number of values used.",
    verification = "The working prints the number of terms or factors evaluated, so the " +
        "range can be checked against the input. The engine stops with a domain error at " +
        "the first index where the expression is undefined.",
    limitations = "The results are double-precision accumulations, not exact sums or " +
        "products. Only one index is supported. Ranges over 100000 terms for a sum or 5000 " +
        "factors for a product are refused.",
)

/** The explanation of real roots found by scanning and bisection. */
fun rootsExplanation(
    input: String,
    result: RootResult?,
    lowerLimit: String = "a",
    upperLimit: String = "b",
): Explanation = calculusExplanation(
    definition = "A root of a function is a value of x where f(x) = 0. On a graph it is a " +
        "crossing of the horizontal axis, or a point where the curve touches it.",
    formula = "Roots: f(x) = 0 on [$lowerLimit, $upperLimit];  bisection uses a bracket " +
        "[u, v] with f(u)·f(v) < 0",
    symbols = listOf(
        "f(x)" to "the function whose zeros are sought",
        "x" to "the value that makes the function zero",
        "$lowerLimit, $upperLimit" to "the ends of the search interval",
        "[u, v]" to "a short bracket known to contain one root",
    ),
    conditions = listOf(
        "The interval must be increasing and f must be defined inside it.",
        "Bisection needs f to change sign across a bracket.",
        "The function should be continuous on the interval; a jump can look like a crossing.",
    ),
    method = "The engine scans the interval at 2000 points, bisects every bracket where " +
        "the sign changes until it is 1e-12 wide, and polishes the root with Newton's " +
        "method. Results closer than 1e-8 are merged.",
    steps = result?.steps ?: listOf(
        "No roots were produced. Check the expression and the search interval.",
    ),
    interpretation = "Each reported number makes ${named(input)} zero to the tolerance of " +
        "the scan. An empty result means no sign change was found in this interval, not " +
        "that the function has no root anywhere.",
    verification = "Every root comes from a bracket where the function changes sign, refined " +
        "until the bracket is no wider than 1e-12. The working reports how many roots were " +
        "found and the interval that was scanned.",
    limitations = "A root that only touches the axis without changing sign is not found. " +
        "Very close roots can be merged, and complex roots are outside this tool. Only the " +
        "chosen interval is searched.",
)

/** The explanation of stationary points and their classification. */
fun extremaExplanation(
    input: String,
    result: ExtremaResult?,
    lowerLimit: String = "a",
    upperLimit: String = "b",
): Explanation = calculusExplanation(
    definition = "An extremum is a local maximum or a local minimum. At an interior " +
        "stationary point the first derivative is zero, and the second derivative says " +
        "which kind of point it is.",
    formula = "Extrema: f′(x) = 0, then f″(x) < 0 gives a local maximum and " +
        "f″(x) > 0 gives a local minimum",
    symbols = listOf(
        "f′(x)" to "the first derivative, zero at a stationary point",
        "f″(x)" to "the second derivative, whose sign classifies the point",
        "x" to "the position of the stationary point",
        "[$lowerLimit, $upperLimit]" to "the interval searched for stationary points",
    ),
    conditions = listOf(
        "The function must be differentiable symbolically at the points considered.",
        "The point must lie inside the chosen interval; endpoints are not tested.",
        "A non-zero second derivative is needed to label a maximum or a minimum.",
    ),
    method = "The tool differentiates f, searches for roots of f′ inside the interval and " +
        "evaluates f at each one. It classifies each point with f″ where that can be " +
        "evaluated, and otherwise with a central difference of f′.",
    steps = result?.steps ?: listOf(
        "No stationary points were produced. Check the expression and the interval.",
    ),
    interpretation = "Each row for ${named(input)} gives x, the value f(x) and the " +
        "classification. The result is local: a point nearby, or outside the interval, " +
        "may be higher or lower.",
    verification = "Each point is a root of the printed first derivative inside the interval, " +
        "and its label follows the sign of the second derivative there. The working prints " +
        "f′ and the number of points found.",
    limitations = "Endpoints and non-stationary corners are not considered. The scan can miss " +
        "a stationary point where f′ touches zero without changing sign, and f″ = 0 is " +
        "reported as a stationary point rather than a definite maximum or minimum.",
)

/** The explanation of the plotted curve, its derivative and its shaded area. */
fun graphExplanation(
    input: String,
    node: Node?,
    derivative: Node?,
    lowerLimit: String = "a",
    upperLimit: String = "b",
): Explanation = calculusExplanation(
    definition = "A graph draws the value of a function against its variable. The same " +
        "picture can show the derivative as a dashed curve and shade the area under the " +
        "function between two limits.",
    formula = "Graph: y = f(x),  f′(x) = lim(h→0) (f(x+h) − f(x)) ÷ h,  " +
        "area = ∫ from $lowerLimit to $upperLimit of f(x) dx",
    symbols = listOf(
        "f(x)" to "the solid curve being plotted",
        "f′(x)" to "the dashed curve, the symbolic derivative of f",
        "x" to "the horizontal axis; the graph always plots along x",
        "$lowerLimit, $upperLimit" to "the limits of the shaded band under the curve",
    ),
    conditions = listOf(
        "The expression must parse; otherwise the tool says that there is nothing to plot.",
        "The plot uses x as the independent variable, even if the expression has other letters.",
        "Only finite sampled values are drawn. Undefined, infinite and very large values " +
            "break the curve instead of making a spike.",
    ),
    method = "The function is sampled across the visible width with between 120 and 900 " +
        "points, and the path is broken wherever a value is not usable. The derivative is " +
        "found symbolically and drawn dashed. The shaded band uses 240 samples clipped to " +
        "the visible x window.",
    steps = if (node == null) {
        listOf("The expression did not parse, so no curve was drawn.")
    } else {
        buildList {
            add("Plotted f = ${NodePrinter.print(node)} across the visible x window.")
            derivative?.let { add("Drew the dashed derivative f′ = ${NodePrinter.print(it)}.") }
        }
    },
    interpretation = "The solid curve shows ${named(input)} over the current view. Drag to " +
        "pan and pinch to zoom about the point you touch. Reset view returns to −10 to 10 " +
        "on both axes.",
    verification = "Drawing samples f at 120 to 900 x values across the view and skips values " +
        "that are undefined, infinite or above 1e12. The shading uses 240 samples clipped " +
        "to the window, and the read-out evaluates f at the lower limit directly. These are " +
        "plotting checks, not a solve.",
    limitations = "The plot uses x only. Sampling can miss narrow peaks or fast oscillations " +
        "between points, so the picture is an illustration rather than a proof. The shaded " +
        "band is drawn from samples and is not the adaptive Simpson value from the " +
        "Integrals tool.",
)

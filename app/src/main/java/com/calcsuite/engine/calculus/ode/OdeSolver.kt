package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.formatNumber
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Rat
import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs
import kotlin.math.exp

/** How the user wants the equation solved. */
enum class SolveMode(
    /** The short label shown on the chip. */
    val label: String,
    /** The full name, used where there is room for it. */
    val fullLabel: String,
) {
    SYMBOLIC("Symbolic", "Symbolic solution"),
    INITIAL_VALUE("Initial value", "Initial value problem"),
    NUMERICAL("Numerical", "Numerical approximation"),
}

/** The numerical domain and settings. */
data class NumericalSettings(
    val from: Double = 0.0,
    val to: Double = 5.0,
    val step: Double = 0.1,
    val method: NumericalMethod = NumericalMethod.RK4,
    /** An optional expression for the exact solution, for the error report. */
    val knownSolution: String? = null,
)

/** Everything the solver needs. */
data class OdeRequest(
    val equation: String,
    val mode: SolveMode = SolveMode.SYMBOLIC,
    val conditions: List<OdeCondition> = emptyList(),
    val numerical: NumericalSettings = NumericalSettings(),
)

/**
 * Chooses a method for an equation and solves it.
 *
 * The dispatcher looks through the implemented methods in the order that
 * gives the most explicit answer, and when none of them applies it says
 * which method would have worked and offers the numerical route instead of
 * inventing a closed form.
 */
object OdeSolver {

    /** Solves the request. */
    fun solve(request: OdeRequest): OdeResult {
        val parsed = OdeParser.parse(request.equation)
        val ode = when (parsed) {
            is CalcResult.Ok -> parsed.value
            is CalcResult.Err -> return OdeResult.Invalid(parsed.error.message)
        }
        val classification = OdeClassifier.classify(ode)

        // An initial value problem has to be well posed before it is attempted.
        if (request.mode == SolveMode.INITIAL_VALUE) {
            val problem = OdeConditionParser.checkSufficiency(request.conditions, ode.order)
            if (problem != null) return OdeResult.Invalid(problem)
        }

        if (request.mode == SolveMode.NUMERICAL) {
            return solveNumerically(request, ode, classification)
        }

        val symbolic = solveSymbolically(request, ode, classification)
        if (symbolic is OdeResult.Unsupported) {
            // An initial value problem still has a numerical answer.
            if (request.mode == SolveMode.INITIAL_VALUE) {
                return solveNumerically(request, ode, classification, symbolic.failure)
            }
            return symbolic
        }
        return symbolic
    }

    // ------------------------------------------------------------- symbolic

    private fun solveSymbolically(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
    ): OdeResult {
        // 1. Separable equations.
        val separable = classification.separable
        if (separable != null && ode.order == 1) {
            val separation = SeparableSolver.separate(ode, separable)
            if (separation != null) {
                return separableResult(request, ode, classification, separable, separation)
            }
        }

        // 2. First-order linear equations.
        if (classification.isFirstOrderLinear) {
            val linear = LinearFirstOrderSolver.solve(ode)
            if (linear != null) {
                return linearResult(request, ode, classification, linear)
            }
        }

        // 3. Linear equations with constant coefficients.
        if (classification.hasConstantCoefficients) {
            val constant = ConstantCoefficientSolver.solve(ode)
            if (constant != null) {
                return constantCoefficientResult(request, ode, classification, constant)
            }
        }

        // 4. Exact equations.
        if (classification.isExact && ode.order == 1) {
            val exact = exactResult(request, ode, classification)
            if (exact != null) return exact
        }

        // 5. Homogeneous first-order equations.
        if (classification.isHomogeneousFirstOrder && ode.order == 1) {
            val homogeneous = homogeneousResult(request, ode, classification)
            if (homogeneous != null) return homogeneous
        }

        return OdeResult.Unsupported(
            OdeFailure(
                message = unsupportedMessage(ode, classification),
                suggestedMethod = suggestedMethod(classification),
                suggestion = "The numerical route will still solve this initial value problem, " +
                    "stepping the solution forward from the starting point.",
            ),
        )
    }

    private fun unsupportedMessage(ode: ParsedOde, classification: OdeClassification): String {
        val dependent = ode.dependent
        return when {
            classification.order >= 2 && !classification.hasConstantCoefficients ->
                "This is a ${classification.order}th order equation whose coefficients are not all " +
                    "constants, so the characteristic equation does not apply, and no other " +
                    "implemented method covers it."
            classification.order >= 2 ->
                "This is a ${classification.order}th order equation with constant coefficients, " +
                    "but the particular solution could not be produced, so no closed form is given."
            OdeParser.containsVariable(ode.rhs, dependent) ->
                "This first-order equation is not separable, not linear in y, and not of the " +
                    "constant-coefficient form, so no implemented method applies to it."
            else ->
                "No implemented method applies to this equation."
        }
    }

    private fun suggestedMethod(classification: OdeClassification): OdeMethod? = when {
        classification.isHomogeneousFirstOrder -> OdeMethod.HOMOGENEOUS
        classification.isExact -> OdeMethod.EXACT
        classification.isFirstOrderLinear -> OdeMethod.FIRST_ORDER_LINEAR
        classification.separable != null -> OdeMethod.SEPARABLE
        classification.hasConstantCoefficients -> OdeMethod.CONSTANT_COEFFICIENT
        else -> null
    }

    // ------------------------------------------------------ separable result

    private fun separableResult(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
        form: SeparableForm,
        separation: SeparableSolver.Separation,
    ): OdeResult {
        val x = ode.independent
        val y = ode.dependent
        val steps = mutableListOf<String>()
        steps.add("The right-hand side separates: y′ = ${NodePrinter.print(form.a)}·${NodePrinter.print(form.b)}.")
        steps.add(
            "Dividing by ${NodePrinter.print(form.b)} and multiplying by dx gives the separated " +
                "equation dy ÷ ${NodePrinter.print(form.b)} = ${NodePrinter.print(form.a)}·dx.",
        )
        steps.add(
            "Integrating both sides: ∫ dy ÷ ${NodePrinter.print(form.b)} = ∫ ${NodePrinter.print(form.a)}·dx, " +
                "so ${NodePrinter.print(separation.gIntegral)} = ${NodePrinter.print(separation.fIntegral)} + C.",
        )

        val inversion = separation.inversion
        val general = inversion?.template?.replace("{C}", "C")
            ?: "${NodePrinter.print(separation.gIntegral)} = ${NodePrinter.print(separation.fIntegral)} + C"

        var particular: String? = null
        var constantText: String? = null
        var solutionNode: Node? = null
        val condition = request.conditions.firstOrNull { it.order == 0 }

        if (condition != null) {
            // In the separated form the constant is additive: C = F(x₀) − G(y₀).
            val f0 = Evaluator.eval(separation.fIntegral, mapOf(x to condition.at)).valueOrNull()
            val g0 = Evaluator.eval(separation.gIntegral, mapOf(y to condition.value)).valueOrNull()
            if (f0 != null && g0 != null) {
                val additive = f0 - g0
                val shown = inversion?.constant?.invoke(additive) ?: additive
                constantText = formatNumber(shown)
                steps.add(
                    "Using y(${formatNumber(condition.at)}) = ${formatNumber(condition.value)}: " +
                        "C = ${NodePrinter.print(separation.fIntegral)} at ${formatNumber(condition.at)} " +
                        "− ${NodePrinter.print(separation.gIntegral)} at ${formatNumber(condition.value)} = " +
                        "${formatNumber(additive)}" +
                        (if (inversion != null && shown != additive) {
                            ", which is ${formatNumber(shown)} in the explicit form below."
                        } else {
                            "."
                        }),
                )
                particular = inversion?.template?.replace("{C}", formatNumber(shown))
                solutionNode = inversion?.node?.invoke(shown)
            }
        }

        var constantNote: String? = null
        if (separation.constantSolutions.isNotEmpty()) {
            val values = separation.constantSolutions.joinToString(", ") { formatNumber(it) }
            constantNote =
                "Dividing by ${NodePrinter.print(form.b)} loses the constant solutions where it " +
                    "is zero. Those are y = $values, and they are solutions of the original " +
                    "equation as well."
            steps.add(constantNote)
        }

        return OdeResult.Solved(
            OdeSolution(
                equation = ode.display,
                method = OdeMethod.SEPARABLE,
                classification = classification,
                generalSolution = general,
                particularSolution = particular,
                constants = listOfNotNull(constantText?.let { "C" to it }),
                steps = steps,
                verification = verifyBySubstitution(ode, solutionNode, x, y),
                interpretation = "The solution describes how $y changes with $x for the " +
                    "separated equation, together with any constant solutions that were lost " +
                    "when the two sides were divided.",
                assumptions = buildList {
                    add("The right-hand side is a product of a factor of $x alone and a factor of $y alone.")
                    if (constantNote != null) add(constantNote)
                },
                numerical = null,
                limitation = null,
                solutionNode = solutionNode,
            ),
        )
    }

    // ----------------------------------------------------- linear result

    private fun linearResult(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
        linear: LinearFirstOrderSolver.Result,
    ): OdeResult {
        val x = ode.independent
        val y = ode.dependent
        val steps = mutableListOf<String>()
        steps.add("Put the equation in the standard form y′ + P(x)·y = Q(x).")
        steps.add("  P(x) = ${NodePrinter.print(linear.p)}")
        steps.add("  Q(x) = ${NodePrinter.print(linear.q)}")
        steps.add("The integrating factor is μ(x) = e^(∫P dx) = e^(${NodePrinter.print(linear.pIntegral)}).")
        steps.add(
            "Multiplying the equation by μ turns the left-hand side into the derivative of " +
                "μ·y, because (μ·y)′ = μ·y′ + μ′·y = μ·(y′ + P·y).",
        )
        steps.add("Integrating gives μ·y = ∫ μ·Q dx + C, so y = (∫ μ·Q dx + C) ÷ μ.")

        val homogeneousText = "C·e^(−${NodePrinter.print(linear.pIntegral)})"
        var general: String
        var particular: String? = null
        var constantText: String? = null
        var solutionNode: Node? = null

        if (linear.particular != null) {
            val particularText = NodePrinter.print(linear.particular)
            general = "$particularText + $homogeneousText"
            steps.add("A particular solution is y_p = $particularText.")

            val condition = request.conditions.firstOrNull { it.order == 0 }
            if (condition != null) {
                val particularValue = Evaluator.eval(linear.particular, mapOf(x to condition.at)).valueOrNull()
                val homogeneousValue = Evaluator.eval(linear.homogeneous, mapOf(x to condition.at)).valueOrNull()
                if (particularValue != null && homogeneousValue != null && abs(homogeneousValue) > 1e-14) {
                    val c = (condition.value - particularValue) / homogeneousValue
                    constantText = formatNumber(c)
                    steps.add(
                        "Using y(${formatNumber(condition.at)}) = ${formatNumber(condition.value)}: " +
                            "C = (${formatNumber(condition.value)} − ${formatNumber(particularValue)}) ÷ " +
                            "${formatNumber(homogeneousValue)} = ${formatNumber(c)}.",
                    )
                    particular = "$particularText + ${formatNumber(c)}·e^(−${NodePrinter.print(linear.pIntegral)})"
                    solutionNode = OdeParser.addTerms(
                        linear.particular,
                        Node.Mul(Node.Num(c), linear.homogeneous),
                    )
                }
            }
        } else {
            general = "y = e^(−${NodePrinter.print(linear.pIntegral)})·(∫ e^(${NodePrinter.print(linear.pIntegral)})·${NodePrinter.print(linear.q)} dx + C)"
            steps.add(
                "The remaining integral ∫ e^(${NodePrinter.print(linear.pIntegral)})·${NodePrinter.print(linear.q)} dx " +
                    "has no elementary antiderivative, so the solution is given in this form.",
            )
        }

        return OdeResult.Solved(
            OdeSolution(
                equation = ode.display,
                method = OdeMethod.FIRST_ORDER_LINEAR,
                classification = classification,
                generalSolution = general,
                particularSolution = particular,
                constants = listOfNotNull(constantText?.let { "C" to it }),
                steps = steps,
                verification = verifyBySubstitution(ode, solutionNode, x, y),
                interpretation = "The solution describes how $y changes with $x for a linear " +
                    "equation: the homogeneous part is the decay or growth set by the equation, and " +
                    "the particular part is the response to the forcing term.",
                assumptions = listOf(
                    "The equation is linear in $y, so an integrating factor exists.",
                    if (linear.particular == null) {
                        "The particular solution is only given in quadrature, because the remaining " +
                            "integral has no elementary antiderivative."
                    } else {
                        "The particular solution was produced by undetermined coefficients."
                    },
                ),
                numerical = null,
                limitation = null,
                solutionNode = solutionNode,
            ),
        )
    }

    // ------------------------------------------- constant coefficient result

    private fun constantCoefficientResult(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
        result: ConstantCoefficientSolver.Result,
    ): OdeResult {
        val x = ode.independent
        val y = ode.dependent
        val steps = mutableListOf<String>()
        steps.add(
            "The equation has constant coefficients, so the homogeneous solution comes from the " +
                "characteristic equation.",
        )
        steps.add(
            "Characteristic equation: " +
                ConstantCoefficientSolver.renderPolynomial(result.characteristic) + " = 0",
        )
        steps.add(
            "Its roots are " + result.roots.joinToString(", ") {
                if (it.isReal) {
                    "r = ${formatNumber(it.real)}" + if (it.multiplicity > 1) " (multiplicity ${it.multiplicity})" else ""
                } else {
                    "r = ${formatNumber(it.real)} ${if (it.imaginary < 0) "−" else "+"} ${formatNumber(abs(it.imaginary))}i"
                }
            } + ".",
        )
        steps.add("That gives the homogeneous solution y_h = ${result.homogeneous}.")

        val particularText = result.particularText
        if (particularText != null) {
            steps.add("A particular solution by undetermined coefficients is y_p = $particularText.")
        } else if (OdeParser.isZero(ode.rhs)) {
            steps.add(
                "The right-hand side is zero, so the equation is homogeneous and the general " +
                    "solution is the answer.",
            )
        } else {
            steps.add(
                "No particular solution could be produced for this forcing term, so only the " +
                    "homogeneous solution is given.",
            )
        }

        var general = result.homogeneous
        if (particularText != null) general = "$particularText + ${result.homogeneous}"
        var particular: String? = null
        var solutionNode: Node? = result.particular
        val constants = mutableListOf<Pair<String, String>>()

        // Apply the conditions by solving for the constants.
        val conditions = request.conditions.filter { it.order < result.basis.size + 1 }
        if (conditions.isNotEmpty() && result.basis.isNotEmpty()) {
            val matrix = ArrayList<List<Double>>()
            val rhs = ArrayList<Double>()
            for (condition in conditions) {
                val row = ArrayList<Double>(result.basis.size)
                for (basis in result.basis) {
                    // A condition on y⁽ᵏ⁾ is a condition on the k-th derivative
                    // of the basis functions, not on the basis functions.
                    var function: Node = basis
                    for (order in 0 until condition.order) {
                        function = DifferentiationEngine.differentiate(function, x).valueOrNull() ?: break
                    }
                    val value = Evaluator.eval(function, mapOf(x to condition.at)).valueOrNull() ?: 0.0
                    row.add(value)
                }
                matrix.add(row)
                rhs.add(condition.value)
            }
            val solved = LinearSolve.solveReal(matrix, rhs)
            if (solved != null) {
                solved.forEachIndexed { index, value ->
                    constants.add("C${index + 1}" to formatNumber(value))
                }
                val terms = result.basis.mapIndexed { index, basis ->
                    val value = solved.getOrNull(index) ?: 0.0
                    if (abs(value) < 1e-14) null else "${formatNumber(value)}·${NodePrinter.print(basis)}"
                }.filterNotNull()
                particular = if (particularText != null) {
                    "$particularText + " + terms.joinToString(" + ")
                } else {
                    terms.joinToString(" + ")
                }
                // The solution as a tree, for the verification.
                var node = result.particular
                for (index in result.basis.indices) {
                    val value = solved.getOrNull(index) ?: 0.0
                    if (abs(value) < 1e-14) continue
                    node = OdeParser.addTerms(
                        node,
                        Node.Mul(Node.Num(value), result.basis[index]),
                    )
                }
                solutionNode = node
                steps.add(
                    "The conditions give " + conditions.size + " equation(s) for " +
                        conditions.size + " constant(s), solved to give " +
                        constants.joinToString(", ") { "${it.first} = ${it.second}" } + ".",
                )
            } else {
                steps.add("The conditions could not be solved for the constants.")
            }
        }

        return OdeResult.Solved(
            OdeSolution(
                equation = ode.display,
                method = OdeMethod.CONSTANT_COEFFICIENT,
                classification = classification,
                generalSolution = general,
                particularSolution = particular,
                constants = constants,
                steps = steps,
                verification = verifyBySubstitution(ode, solutionNode, x, y),
                interpretation = "The homogeneous part is the motion the equation would have " +
                    "without any forcing term; the particular part is the response to the " +
                    "forcing term.",
                assumptions = listOf(
                    "The coefficients of the derivatives are constants, so the characteristic " +
                        "equation applies.",
                    if (particularText == null) {
                        "No particular solution was produced, so the answer covers the homogeneous " +
                            "solution only."
                    } else {
                        "The particular solution was produced by undetermined coefficients."
                    },
                ),
                numerical = null,
                limitation = null,
                solutionNode = solutionNode,
            ),
        )
    }

    // --------------------------------------------------------- exact result

    /**
     * The exact solution, for the case where the closed form is a sum of two
     * single-variable integrals.
     */
    private fun exactResult(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
    ): OdeResult? {
        val x = ode.independent
        val y = ode.dependent
        val a0 = ode.coefficient(0) ?: return null
        val a1 = ode.coefficient(1) ?: return null
        // M = a0, N = a1, and the solution is ∫M dx + ∫N dy = C.
        val mIntegral = IntegrationEngine.antiderivative(a0, x) ?: return null
        val nIntegral = IntegrationEngine.antiderivative(a1, y) ?: return null
        val steps = mutableListOf<String>()
        steps.add("The equation is exact: the mixed partials agree, so no integrating factor is needed.")
        steps.add("Writing it as M dx + N dy = 0 with M = ${NodePrinter.print(a0)} and N = ${NodePrinter.print(a1)},")
        steps.add(
            "the solution is ∫M dx + ∫N dy = C, that is " +
                "${NodePrinter.print(mIntegral)} + ${NodePrinter.print(nIntegral)} = C.",
        )
        val general = "${NodePrinter.print(mIntegral)} + ${NodePrinter.print(nIntegral)} = C"
        return OdeResult.Solved(
            OdeSolution(
                equation = ode.display,
                method = OdeMethod.EXACT,
                classification = classification,
                generalSolution = general,
                particularSolution = null,
                constants = emptyList(),
                steps = steps,
                verification = null,
                interpretation = "The left-hand side is the derivative of a potential function, " +
                    "so the solution is a level curve of that potential.",
                assumptions = listOf(
                    "The equation is exact, so the two sides can be integrated directly.",
                ),
                numerical = null,
                limitation = "The constant is not determined unless a condition is given, and the " +
                    "condition would have to be applied to the implicit form.",
            ),
        )
    }

    // --------------------------------------------------- homogeneous result

    /**
     * The solution of a homogeneous first-order equation, for the case where
     * the right-hand side is linear in the ratio y/x.
     */
    private fun homogeneousResult(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
    ): OdeResult? {
        val x = ode.independent
        val y = ode.dependent
        // The right-hand side has to be a·(y/x) + b.
        val ratio = findRatio(ode.rhs, x, y) ?: return null
        val a = ratio.first
        val b = ratio.second
        val steps = mutableListOf<String>()
        steps.add("The right-hand side depends only on the ratio y/x, so the equation is homogeneous.")
        steps.add("Substitute y = v·x, so y′ = v + x·dv/dx and the equation becomes x·dv/dx = ${formatNumber(a)}·v + ${formatNumber(b)} − v.")
        if (abs(a - 1.0) < 1e-12) {
            if (abs(b) < 1e-12) {
                steps.add("That gives x·dv/dx = 0, so v is constant and y = C·x.")
                return OdeResult.Solved(
                    OdeSolution(
                        equation = ode.display,
                        method = OdeMethod.HOMOGENEOUS,
                        classification = classification,
                        generalSolution = "y = C·x",
                        particularSolution = null,
                        constants = emptyList(),
                        steps = steps,
                        verification = null,
                        interpretation = "Every straight line through the origin is a solution.",
                        assumptions = listOf("The right-hand side depends only on y/x."),
                        numerical = null,
                        limitation = null,
                    ),
                )
            }
            return null
        }
        steps.add("Separating gives dv ÷ ((${formatNumber(a)} − 1)·v + ${formatNumber(b)}) = dx ÷ x.")
        val general = "y = x·(C·x^${formatNumber(a - 1)} − ${formatNumber(b)}) ÷ ${formatNumber(a - 1)}"
        steps.add("Integrating and solving for y gives $general.")
        return OdeResult.Solved(
            OdeSolution(
                equation = ode.display,
                method = OdeMethod.HOMOGENEOUS,
                classification = classification,
                generalSolution = general,
                particularSolution = null,
                constants = emptyList(),
                steps = steps,
                verification = null,
                interpretation = "The solution curves are the straight lines of a family whose " +
                    "shape is set by the ratio in the equation.",
                assumptions = listOf("The right-hand side depends only on y/x."),
                numerical = null,
                limitation = null,
            ),
        )
    }

    /**
     * Recognises a right-hand side of the form `a·(y/x) + b`.
     */
    private fun findRatio(node: Node, x: String, y: String): Pair<Double, Double>? {
        // a·(y/x) + b, or a·(y/x).
        if (node is Node.Add) {
            val left = findRatio(node.a, x, y) ?: return null
            val right = (node.b as? Node.Num)?.value ?: return null
            return left.first to (left.second + right)
        }
        if (node is Node.Mul && node.a is Node.Num) {
            val quotient = node.b
            if (quotient is Node.Div &&
                quotient.a is Node.Var && quotient.a.name.equals(y, ignoreCase = true) &&
                quotient.b is Node.Var && quotient.b.name.equals(x, ignoreCase = true)
            ) {
                return node.a.value to 0.0
            }
        }
        if (node is Node.Div &&
            node.a is Node.Var && node.a.name.equals(y, ignoreCase = true) &&
            node.b is Node.Var && node.b.name.equals(x, ignoreCase = true)
        ) {
            return 1.0 to 0.0
        }
        return null
    }

    // ------------------------------------------------------- numerical route

    private fun solveNumerically(
        request: OdeRequest,
        ode: ParsedOde,
        classification: OdeClassification,
        fallback: OdeFailure? = null,
    ): OdeResult {
        val condition = request.conditions.firstOrNull { it.order == 0 }
        val x0 = condition?.at ?: request.numerical.from
        val y0 = condition?.value ?: 0.0
        if (condition == null && request.mode == SolveMode.INITIAL_VALUE) {
            return OdeResult.Invalid("An initial value problem needs a starting value y(x₀) = …")
        }
        val settings = request.numerical
        if (settings.step <= 0.0) {
            return OdeResult.Invalid("The step size must be greater than zero.")
        }
        if (settings.to <= settings.from) {
            return OdeResult.Invalid("The end of the interval must be greater than its start.")
        }
        val steps = ((settings.to - settings.from) / settings.step).toInt()
        if (steps > NumericalOdeSolver.MAX_STEPS) {
            return OdeResult.Invalid(
                "That would take $steps steps, which is more than the tool will run. " +
                    "Use a larger step or a shorter interval.",
            )
        }

        val known = settings.knownSolution
            ?.let { CalculusParserBridge.parse(it).valueOrNull() }
        val numerical = NumericalOdeSolver.solve(
            ode = ode,
            method = settings.method,
            x0 = x0,
            y0 = y0,
            to = settings.to,
            step = settings.step,
            knownSolution = known,
        )

        val stepsText = mutableListOf<String>()
        stepsText.addAll(numerical.steps)
        if (fallback != null) {
            stepsText.add("No closed form was produced: ${fallback.message}")
        }

        return OdeResult.Solved(
            OdeSolution(
                equation = ode.display,
                method = OdeMethod.NUMERICAL,
                classification = classification,
                generalSolution = null,
                particularSolution = null,
                constants = emptyList(),
                steps = stepsText,
                verification = null,
                interpretation = "This is an approximation, not an exact solution. The values " +
                    "are the best the chosen method can give at the step size used; halving the " +
                    "step brings them closer to the true solution.",
                assumptions = listOf(
                    "The solution is approximated, not solved exactly.",
                    "The method is ${settings.method.label}, which is ${settings.method.order} " +
                        "order accurate.",
                ),
                numerical = numerical,
                limitation = "A numerical solution is only as good as its step size and method.",
            ),
        )
    }

    // ----------------------------------------------------------- verification

    /**
     * Substitutes the solution back into the equation and measures the residual.
     *
     * The solution is differentiated with the engine's own differentiation, so
     * the check is a real one: the derivative of the answer is compared with the
     * right-hand side of the equation, at a few points along the solution.
     */
    private fun verifyBySubstitution(
        ode: ParsedOde,
        solutionNode: Node?,
        x: String,
        y: String,
    ): OdeVerification? {
        if (solutionNode == null) return null
        if (OdeParser.containsVariable(solutionNode, y)) return null

        // The derivative of the solution, to the order of the equation.
        var derivative: Node = solutionNode
        repeat(ode.order) {
            derivative = DifferentiationEngine.differentiate(derivative, x).valueOrNull() ?: return null
        }

        var worst = 0.0
        var checked = 0
        for (at in listOf(0.4, 0.9, 1.3)) {
            val value = Evaluator.eval(solutionNode, mapOf(x to at)).valueOrNull() ?: continue
            val environment = mutableMapOf(x to at, y to value)
            // The lower derivatives are needed by the right-hand side.
            var lower: Node = solutionNode
            for (order in 1 until ode.order) {
                lower = DifferentiationEngine.differentiate(lower, x).valueOrNull() ?: break
                environment[OdeParser.derivativeName(order - 1)] =
                    Evaluator.eval(lower, mapOf(x to at)).valueOrNull() ?: 0.0
            }
            val left = Evaluator.eval(derivative, environment).valueOrNull() ?: continue
            val right = Evaluator.eval(ode.normalisedRhs, environment).valueOrNull() ?: continue
            val residual = abs(left - right)
            if (residual > worst) worst = residual
            checked++
        }
        if (checked == 0) return null
        return OdeVerification(
            verified = worst < 1e-6 * maxOf(1.0, worst),
            what = "The solution was differentiated and the result compared with the " +
                "right-hand side of the equation at several points.",
            detail = "Largest residual over the points checked: ${formatNumber(worst)}",
        )
    }
}

/** A small bridge so the ODE code can reach the expression parser. */
internal object CalculusParserBridge {
    fun parse(text: String): CalcResult<Node> =
        com.calcsuite.engine.calculus.CalculusParser.parse(text)
}

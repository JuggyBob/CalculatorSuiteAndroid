package com.calcsuite.engine.advanced

import com.calcsuite.engine.calculus.Node
import kotlin.math.abs

/**
 * Evaluates an expression tree over the complex numbers.
 *
 * The arithmetic and the functions of the real engine are the same ones;
 * what changes is that every value carries an imaginary part, and the
 * functions that are multi-valued are taken on their principal branch.
 */
object ComplexEvaluator {

    /**
     * The value of a tree at a point.
     *
     * Returns null when the value is not defined there.
     */
    fun eval(node: Node, environment: Map<String, ComplexNum>): ComplexNum? = when (node) {
        is Node.Num -> ComplexNum(node.value, 0.0)
        is Node.Const -> when (node.name.lowercase()) {
            "pi" -> ComplexNum(Math.PI, 0.0)
            "e" -> ComplexNum(Math.E, 0.0)
            "tau" -> ComplexNum(2 * Math.PI, 0.0)
            "phi" -> ComplexNum((1 + Math.sqrt(5.0)) / 2, 0.0)
            else -> null
        }
        is Node.Var -> environment[node.name.lowercase()]
        is Node.Neg -> eval(node.a, environment)?.let { -it }
        is Node.Add -> {
            val a = eval(node.a, environment) ?: return null
            val b = eval(node.b, environment) ?: return null
            a + b
        }
        is Node.Sub -> {
            val a = eval(node.a, environment) ?: return null
            val b = eval(node.b, environment) ?: return null
            a - b
        }
        is Node.Mul -> {
            val a = eval(node.a, environment) ?: return null
            val b = eval(node.b, environment) ?: return null
            a * b
        }
        is Node.Div -> {
            val a = eval(node.a, environment) ?: return null
            val b = eval(node.b, environment) ?: return null
            if (b.modulus < 1e-300) null else a / b
        }
        is Node.Pow -> {
            val a = eval(node.a, environment) ?: return null
            val b = eval(node.b, environment) ?: return null
            if (b.im == 0.0 && b.re == Math.rint(b.re) && Math.abs(b.re) <= 1024) {
                a.pow(b.re.toInt())
            } else {
                a.pow(b)
            }
        }
        is Node.Fun -> {
            val args = node.args.map { eval(it, environment) ?: return null }
            apply(node.name, args)
        }
    }

    /** The value of a function of the complex numbers. */
    fun apply(name: String, args: List<ComplexNum>): ComplexNum? = when (name) {
        "sin" -> args.firstOrNull()?.sin()
        "cos" -> args.firstOrNull()?.cos()
        "tan" -> args.firstOrNull()?.tan()
        "sinh" -> args.firstOrNull()?.sinh()
        "cosh" -> args.firstOrNull()?.cosh()
        "tanh" -> args.firstOrNull()?.tanh()
        "exp" -> args.firstOrNull()?.exp()
        "ln", "log" -> args.firstOrNull()?.ln()
        "log2" -> args.firstOrNull()?.log10()?.let { it / ComplexNum(Math.log(2.0), 0.0) }
        "sqrt" -> args.firstOrNull()?.sqrt()
        "cbrt" -> args.firstOrNull()?.let { z ->
            // The principal cube root is the one whose argument is the
            // smallest in absolute value, so it lies between −π/3 and π/3.
            z.roots(3).minByOrNull { abs(it.argument) }
        }
        "abs" -> args.firstOrNull()?.let { ComplexNum(it.modulus, 0.0) }
        "sign" -> args.firstOrNull()?.let {
            if (it.modulus < 1e-15) ComplexNum(0.0, 0.0) else it / ComplexNum(it.modulus, 0.0)
        }
        "atan" -> args.firstOrNull()?.let { z ->
            // The principal value of the arctangent, from the logarithm.
            val i = ComplexNum(0.0, 1.0)
            val onePlus = ComplexNum(1.0, 0.0) + i * z
            val oneMinus = ComplexNum(1.0, 0.0) - i * z
            (onePlus.ln() - oneMinus.ln()) / (i * ComplexNum(2.0, 0.0))
        }
        "asin" -> args.firstOrNull()?.let { z ->
            // The principal value, asin(z) = −i·ln(iz + √(1 − z²)).
            val i = ComplexNum(0.0, 1.0)
            val root = (ComplexNum(1.0, 0.0) - z * z).sqrt()
            (i * z + root).ln() * ComplexNum(0.0, -1.0)
        }
        "acos" -> args.firstOrNull()?.let { z ->
            // The principal value, acos(z) = π/2 − asin(z).
            val i = ComplexNum(0.0, 1.0)
            val root = (ComplexNum(1.0, 0.0) - z * z).sqrt()
            val asin = (i * z + root).ln() * ComplexNum(0.0, -1.0)
            ComplexNum(Math.PI / 2, 0.0) - asin
        }
        "min" -> args.minByOrNull { it.re }?.let { ComplexNum(it.re, 0.0) }
        "max" -> args.maxByOrNull { it.re }?.let { ComplexNum(it.re, 0.0) }
        else -> null
    }
}

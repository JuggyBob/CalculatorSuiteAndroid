package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.Rat
import kotlin.math.abs

/**
 * A small linear-algebra helper for the ODE solvers.
 *
 * Two systems are needed: an exact one over rationals, used to find the
 * undetermined coefficients of a particular solution, and a real one, used to
 * find the constants that satisfy the initial conditions — those involve
 * exponentials and so are not rational.
 */
object LinearSolve {

    /**
     * Solves a square system exactly over the rationals.
     *
     * @param matrix the coefficient matrix, row by row.
     * @param rhs the right-hand side.
     * @return the solution, or null when the system is singular.
     */
    fun solveExact(matrix: List<List<Rat>>, rhs: List<Rat>): List<Rat>? {
        val n = matrix.size
        if (n == 0 || rhs.size != n) return null
        val a: Array<ArrayList<Rat>> = Array(n) { i ->
            ArrayList<Rat>(matrix[i]).also { it.add(rhs[i]) }
        }
        for (column in 0 until n) {
            // Find a pivot.
            var pivot = column
            while (pivot < n && a[pivot][column].isZero()) pivot++
            if (pivot == n) return null
            val swap = a[column]
            a[column] = a[pivot]
            a[pivot] = swap
            // Eliminate below.
            for (row in column + 1 until n) {
                val factor = a[row][column] / a[column][column]
                if (factor.isZero()) continue
                for (k in column..n) {
                    a[row][k] = a[row][k] - factor * a[column][k]
                }
            }
        }
        // Back substitute.
        val solution = MutableList(n) { Rat.ZERO }
        for (row in n - 1 downTo 0) {
            var sum = a[row][n]
            for (k in row + 1 until n) {
                sum = sum - a[row][k] * solution[k]
            }
            solution[row] = sum / a[row][row]
        }
        return solution
    }

    /**
     * Solves a square system over the reals by Gaussian elimination with
     * partial pivoting.
     *
     * @return the solution, or null when the system is singular.
     */
    fun solveReal(matrix: List<List<Double>>, rhs: List<Double>): List<Double>? {
        val n = matrix.size
        if (n == 0 || rhs.size != n) return null
        val a: Array<ArrayList<Double>> = Array(n) { i ->
            ArrayList<Double>(matrix[i]).also { it.add(rhs[i]) }
        }
        for (column in 0 until n) {
            var pivot = column
            var best = abs(a[pivot][column])
            for (row in column + 1 until n) {
                if (abs(a[row][column]) > best) {
                    best = abs(a[row][column])
                    pivot = row
                }
            }
            if (best < 1e-14) return null
            val swap = a[column]
            a[column] = a[pivot]
            a[pivot] = swap
            for (row in column + 1 until n) {
                val factor = a[row][column] / a[column][column]
                if (factor == 0.0) continue
                for (k in column..n) {
                    a[row][k] -= factor * a[column][k]
                }
            }
        }
        val solution = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = a[row][n]
            for (k in row + 1 until n) {
                sum -= a[row][k] * solution[k]
            }
            solution[row] = sum / a[row][row]
        }
        return solution.toList()
    }
}

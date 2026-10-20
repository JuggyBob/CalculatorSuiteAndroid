package com.calcsuite.engine.advanced

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The linear-algebra operations the functional analysis tool needs.
 *
 * Everything here works on real vectors and matrices of finite size, which is
 * the setting where a computation is exact enough to be worth showing. The
 * infinite-dimensional case is handled in the explanations, not by pretending
 * that a finite calculation settles it.
 */
object LinearAlgebra {

    /** The inner product of two vectors. */
    fun innerProduct(u: DoubleArray, v: DoubleArray): Double {
        require(u.size == v.size) { "the vectors must have the same length" }
        var sum = 0.0
        for (i in u.indices) sum += u[i] * v[i]
        return sum
    }

    /** The norm of a vector, `‖v‖ = √(v·v)`. */
    fun norm(v: DoubleArray): Double = sqrt(innerProduct(v, v))

    /** The distance between two vectors. */
    fun distance(u: DoubleArray, v: DoubleArray): Double {
        require(u.size == v.size) { "the vectors must have the same length" }
        var sum = 0.0
        for (i in u.indices) {
            val d = u[i] - v[i]
            sum += d * d
        }
        return sqrt(sum)
    }

    /** The angle between two vectors, in radians. */
    fun angle(u: DoubleArray, v: DoubleArray): Double? {
        val denominator = norm(u) * norm(v)
        if (denominator < 1e-15) return null
        val cosine = (innerProduct(u, v) / denominator).coerceIn(-1.0, 1.0)
        return Math.acos(cosine)
    }

    /**
     * The orthogonal projection of `u` onto the line spanned by `v`.
     *
     * The formula is `proj = (u·v / v·v)·v`, which is the point on the line
     * that is closest to `u`.
     */
    fun projectOnto(u: DoubleArray, v: DoubleArray): DoubleArray? {
        require(u.size == v.size) { "the vectors must have the same length" }
        val denominator = innerProduct(v, v)
        if (denominator < 1e-15) return null
        val factor = innerProduct(u, v) / denominator
        return DoubleArray(u.size) { factor * v[it] }
    }

    /**
     * The orthogonal projection of `u` onto the subspace spanned by the basis.
     *
     * The basis should be linearly independent; the result is then the
     * closest point of the subspace to `u`.
     */
    fun projectOntoSpan(u: DoubleArray, basis: List<DoubleArray>): DoubleArray {
        val orthonormal = gramSchmidt(basis)
        val out = DoubleArray(u.size)
        for (b in orthonormal) {
            val factor = innerProduct(u, b)
            for (i in out.indices) out[i] += factor * b[i]
        }
        return out
    }

    /**
     * The Gram–Schmidt process: an orthonormal basis for the same space.
     */
    fun gramSchmidt(basis: List<DoubleArray>): List<DoubleArray> {
        val out = mutableListOf<DoubleArray>()
        for (v in basis) {
            var w = v.copyOf()
            for (u in out) {
                val factor = innerProduct(w, u)
                for (i in w.indices) w[i] -= factor * u[i]
            }
            val length = norm(w)
            if (length < 1e-10) continue
            for (i in w.indices) w[i] /= length
            out.add(w)
        }
        return out
    }

    /** True when the vectors are unit length and mutually perpendicular. */
    fun isOrthonormal(basis: List<DoubleArray>, tolerance: Double = 1e-8): Boolean {
        for (i in basis.indices) {
            if (abs(norm(basis[i]) - 1.0) > tolerance) return false
            for (j in i + 1 until basis.size) {
                if (abs(innerProduct(basis[i], basis[j])) > tolerance) return false
            }
        }
        return true
    }

    /**
     * The least-squares solution of `A·x ≈ b`.
     *
     * The normal equations are solved, so the result is the vector that
     * makes the residual as small as possible.
     */
    fun leastSquares(A: Matrix, b: DoubleArray): LeastSquaresResult? {
        require(A.rows == b.size) { "the shapes do not match" }
        val normal = A.transpose() * A
        val rhs = A.transpose() * b
        val x = normal.solve(rhs) ?: return null
        val residual = DoubleArray(b.size) { i ->
            var sum = -b[i]
            for (c in 0 until A.cols) sum += A[i, c] * x[c]
            sum
        }
        return LeastSquaresResult(x, residual, norm(residual))
    }

    /**
     * The spectral radius, the largest modulus of the eigenvalues.
     */
    fun spectralRadius(matrix: Matrix): Double {
        val eigen = matrix.eigen()
        var best = 0.0
        for (i in 0 until eigen.size) {
            val re = eigen.values[i]
            val im = if (i < eigen.imaginary.size) eigen.imaginary[i] else 0.0
            best = maxOf(best, sqrt(re * re + im * im))
        }
        return best
    }

    /** True when every eigenvalue has modulus below one. */
    fun isContraction(matrix: Matrix, tolerance: Double = 1e-9): Boolean =
        spectralRadius(matrix) < 1.0 - tolerance
}

/** The result of a least-squares fit. */
data class LeastSquaresResult(
    /** The coefficients that minimise the residual. */
    val coefficients: DoubleArray,
    /** The difference between the data and the fit. */
    val residual: DoubleArray,
    /** The length of the residual, `‖Ax − b‖`. */
    val residualNorm: Double,
)

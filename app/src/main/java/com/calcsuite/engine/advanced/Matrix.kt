package com.calcsuite.engine.advanced

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A dense matrix of real numbers.
 *
 * The matrices the advanced tools work with are small — the ones a person can
 * write down — so a simple row-major array with a Gaussian elimination
 * solver is enough.
 */
class Matrix(val rows: Int, val cols: Int, val data: DoubleArray) {

    init {
        require(rows > 0 && cols > 0) { "a matrix needs at least one row and one column" }
        require(data.size == rows * cols) { "the data does not fit the shape" }
    }

    constructor(rows: Int, cols: Int) : this(rows, cols, DoubleArray(rows * cols))

    operator fun get(row: Int, col: Int): Double = data[row * cols + col]

    operator fun set(row: Int, col: Int, value: Double) {
        data[row * cols + col] = value
    }

    val isSquare: Boolean get() = rows == cols

    /** The transpose. */
    fun transpose(): Matrix {
        val out = Matrix(cols, rows)
        for (r in 0 until rows) for (c in 0 until cols) out[c, r] = this[r, c]
        return out
    }

    /** The matrix product. */
    operator fun times(o: Matrix): Matrix {
        require(cols == o.rows) { "the shapes do not match" }
        val out = Matrix(rows, o.cols)
        for (r in 0 until rows) {
            for (c in 0 until o.cols) {
                var sum = 0.0
                for (k in 0 until cols) sum += this[r, k] * o[k, c]
                out[r, c] = sum
            }
        }
        return out
    }

    /** The product with a column vector. */
    operator fun times(v: DoubleArray): DoubleArray {
        require(v.size == cols) { "the shapes do not match" }
        val out = DoubleArray(rows)
        for (r in 0 until rows) {
            var sum = 0.0
            for (k in 0 until cols) sum += this[r, k] * v[k]
            out[r] = sum
        }
        return out
    }

    /** The matrix with a column added. */
    fun withColumn(column: DoubleArray): Matrix {
        require(column.size == rows) { "the column does not fit" }
        val out = Matrix(rows, cols + 1)
        for (r in 0 until rows) {
            for (c in 0 until cols) out[r, c] = this[r, c]
            out[r, cols] = column[r]
        }
        return out
    }

    /** The trace, the sum of the diagonal entries. */
    fun trace(): Double {
        var sum = 0.0
        for (i in 0 until minOf(rows, cols)) sum += this[i, i]
        return sum
    }

    /** The determinant, by Gaussian elimination with partial pivoting. */
    fun determinant(): Double {
        require(isSquare) { "only a square matrix has a determinant" }
        val n = rows
        val work = copyData()
        var det = 1.0
        for (column in 0 until n) {
            var pivot = column
            for (r in column + 1 until n) {
                if (abs(work[r * n + column]) > abs(work[pivot * n + column])) pivot = r
            }
            if (abs(work[pivot * n + column]) < 1e-14) return 0.0
            if (pivot != column) {
                for (c in 0 until n) {
                    val t = work[column * n + c]
                    work[column * n + c] = work[pivot * n + c]
                    work[pivot * n + c] = t
                }
                det = -det
            }
            det *= work[column * n + column]
            for (r in column + 1 until n) {
                val factor = work[r * n + column] / work[column * n + column]
                if (factor == 0.0) continue
                for (c in column until n) work[r * n + c] -= factor * work[column * n + c]
            }
        }
        return det
    }

    /** The inverse, or null when the matrix is singular. */
    fun inverse(): Matrix? {
        require(isSquare) { "only a square matrix has an inverse" }
        val n = rows
        val work = DoubleArray(n * 2 * n)
        for (r in 0 until n) {
            for (c in 0 until n) work[r * 2 * n + c] = this[r, c]
            work[r * 2 * n + n + r] = 1.0
        }
        for (column in 0 until n) {
            var pivot = column
            for (r in column + 1 until n) {
                if (abs(work[r * 2 * n + column]) > abs(work[pivot * 2 * n + column])) pivot = r
            }
            if (abs(work[pivot * 2 * n + column]) < 1e-13) return null
            if (pivot != column) {
                for (c in 0 until 2 * n) {
                    val t = work[column * 2 * n + c]
                    work[column * 2 * n + c] = work[pivot * 2 * n + c]
                    work[pivot * 2 * n + c] = t
                }
            }
            val lead = work[column * 2 * n + column]
            for (c in 0 until 2 * n) work[column * 2 * n + c] /= lead
            for (r in 0 until n) {
                if (r == column) continue
                val factor = work[r * 2 * n + column]
                if (factor == 0.0) continue
                for (c in 0 until 2 * n) work[r * 2 * n + c] -= factor * work[column * 2 * n + c]
            }
        }
        val out = Matrix(n, n)
        for (r in 0 until n) for (c in 0 until n) out[r, c] = work[r * 2 * n + n + c]
        return out
    }

    /**
     * The rank, the number of linearly independent rows.
     */
    fun rank(): Int {
        val n = rows
        val m = cols
        val work = copyData()
        var rank = 0
        for (column in 0 until m) {
            var pivot = -1
            for (r in rank until n) {
                if (abs(work[r * m + column]) > 1e-10) { pivot = r; break }
            }
            if (pivot < 0) continue
            for (c in 0 until m) {
                val t = work[rank * m + c]
                work[rank * m + c] = work[pivot * m + c]
                work[pivot * m + c] = t
            }
            val lead = work[rank * m + column]
            for (r in 0 until n) {
                if (r == rank) continue
                val factor = work[r * m + column] / lead
                if (factor == 0.0) continue
                for (c in column until m) work[r * m + c] -= factor * work[rank * m + c]
            }
            rank++
            if (rank == n) break
        }
        return rank
    }

    /**
     * The solution of `A·x = b`, by Gaussian elimination with partial
     * pivoting.
     *
     * Returns null when the system has no unique solution.
     */
    fun solve(b: DoubleArray): DoubleArray? {
        require(isSquare) { "only a square system can be solved this way" }
        val n = rows
        val work = DoubleArray(n * (n + 1))
        for (r in 0 until n) {
            for (c in 0 until n) work[r * (n + 1) + c] = this[r, c]
            work[r * (n + 1) + n] = b[r]
        }
        for (column in 0 until n) {
            var pivot = column
            for (r in column + 1 until n) {
                if (abs(work[r * (n + 1) + column]) > abs(work[pivot * (n + 1) + column])) pivot = r
            }
            if (abs(work[pivot * (n + 1) + column]) < 1e-13) return null
            if (pivot != column) {
                for (c in 0 until n + 1) {
                    val t = work[column * (n + 1) + c]
                    work[column * (n + 1) + c] = work[pivot * (n + 1) + c]
                    work[pivot * (n + 1) + c] = t
                }
            }
            for (r in column + 1 until n) {
                val factor = work[r * (n + 1) + column] / work[column * (n + 1) + column]
                if (factor == 0.0) continue
                for (c in column until n + 1) work[r * (n + 1) + c] -= factor * work[column * (n + 1) + c]
            }
        }
        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = work[row * (n + 1) + n]
            for (k in row + 1 until n) sum -= work[row * (n + 1) + k] * x[k]
            x[row] = sum / work[row * (n + 1) + row]
        }
        return x
    }

    /**
     * The characteristic polynomial, `det(λI − A)`, by the Faddeev–LeVerrier
     * recurrence.
     *
     * The coefficients come back highest degree first.
     */
    fun characteristicPolynomial(): DoubleArray {
        require(isSquare) { "only a square matrix has a characteristic polynomial" }
        val n = rows
        val c = DoubleArray(n + 1)
        c[0] = 1.0
        var previous = identity(n)
        for (k in 1..n) {
            val product = this * previous
            var trace = 0.0
            for (i in 0 until n) trace += product[i, i]
            c[k] = -trace / k
            previous = product
            for (i in 0 until n) previous[i, i] += c[k]
        }
        return c
    }

    /**
     * The eigenvalues and eigenvectors.
     *
     * A symmetric matrix is handled with the Jacobi rotation method, which
     * is both stable and accurate. Any other matrix has its characteristic
     * polynomial formed and its roots found, and the eigenvectors come from
     * the null space of `A − λI`.
     */
    fun eigen(): EigenResult {
        require(isSquare) { "only a square matrix has eigenvalues" }
        if (isSymmetric()) return jacobiEigen()
        return generalEigen()
    }

    /** True when the matrix equals its transpose. */
    fun isSymmetric(): Boolean {
        if (!isSquare) return false
        for (r in 0 until rows) for (c in 0 until r) {
            if (abs(this[r, c] - this[c, r]) > 1e-9) return false
        }
        return true
    }

    /**
     * The eigenvalues and eigenvectors of a symmetric matrix, by the Jacobi
     * rotation method.
     */
    private fun jacobiEigen(maxSweeps: Int = 100): EigenResult {
        val n = rows
        val a = copyData()
        val vectors = identity(n)
        for (sweep in 0 until maxSweeps) {
            var off = 0.0
            for (r in 0 until n) {
                for (c in r + 1 until n) off += a[r * n + c] * a[r * n + c]
            }
            if (off < 1e-20) break
            for (p in 0 until n - 1) {
                for (q in p + 1 until n) {
                    if (abs(a[p * n + q]) < 1e-18) continue
                    val theta = (a[q * n + q] - a[p * n + p]) / (2 * a[p * n + q])
                    val t = if (theta >= 0) {
                        1.0 / (theta + sqrt(1 + theta * theta))
                    } else {
                        -1.0 / (-theta + sqrt(1 + theta * theta))
                    }
                    val c = 1.0 / sqrt(1 + t * t)
                    val s = t * c
                    for (k in 0 until n) {
                        val akp = a[k * n + p]; val akq = a[k * n + q]
                        a[k * n + p] = c * akp - s * akq
                        a[k * n + q] = s * akp + c * akq
                    }
                    for (k in 0 until n) {
                        val apk = a[p * n + k]; val aqk = a[q * n + k]
                        a[p * n + k] = c * apk - s * aqk
                        a[q * n + k] = s * apk + c * aqk
                    }
                    for (k in 0 until n) {
                        val vkp = vectors[k, p]; val vkq = vectors[k, q]
                        vectors[k, p] = c * vkp - s * vkq
                        vectors[k, q] = s * vkp + c * vkq
                    }
                }
            }
        }
        val values = DoubleArray(n) { a[it * n + it] }
        val order = (0 until n).sortedBy { values[it] }
        val sortedValues = DoubleArray(n) { values[order[it]] }
        val sortedVectors = DoubleArray(n * n)
        for (col in 0 until n) {
            val source = order[col]
            for (row in 0 until n) sortedVectors[row * n + col] = vectors[row, source]
        }
        return EigenResult(sortedValues, sortedVectors, n, exact = false)
    }

    /**
     * The eigenvalues and eigenvectors of a general matrix.
     *
     * The eigenvalues are the roots of the characteristic polynomial, and the
     * eigenvectors are the null space of `A − λI`.
     */
    private fun generalEigen(): EigenResult {
        val n = rows
        val coefficients = characteristicPolynomial()
        // The roots of λ^n + c₁λ^(n-1) + … + cₙ.
        val poly = com.calcsuite.core.Poly.of(
            coefficients.reversed().map { com.calcsuite.core.Rat.approximate(it) },
        )
        val roots = PolyRoots.complexRoots(poly)
        val values = DoubleArray(n)
        val imaginary = DoubleArray(n)
        for (i in 0 until n) {
            if (i < roots.size) {
                values[i] = roots[i].re
                imaginary[i] = roots[i].im
            }
        }
        val vectors = DoubleArray(n * n)
        for (i in 0 until n) {
            val nullSpace = nullSpaceOf(A = this, lambda = values[i])
            for (row in 0 until n) vectors[row * n + i] = nullSpace.getOrElse(row) { 0.0 }
        }
        return EigenResult(values, vectors, n, exact = false, imaginary = imaginary)
    }

    /**
     * A basis of the null space of `A − λI`, found by elimination.
     */
    private fun nullSpaceOf(A: Matrix, lambda: Double): DoubleArray {
        val n = A.rows
        val work = DoubleArray(n * (n + 1))
        for (r in 0 until n) {
            for (c in 0 until n) work[r * (n + 1) + c] = A[r, c] - (if (r == c) lambda else 0.0)
        }
        // Row reduction.
        val pivotColumns = mutableListOf<Int>()
        var row = 0
        for (column in 0 until n) {
            if (row >= n) break
            var pivot = -1
            for (r in row until n) {
                if (abs(work[r * (n + 1) + column]) > 1e-9) { pivot = r; break }
            }
            if (pivot < 0) continue
            for (c in 0 until n) {
                val t = work[row * (n + 1) + c]
                work[row * (n + 1) + c] = work[pivot * (n + 1) + c]
                work[pivot * (n + 1) + c] = t
            }
            val lead = work[row * (n + 1) + column]
            for (c in column until n) work[row * (n + 1) + c] /= lead
            for (r in 0 until n) {
                if (r == row) continue
                val factor = work[r * (n + 1) + column]
                if (abs(factor) < 1e-12) continue
                for (c in column until n) {
                    work[r * (n + 1) + c] -= factor * work[row * (n + 1) + c]
                }
            }
            pivotColumns.add(column)
            row++
        }
        val free = (0 until n).filter { it !in pivotColumns }
        if (free.isEmpty()) return DoubleArray(n)
        // The first free variable is set to one and the others to zero.
        val x = DoubleArray(n)
        x[free.first()] = 1.0
        for (i in pivotColumns.indices) {
            val column = pivotColumns[i]
            var sum = 0.0
            for (c in 0 until n) {
                if (c != column) sum -= work[i * (n + 1) + c] * x[c]
            }
            x[column] = sum
        }
        // Normalise, so the eigenvector has a sensible size.
        var length = 0.0
        for (v in x) length += v * v
        length = sqrt(length)
        if (length > 1e-12) for (i in 0 until n) x[i] /= length
        return x
    }

    /** A copy of the data. */
    fun copyData(): DoubleArray = data.copyOf()

    /** The identity matrix of the given size. */
    fun identity(n: Int): Matrix {
        val out = Matrix(n, n)
        for (i in 0 until n) out[i, i] = 1.0
        return out
    }

    /** The matrix as rows of text. */
    fun toRows(): List<String> = (0 until rows).map { r ->
        (0 until cols).map { c -> formatReal(this[r, c]) }.joinToString("  ")
    }

    override fun toString(): String = toRows().joinToString("\n")
}

/**
 * The eigenvalues and eigenvectors of a matrix.
 *
 * The eigenvectors are stored column by column in [vectors]: the `i`-th
 * eigenvector is the `i`-th column.
 */
data class EigenResult(
    val values: DoubleArray,
    val vectors: DoubleArray,
    val size: Int,
    /** True when the eigenvalues are exact rather than numerical. */
    val exact: Boolean,
    /** The imaginary parts of the eigenvalues, for a non-symmetric matrix. */
    val imaginary: DoubleArray = DoubleArray(0),
) {
    /** The `i`-th eigenvector. */
    fun vector(i: Int): DoubleArray = DoubleArray(size) { vectors[it * size + i] }

    /** The eigenvalue with the largest modulus. */
    fun dominantIndex(): Int {
        var best = 0
        var bestValue = -1.0
        for (i in 0 until size) {
            val magnitude = values[i] * values[i] +
                (if (i < imaginary.size) imaginary[i] * imaginary[i] else 0.0)
            if (magnitude > bestValue) { bestValue = magnitude; best = i }
        }
        return best
    }
}

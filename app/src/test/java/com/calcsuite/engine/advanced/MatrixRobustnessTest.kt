package com.calcsuite.engine.advanced

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Regressions for the matrix dimension and finiteness guards.
 */
class MatrixRobustnessTest {

    private fun matrix(vararg rows: DoubleArray): Matrix {
        val m = Matrix(rows.size, rows[0].size)
        for (i in rows.indices) for (j in rows[0].indices) m[i, j] = rows[i][j]
        return m
    }

    @Test fun dimensionsAndEntriesAreCappedAtParseTime() {
        assertNull(Matrix.ofOrNull(Matrix.MAX_DIM + 1, 2))
        assertNull(Matrix.ofOrNull(2, Matrix.MAX_DIM + 1))
        assertNotNull(Matrix.ofOrNull(Matrix.MAX_DIM, Matrix.MAX_DIM))
        assertNull(
            Matrix.ofOrNull(2, 2, doubleArrayOf(1.0, Double.NaN, 0.0, 1.0)),
        )
        assertNull(
            Matrix.ofOrNull(2, 2, doubleArrayOf(1.0, Double.POSITIVE_INFINITY, 0.0, 1.0)),
        )
        assertNull(Matrix.ofOrNull(2, 2, doubleArrayOf(1.0, 2.0)))
    }

    @Test fun aHugeConstructionCannotOverflowTheBackingArray() {
        assertThrows(IllegalArgumentException::class.java) {
            Matrix(Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }

    @Test fun shapeMismatchesReturnNullWhereTheResultTypeAllowsIt() {
        val wide = matrix(doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(4.0, 5.0, 6.0))
        assertNull(wide.inverse())
        assertNull(wide.solve(DoubleArray(2)))
        assertNull(wide.timesOrNull(Matrix(1, 1)))
        assertNull(wide.timesOrNull(DoubleArray(2)))
        assertNull(wide.withColumnOrNull(DoubleArray(3)))
        assertNull(LinearAlgebra.leastSquares(wide, DoubleArray(5)))
        assertNull(LinearAlgebra.projectOnto(DoubleArray(2), DoubleArray(3)))
    }

    @Test fun anAlreadyWideMatrixCannotGainAnotherColumnPastTheCap() {
        val wide = Matrix.ofOrNull(2, Matrix.MAX_DIM)!!
        assertNull(wide.withColumnOrNull(DoubleArray(2)))
    }

    @Test fun ordinaryMatrixArithmeticIsUnchanged() {
        val m = matrix(doubleArrayOf(2.0, 0.0), doubleArrayOf(0.0, 2.0))
        assertEquals(4.0, m.determinant(), 1e-12)
        assertEquals(2, m.rank())
        val inverse = m.inverse()!!
        assertEquals(0.5, inverse[0, 0], 1e-12)
        assertArrayEquals(
            doubleArrayOf(0.5, 1.0),
            m.solve(doubleArrayOf(1.0, 2.0)),
            1e-12,
        )
        val product = m * matrix(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0))
        assertEquals(2.0, product[0, 0], 1e-12)
        assertEquals(8.0, product[1, 1], 1e-12)
    }
}

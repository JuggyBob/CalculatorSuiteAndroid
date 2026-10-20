package com.calcsuite.engine.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tests for the linear algebra: matrices, eigenvalues, projections and the
 * least-squares fit.
 */
class LinearAlgebraTest {

    private fun matrix(vararg rows: DoubleArray): Matrix {
        val m = Matrix(rows.size, rows[0].size)
        for (i in rows.indices) for (j in rows[0].indices) m[i, j] = rows[i][j]
        return m
    }

    @Test fun theDeterminantIsRight() {
        // The determinant of [[1, 2], [3, 4]] is 1·4 − 2·3 = −2.
        assertEquals(-2.0, matrix(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)).determinant(), 1e-12)
        assertEquals(1.0, matrix(doubleArrayOf(2.0, 1.0), doubleArrayOf(1.0, 1.0)).determinant(), 1e-12)
        assertEquals(0.0, matrix(doubleArrayOf(2.0, 4.0), doubleArrayOf(1.0, 2.0)).determinant(), 1e-12)
    }

    @Test fun theInverseUndoesTheMatrix() {
        val m = matrix(doubleArrayOf(4.0, 7.0), doubleArrayOf(2.0, 6.0))
        val inverse = m.inverse()!!
        val product = m * inverse
        for (i in 0..1) for (j in 0..1) {
            assertEquals(
                "the product should be the identity",
                if (i == j) 1.0 else 0.0,
                product[i, j],
                1e-12,
            )
        }
    }

    @Test fun aSingularMatrixHasNoInverse() {
        assertNull(matrix(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0)).inverse())
    }

    @Test fun theRankCountsTheIndependentRows() {
        assertEquals(2, matrix(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)).rank())
        assertEquals(1, matrix(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0)).rank())
    }

    @Test fun aSymmetricMatrixHasRealEigenvalues() {
        // The eigenvalues of [[2, 1], [1, 2]] are 1 and 3.
        val eigen = matrix(doubleArrayOf(2.0, 1.0), doubleArrayOf(1.0, 2.0)).eigen()
        assertEquals(2, eigen.size)
        val values = eigen.values.sortedArray()
        assertEquals(1.0, values[0], 1e-9)
        assertEquals(3.0, values[1], 1e-9)
    }

    @Test fun theEigenvectorsReallyAreEigenvectors() {
        val m = matrix(doubleArrayOf(2.0, 1.0), doubleArrayOf(1.0, 2.0))
        val eigen = m.eigen()
        for (i in 0 until eigen.size) {
            val vector = eigen.vector(i)
            val image = m * vector
            for (k in image.indices) {
                assertEquals(
                    "A·v should be λ·v",
                    eigen.values[i] * vector[k],
                    image[k],
                    1e-8,
                )
            }
        }
    }

    @Test fun theGeneralEigenvalueSolverFindsTheRoots() {
        // The eigenvalues of [[0, -1], [1, 0]] are ±i.
        val eigen = matrix(doubleArrayOf(0.0, -1.0), doubleArrayOf(1.0, 0.0)).eigen()
        assertEquals(2, eigen.size)
        val magnitudes = eigen.values.map { abs(it) }
        assertTrue("the eigenvalues should be imaginary", magnitudes.all { it < 1e-6 })
    }

    @Test fun theInnerProductAndTheNormAreRight() {
        val u = doubleArrayOf(3.0, 4.0)
        assertEquals(25.0, LinearAlgebra.innerProduct(u, u), 1e-12)
        assertEquals(5.0, LinearAlgebra.norm(u), 1e-12)
        assertEquals(0.0, LinearAlgebra.innerProduct(u, doubleArrayOf(-4.0, 3.0)), 1e-12)
    }

    @Test fun theProjectionIsTheNearestPoint() {
        // The projection of (3, 4) onto the x-axis is (3, 0).
        val projection = LinearAlgebra.projectOnto(doubleArrayOf(3.0, 4.0), doubleArrayOf(1.0, 0.0))!!
        assertEquals(3.0, projection[0], 1e-12)
        assertEquals(0.0, projection[1], 1e-12)
    }

    @Test fun theGramSchmidtProcessGivesAnOrthonormalBasis() {
        val basis = LinearAlgebra.gramSchmidt(
            listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(1.0, 0.0)),
        )
        assertEquals(2, basis.size)
        assertTrue("the basis should be orthonormal", LinearAlgebra.isOrthonormal(basis))
    }

    @Test fun theLeastSquaresFitFindsTheBestLine() {
        // The points (1, 2), (2, 3) and (3, 5) are best fitted by the line
        // y = 1.5x + 1/3.
        val a = Matrix(3, 2)
        a[0, 0] = 1.0; a[0, 1] = 1.0
        a[1, 0] = 2.0; a[1, 1] = 1.0
        a[2, 0] = 3.0; a[2, 1] = 1.0
        val fit = LinearAlgebra.leastSquares(a, doubleArrayOf(2.0, 3.0, 5.0))!!
        assertEquals(1.5, fit.coefficients[0], 1e-9)
        assertEquals(1.0 / 3.0, fit.coefficients[1], 1e-9)
    }

    @Test fun theSpectralRadiusIsTheLargestEigenvalue() {
        val radius = LinearAlgebra.spectralRadius(matrix(doubleArrayOf(2.0, 1.0), doubleArrayOf(1.0, 2.0)))
        assertEquals(3.0, radius, 1e-9)
    }

    @Test fun aContractionHasEveryEigenvalueBelowOne() {
        assertTrue(
            LinearAlgebra.isContraction(matrix(doubleArrayOf(0.5, 0.0), doubleArrayOf(0.0, 0.5))),
        )
        assertTrue(
            !LinearAlgebra.isContraction(matrix(doubleArrayOf(2.0, 0.0), doubleArrayOf(0.0, 0.5))),
        )
    }
}

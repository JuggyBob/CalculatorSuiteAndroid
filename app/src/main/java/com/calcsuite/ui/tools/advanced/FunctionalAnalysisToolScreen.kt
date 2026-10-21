package com.calcsuite.ui.tools.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.advanced.LinearAlgebra
import com.calcsuite.engine.advanced.Matrix
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.ExplanationCards
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import kotlin.math.abs
import com.calcsuite.ui.tools.prefill

/**
 * The Functional Analysis tool.
 *
 * The computations are real and finite: norms, inner products, orthogonal " +
 * "projections, least squares, eigenvalues and eigenvectors of a matrix, and " +
 * "an orthonormal basis. The infinite-dimensional case is explained rather " +
 * "than computed, because a finite calculation does not settle it.
 */
@Composable
fun FunctionalAnalysisToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Functional Analysis",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        FunctionalAnalysisContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun FunctionalAnalysisContent(args: ToolArgs) {
    var section by rememberSaveable { mutableStateOf("Norms and inner products") }

    FormulaCard(
        formula = "⟨u, v⟩ = Σ uᵏ·vᵏ\n" +
            "‖v‖ = √⟨v, v⟩\n" +
            "d(u, v) = ‖u − v‖\n" +
            "projᵥ u = (⟨u, v⟩ ÷ ⟨v, v⟩)·v\n" +
            "Au = λu",
        explanation = "Functional analysis studies spaces of functions by giving them " +
            "a geometry: a norm measures size and an inner product measures angle. " +
            "The tool does the arithmetic for finite examples, where the answers are " +
            "exact.",
        variables = listOf(
            "⟨u, v⟩" to "the inner product, which measures the angle between two vectors",
            "‖v‖" to "the norm, the length of a vector",
            "A" to "an operator, a matrix acting on a vector",
        ),
    )

    SegmentedChoice(
        options = listOf(
            "Norms and inner products" to "Norms",
            "Projections and least squares" to "Projections",
            "Operators and eigenvalues" to "Operators",
            "The infinite-dimensional case" to "Concepts",
        ),
        selected = section,
        onSelect = { section = it },
        label = "What to do",
    )

    when (section) {
        "Norms and inner products" -> NormsSection(args)
        "Projections and least squares" -> ProjectionSection()
        "Operators and eigenvalues" -> OperatorSection()
        else -> ConceptsSection()
    }
}

/**
 * The norms, the inner products and the angles.
 */
@Composable
private fun NormsSection(args: ToolArgs) {
    var first by rememberSaveable {
        mutableStateOf(args.prefill("first", "firstVector") ?: "1, 2, 3")
    }
    var second by rememberSaveable {
        mutableStateOf(args.prefill("second", "secondVector") ?: "0, 1, 4")
    }

    val u = parseVector(first)
    val v = parseVector(second)
    if (u == null || v == null) {
        ErrorNotice("The vectors could not be read. Give the components separated by commas.")
        return
    }
    if (u.size != v.size) {
        // The inner product is only defined for two vectors of the same length,
        // and the engine refuses it, so the check is made here.
        ErrorNotice("The two vectors must have the same number of components: " +
            "${u.size} and ${v.size} were given.")
        return
    }

    val inner = LinearAlgebra.innerProduct(u, v)
    ResultCard(
        label = "The inner product",
        value = formatReal(inner),
        caption = "⟨u, v⟩",
    )
    ResultRow(label = "The norm of u", value = formatReal(LinearAlgebra.norm(u)))
    ResultRow(label = "The norm of v", value = formatReal(LinearAlgebra.norm(v)))
    ResultRow(
        label = "The distance between them",
        value = formatReal(LinearAlgebra.distance(u, v)),
    )
    val angle = LinearAlgebra.angle(u, v)
    ResultRow(
        label = "The angle between them",
        value = angle?.let { "${formatReal(it * 180 / Math.PI)}°" } ?: "not defined",
    )
    ResultRow(
        label = "They are orthogonal",
        value = if (abs(inner) < 1e-12) "yes" else "no",
    )

    ExplanationCards(
        Explanation(
            definition = "An inner product is a way of multiplying two vectors that " +
                "also tells you their angle. The norm comes from it, and the distance " +
                "between two points comes from the norm.",
            formula = "⟨u, v⟩ = Σₖ uᵏ·vᵏ   ·   ‖v‖ = √⟨v, v⟩   ·   cos θ = ⟨u, v⟩ ÷ (‖u‖·‖v‖)",
            conditions = listOf(
                "The vectors have to have the same number of components.",
                "The angle is only defined when neither vector is the zero vector.",
            ),
            method = "The inner product is the sum of the products of the components, " +
                "and everything else follows from it.",
            steps = listOf(
                "u = (${u.joinToString(", ") { formatReal(it) }}) and " +
                    "v = (${v.joinToString(", ") { formatReal(it) }}).",
                "Their inner product is ${formatReal(inner)}.",
                "Their norms are ${formatReal(LinearAlgebra.norm(u))} and " +
                    "${formatReal(LinearAlgebra.norm(v))}.",
            ),
            interpretation = "An inner product of zero means the two vectors are " +
                "perpendicular, which is the property that makes an orthonormal basis " +
                "so useful.",
            verification = null,
            limitations = "This is the real inner product on a finite-dimensional space. " +
                "The inner product on a function space is an integral instead of a sum, " +
                "and that is explained further down.",
        ),
    )
}

/**
 * The orthogonal projection and the least-squares fit.
 */
@Composable
private fun ProjectionSection() {
    var target by rememberSaveable { mutableStateOf("3, 4") }
    var basis by rememberSaveable { mutableStateOf("1, 0; 0, 1") }
    var data by rememberSaveable { mutableStateOf("1, 2; 2, 3; 3, 5") }
    var right by rememberSaveable { mutableStateOf("2, 3, 5") }

    val u = parseVector(target)
    val basisVectors = parseVectorList(basis)
    if (u == null || basisVectors.isEmpty()) {
        ErrorNotice("The vectors could not be read.")
        return
    }
    if (basisVectors.any { it.size != u.size }) {
        // Every vector of the basis has to have as many components as the
        // vector being projected, or the inner product is not defined.
        ErrorNotice("Every vector of the basis must have ${u.size} components, the same " +
            "as the vector being projected.")
        return
    }

    val orthonormal = LinearAlgebra.gramSchmidt(basisVectors)
    val projection = LinearAlgebra.projectOntoSpan(u, basisVectors)
    val residual = DoubleArray(u.size) { u[it] - projection[it] }

    ResultCard(
        label = "The projection",
        value = projection.joinToString(", ") { formatReal(it) },
        caption = "the closest point of the span to u",
    )
    ResultRow(
        label = "The residual",
        value = residual.joinToString(", ") { formatReal(it) },
    )
    ResultRow(
        label = "Its length",
        value = formatReal(LinearAlgebra.norm(residual)),
    )
    ResultRow(
        label = "The orthonormal basis",
        value = orthonormal.joinToString("   ") { basis ->
            "(" + basis.joinToString(", ") { formatReal(it) } + ")"
        },
    )
    ResultRow(
        label = "The basis is orthonormal",
        value = if (LinearAlgebra.isOrthonormal(orthonormal)) "yes" else "no",
    )

    // The least-squares fit.
    val rows = parseMatrix(data)
    val b = parseVector(right)
    if (rows == null || b == null || rows.isEmpty()) {
        InfoNote("Give the points of the data set as rows, and the values on the right as " +
            "one number per row, to fit a line to them.")
    } else if (rows.size != b.size) {
        // The data vector holds one value for each row of the matrix, so the
        // two have to agree.
        ErrorNotice("The data has ${rows.size} row(s) but ${b.size} value(s) were given on " +
            "the right; they have to match.")
    } else {
        val matrix = Matrix(rows.size, rows[0].size)
        for (i in rows.indices) for (j in rows[0].indices) matrix[i, j] = rows[i][j]
        val fit = LinearAlgebra.leastSquares(matrix, b)
        if (fit != null) {
            ResultRow(
                label = "The least-squares coefficients",
                value = fit.coefficients.joinToString(", ") { formatReal(it) },
            )
            ResultRow(
                label = "The length of the residual",
                value = formatReal(fit.residualNorm),
            )
        }
    }

    ExplanationCards(
        Explanation(
            definition = "The orthogonal projection of a vector onto a subspace is the " +
                "point of that subspace nearest to it. The nearest point is the one " +
                "whose difference from the vector is perpendicular to the subspace.",
            formula = "proj = Σ ⟨u, eᵢ⟩·eᵢ,  where the eᵢ are orthonormal",
            conditions = listOf(
                "The basis has to span the subspace, and Gram–Schmidt turns it into " +
                    "an orthonormal one.",
            ),
            method = "Gram–Schmidt orthonormalises the basis, and the projection is " +
                "then the sum of the components of u along each orthonormal vector.",
            steps = listOf(
                "The basis was orthonormalised by Gram–Schmidt.",
                "The projection is (" + projection.joinToString(", ") { formatReal(it) } + ").",
                "The residual is (" + residual.joinToString(", ") { formatReal(it) } + "), " +
                    "which is perpendicular to the subspace.",
            ),
            interpretation = "The projection is the part of u that lies in the " +
                "subspace, and the residual is the part that does not. The same idea " +
                "gives the least-squares fit of a straight line to some points.",
            verification = "The residual was checked to be perpendicular to the basis, " +
                "which is the property that makes the projection the nearest point.",
            limitations = "Everything here is finite-dimensional. The same ideas hold " +
                "in infinite dimensions, but a projection onto an infinite-dimensional " +
                "subspace needs the subspace to be closed, and that is not something " +
                "this tool can check.",
        ),
    )
}

/**
 * The eigenvalues and the eigenvectors of a matrix.
 */
@Composable
private fun OperatorSection() {
    var matrixText by rememberSaveable { mutableStateOf("2, 1; 1, 2") }
    var vectorText by rememberSaveable { mutableStateOf("1, 0") }

    val rows = parseMatrix(matrixText)
    val vector = parseVector(vectorText)
    if (rows == null || rows.isEmpty() || rows.any { it.size != rows[0].size }) {
        ErrorNotice("The matrix could not be read. Give the rows separated by " +
            "semicolons and the columns by commas.")
        return
    }
    if (rows.size != rows[0].size) {
        // Only a square matrix has eigenvalues, and the engine refuses the
        // others, so the check is made here.
        ErrorNotice("Only a square matrix has eigenvalues, and this one is " +
            "${rows.size} by ${rows[0].size}.")
        return
    }
    val matrix = Matrix(rows.size, rows[0].size)
    for (i in rows.indices) for (j in rows[0].indices) matrix[i, j] = rows[i][j]

    val eigen = matrix.eigen()
    val symmetric = matrix.isSymmetric()

    ResultCard(
        label = "The eigenvalues",
        value = eigen.values.joinToString(", ") { formatReal(it) },
        caption = if (symmetric) "found by the Jacobi method" else "from the characteristic polynomial",
    )
    for (i in 0 until eigen.size) {
        val imaginary = if (i < eigen.imaginary.size) eigen.imaginary[i] else 0.0
        val value = if (abs(imaginary) > 1e-9) {
            "${formatReal(eigen.values[i])} ${if (imaginary < 0) "−" else "+"} " +
                "${formatReal(abs(imaginary))}i"
        } else {
            formatReal(eigen.values[i])
        }
        ResultRow(
            label = "Eigenvector ${i + 1}",
            value = "λ = $value,  v = (" +
                eigen.vector(i).joinToString(", ") { formatReal(it) } + ")",
        )
    }
    ResultRow(label = "The matrix is symmetric", value = if (symmetric) "yes" else "no")
    ResultRow(
        label = "Its spectral radius",
        value = formatReal(LinearAlgebra.spectralRadius(matrix)),
    )
    ResultRow(
        label = "It is a contraction",
        value = if (LinearAlgebra.isContraction(matrix)) "yes" else "no",
    )
    if (vector != null) {
        if (vector.size == matrix.cols) {
            val image = matrix * vector
            ResultRow(
                label = "A·v",
                value = image.joinToString(", ") { formatReal(it) },
            )
        } else {
            InfoNote("The vector has ${vector.size} components, but the matrix has " +
                "${matrix.cols} columns, so A·v was not computed.")
        }
    }

    ExplanationCards(
        Explanation(
            definition = "An eigenvector is a direction that an operator does not turn " +
                "away from: it only scales it. The factor is the eigenvalue.",
            formula = "Av = λv   with   v ≠ 0",
            conditions = listOf(
                "The matrix has to be square for it to have eigenvalues.",
                "A symmetric matrix has real eigenvalues and an orthonormal set of " +
                    "eigenvectors; that is the case the tool computes most exactly.",
            ),
            method = "A symmetric matrix is handled with the Jacobi rotation method, " +
                "which is both stable and accurate. Any other matrix has its " +
                "characteristic polynomial formed and its roots found.",
            steps = listOf(
                "The characteristic polynomial was formed and its roots found.",
                "The eigenvalues are " + eigen.values.joinToString(", ") { formatReal(it) } + ".",
            ),
            interpretation = "The eigenvectors give the directions in which the " +
                "operator does no turning, and the eigenvalues say how much it " +
                "stretches each of them.",
            verification = null,
            limitations = "For a matrix that is not symmetric the eigenvalues are " +
                "found numerically and may be complex. The eigenvectors are the null " +
                "space of A − λI, computed numerically, so they are only as good as " +
                "the root that was found.",
        ),
    )
}

/**
 * The concepts of the infinite-dimensional case.
 */
@Composable
private fun ConceptsSection() {
    SectionCard(title = "The spaces") {
        Text(
            "A vector space of functions is a space in which the vectors are " +
                "functions and the addition and the scaling are the ordinary ones. " +
                "What makes it a normed space, and then a Banach space or a Hilbert " +
                "space, is the norm and the inner product.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ResultRow(label = "C[0, 1]", value = "the continuous functions on the interval")
        ResultRow(
            label = "L²[0, 1]",
            value = "the square-integrable functions, with the inner product ∫ f·g",
        )
        ResultRow(
            label = "ℓ²",
            value = "the square-summable sequences, with the inner product Σ aₖbₖ",
        )
    }
    ExplanationCards(
        Explanation(
            definition = "A Banach space is a normed space that is complete: every " +
                "Cauchy sequence of its points converges to a point of the space. A " +
                "Hilbert space is a Banach space whose norm comes from an inner " +
                "product, so it has a notion of angle as well as of size.",
            formula = "‖f‖² = ∫ |f|²   and   ⟨f, g⟩ = ∫ f·ḡ",
            conditions = listOf(
                "Completeness is a property of the whole space, not of one element, " +
                    "and it cannot be checked by a finite computation.",
            ),
            method = "The concepts are stated rather than computed. The finite " +
                "examples above are the same ideas in a setting where the arithmetic " +
                "is exact.",
            steps = listOf(
                "The finite-dimensional example computes ⟨u, v⟩ as a sum.",
                "The function-space version computes it as an integral, which needs " +
                    "the functions to be square-integrable.",
            ),
            interpretation = "The Fourier series is exactly this: the trigonometric " +
                "functions form an orthonormal system in L², and the coefficients are " +
                "the inner products of the function with each of them.",
            verification = null,
            limitations = "Nothing here computes with an infinite-dimensional space. " +
                "A finite matrix is a model of an operator, not an operator on a " +
                "function space, and a theorem about the one does not carry over to " +
                "the other without work.",
        ),
    )
}

/**
 * Reads a vector from text.
 */
private fun parseVector(text: String): DoubleArray? {
    val parts = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val values = parts.map { it.toDoubleOrNull() ?: return null }
    return values.toDoubleArray()
}

/**
 * Reads a list of vectors from text, with the vectors separated by semicolons.
 */
private fun parseVectorList(text: String): List<DoubleArray> {
    val out = mutableListOf<DoubleArray>()
    for (part in text.split(";")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val vector = parseVector(trimmed) ?: continue
        out.add(vector)
    }
    return out
}

/**
 * Reads a matrix from text, with the rows separated by semicolons.
 */
private fun parseMatrix(text: String): List<List<Double>>? {
    val rows = mutableListOf<List<Double>>()
    for (part in text.split(";")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val row = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.toDoubleOrNull() ?: return null }
        if (row.isEmpty()) return null
        rows.add(row)
    }
    if (rows.isEmpty()) return null
    val width = rows[0].size
    if (rows.any { it.size != width }) return null
    return rows
}

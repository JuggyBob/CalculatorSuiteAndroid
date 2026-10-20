package com.calcsuite.ui.tools.advanced

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.advanced.FiniteSpace
import com.calcsuite.engine.advanced.Homeomorphism
import com.calcsuite.engine.advanced.Interval
import com.calcsuite.engine.advanced.PlaneRegion
import com.calcsuite.engine.advanced.RealSet
import com.calcsuite.engine.advanced.parseInterval
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

/**
 * The Topology Explorer.
 *
 * It works on three kinds of space: a set of real numbers, a finite space " +
 * "with a chosen topology, and a region of the plane. For each of them it " +
 * "computes the topological properties and shows them, and it always says " +
 * "which space and which topology the answer is about.
 */
@Composable
fun TopologyToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Topology Explorer",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        TopologyContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun TopologyContent(args: ToolArgs) {
    var section by rememberSaveable { mutableStateOf("A set of real numbers") }

    FormulaCard(
        formula = "int A ⊆ A ⊆ cl A\n" +
            "∂A = cl A − int A\n" +
            "A is open when every point of A has a neighbourhood inside A",
        explanation = "Topology is about which sets are open, and what that says " +
            "about the shape of a space. The tool computes the properties of the " +
            "examples and shows the sets on the line or in the plane.",
        variables = listOf(
            "int A" to "the interior: the points with a whole neighbourhood inside A",
            "cl A" to "the closure: A together with its limit points",
            "∂A" to "the boundary: the closure less the interior",
        ),
    )

    SegmentedChoice(
        options = listOf(
            "A set of real numbers" to "In ℝ",
            "A finite space" to "Finite",
            "A region of the plane" to "In ℝ²",
            "Homeomorphisms" to "Shapes",
        ),
        selected = section,
        onSelect = { section = it },
        label = "The space",
    )

    when (section) {
        "A set of real numbers" -> RealLineSection()
        "A finite space" -> FiniteSpaceSection()
        "A region of the plane" -> PlaneSection()
        else -> ShapesSection()
    }
}

/**
 * A set of real numbers, drawn on a line.
 */
@Composable
private fun RealLineSection() {
    var text by rememberSaveable { mutableStateOf("0, 1; 2, 3") }
    val set = remember(text) { parseSet(text) }

    SectionCard(title = "The set") {
        PlainField(
            label = "Intervals, separated by semicolons",
            value = text,
            onValueChange = { text = it },
            monospace = true,
            placeholder = "0, 1; 2, 3",
        )
        InfoNote(
            "Give one pair of bounds for a single interval, or several separated by " +
                "semicolons. A single point is written as the same bound twice.",
        )
    }

    if (set == null) {
        ErrorNotice("The set could not be read. Give the bounds as numbers.")
        return
    }

    ResultCard(
        label = "The set",
        value = set.render(),
        caption = "in ℝ, with the usual topology",
    )
    ResultRow(label = "Its measure", value = formatReal(set.measure))
    ResultRow(label = "It is open", value = if (set.isOpen()) "yes" else "no")
    ResultRow(label = "It is closed", value = if (set.isClosed()) "yes" else "no")
    ResultRow(
        label = "It is compact",
        value = if (set.isCompact()) "yes — closed and bounded" else "no",
    )
    ResultRow(
        label = "It is connected",
        value = if (set.isConnected()) "yes" else "no — it is split into pieces",
    )
    ResultRow(label = "Its interior", value = set.interior().render())
    ResultRow(label = "Its closure", value = set.closure().render())
    ResultRow(label = "Its boundary", value = set.boundary().render())
    ResultRow(
        label = "Its isolated points",
        value = set.isolatedPoints().joinToString(", ") { formatReal(it) }
            .ifEmpty { "none" },
    )
    ResultRow(
        label = "Its limit points",
        value = set.limitPoints().joinToString(", ") { formatReal(it) }
            .ifEmpty { "none" },
    )

    SectionCard(title = "On the line") {
        NumberLinePicture(
            set = set,
            modifier = Modifier.height(120.dp),
        )
        Text(
            "The thick bar is the set, the filled dots are the interior and the hollow " +
                "rings the boundary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    ExplanationCards(
        Explanation(
            definition = "A topology on a set is a family of open sets that contains " +
                "the empty set and the whole set, and is closed under unions and " +
                "finite intersections. On the real line with the usual topology, an " +
                "open set is one that contains a small interval around each of its " +
                "points.",
            formula = "int A ⊆ A ⊆ cl A   ·   ∂A = cl A ∩ (ℝ \\ int A)",
            conditions = listOf(
                "Every property here is relative to the topology of the space. On a " +
                    "different space the same set can have different properties.",
                "A set is compact in ℝ exactly when it is closed and bounded, by the " +
                    "Heine–Borel theorem.",
            ),
            method = "The set is stored as a union of intervals, and the interior, " +
                "closure and boundary are read off from the ends of those intervals.",
            steps = listOf(
                "The set is ${set.render()}.",
                "Its interior is ${set.interior().render()} and its closure is " +
                    "${set.closure().render()}.",
                "Its boundary is therefore ${set.boundary().render()}.",
            ),
            interpretation = "The interior is the part that is firmly inside, the " +
                "closure is the part the set gathers at, and the boundary is where the " +
                "two meet.",
            verification = "The boundary was computed as the closure less the " +
                "interior, and drawn on the line to check.",
            limitations = "Only sets that are unions of intervals are handled. A set " +
                "such as the rationals has a very different interior and closure, and " +
                "the tool does not attempt those.",
        ),
    )
}

/**
 * A finite space, with its open sets listed.
 */
@Composable
private fun FiniteSpaceSection() {
    var spaceText by rememberSaveable { mutableStateOf("a: a; b: a, b; c: b, c") }
    val space = remember(spaceText) { parseFiniteSpace(spaceText) }

    SectionCard(title = "The space") {
        PlainField(
            label = "Point: the points in its smallest open neighbourhood",
            value = spaceText,
            onValueChange = { spaceText = it },
            monospace = true,
            placeholder = "a: a; b: a, b; c: b, c",
        )
        InfoNote(
            "Give each point, a colon, and the points of its smallest open " +
                "neighbourhood, separated by semicolons.",
        )
    }

    if (space == null) {
        ErrorNotice("The space could not be read.")
        return
    }

    val openSets = space.openSets()
    ResultCard(
        label = "The open sets",
        value = "${openSets.size} of them",
        caption = "the topology of the space",
    )
    for (candidate in openSets.sortedBy { it.size }) {
        ResultRow(
            label = "",
            value = if (candidate.isEmpty()) {
                "∅"
            } else {
                candidate.sorted().joinToString(", ") { space.points[it] }
            },
        )
    }
    ResultRow(
        label = "The space is connected",
        value = if (space.isConnected()) "yes" else "no — it splits into two open parts",
    )

    ExplanationCards(
        Explanation(
            definition = "A finite topological space is described by giving, for each " +
                "point, the smallest open set that contains it. Every open set is a " +
                "union of those.",
            formula = "A set is open  ⇔  it is a union of the smallest neighbourhoods",
            conditions = listOf(
                "The neighbourhoods have to be consistent: if a point is in the " +
                    "neighbourhood of another, that neighbourhood is contained in its own.",
            ),
            method = "Every union of the smallest neighbourhoods is formed, and the " +
                "duplicates are removed. That family is the topology.",
            steps = listOf(
                "The space has ${space.points.size} points.",
                "There are ${openSets.size} open sets, and they are listed above.",
            ),
            interpretation = "A space is connected when it cannot be split into two " +
                "non-empty open parts. A finite space with a single point is always " +
                "connected.",
            verification = "The open sets were formed by union and the list was " +
                "checked to contain the empty set and the whole space.",
            limitations = "This is a genuine finite topology, but the examples are " +
                "small. Nothing here says anything about an infinite space.",
        ),
    )
}

/**
 * A region of the plane.
 */
@Composable
private fun PlaneSection() {
    var kindName by rememberSaveable { mutableStateOf(PlaneRegion.Kind.DISC.name) }
    var centre by rememberSaveable { mutableStateOf("0, 0") }
    var radius by rememberSaveable { mutableStateOf("2") }
    val kind = PlaneRegion.Kind.valueOf(kindName)
    val centreParts = centre.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
    val region = PlaneRegion(
        kind = kind,
        centreX = centreParts.getOrElse(0) { 0.0 },
        centreY = centreParts.getOrElse(1) { 0.0 },
        radius = radius.trim().toDoubleOrNull() ?: 1.0,
    )

    SectionCard(title = "The region") {
        SegmentedChoice(
            options = PlaneRegion.Kind.entries.map { it to it.label },
            selected = kind,
            onSelect = { kindName = it.name },
            label = "The shape",
        )
        PlainField(
            label = "The centre",
            value = centre,
            onValueChange = { centre = it },
            monospace = true,
            placeholder = "0, 0",
        )
        if (kind == PlaneRegion.Kind.DISC) {
            PlainField(
                label = "The radius",
                value = radius,
                onValueChange = { radius = it },
                monospace = true,
                placeholder = "2",
            )
        }
    }

    ResultCard(
        label = "The region",
        value = region.render(),
        caption = "in ℝ², with the usual topology",
    )
    ResultRow(label = "It is open", value = if (region.isOpen()) "yes" else "no")
    ResultRow(label = "It is closed", value = if (region.isClosed()) "yes" else "no")
    ResultRow(
        label = "It is connected",
        value = if (region.isConnected()) "yes" else "no",
    )
    ResultRow(label = "Its area", value = formatReal(region.area))
    ResultRow(label = "Its interior", value = region.interior().render())
    ResultRow(label = "Its closure", value = region.closure().render())

    SectionCard(title = "In the plane") {
        RegionPicture(region = region, modifier = Modifier.height(240.dp))
        Text(
            "The filled disc is the region and the ring around it is its boundary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    ExplanationCards(
        Explanation(
            definition = "In the plane with the usual topology, an open set contains a " +
                "small disc around each of its points. A closed set contains all its " +
                "limit points.",
            formula = "U is open  ⇔  every point of U has a disc inside U",
            conditions = listOf(
                "The properties are relative to the usual topology on the plane.",
            ),
            method = "The region is stored as its centre, its radius and its shape, " +
                "and the properties follow from that description.",
            steps = listOf(
                "The region is ${region.render()}.",
                "Its area is ${formatReal(region.area)}.",
            ),
            interpretation = "The whole plane is both open and closed; a disc is open " +
                "but not closed, because its edge is a limit point of it.",
            verification = "The boundary is drawn around the region, and it is the " +
                "part of the closure that is not in the interior.",
            limitations = "Only a disc, a square, a half plane and the whole plane are " +
                "handled. An arbitrary region would need its own description.",
        ),
    )
}

/**
 * The homeomorphism examples.
 */
@Composable
private fun ShapesSection() {
    val examples = remember { Homeomorphism.examples() }
    for (example in examples) {
        SectionCard(title = example.name) {
            ResultRow(label = "From", value = example.from)
            ResultRow(label = "To", value = example.to)
            ResultRow(label = "The map", value = example.formula)
            Text(
                example.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    ExplanationCards(
        Explanation(
            definition = "A homeomorphism is a continuous map with a continuous " +
                "inverse. Two spaces are homeomorphic when such a map joins them, and " +
                "then they are the same space up to shape.",
            formula = "f is a homeomorphism  ⇔  f and f⁻¹ are both continuous",
            conditions = listOf(
                "A homeomorphism preserves the topological properties: connectedness, " +
                    "compactness, the number of holes.",
                "It does not preserve the metric properties: distances and angles.",
            ),
            method = "The examples are given with their maps and their inverses, and " +
                "the property that they preserve is named.",
            steps = listOf(
                "The real line and the open interval are joined by a map that can be " +
                    "undone continuously.",
                "A square and a disc are joined by stretching the square out.",
            ),
            interpretation = "A circle and a square look nothing alike, but you can " +
                "stretch one into the other without tearing it, so they are the same " +
                "space as far as topology is concerned.",
            verification = null,
            limitations = "The maps are given, not searched for. Deciding whether two " +
                "arbitrary spaces are homeomorphic is a deep question that this tool " +
                "does not attempt.",
        ),
    )
}

/**
 * A set of real numbers drawn on a line.
 */
@Composable
private fun NumberLinePicture(set: RealSet, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val from = -1.0
            val to = 6.0
            val axisY = size.height * 0.6f
            fun toPx(x: Double) = ((x - from) / (to - from) * size.width).toFloat()

            drawLine(
                Color(0xFF9E9E9E),
                Offset(0f, axisY),
                Offset(size.width, axisY),
                strokeWidth = 2f,
            )
            for (tick in 0..6) {
                val x = toPx(tick.toDouble())
                drawLine(
                    Color(0xFF9E9E9E),
                    Offset(x, axisY - 6f),
                    Offset(x, axisY + 6f),
                    strokeWidth = 1.4f,
                )
            }

            // The set.
            for (interval in set.intervals) {
                val left = toPx(interval.from).coerceIn(0f, size.width)
                val right = toPx(interval.to).coerceIn(0f, size.width)
                drawLine(
                    Color(0xFF7BE3B0),
                    Offset(left, axisY),
                    Offset(right, axisY),
                    strokeWidth = 8f,
                )
            }
            // The boundary points.
            for (point in set.boundary().intervals) {
                val x = toPx(point.from).coerceIn(0f, size.width)
                drawCircle(Color(0xFF101410), radius = 7f, center = Offset(x, axisY))
                drawCircle(Color(0xFFFFC46B), radius = 5f, center = Offset(x, axisY))
            }
        }
    }
}

/**
 * A region drawn in the plane.
 */
@Composable
private fun RegionPicture(region: PlaneRegion, modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val scale = size.minDimension / 8f
            when (region.kind) {
                PlaneRegion.Kind.DISC -> {
                    val radius = (region.radius * scale).toFloat()
                    drawCircle(
                        Color(0xFF7BE3B0),
                        radius = radius,
                        center = centre,
                    )
                    drawCircle(
                        Color(0xFFFFC46B),
                        radius = radius,
                        center = centre,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                    )
                }
                PlaneRegion.Kind.RECTANGLE -> {
                    val side = (region.radius * scale * 2).toFloat()
                    drawRect(
                        Color(0xFF7BE3B0),
                        topLeft = Offset(centre.x - side / 2, centre.y - side / 2),
                        size = androidx.compose.ui.geometry.Size(side, side),
                    )
                    drawRect(
                        Color(0xFFFFC46B),
                        topLeft = Offset(centre.x - side / 2, centre.y - side / 2),
                        size = androidx.compose.ui.geometry.Size(side, side),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                    )
                }
                else -> {
                    drawRect(
                        Color(0x337BE3B0),
                        topLeft = Offset(0f, 0f),
                        size = size,
                    )
                }
            }
            // The axes.
            drawLine(
                Color(0x669E9E9E),
                Offset(centre.x, 0f),
                Offset(centre.x, size.height),
                strokeWidth = 1.2f,
            )
            drawLine(
                Color(0x669E9E9E),
                Offset(0f, centre.y),
                Offset(size.width, centre.y),
                strokeWidth = 1.2f,
            )
        }
    }
}

/**
 * Reads a set of real numbers from text.
 */
private fun parseSet(text: String): RealSet? {
    val intervals = mutableListOf<Interval>()
    for (part in text.split(";")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val interval = parseInterval(trimmed) ?: return null
        intervals.add(interval)
    }
    if (intervals.isEmpty()) return null
    return RealSet.fromIntervals(intervals)
}

/**
 * Reads a finite space from text.
 */
private fun parseFiniteSpace(text: String): FiniteSpace? {
    val points = mutableListOf<String>()
    val groups = mutableListOf<List<String>>()
    for (part in text.split(";")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        val pieces = trimmed.split(":")
        if (pieces.size != 2) return null
        val name = pieces[0].trim()
        if (name.isEmpty()) return null
        points.add(name)
        groups.add(pieces[1].split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }
    if (points.isEmpty()) return null
    val indices = mutableListOf<List<Int>>()
    for (group in groups) {
        val row = mutableListOf<Int>()
        for (name in group) {
            val index = points.indexOf(name)
            if (index < 0) return null
            row.add(index)
        }
        indices.add(row)
    }
    return FiniteSpace(points, indices)
}

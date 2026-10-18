package com.calcsuite.ui.tools.geometry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.geometry.CircleMode
import com.calcsuite.engine.geometry.PolygonMode
import com.calcsuite.engine.geometry.RhombusMode
import com.calcsuite.engine.geometry.ShapeKind
import com.calcsuite.engine.geometry.TriangleMode

/**
 * Labelled outline of the selected plane shape. Dimension lines carry the raw
 * text of the fields the shape needs, so the drawing reacts as the user types.
 */
@Composable
internal fun ShapeDiagram(
    kind: ShapeKind,
    triangleMode: TriangleMode,
    circleMode: CircleMode,
    rhombusMode: RhombusMode,
    polygonMode: PolygonMode,
    values: Map<String, String>,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val palette = DiagramPalette(
        fill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        outline = MaterialTheme.colorScheme.primary,
        accent = MaterialTheme.colorScheme.tertiary,
        text = MaterialTheme.colorScheme.onSurface,
        guide = MaterialTheme.colorScheme.outline,
    )
    Canvas(modifier.fillMaxWidth().height(196.dp)) {
        val ctx = DiagramCtx(palette, measurer, unit, values)
        when (kind) {
            ShapeKind.SQUARE -> drawSquare(ctx)
            ShapeKind.RECTANGLE -> drawRectangle(ctx)
            ShapeKind.TRIANGLE -> when (triangleMode) {
                TriangleMode.BASE_HEIGHT -> drawTriangleBaseHeight(ctx)
                TriangleMode.THREE_SIDES -> drawTriangleSides(ctx)
                TriangleMode.TWO_SIDES_INCLUDED_ANGLE -> drawTriangleAngle(ctx)
            }
            ShapeKind.EQUILATERAL_TRIANGLE -> drawEquilateral(ctx)
            ShapeKind.PARALLELOGRAM -> drawParallelogram(ctx)
            ShapeKind.RHOMBUS -> when (rhombusMode) {
                RhombusMode.DIAGONALS -> drawRhombusDiagonals(ctx)
                RhombusMode.SIDE_ANGLE -> drawRhombusSideAngle(ctx)
            }
            ShapeKind.TRAPEZOID -> drawTrapezoid(ctx)
            ShapeKind.CIRCLE -> drawCircleShape(ctx, circleMode)
            ShapeKind.SEMICIRCLE -> drawSemicircle(ctx)
            ShapeKind.ELLIPSE -> drawEllipse(ctx)
            ShapeKind.REGULAR_POLYGON -> drawRegularPolygon(ctx, polygonMode)
        }
    }
}

private fun DrawScope.shapePath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, point ->
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun DrawScope.fillAndStroke(ctx: DiagramCtx, path: Path) {
    drawPath(path, color = ctx.palette.fill)
    drawPath(path, color = ctx.palette.outline, style = Stroke(width = dpScaled(2f)))
}

// --------------------------------------------------------------- 2D shapes

private fun DrawScope.drawSquare(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val side = minOf(f.width, f.height) * 0.78f
    val left = f.cx - side / 2f
    val top = f.cy - side / 2f
    drawRect(ctx.palette.fill, Offset(left, top), Size(side, side))
    drawRect(ctx.palette.outline, Offset(left, top), Size(side, side), style = Stroke(width = dpScaled(2f)))
    dimension(ctx, Offset(left, top + side), Offset(left + side, top + side), ctx.text("s", "side"), LabelSide.BELOW)
}

private fun DrawScope.drawRectangle(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val ratio = ctx.ratio("width", "height", 1.6f)
    var width = f.width * 0.84f
    var height = width / ratio
    if (height > f.height * 0.78f) {
        height = f.height * 0.78f
        width = height * ratio
    }
    val left = f.cx - width / 2f
    val top = f.cy - height / 2f
    drawRect(ctx.palette.fill, Offset(left, top), Size(width, height))
    drawRect(ctx.palette.outline, Offset(left, top), Size(width, height), style = Stroke(width = dpScaled(2f)))
    dimension(ctx, Offset(left, top + height), Offset(left + width, top + height), ctx.text("w", "width"), LabelSide.BELOW)
    dimension(ctx, Offset(left + width, top), Offset(left + width, top + height), ctx.text("h", "height"), LabelSide.RIGHT)
}

private fun DrawScope.drawTriangleBaseHeight(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val apex = Offset(f.cx, f.top + 6f)
    val left = Offset(f.left + 10f, f.bottom - 18f)
    val right = Offset(f.right - 10f, f.bottom - 18f)
    fillAndStroke(ctx, shapePath(listOf(apex, left, right)))
    val foot = Offset(f.cx, left.y)
    guideLine(apex, foot, ctx.palette.guide)
    rightAngleMark(foot, apex, right, ctx.palette.guide)
    label(ctx, ctx.text("h", "height"), Offset(f.cx, (apex.y + foot.y) / 2f), LabelSide.RIGHT)
    dimension(ctx, left, right, ctx.text("b", "base"), LabelSide.BELOW)
}

private fun DrawScope.drawTriangleSides(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val apex = Offset(f.cx, f.top + 6f)
    val left = Offset(f.left + 12f, f.bottom - 18f)
    val right = Offset(f.right - 12f, f.bottom - 18f)
    fillAndStroke(ctx, shapePath(listOf(apex, left, right)))
    dimension(ctx, left, right, ctx.text("a", "sideA"), LabelSide.BELOW)
    dimension(ctx, right, apex, ctx.text("b", "sideB"), LabelSide.RIGHT)
    dimension(ctx, apex, left, ctx.text("c", "sideC"), LabelSide.LEFT)
}

private fun DrawScope.drawTriangleAngle(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val vertex = Offset(f.left + 24f, f.bottom - 20f)
    val up = Offset(f.left + 42f, f.top + 14f)
    val across = Offset(f.right - 14f, f.bottom - 44f)
    fillAndStroke(ctx, shapePath(listOf(vertex, up, across)))
    dimension(ctx, vertex, up, ctx.text("a", "sideA"), LabelSide.LEFT)
    dimension(ctx, vertex, across, ctx.text("b", "sideB"), LabelSide.BELOW)
    val arcRadius = f.width * 0.08f
    angleArc(vertex, up, across, arcRadius, ctx.palette.accent)
    val bisector = ((up - vertex) / (up - vertex).getDistance() + (across - vertex) / (across - vertex).getDistance())
    centeredLabel(
        ctx,
        ctx.text("C", "angle"),
        vertex + bisector / bisector.getDistance() * (arcRadius + dpScaled(16f)),
        ctx.palette.accent,
    )
}

private fun DrawScope.drawEquilateral(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val side = minOf(f.width * 0.82f, f.height * 1.12f)
    val height = side * (kotlin.math.sqrt(3.0) / 2.0).toFloat()
    val left = Offset(f.cx - side / 2f, f.cy + height / 2f)
    val right = Offset(f.cx + side / 2f, left.y)
    val apex = Offset(f.cx, left.y - height)
    fillAndStroke(ctx, shapePath(listOf(apex, left, right)))
    dimension(ctx, left, right, ctx.text("s", "side"), LabelSide.BELOW)
}

private fun DrawScope.drawParallelogram(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val topY = f.top + 10f
    val baseY = f.bottom - 16f
    val skew = f.width * 0.15f
    val left = f.left + 10f
    val width = f.width - skew - 20f
    val bottomLeft = Offset(left, baseY)
    val bottomRight = Offset(left + width, baseY)
    val topLeft = Offset(left + skew, topY)
    val topRight = Offset(left + skew + width, topY)
    fillAndStroke(ctx, shapePath(listOf(topLeft, topRight, bottomRight, bottomLeft)))
    val foot = Offset(topLeft.x, baseY)
    guideLine(topLeft, foot, ctx.palette.guide)
    rightAngleMark(foot, topLeft, bottomRight, ctx.palette.guide)
    label(ctx, ctx.text("h", "height"), Offset(foot.x, (topY + baseY) / 2f), LabelSide.RIGHT)
    dimension(ctx, bottomLeft, bottomRight, ctx.text("b", "base"), LabelSide.BELOW)
    label(ctx, ctx.text("a", "side"), (bottomLeft + topLeft) / 2f, LabelSide.LEFT)
}

private fun DrawScope.drawRhombusDiagonals(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val ratio = ctx.ratio("diagP", "diagQ", 1.25f, 0.45f, 2.2f)
    var halfWidth = f.width * 0.40f
    var halfHeight = halfWidth / ratio
    if (halfHeight > f.height * 0.42f) {
        halfHeight = f.height * 0.42f
        halfWidth = halfHeight * ratio
    }
    val center = Offset(f.cx, f.cy)
    val top = Offset(f.cx, f.cy - halfHeight)
    val bottom = Offset(f.cx, f.cy + halfHeight)
    val left = Offset(f.cx - halfWidth, f.cy)
    val right = Offset(f.cx + halfWidth, f.cy)
    fillAndStroke(ctx, shapePath(listOf(top, right, bottom, left)))
    guideLine(left, right, ctx.palette.guide, floatArrayOf(6f, 6f))
    guideLine(top, bottom, ctx.palette.guide, floatArrayOf(6f, 6f))
    label(ctx, ctx.text("p", "diagP"), (center + right) / 2f, LabelSide.BELOW, ctx.palette.accent)
    label(ctx, ctx.text("q", "diagQ"), (top + center) / 2f, LabelSide.RIGHT, ctx.palette.accent)
}

private fun DrawScope.drawRhombusSideAngle(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    var halfWidth = f.width * 0.34f
    var halfHeight = f.height * 0.40f
    if (halfWidth > halfHeight * 1.2f) halfWidth = halfHeight * 1.2f
    val center = Offset(f.cx, f.cy)
    val top = Offset(f.cx, f.cy - halfHeight)
    val bottom = Offset(f.cx, f.cy + halfHeight)
    val left = Offset(f.cx - halfWidth, f.cy)
    val right = Offset(f.cx + halfWidth, f.cy)
    fillAndStroke(ctx, shapePath(listOf(top, right, bottom, left)))
    label(ctx, ctx.text("a", "side"), (top + right) / 2f, LabelSide.RIGHT)
    angleArc(right, top, bottom, f.width * 0.08f, ctx.palette.accent)
    label(ctx, ctx.text("θ", "angle"), right + Offset(-f.width * 0.09f, 0f), LabelSide.LEFT, ctx.palette.accent)
}

private fun DrawScope.drawTrapezoid(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val topY = f.top + 10f
    val baseY = f.bottom - 16f
    val inset = f.width * 0.14f
    val bottomLeft = Offset(f.left + 8f, baseY)
    val bottomRight = Offset(f.right - 8f, baseY)
    val topLeft = Offset(bottomLeft.x + inset, topY)
    val topRight = Offset(bottomRight.x - inset, topY)
    fillAndStroke(ctx, shapePath(listOf(topLeft, topRight, bottomRight, bottomLeft)))
    val foot = Offset(topLeft.x, baseY)
    guideLine(topLeft, foot, ctx.palette.guide)
    rightAngleMark(foot, topLeft, bottomRight, ctx.palette.guide)
    label(ctx, ctx.text("h", "height"), Offset(topLeft.x, (topY + baseY) / 2f), LabelSide.RIGHT)
    dimension(ctx, bottomLeft, bottomRight, ctx.text("b", "baseB"), LabelSide.BELOW)
    dimension(ctx, topLeft, topRight, ctx.text("a", "baseA"), LabelSide.ABOVE)
    if (ctx.raw("leg1") != null) {
        label(ctx, ctx.text("leg₁", "leg1"), (bottomLeft + topLeft) / 2f, LabelSide.LEFT)
    }
    if (ctx.raw("leg2") != null) {
        label(ctx, ctx.text("leg₂", "leg2"), (bottomRight + topRight) / 2f, LabelSide.RIGHT)
    }
}

private fun DrawScope.drawCircleShape(ctx: DiagramCtx, mode: CircleMode) {
    val f = DiagramFrame.of(size)
    val radius = minOf(f.width, f.height) * 0.42f
    val center = Offset(f.cx, f.cy)
    drawCircle(ctx.palette.fill, radius, center)
    drawCircle(ctx.palette.outline, radius, center, style = Stroke(width = dpScaled(2f)))
    when (mode) {
        CircleMode.RADIUS -> dimension(
            ctx, center, center + Offset(radius, 0f), ctx.text("r", "radius"), LabelSide.BELOW,
            ctx.palette.accent, ctx.palette.accent,
        )
        CircleMode.DIAMETER -> dimension(
            ctx,
            center - Offset(radius, 0f),
            center + Offset(radius, 0f),
            ctx.text("d", "diameter"),
            LabelSide.BELOW,
            ctx.palette.accent,
            ctx.palette.accent,
        )
        CircleMode.CIRCUMFERENCE -> label(
            ctx, ctx.text("C", "circumference"), Offset(center.x, center.y - radius), LabelSide.ABOVE,
            ctx.palette.accent,
        )
    }
}

private fun DrawScope.drawSemicircle(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val radius = minOf(f.width * 0.45f, f.height * 0.80f)
    val baseY = f.bottom - 18f
    val path = Path().apply {
        moveTo(f.cx - radius, baseY)
        arcTo(
            rect = Rect(f.cx - radius, baseY - radius, f.cx + radius, baseY + radius),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        close()
    }
    fillAndStroke(ctx, path)
    dimension(ctx, Offset(f.cx, baseY), Offset(f.cx + radius, baseY), ctx.text("r", "radius"), LabelSide.BELOW)
}

private fun DrawScope.drawEllipse(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val ratio = ctx.ratio("semiMajor", "semiMinor", 1.6f, 0.35f, 3f)
    var a = f.width * 0.42f
    var b = a / ratio
    if (b > f.height * 0.40f) {
        b = f.height * 0.40f
        a = b * ratio
    }
    val topLeft = Offset(f.cx - a, f.cy - b)
    drawOval(ctx.palette.fill, topLeft, Size(a * 2f, b * 2f))
    drawOval(ctx.palette.outline, topLeft, Size(a * 2f, b * 2f), style = Stroke(width = dpScaled(2f)))
    dimension(ctx, Offset(f.cx, f.cy), Offset(f.cx + a, f.cy), ctx.text("a", "semiMajor"), LabelSide.BELOW)
    dimension(ctx, Offset(f.cx, f.cy), Offset(f.cx, f.cy - b), ctx.text("b", "semiMinor"), LabelSide.RIGHT)
}

private fun DrawScope.drawRegularPolygon(ctx: DiagramCtx, mode: PolygonMode) {
    val f = DiagramFrame.of(size)
    val sides = ctx.int("sides", 6)
    val radius = minOf(f.width, f.height) * 0.40f
    val center = Offset(f.cx, f.cy)
    val vertices = polygonVertices(center, radius, sides)
    fillAndStroke(ctx, shapePath(vertices))
    when (mode) {
        PolygonMode.SIDE -> {
            val bottomEdge = vertices.zipWithNext { a, b -> a to b }
                .maxByOrNull { (a, b) -> (a.y + b.y) / 2f }
            if (bottomEdge != null) {
                dimension(ctx, bottomEdge.first, bottomEdge.second, ctx.text("s", "side"), LabelSide.BELOW)
            }
        }
        PolygonMode.CIRCUMRADIUS -> {
            val top = vertices.first()
            dimension(ctx, center, top, ctx.text("R", "circumradius"), LabelSide.RIGHT, ctx.palette.accent, ctx.palette.accent)
        }
    }
    centeredLabel(ctx, ctx.text("n", "sides"), center)
}

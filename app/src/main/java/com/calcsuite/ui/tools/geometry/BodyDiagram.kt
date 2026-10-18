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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.geometry.BodyKind
import com.calcsuite.engine.geometry.TriangularPyramidMode

/**
 * Labelled pseudo-3D sketch of the selected body. Dimension labels mirror the
 * raw field text so the drawing follows along as the user types.
 */
@Composable
internal fun BodyDiagram(
    kind: BodyKind,
    pyramidMode: TriangularPyramidMode,
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
    Canvas(modifier.fillMaxWidth().height(204.dp)) {
        val ctx = DiagramCtx(palette, measurer, unit, values)
        when (kind) {
            BodyKind.CUBE -> drawBox(ctx, depthFraction = 0.50f, labelA = "a", idA = "a")
            BodyKind.RECTANGULAR_PRISM ->
                drawBox(ctx, depthFraction = 0.42f, labelA = "a", idA = "a", labelB = "b", idB = "b", labelC = "c", idC = "c")
            BodyKind.SPHERE -> drawSphere(ctx)
            BodyKind.HEMISPHERE -> drawHemisphere(ctx)
            BodyKind.CYLINDER -> drawCylinder(ctx)
            BodyKind.CONE -> drawCone(ctx)
            BodyKind.SQUARE_PYRAMID -> drawSquarePyramid(ctx)
            BodyKind.TRIANGULAR_PRISM -> drawTriangularPrism(ctx)
            BodyKind.TRIANGULAR_PYRAMID -> drawTriangularPyramid(ctx, pyramidMode)
        }
    }
}

private fun DrawScope.boxPath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, point ->
        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun DrawScope.face(ctx: DiagramCtx, points: List<Offset>) {
    val path = boxPath(points)
    drawPath(path, color = ctx.palette.fill)
    drawPath(path, color = ctx.palette.outline, style = Stroke(width = dpScaled(1.8f)))
}

// -------------------------------------------------------------------- box

private fun DrawScope.drawBox(
    ctx: DiagramCtx,
    depthFraction: Float,
    labelA: String,
    idA: String,
    labelB: String? = null,
    idB: String? = null,
    labelC: String? = null,
    idC: String? = null,
) {
    val f = DiagramFrame.of(size)
    val frontHeight = minOf(f.height * 0.62f, f.width * 0.55f)
    val frontWidth = if (labelB != null && idB != null) {
        frontHeight * ctx.ratio(idA, idB, 1.4f, 0.5f, 2.2f).coerceIn(0.8f, 2.0f)
    } else {
        frontHeight
    }
    val depth = minOf(frontWidth, frontHeight) * depthFraction
    val left = f.cx - frontWidth / 2f - depth * 0.2f
    val top = f.cy - frontHeight / 2f + depth * 0.28f
    val front = Rect(left, top, left + frontWidth, top + frontHeight)
    val back = front.translate(Offset(depth, -depth))
    face(
        ctx,
        listOf(
            Offset(front.left, front.top),
            Offset(front.right, front.top),
            Offset(back.right, back.top),
            Offset(back.left, back.top),
        ),
    )
    face(
        ctx,
        listOf(
            Offset(front.right, front.top),
            Offset(back.right, back.top),
            Offset(back.right, back.bottom),
            Offset(front.right, front.bottom),
        ),
    )
    face(
        ctx,
        listOf(
            Offset(front.left, front.top),
            Offset(front.right, front.top),
            Offset(front.right, front.bottom),
            Offset(front.left, front.bottom),
        ),
    )
    dimension(ctx, Offset(front.left, front.bottom), Offset(front.right, front.bottom), ctx.text(labelA, idA), LabelSide.BELOW)
    if (labelB != null && idB != null) {
        dimension(
            ctx,
            Offset(front.left, front.top),
            Offset(front.left, front.bottom),
            ctx.text(labelB, idB),
            LabelSide.LEFT,
        )
    }
    if (labelC != null && idC != null) {
        dimension(
            ctx,
            Offset(front.right, front.top),
            Offset(back.right, back.top),
            ctx.text(labelC, idC),
            LabelSide.ABOVE,
            ctx.palette.accent,
            ctx.palette.accent,
        )
    }
}

// ----------------------------------------------------------------- sphere

private fun DrawScope.drawSphere(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val radius = minOf(f.width, f.height) * 0.40f
    val center = Offset(f.cx, f.cy)
    drawCircle(ctx.palette.fill, radius, center)
    drawCircle(ctx.palette.outline, radius, center, style = Stroke(width = dpScaled(2f)))
    val ry = radius * 0.28f
    drawOval(
        color = ctx.palette.guide,
        topLeft = Offset(center.x - radius, center.y - ry),
        size = Size(radius * 2f, ry * 2f),
        style = Stroke(width = dpScaled(1.3f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))),
    )
    dimension(
        ctx, center, Offset(center.x + radius, center.y), ctx.text("r", "radius"), LabelSide.BELOW,
        ctx.palette.accent, ctx.palette.accent,
    )
}

// ------------------------------------------------------------- hemisphere

private fun DrawScope.drawHemisphere(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val radius = minOf(f.width * 0.42f, f.height * 0.74f)
    val baseY = f.bottom - 28f
    val center = Offset(f.cx, baseY)
    val dome = Path().apply {
        moveTo(center.x - radius, baseY)
        arcTo(
            rect = Rect(center.x - radius, baseY - radius, center.x + radius, baseY + radius),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        close()
    }
    drawPath(dome, color = ctx.palette.fill)
    drawPath(dome, color = ctx.palette.outline, style = Stroke(width = dpScaled(2f)))
    val ry = radius * 0.20f
    drawOval(
        color = ctx.palette.guide,
        topLeft = Offset(center.x - radius, baseY - ry),
        size = Size(radius * 2f, ry * 2f),
        style = Stroke(width = dpScaled(1.4f)),
    )
    dimension(
        ctx, center, Offset(center.x + radius, baseY), ctx.text("r", "radius"), LabelSide.BELOW,
        ctx.palette.accent, ctx.palette.accent,
    )
}

// --------------------------------------------------------------- cylinder

private fun DrawScope.drawCylinder(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val topY = f.top + 26f
    val bottomY = f.bottom - 24f
    val radius = minOf(f.width * 0.30f, (bottomY - topY) * 0.72f)
    val ry = radius * 0.28f
    val centerX = f.cx
    drawRect(
        color = ctx.palette.fill,
        topLeft = Offset(centerX - radius, topY),
        size = Size(radius * 2f, bottomY - topY),
    )
    drawLine(ctx.palette.outline, Offset(centerX - radius, topY), Offset(centerX - radius, bottomY), strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, Offset(centerX + radius, topY), Offset(centerX + radius, bottomY), strokeWidth = dpScaled(2f))
    drawOval(
        color = ctx.palette.fill,
        topLeft = Offset(centerX - radius, bottomY - ry),
        size = Size(radius * 2f, ry * 2f),
    )
    drawArc(
        color = ctx.palette.outline,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - radius, bottomY - ry),
        size = Size(radius * 2f, ry * 2f),
        style = Stroke(width = dpScaled(2f)),
    )
    drawArc(
        color = ctx.palette.guide,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centerX - radius, bottomY - ry),
        size = Size(radius * 2f, ry * 2f),
        style = Stroke(width = dpScaled(1.3f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))),
    )
    drawOval(
        color = ctx.palette.fill,
        topLeft = Offset(centerX - radius, topY - ry),
        size = Size(radius * 2f, ry * 2f),
    )
    drawOval(
        color = ctx.palette.outline,
        topLeft = Offset(centerX - radius, topY - ry),
        size = Size(radius * 2f, ry * 2f),
        style = Stroke(width = dpScaled(2f)),
    )
    dimension(
        ctx,
        Offset(centerX, topY),
        Offset(centerX + radius, topY),
        ctx.text("r", "radius"),
        LabelSide.ABOVE,
        ctx.palette.accent,
        ctx.palette.accent,
    )
    val leftRail = centerX - radius - dpScaled(18f)
    guideLine(Offset(centerX - radius, topY), Offset(leftRail, topY), ctx.palette.guide, floatArrayOf(5f, 5f))
    guideLine(Offset(centerX - radius, bottomY), Offset(leftRail, bottomY), ctx.palette.guide, floatArrayOf(5f, 5f))
    dimension(ctx, Offset(leftRail, topY), Offset(leftRail, bottomY), ctx.text("h", "height"), LabelSide.LEFT)
}

// ------------------------------------------------------------------- cone

private fun DrawScope.drawCone(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val apex = Offset(f.cx, f.top + 22f)
    val baseY = f.bottom - 26f
    val radius = minOf(f.width * 0.32f, (baseY - apex.y) * 0.72f)
    val ry = radius * 0.28f
    val left = Offset(f.cx - radius, baseY)
    val right = Offset(f.cx + radius, baseY)
    val body = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(left.x, left.y)
        arcTo(
            rect = Rect(left.x, baseY - ry, right.x, baseY + ry),
            startAngleDegrees = 180f,
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
        close()
    }
    // Hidden back edge of the base: the upper half of the ellipse, dashed.
    drawArc(
        color = ctx.palette.guide,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(left.x, baseY - ry),
        size = Size(radius * 2f, ry * 2f),
        style = Stroke(width = dpScaled(1.4f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))),
    )
    drawPath(body, color = ctx.palette.fill)
    drawPath(body, color = ctx.palette.outline, style = Stroke(width = dpScaled(2f)))
    guideLine(apex, Offset(f.cx, baseY), ctx.palette.guide)
    dimension(
        ctx,
        Offset(f.cx, baseY),
        Offset(f.cx + radius, baseY),
        ctx.text("r", "radius"),
        LabelSide.BELOW,
        ctx.palette.accent,
        ctx.palette.accent,
    )
    label(ctx, ctx.text("h", "height"), Offset(f.cx, (apex.y + baseY) / 2f), LabelSide.RIGHT)
}

// --------------------------------------------------------- square pyramid

private fun DrawScope.drawSquarePyramid(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val baseWidth = f.width * 0.56f
    val depth = baseWidth * 0.34f
    val skew = baseWidth * 0.26f
    val frontY = f.bottom - 30f
    val backY = frontY - depth
    val frontLeft = Offset(f.cx - baseWidth / 2f - skew / 2f, frontY)
    val frontRight = Offset(frontLeft.x + baseWidth, frontY)
    val backLeft = Offset(frontLeft.x + skew, backY)
    val backRight = Offset(frontRight.x + skew, backY)
    val center = Offset((frontLeft.x + backRight.x) / 2f, (frontY + backY) / 2f)
    val apex = Offset(center.x, f.top + 16f)
    face(ctx, listOf(frontLeft, frontRight, backRight, backLeft))
    drawLine(ctx.palette.outline, apex, frontLeft, strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, apex, frontRight, strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, apex, backLeft, strokeWidth = dpScaled(1.4f))
    drawLine(ctx.palette.outline, apex, backRight, strokeWidth = dpScaled(1.4f))
    guideLine(apex, center, ctx.palette.guide)
    rightAngleMark(center, apex, frontRight, ctx.palette.guide)
    label(ctx, ctx.text("h", "height"), Offset(center.x, (apex.y + center.y) / 2f), LabelSide.RIGHT)
    dimension(ctx, frontLeft, frontRight, ctx.text("a", "baseEdge"), LabelSide.BELOW)
}

// ------------------------------------------------------- triangular prism

private fun DrawScope.drawTriangularPrism(ctx: DiagramCtx) {
    val f = DiagramFrame.of(size)
    val edge = f.width * 0.42f
    val triHeight = edge * 0.87f
    val baseY = f.bottom - 24f
    val left = Offset(f.left + 34f, baseY)
    val right = Offset(left.x + edge, baseY)
    val top = Offset((left.x + right.x) / 2f, baseY - triHeight)
    val shift = Offset(f.width * 0.16f, -f.height * 0.22f)
    face(ctx, listOf(left, right, top))
    val backLeft = left + shift
    val backRight = right + shift
    val backTop = top + shift
    drawPath(boxPath(listOf(backLeft, backRight, backTop)), color = ctx.palette.outline, style = Stroke(width = dpScaled(1.4f)))
    drawLine(ctx.palette.outline, left, backLeft, strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, right, backRight, strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, top, backTop, strokeWidth = dpScaled(1.4f))
    dimension(ctx, left, right, ctx.text("a", "baseEdge"), LabelSide.BELOW)
    dimension(ctx, right, backRight, ctx.text("h", "height"), LabelSide.RIGHT, ctx.palette.accent, ctx.palette.accent)
}

// ------------------------------------------------------ triangular pyramid

private fun DrawScope.drawTriangularPyramid(ctx: DiagramCtx, mode: TriangularPyramidMode) {
    val f = DiagramFrame.of(size)
    val baseWidth = f.width * 0.50f
    val depth = baseWidth * 0.34f
    val skew = baseWidth * 0.22f
    val frontY = f.bottom - 32f
    val frontLeft = Offset(f.cx - baseWidth / 2f - skew / 2f, frontY)
    val frontRight = Offset(frontLeft.x + baseWidth, frontY)
    val back = Offset((frontLeft.x + frontRight.x) / 2f + skew, frontY - depth)
    val center = Offset((frontLeft.x + frontRight.x + back.x) / 3f, (frontY + frontY + back.y) / 3f)
    val apex = Offset(center.x, f.top + 18f)
    face(ctx, listOf(frontLeft, frontRight, back))
    drawLine(ctx.palette.outline, apex, frontLeft, strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, apex, frontRight, strokeWidth = dpScaled(2f))
    drawLine(ctx.palette.outline, apex, back, strokeWidth = dpScaled(1.4f))
    guideLine(apex, center, ctx.palette.guide)
    when (mode) {
        TriangularPyramidMode.REGULAR_TETRAHEDRON -> {
            dimension(ctx, frontLeft, frontRight, ctx.text("a", "a"), LabelSide.BELOW)
        }
        TriangularPyramidMode.GENERAL -> {
            dimension(ctx, frontLeft, frontRight, ctx.text("a", "sideA"), LabelSide.BELOW)
            dimension(ctx, frontRight, back, ctx.text("b", "sideB"), LabelSide.RIGHT)
            dimension(ctx, back, frontLeft, ctx.text("c", "sideC"), LabelSide.LEFT)
            if (ctx.raw("height") != null) {
                label(ctx, ctx.text("h", "height"), Offset(center.x, (apex.y + center.y) / 2f), LabelSide.RIGHT)
            }
        }
    }
}

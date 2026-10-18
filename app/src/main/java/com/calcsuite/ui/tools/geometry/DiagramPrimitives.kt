package com.calcsuite.ui.tools.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.calcsuite.core.Num
import kotlin.math.PI
import kotlin.math.atan2

/** Where a dimension label sits relative to its dimension line. */
internal enum class LabelSide { ABOVE, BELOW, LEFT, RIGHT }

/** Drawing area of a diagram after reserving room for labels. */
internal class DiagramFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /**
         * Drawing area of the diagram. Margins default to a fraction of the
         * canvas so the frame keeps the same visual weight at every density.
         */
        fun of(
            size: Size,
            marginX: Float = size.width * 0.17f,
            marginY: Float = size.height * 0.14f,
        ): DiagramFrame = DiagramFrame(
            left = marginX,
            top = marginY,
            right = maxOf(size.width - marginX, marginX + 1f),
            bottom = maxOf(size.height - marginY, marginY + 1f),
        )
    }
}

/** A dp value converted to pixels inside a draw scope (stroke widths, gaps). */
internal fun DrawScope.dpScaled(value: Float): Float = value * density

/** Colors captured outside the Canvas draw phase. */
internal data class DiagramPalette(
    val fill: Color,
    val outline: Color,
    val accent: Color,
    val text: Color,
    val guide: Color,
)

/**
 * Everything a diagram function needs: palette, text measurer, the raw field
 * text and the user's length unit. Labels show the raw input so they update as
 * the user types; an empty field degrades to the bare symbol.
 */
internal class DiagramCtx(
    val palette: DiagramPalette,
    val measurer: TextMeasurer,
    val unit: String,
    private val values: Map<String, String>,
) {
    fun raw(id: String): String? = values[id]?.trim()?.takeIf { it.isNotEmpty() }

    /** "s = 3 cm" while typing, "s" when the field is empty. */
    fun text(symbol: String, id: String): String {
        val raw = raw(id) ?: return symbol
        return if (unit.isBlank()) "$symbol = $raw" else "$symbol = $raw $unit"
    }

    fun number(id: String): Float? = raw(id)?.let { text ->
        val value = Num.parseOrNull(text) ?: return null
        val asDouble = value.toDouble()
        if (!asDouble.isFinite() || asDouble <= 0.0) return null
        if (asDouble > 1e9) 1e9f else asDouble.toFloat()
    }

    fun int(id: String, fallback: Int): Int =
        raw(id)?.let { Num.parseOrNull(it)?.toInt() }?.takeIf { it >= 3 } ?: fallback

    /** Aspect ratio a : b of two fields, clamped so extreme inputs stay drawable. */
    fun ratio(idA: String, idB: String, fallback: Float, min: Float = 0.3f, max: Float = 3.2f): Float {
        val a = number(idA) ?: return fallback
        val b = number(idB) ?: return fallback
        if (b <= 0f) return fallback
        return (a / b).coerceIn(min, max)
    }
}

// -------------------------------------------------------------- primitives

/** Dimension line with end ticks and a centred label. */
internal fun DrawScope.dimension(
    ctx: DiagramCtx,
    from: Offset,
    to: Offset,
    caption: String,
    side: LabelSide,
    lineColor: Color = ctx.palette.outline,
    textColor: Color = ctx.palette.text,
) {
    drawLine(lineColor, from, to, strokeWidth = dpScaled(1.5f))
    val direction = to - from
    val length = direction.getDistance()
    if (length > 0.5f) {
        val normal = Offset(-direction.y / length, direction.x / length)
        val tick = dpScaled(5f)
        drawLine(lineColor, from - normal * tick, from + normal * tick, strokeWidth = dpScaled(1.5f))
        drawLine(lineColor, to - normal * tick, to + normal * tick, strokeWidth = dpScaled(1.5f))
    }
    label(ctx, caption, (from + to) / 2f, side, textColor)
}

/** Text placed next to an anchor point. */
internal fun DrawScope.label(
    ctx: DiagramCtx,
    text: String,
    anchor: Offset,
    side: LabelSide,
    textColor: Color = ctx.palette.text,
) {
    val textLayout = layout(ctx, text, textColor)
    val gap = dpScaled(5f)
    val topLeft = when (side) {
        LabelSide.ABOVE -> Offset(anchor.x - textLayout.size.width / 2f, anchor.y - textLayout.size.height - gap)
        LabelSide.BELOW -> Offset(anchor.x - textLayout.size.width / 2f, anchor.y + gap)
        LabelSide.LEFT -> Offset(anchor.x - textLayout.size.width - gap, anchor.y - textLayout.size.height / 2f)
        LabelSide.RIGHT -> Offset(anchor.x + gap, anchor.y - textLayout.size.height / 2f)
    }
    drawText(textLayout, topLeft = clampInside(textLayout, topLeft))
}

/** Text centred on an anchor point. */
internal fun DrawScope.centeredLabel(
    ctx: DiagramCtx,
    text: String,
    center: Offset,
    textColor: Color = ctx.palette.text,
) {
    val textLayout = layout(ctx, text, textColor)
    val topLeft = Offset(center.x - textLayout.size.width / 2f, center.y - textLayout.size.height / 2f)
    drawText(textLayout, topLeft = clampInside(textLayout, topLeft))
}

/** Keeps a label fully inside the canvas so nothing is clipped at the edges. */
private fun DrawScope.clampInside(textLayout: TextLayoutResult, topLeft: Offset): Offset {
    val maxX = (size.width - textLayout.size.width).coerceAtLeast(0f)
    val maxY = (size.height - textLayout.size.height).coerceAtLeast(0f)
    return Offset(topLeft.x.coerceIn(0f, maxX), topLeft.y.coerceIn(0f, maxY))
}

private fun layout(ctx: DiagramCtx, text: String, color: Color): TextLayoutResult =
    ctx.measurer.measure(text, style = TextStyle(fontSize = 11.sp, color = color))

/** Dashed construction line (heights, hidden edges, auxiliary radii). */
internal fun DrawScope.guideLine(
    from: Offset,
    to: Offset,
    color: Color,
    dash: FloatArray = floatArrayOf(9f, 8f),
) {
    drawLine(color, from, to, strokeWidth = dpScaled(1.3f), pathEffect = PathEffect.dashPathEffect(dash))
}

/** Small square marking a right angle at [at] between the rays to A and B. */
internal fun DrawScope.rightAngleMark(at: Offset, towardA: Offset, towardB: Offset, color: Color) {
    val size = dpScaled(9f)
    fun unit(p: Offset): Offset {
        val v = p - at
        val length = v.getDistance()
        return if (length < 0.001f) Offset.Zero else v / length
    }

    val first = unit(towardA)
    val second = unit(towardB)
    if (first == Offset.Zero || second == Offset.Zero) return
    val p1 = at + first * size
    val p2 = at + first * size + second * size
    val p3 = at + second * size
    drawLine(color, p1, p2, strokeWidth = dpScaled(1.3f))
    drawLine(color, p2, p3, strokeWidth = dpScaled(1.3f))
}

/** Circular arc between the rays vertex→[from] and vertex→[to]. */
internal fun DrawScope.angleArc(
    vertex: Offset,
    from: Offset,
    to: Offset,
    radius: Float,
    color: Color,
) {
    val start = atan2((from.y - vertex.y).toDouble(), (from.x - vertex.x).toDouble())
    val end = atan2((to.y - vertex.y).toDouble(), (to.x - vertex.x).toDouble())
    var sweep = end - start
    while (sweep > PI) sweep -= 2 * PI
    while (sweep < -PI) sweep += 2 * PI
    drawArc(
        color = color,
        startAngle = Math.toDegrees(start).toFloat(),
        sweepAngle = Math.toDegrees(sweep).toFloat(),
        useCenter = false,
        topLeft = Offset(vertex.x - radius, vertex.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = dpScaled(1.4f)),
    )
}

/** Regular polygon vertices, first vertex pointing up. */
internal fun polygonVertices(center: Offset, radius: Float, sides: Int): List<Offset> {
    val n = sides.coerceIn(3, 64)
    return (0 until n).map { index ->
        val angle = -PI / 2 + 2 * PI * index / n
        Offset(
            center.x + (radius * kotlin.math.cos(angle)).toFloat(),
            center.y + (radius * kotlin.math.sin(angle)).toFloat(),
        )
    }
}

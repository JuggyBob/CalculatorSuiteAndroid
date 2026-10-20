package com.calcsuite.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * The Calculus tool glyph: an integral sign followed by a small x.
 *
 * Both parts are *stroked* centrelines rather than filled outlines. A stroked
 * curve is defined by points a reader can check, whereas a filled outline has
 * to be traced by hand and silently collapses into a sliver when the points
 * are even slightly wrong.
 *
 * The stroke colour below is never seen — render sites apply the theme's accent
 * tint, which covers strokes as well as fills — but it is set to black so the
 * vector is legible if it is ever drawn untinted.
 */
val CalculusIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Calculus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The integral sign: two arcs, the top hook opening to the right and
        // the bottom hook to the left, which is what makes it read as ∫ rather
        // than as a J.
        addPath(
            pathData = PathData {
                moveTo(13.6f, 3.4f)
                curveTo(8.4f, 3.4f, 8.4f, 7.6f, 10.0f, 12.0f)
                curveTo(11.6f, 16.4f, 11.6f, 20.6f, 6.4f, 20.6f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        )
        // The x, set beside the lower half the way it is typeset after ∫. It is
        // stroked thinner than the integral so the crossing stays open instead
        // of filling into a blob at small sizes.
        addPath(
            pathData = PathData {
                moveTo(15.6f, 13.2f)
                lineTo(20.4f, 18.0f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
        )
        addPath(
            pathData = PathData {
                moveTo(20.4f, 13.2f)
                lineTo(15.6f, 18.0f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

/**
 * The Inequalities glyph: a number line with a shaded solution segment that
 * ends in an open circle.
 *
 * The open circle is the point of the picture — it is what separates an
 * inequality from an equation, and it is the same convention the tool's number
 * line uses.
 *
 * As with [CalculusIcon] the stroke colour is never seen, because render sites
 * apply the theme's accent tint over both strokes and fills.
 */
val InequalityIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Inequality",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The axis.
        addPath(
            pathData = PathData {
                moveTo(2f, 12.5f)
                lineTo(21.4f, 12.5f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
        )
        // The solution segment, drawn thick so it reads as shaded.
        addPath(
            pathData = PathData {
                moveTo(3.2f, 12.5f)
                lineTo(11f, 12.5f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3.4f,
            strokeLineCap = StrokeCap.Round,
        )
        // The open circle: the endpoint is excluded.
        addPath(
            pathData = PathData {
                moveTo(8.8f, 12.5f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, 4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, -4.4f, 0f)
                close()
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
        )
        // A tick on the axis, so the line reads as a number line.
        addPath(
            pathData = PathData {
                moveTo(17.2f, 10.4f)
                lineTo(17.2f, 14.6f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

/**
 * The Integrals glyph: a plain integral sign.
 *
 * It is drawn in the same way as the other hand-made glyphs here — a stroked
 * centreline rather than a filled outline — so that it sits with them in size,
 * weight and colour. The sign alone is enough to say "integral"; there is no
 * second character to compete with it.
 */
val IntegralIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Integral",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // The integral sign: the top hook opens to the right and the bottom
        // hook to the left, which is what makes it read as ∫ rather than as a J.
        addPath(
            pathData = PathData {
                moveTo(14.2f, 3.2f)
                curveTo(9.0f, 3.2f, 9.0f, 7.8f, 10.6f, 12.0f)
                curveTo(12.2f, 16.2f, 12.2f, 20.8f, 7.0f, 20.8f)
            },
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        )
    }.build()
}

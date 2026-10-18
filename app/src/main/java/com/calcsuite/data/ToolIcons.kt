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

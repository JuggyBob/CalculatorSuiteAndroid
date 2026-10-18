package com.calcsuite.ui.tools.geometry

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Geometry batch dispatcher: two tools, "2D Shapes" and "3D Bodies".
 * Returns false for ids that belong to another batch so the host can continue
 * its own dispatch chain.
 */
@Composable
fun GeometryTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "geometry.shapes" -> ShapesToolScreen(args)
        "geometry.bodies" -> BodiesToolScreen(args)
        else -> return false
    }
    return true
}

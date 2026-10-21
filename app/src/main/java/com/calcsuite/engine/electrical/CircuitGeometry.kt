package com.calcsuite.engine.electrical

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the parts of a circuit are drawn.
 *
 * The drawing uses **world units**: a component is a fixed size in those
 * units, and the picture is scaled by the zoom. The terminals are placed
 * from the component's own position and turn, so a component keeps its
 * connections when it is moved or turned.
 */
object CircuitGeometry {

    /** How wide a part is drawn. */
    const val COMPONENT_WIDTH = 90.0

    /** How tall a part is drawn. */
    const val COMPONENT_HEIGHT = 44.0

    /** How far the terminals are from the middle of the part. */
    const val TERMINAL_OFFSET = COMPONENT_WIDTH / 2

    /**
     * The terminal of a part, in world units.
     *
     * The first terminal is on the left and the second on the right, and a
     * part with one terminal has it in the middle.
     */
    fun terminalPosition(component: CircuitComponent, index: Int): Pair<Double, Double> {
        val local = when {
            component.terminalCount <= 1 -> 0.0 to 0.0
            index == 0 -> -TERMINAL_OFFSET to 0.0
            else -> TERMINAL_OFFSET to 0.0
        }
        val turn = component.rotation * (Math.PI / 2.0)
        val x = local.first * cos(turn) - local.second * sin(turn)
        val y = local.first * sin(turn) + local.second * cos(turn)
        return component.x + x to component.y + y
    }

    /** The point of a terminal, in world units. */
    fun terminalPoint(circuit: Circuit, terminal: TerminalRef): Pair<Double, Double>? {
        val component = circuit.component(terminal.componentId) ?: return null
        return terminalPosition(component, terminal.index)
    }

    /** The rectangle a part is drawn in. */
    fun componentBounds(component: CircuitComponent): DoubleArray {
        val halfWidth = COMPONENT_WIDTH / 2
        val halfHeight = COMPONENT_HEIGHT / 2
        return doubleArrayOf(
            component.x - halfWidth,
            component.y - halfHeight,
            component.x + halfWidth,
            component.y + halfHeight,
        )
    }

    /** True when a world point is inside a part. */
    fun hits(component: CircuitComponent, x: Double, y: Double, slack: Double = 8.0): Boolean {
        val bounds = componentBounds(component)
        return x >= bounds[0] - slack && x <= bounds[2] + slack &&
            y >= bounds[1] - slack && y <= bounds[3] + slack
    }

    /** The part under a world point, the last one added first. */
    fun componentAt(circuit: Circuit, x: Double, y: Double): CircuitComponent? =
        circuit.components.lastOrNull { hits(it, x, y) }

    /** The terminal under a world point, within the given distance. */
    fun terminalAt(
        circuit: Circuit,
        x: Double,
        y: Double,
        distance: Double,
    ): TerminalRef? {
        var found: TerminalRef? = null
        var best = distance
        for (component in circuit.components) {
            for (terminal in component.terminals) {
                val point = terminalPosition(component, terminal.index)
                val gap = abs(point.first - x) + abs(point.second - y)
                if (gap < best) {
                    best = gap
                    found = terminal
                }
            }
        }
        return found
    }

    /** The grid the parts snap to. */
    const val GRID = 10.0

    /** A point moved onto the grid. */
    fun snap(value: Double): Double = Math.round(value / GRID) * GRID
}

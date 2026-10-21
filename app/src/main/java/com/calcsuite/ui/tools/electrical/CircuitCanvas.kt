package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.calcsuite.engine.electrical.CircuitComponent
import com.calcsuite.engine.electrical.CircuitGeometry
import com.calcsuite.engine.electrical.CircuitTopology
import com.calcsuite.engine.electrical.ComponentKind
import com.calcsuite.engine.electrical.ComponentResult
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/**
 * How the world is shown on the screen.
 *
 * @param centreX the middle of the picture, in world units.
 * @param centreY the middle of the picture, in world units.
 * @param zoom how many world units there are in a pixel.
 * @param width the width of the picture, in pixels.
 * @param height the height of the picture, in pixels.
 */
data class CanvasTransform(
    val centreX: Double,
    val centreY: Double,
    val zoom: Double,
    val width: Float,
    val height: Float,
) {
    /** The screen point of a world point. */
    fun screen(x: Double, y: Double): Offset {
        val sx = width / 2 + (x - centreX) / zoom
        val sy = height / 2 - (y - centreY) / zoom
        return Offset(sx.toFloat(), sy.toFloat())
    }

    /** The world point of a screen point. */
    fun world(point: Offset): Pair<Double, Double> {
        val x = centreX + (point.x - width / 2) * zoom
        val y = centreY - (point.y - height / 2) * zoom
        return x to y
    }
}

/**
 * The circuit canvas.
 *
 * The picture is drawn in world units and scaled by the zoom, so a part is
 * always the same size on the paper and the slope of a wire means the same
 * thing everywhere. A drag on a part moves that part; a drag on the paper
 * moves the paper; a pinch makes the picture larger. A tap on a part selects
 * it, and a tap on a terminal starts a wire.
 *
 * Nothing is drawn outside the canvas: a part that has been dragged past
 * the edge is cut off at the edge, the way a picture on paper is.
 */
@Composable
fun CircuitCanvas(
    state: CircuitEditorState,
    onState: (CircuitEditorState) -> Unit,
    modifier: Modifier = Modifier,
    results: Map<String, ComponentResult> = emptyMap(),
    showValues: Boolean = false,
    onSelect: (String?) -> Unit = {},
    /** The part that is being dragged onto the canvas, if there is one. */
    dragKind: ComponentKind? = null,
    /** Where that part is being dragged to, in the whole screen's places. */
    dragPosition: Offset? = null,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()

    // The gestures read the state as it is at the moment of the touch, not
    // the state as it was when the gesture began. Without this a wire that
    // has been started would be forgotten by the time it was finished.
    val current by rememberUpdatedState(state)
    val send by rememberUpdatedState(onState)
    val select by rememberUpdatedState(onSelect)

    val lineColor = MaterialTheme.colorScheme.primary
    val wireColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val selectColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val valueColor = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val density = LocalDensity.current

    /**
     * The world transform, worked out from the state as it is now.
     *
     * The gesture handlers are not restarted every time the picture is
     * moved, so the state they hold is not the current one. Reading the
     * centre of the view from the captured state would put a touch in the
     * wrong place as soon as the picture had been panned.
     */
    fun transformFor(s: CircuitEditorState): CanvasTransform = CanvasTransform(
        centreX = s.viewCentreX,
        centreY = s.viewCentreY,
        zoom = s.zoom,
        width = canvasSize.width.toFloat().coerceAtLeast(1f),
        height = canvasSize.height.toFloat().coerceAtLeast(1f),
    )

    Box(
        modifier
            .clipToBounds()
            .background(surface)
            .semantics { contentDescription = "The circuit" }
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                // The gesture is not restarted when the circuit changes: a
                // drag changes the circuit on every step, and restarting the
                // gesture would throw the drag away halfway through. The
                // state is read as it is, through `current`.
                // One gesture watches the touch from the start to the end, so
                // that a tap and a drag cannot fight over it. It drags the
                // part under the finger, or the paper when the finger is on
                // the paper, or zooms when there are two fingers down. A
                // touch that never moved is a tap.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = transformFor(current).world(down.position)
                    // What was under the finger when it went down. A terminal
                    // is not a handle for dragging, so that a wire can still
                    // be started from one.
                    val onTerminal = CircuitGeometry.terminalAt(
                        current.circuit,
                        start.first,
                        start.second,
                        26.0 * current.zoom,
                    ) != null
                    val grabbed = if (onTerminal) {
                        null
                    } else {
                        CircuitGeometry.componentAt(current.circuit, start.first, start.second)
                    }

                    var moved = false
                    var pinched = false
                    var last = down.position
                    var lastSpread = 0f
                    // Where the part is being dragged to, in world units, so
                    // that it follows the finger exactly.
                    var dragTarget: Pair<Double, Double>? = null

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            // Two fingers: the picture is zoomed about the
                            // point between them.
                            pinched = true
                            moved = true
                            val first = pressed[0].position
                            val second = pressed[1].position
                            val middle = (first + second) / 2f
                            val spread = (first - second).getDistance()
                            val now = current
                            val factor = if (lastSpread > 0f) spread / lastSpread else 1f
                            val zoom = (now.zoom * factor).coerceIn(0.1, 4.0)
                            val world = transformFor(now).world(middle)
                            send(
                                now.copy(
                                    viewCentreX = world.first -
                                        (middle.x - size.width / 2f) * zoom,
                                    viewCentreY = world.second +
                                        (middle.y - size.height / 2f) * zoom,
                                    zoom = zoom,
                                ),
                            )
                            last = middle
                            lastSpread = spread
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        val change = pressed.first()
                        if (!change.positionChanged()) continue
                        val distance = (change.position - down.position).getDistance()
                        if (!moved && distance > viewConfiguration.touchSlop) {
                            moved = true
                            last = change.position
                        }
                        if (!moved) continue

                        val now = current
                        val pan = change.position - last
                        if (grabbed != null && !pinched) {
                            // The part follows the finger exactly: its place
                            // is kept as the drag goes on, and it is put on
                            // the grid when it is let go.
                            val part = now.circuit.component(grabbed.id)
                            if (part != null) {
                                val target = dragTarget ?: (part.x to part.y)
                                val next = (target.first + pan.x * now.zoom) to
                                    (target.second - pan.y * now.zoom)
                                dragTarget = next
                                send(
                                    CircuitEditor.moveComponent(
                                        now,
                                        part.id,
                                        next.first,
                                        next.second,
                                        record = false,
                                        snap = false,
                                    ),
                                )
                            }
                        } else if (!pinched) {
                            // The paper follows the finger.
                            send(
                                now.copy(
                                    viewCentreX = now.viewCentreX - pan.x * now.zoom,
                                    viewCentreY = now.viewCentreY + pan.y * now.zoom,
                                ),
                            )
                        }
                        last = change.position
                        change.consume()
                    }

                    // A part that was dragged is put on the grid, and the
                    // drag can be undone.
                    val target = dragTarget
                    dragTarget = null
                    if (target != null && grabbed != null) {
                        val now = current
                        val part = now.circuit.component(grabbed.id)
                        if (part != null) {
                            send(
                                CircuitEditor.moveComponent(
                                    now,
                                    part.id,
                                    part.x,
                                    part.y,
                                    record = true,
                                ),
                            )
                        }
                    }

                    if (!moved) {
                        // A tap.
                        val now = current
                        val world = transformFor(now).world(down.position)
                        val wiring = now.action as? EditorAction.Wiring
                        if (wiring != null) {
                            val target = CircuitGeometry.terminalAt(
                                now.circuit,
                                world.first,
                                world.second,
                                26.0 * now.zoom,
                            )
                            send(
                                if (target != null && target != wiring.from) {
                                    CircuitEditor.addWire(now, wiring.from, target)
                                        .copy(action = EditorAction.Idle)
                                } else {
                                    now.copy(action = EditorAction.Idle)
                                },
                            )
                        } else {
                            val placing = now.action as? EditorAction.Placing
                            if (placing != null) {
                                val placed = CircuitEditor.addComponent(
                                    now,
                                    placing.kind,
                                    world.first,
                                    world.second,
                                ).copy(action = EditorAction.Idle)
                                send(placed)
                                // The part that has just been put down is
                                // opened, so that its value can be given
                                // straight away.
                                select(placed.selected)
                            } else {
                                val terminal = CircuitGeometry.terminalAt(
                                    now.circuit,
                                    world.first,
                                    world.second,
                                    26.0 * now.zoom,
                                )
                                if (terminal != null) {
                                    send(
                                        now.copy(
                                            action = EditorAction.Wiring(
                                                terminal,
                                                world.first,
                                                world.second,
                                            ),
                                        ),
                                    )
                                } else {
                                    val component = CircuitGeometry.componentAt(
                                        now.circuit,
                                        world.first,
                                        world.second,
                                    )
                                    send(now.copy(selected = component?.id))
                                    select(component?.id)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val view = CanvasTransform(
                centreX = state.viewCentreX,
                centreY = state.viewCentreY,
                zoom = state.zoom,
                width = size.width,
                height = size.height,
            )
            val stroke = 2f * density.density
            val small = 11.sp

            // The engineering grid, over the whole of the picture.
            val step = 40.0
            val left = state.viewCentreX - size.width * state.zoom / 2
            val right = state.viewCentreX + size.width * state.zoom / 2
            val bottom = state.viewCentreY - size.height * state.zoom / 2
            val top = state.viewCentreY + size.height * state.zoom / 2
            var gx = floor(left / step) * step
            while (gx <= right) {
                val x = ((gx - state.viewCentreX) / state.zoom + size.width / 2).toFloat()
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                gx += step
            }
            var gy = floor(bottom / step) * step
            while (gy <= top) {
                val y = (size.height / 2 - (gy - state.viewCentreY) / state.zoom).toFloat()
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                gy += step
            }

            val topology = CircuitTopology(state.circuit)

            // The wires.
            for (wire in state.circuit.wires) {
                val from = CircuitGeometry.terminalPoint(state.circuit, wire.from) ?: continue
                val to = CircuitGeometry.terminalPoint(state.circuit, wire.to) ?: continue
                drawWire(view, from.first to from.second, to.first to to.second, wireColor, stroke)
            }
            // The wire being drawn.
            val wiring = state.action as? EditorAction.Wiring
            if (wiring != null) {
                val from = CircuitGeometry.terminalPoint(state.circuit, wiring.from)
                if (from != null) {
                    drawWire(
                        view,
                        from.first to from.second,
                        wiring.to to wiring.toY,
                        selectColor,
                        stroke,
                    )
                }
            }

            // The parts.
            for (component in state.circuit.components) {
                drawComponent(
                    view = view,
                    component = component,
                    selected = component.id == state.selected,
                    color = lineColor,
                    selectColor = selectColor,
                    stroke = stroke,
                    measurer = measurer,
                    labelColor = labelColor,
                    valueColor = valueColor,
                    result = results[component.id],
                    showValue = showValues,
                    labelStyle = TextStyle(fontSize = small, color = labelColor),
                    valueStyle = TextStyle(fontSize = small, color = valueColor),
                )
            }

            // The part that is being dragged onto the canvas, shown faintly
            // where it would land.
            if (dragKind != null && dragPosition != null) {
                val ghost = Offset(dragPosition.x, dragPosition.y)
                drawCircle(
                    color = selectColor.copy(alpha = 0.18f),
                    radius = 26f * density.density,
                    center = ghost,
                )
            }

            // The junction dots.
            for (node in topology.junctionNodes()) {
                val terminals = topology.terminalsIn(node)
                val point = terminals.firstOrNull()?.let {
                    CircuitGeometry.terminalPoint(state.circuit, it)
                } ?: continue
                drawCircle(
                    color = wireColor,
                    radius = 4f * density.density,
                    center = view.screen(point.first, point.second),
                )
            }

            // The terminals.
            for (component in state.circuit.components) {
                for (terminal in component.terminals) {
                    val point = CircuitGeometry.terminalPosition(component, terminal.index)
                    val x = ((point.first - state.viewCentreX) / state.zoom + size.width / 2).toFloat()
                    val y = (size.height / 2 - (point.second - state.viewCentreY) / state.zoom).toFloat()
                    drawCircle(
                        color = if (wiring?.from == terminal) selectColor else wireColor,
                        radius = 3.5f * density.density,
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}

/** Draws a wire between two points, as a right-angled line. */
private fun DrawScope.drawWire(
    view: CanvasTransform,
    from: Pair<Double, Double>,
    to: Pair<Double, Double>,
    color: Color,
    stroke: Float,
) {
    val a = view.screen(from.first, from.second)
    val b = view.screen(to.first, to.second)
    val path = Path()
    path.moveTo(a.x, a.y)
    val middle = (a.x + b.x) / 2
    path.lineTo(middle, a.y)
    path.lineTo(middle, b.y)
    path.lineTo(b.x, b.y)
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/**
 * Draws one part, its symbol, its name and its value.
 *
 * The part is drawn along its own axis, so a part that has been turned is
 * turned on the paper as well: its leads, its symbol and the way up it lies
 * all follow the direction from its first terminal to its second.
 */
private fun DrawScope.drawComponent(
    view: CanvasTransform,
    component: CircuitComponent,
    selected: Boolean,
    color: Color,
    selectColor: Color,
    stroke: Float,
    measurer: TextMeasurer,
    labelColor: Color,
    valueColor: Color,
    result: ComponentResult?,
    showValue: Boolean,
    labelStyle: TextStyle,
    valueStyle: TextStyle,
) {
    val first = CircuitGeometry.terminalPosition(component, 0)
    val second = CircuitGeometry.terminalPosition(component, if (component.terminalCount > 1) 1 else 0)
    val a = view.screen(first.first, first.second)
    val b = view.screen(second.first, second.second)
    val middle = view.screen(component.x, component.y)

    // The direction from the first terminal to the second, and the way
    // across it.
    var dx = b.x - a.x
    var dy = b.y - a.y
    val span = kotlin.math.sqrt(dx * dx + dy * dy)
    if (span < 0.001f) {
        dx = 1f
        dy = 0f
    } else {
        dx /= span
        dy /= span
    }
    val acrossX = -dy
    val acrossY = dx

    if (selected) {
        val bounds = CircuitGeometry.componentBounds(component)
        val topLeft = view.screen(bounds[0], bounds[3])
        val bottomRight = view.screen(bounds[2], bounds[1])
        drawRect(
            color = selectColor.copy(alpha = 0.16f),
            topLeft = Offset(min(topLeft.x, bottomRight.x), min(topLeft.y, bottomRight.y)),
            size = androidx.compose.ui.geometry.Size(
                abs(bottomRight.x - topLeft.x),
                abs(bottomRight.y - topLeft.y),
            ),
        )
    }

    drawPart(
        kind = component.kind,
        first = a,
        second = b,
        colour = color,
        stroke = stroke,
        closed = component.switchClosed,
        unit = (1.0 / view.zoom).toFloat(),
    )

    // The name is written beside the part, on the side that is free, and the
    // value is written on the other side.
    val nameLayout = measurer.measure(component.label, labelStyle)
    val valueLayout = if (showValue && result != null) {
        measurer.measure(
            "${com.calcsuite.engine.electrical.CircuitExplainer.number(result.voltage)} V · " +
                "${com.calcsuite.engine.electrical.CircuitExplainer.number(result.current)} A",
            valueStyle,
        )
    } else {
        null
    }
    // The name is centred on the part and written above it, clear of the
    // symbol. The distances are scaled with the zoom, so the name never
    // lies on top of the part it names.
    val unit = (1.0 / view.zoom).toFloat()
    val halfAcross = 10f * unit
    val halfAlong = 22f * unit
    val standsUp = abs(acrossX) > 0.5f
    val gap = 5f

    val nameX: Float
    val nameY: Float
    val valueX: Float
    val valueY: Float
    if (component.kind == ComponentKind.GROUND) {
        // The ground symbol hangs below its point, so the name is written
        // above the point and the value below the symbol.
        nameX = middle.x - nameLayout.size.width / 2
        nameY = middle.y - nameLayout.size.height - 8f
        valueX = middle.x - (valueLayout?.size?.width ?: 0) / 2
        valueY = middle.y + 12f * unit
    } else {
        val top = if (standsUp) {
            middle.y - (halfAlong + halfAcross)
        } else {
            middle.y - halfAcross
        }
        val bottom = if (standsUp) {
            middle.y + (halfAlong + halfAcross)
        } else {
            middle.y + halfAcross
        }
        nameX = middle.x - nameLayout.size.width / 2
        nameY = top - gap - nameLayout.size.height
        valueX = middle.x - (valueLayout?.size?.width ?: 0) / 2
        valueY = bottom + gap
    }
    drawText(textLayoutResult = nameLayout, topLeft = Offset(nameX, nameY))
    if (valueLayout != null) {
        drawText(textLayoutResult = valueLayout, topLeft = Offset(valueX, valueY))
    }
}

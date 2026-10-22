package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.electrical.ComponentKind

/**
 * The palette of parts.
 *
 * It is a strip rather than a sheet, so that it takes as little of the
 * canvas as it can while still being easy to reach. Every part is drawn as
 * the symbol that stands for it - the box of a resistor, the circle of a
 * source, the arm of a switch and the earth symbol of the ground - so that
 * the palette and the canvas show the same pictures.
 *
 * A part can be dragged out of the palette onto the canvas, or chosen and
 * then put down with a tap on the canvas.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ComponentPalette(
    onPick: (ComponentKind) -> Unit,
    modifier: Modifier = Modifier,
    onDragStart: (ComponentKind, Offset) -> Unit = { _, _ -> },
    onDrag: (ComponentKind, Offset) -> Unit = { _, _ -> },
    onDragEnd: (ComponentKind, Offset) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxWidth()) {
        // The parts are laid out in rows rather than in a strip, so that the
        // whole palette is on the screen and a part can be dragged out of it
        // without a scroll fighting with the drag.
        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (kind in ComponentKind.entries) {
                PaletteItem(
                    kind = kind,
                    onPick = { onPick(kind) },
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                )
            }
        }
    }
}

/** One part in the palette. */
@Composable
private fun PaletteItem(
    kind: ComponentKind,
    onPick: () -> Unit,
    onDragStart: (ComponentKind, Offset) -> Unit,
    onDrag: (ComponentKind, Offset) -> Unit,
    onDragEnd: (ComponentKind, Offset) -> Unit,
) {
    // The place of the item in the whole screen, so that a part that is
    // dragged out of the palette can be placed where the finger is.
    val root = remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val colour = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .width(80.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onGloballyPositioned { root.value = it.positionInRoot() }
            .pointerInput(kind) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    var pointer = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            val moved = (change.position - down.position).getDistance()
                            if (!dragged && moved > viewConfiguration.touchSlop) {
                                dragged = true
                                onDragStart(kind, root.value + change.position)
                            }
                            if (dragged) {
                                change.consume()
                                onDrag(kind, root.value + change.position)
                            }
                        }
                    }
                    if (dragged) {
                        onDragEnd(kind, root.value + down.position)
                    } else {
                        onPick()
                    }
                }
            }
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(
            Modifier
                .size(width = 34.dp, height = 22.dp)
                .semantics { contentDescription = kind.label },
        ) {
            val stroke = 1.8f * density.density
            val centre = Offset(size.width / 2f, size.height / 2f)
            val length = size.width * 0.8f
            val width = size.height * 0.7f
            when (kind) {
                ComponentKind.GROUND ->
                    drawSymbol(
                        kind = kind,
                        centre = Offset(centre.x, centre.y - width * 0.2f),
                        length = 0f,
                        width = width,
                        horizontal = true,
                        colour = colour,
                        stroke = stroke,
                    )
                ComponentKind.SWITCH ->
                    drawSymbol(
                        kind = kind,
                        centre = centre,
                        length = length,
                        width = width,
                        horizontal = true,
                        colour = colour,
                        stroke = stroke,
                        closed = false,
                    )
                else ->
                    drawSymbol(
                        kind = kind,
                        centre = centre,
                        length = length,
                        width = width,
                        horizontal = true,
                        colour = colour,
                        stroke = stroke,
                    )
            }
        }
        Text(
            kind.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

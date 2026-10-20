package com.calcsuite.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.calcsuite.ui.components.clickableRow
import com.calcsuite.ui.theme.fromHsl
import com.calcsuite.ui.theme.colourToHex
import com.calcsuite.ui.theme.parseColour
import com.calcsuite.ui.theme.toHsl
import kotlin.math.roundToInt

/**
 * The sheet for choosing a custom accent colour.
 *
 * The colour is picked from a square of saturation and brightness with a hue
 * slider under it, and it can also be typed in as hexadecimal. Whatever is
 * chosen is only applied when the user confirms it, so a colour is never lost
 * by accident.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccentPickerSheet(
    initial: Color,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = initial.toHsl()
    var hue by remember { mutableStateOf(start.hue) }
    var saturation by remember { mutableStateOf(start.saturation) }
    var brightness by remember { mutableStateOf(start.lightness) }
    var hex by remember { mutableStateOf(colourToHex(initial)) }
    var hexError by remember { mutableStateOf<String?>(null) }

    val chosen = fromHsl(hue, saturation, brightness)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Custom accent colour", style = MaterialTheme.typography.titleSmall)

            // The preview and the value.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(chosen, MaterialTheme.shapes.small)
                        .semantics { contentDescription = "The chosen colour" },
                )
                Text(
                    colourToHex(chosen),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            // The square of saturation and brightness.
            ColourSquare(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onChange = { s, b ->
                    saturation = s
                    brightness = b
                    hex = colourToHex(fromHsl(hue, s, b))
                    hexError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )

            // The hue.
            Column {
                Text("Hue", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        hex = colourToHex(fromHsl(it, saturation, brightness))
                        hexError = null
                    },
                    valueRange = 0f..1f,
                )
            }

            // The value typed in.
            OutlinedTextField(
                value = hex,
                onValueChange = {
                    hex = it
                    val parsed = parseColour(it)
                    if (parsed == null) {
                        hexError = "That is not a colour. Use #RRGGBB."
                    } else {
                        hexError = null
                        val hsl = parsed.toHsl()
                        hue = hsl.hue
                        saturation = hsl.saturation
                        brightness = hsl.lightness
                    }
                },
                label = { Text("Hexadecimal") },
                placeholder = { Text("#1B6B4B") },
                singleLine = true,
                isError = hexError != null,
                supportingText = hexError?.let { { Text(it) } },
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onApply(chosen) },
                    enabled = hexError == null,
                ) { Text("Use this colour") }
            }
        }
    }
}

/**
 * The square of saturation and brightness for one hue.
 *
 * The colour at a point is the hue with that saturation and brightness, so
 * tapping or dragging inside the square picks the colour under the finger.
 */
@Composable
private fun ColourSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clipToBounds()
            .pointerInput(hue) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onChange(
                        (offset.x / width).coerceIn(0f, 1f),
                        1f - (offset.y / height).coerceIn(0f, 1f),
                    )
                }
            }
            .then(Modifier.pointerInput(hue) {
                detectDragGestures { change, _ ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onChange(
                        (change.position.x / width).coerceIn(0f, 1f),
                        1f - (change.position.y / height).coerceIn(0f, 1f),
                    )
                }
            })
            .semantics { contentDescription = "The colour picker" },
    ) {
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            drawSaturationSquare(hue, brightness)
            // The marker.
            val x = saturation * size.width
            val y = (1f - brightness) * size.height
            drawCircle(Color.White, radius = 9f, center = Offset(x, y))
            drawCircle(Color.Black, radius = 7f, center = Offset(x, y))
        }
    }
}

/**
 * The square: the colour gets paler to the right and darker downwards.
 */
private fun DrawScope.drawSaturationSquare(hue: Float, brightness: Float) {
    val steps = 24
    val cellWidth = size.width / steps
    val cellHeight = size.height / steps
    for (i in 0 until steps) {
        for (j in 0 until steps) {
            val saturation = (i + 0.5f) / steps
            val lightness = (1f - (j + 0.5f) / steps) * brightness
            drawRect(
                color = fromHsl(hue, saturation, lightness),
                topLeft = Offset(i * cellWidth, j * cellHeight),
                size = androidx.compose.ui.geometry.Size(cellWidth + 1f, cellHeight + 1f),
            )
        }
    }
}

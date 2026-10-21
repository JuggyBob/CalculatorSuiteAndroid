package com.calcsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The buttons that sit in a row of actions.
 *
 * They all share one style, so that a row of them has one height and one
 * look, whatever the label is:
 *
 * - the label is **one line**. A two-word label is shortened rather than
 *   wrapped, because a button that grows to two lines makes the row it is
 *   in look broken;
 * - the padding is tighter than the Material default, which is what lets a
 *   two-word label fit in half a card;
 * - the icon, when there is one, sits beside the label rather than
 *   floating, and it is the same size as the text.
 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** True for the button that is the main action, false for the others. */
    emphasised: Boolean = false,
    /** True for a button that sits in the background rather than in a card. */
    plain: Boolean = false,
    enabled: Boolean = true,
) {
    val label: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    val shape = MaterialTheme.shapes.small
    val padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    when {
        plain -> TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            label()
        }
        emphasised -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
        ) {
            label()
        }
        else -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
        ) {
            label()
        }
    }
}

/**
 * A row of actions that share the width evenly.
 *
 * The buttons are given the same share of the row on purpose, so that a row
 * of them reads as one group and none of them is left narrower than the
 * label it holds.
 */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

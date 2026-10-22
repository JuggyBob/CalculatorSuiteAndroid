package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.electrical.CalculatorResult
import com.calcsuite.engine.electrical.ElectricalCalculators
import com.calcsuite.engine.electrical.ElectricalUnits
import com.calcsuite.engine.electrical.ResistorNetwork
import com.calcsuite.ui.components.ActionButton
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import java.math.BigDecimal

/**
 * One resistor in the list.
 *
 * The name it is given never changes, so that removing one resistor does
 * not rename the others.
 */
class ResistorInput(label: String, value: String) {
    val label: String = label
    var value: String by mutableStateOf(value)
}

/**
 * The list of resistors of a series or parallel calculation.
 *
 * The list is as long as the user needs: there is no limit on it, and every
 * resistor can be taken away again.
 */
@Composable
fun ResistorListPanel(
    title: String,
    parallel: Boolean,
    initial: List<Pair<String, String>> = listOf("100" to "100", "200" to "200"),
) {
    var voltage by rememberSaveable { mutableStateOf("12") }
    var prefix by rememberSaveable { mutableStateOf("none") }
    val resistors = remember {
        mutableStateListOf<ResistorInput>().apply {
            initial.forEach { add(ResistorInput(it.first, it.second)) }
        }
    }
    var next by remember { mutableStateOf(initial.size + 1) }

    val v = ElectricalUnits.toBaseOrNull(Num.parseOrNull(voltage), prefix)
    val values = resistors.mapNotNull { resistor ->
        Num.parseOrNull(resistor.value)?.let { resistor.label to it }
    }
    val result: CalcResult<CalculatorResult>? = remember(resistors.size, values, v, parallel) {
        if (v == null || values.isEmpty()) {
            null
        } else if (parallel) {
            ElectricalCalculators.parallel(values, v)
        } else {
            ElectricalCalculators.series(values, v)
        }
    }

    SectionCard(title = title) {
        NumberField(
            label = "The source voltage (V)",
            value = voltage,
            onValueChange = { voltage = it },
        )
        SegmentedChoice(
            label = "SI prefix",
            options = ElectricalUnits.prefixes.map { it.id to it.shortLabel },
            selected = prefix,
            onSelect = { prefix = it },
        )
        for (index in resistors.indices) {
            val resistor = resistors[index]
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberField(
                    label = "${resistor.label} (Ω)",
                    value = resistor.value,
                    onValueChange = { resistors[index].value = it },
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { resistors.removeAt(index) },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Take away ${resistor.label}",
                    )
                }
            }
        }
        ActionButton(
            text = "Add resistor",
            onClick = {
                resistors.add(ResistorInput("R$next", "100"))
                next++
            },
            icon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

/**
 * How the parts of a mixed network are joined.
 */
enum class GroupKind(val label: String, val shortLabel: String) {
    SERIES("in series", "Series"),
    PARALLEL("in parallel", "Parallel"),
}

/**
 * One part of a mixed network: a resistor, or a group of parts.
 */
sealed interface NetworkItem {
    val label: String
}

/** One resistor of a mixed network. */
class NetworkResistor(override val label: String, value: String) : NetworkItem {
    var value: String by mutableStateOf(value)
}

/** A group of parts, joined in series or in parallel. */
class NetworkGroup(
    override val label: String,
    val kind: GroupKind,
    val children: SnapshotStateList<NetworkItem>,
) : NetworkItem

/**
 * The mixed network: a tree of resistors and groups.
 *
 * The user says how the parts are joined, by making a series group or a
 * parallel group, and the groups may hold other groups.
 */
@Composable
fun MixedNetworkPanel() {
    var voltage by rememberSaveable { mutableStateOf("12") }
    var next by remember { mutableStateOf(1) }
    val items = remember {
        mutableStateListOf<NetworkItem>().apply {
            add(NetworkResistor("R1", "100"))
            add(NetworkResistor("R2", "200"))
        }
    }

    val network = remember(items.size, items.toString()) { toNetwork(items) }
    val v = Num.parseOrNull(voltage)
    val result = remember(network, v) {
        if (network == null) null else ElectricalCalculators.network(network, v)
    }

    SectionCard(title = "A series and parallel network") {
        Text(
            "Add resistors, and say how they are joined. A group may hold " +
                "other groups.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NetworkItems(
            items = items,
            next = next,
            onNext = { next = it },
            modifier = Modifier.padding(top = 8.dp),
        )
        NumberField(
            label = "The source voltage (V)",
            value = voltage,
            onValueChange = { voltage = it },
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

/**
 * The list of parts of a mixed network, and the groups inside it.
 */
@Composable
private fun NetworkItems(
    items: SnapshotStateList<NetworkItem>,
    next: Int,
    onNext: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in items.indices) {
            when (val item = items[index]) {
                is NetworkResistor -> Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberField(
                        label = "${item.label} (Ω)",
                        value = item.value,
                        onValueChange = { items[index] = item.copyValue(it) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { items.removeAt(index) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Take away ${item.label}",
                        )
                    }
                }
                is NetworkGroup -> GroupBox(
                    group = item,
                    next = next,
                    onNext = onNext,
                    onRemove = { items.removeAt(index) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = "Add resistor",
                onClick = {
                    items.add(NetworkResistor("R$next", "100"))
                    onNext(next + 1)
                },
                icon = Icons.Filled.Add,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                text = "Add series group",
                onClick = {
                    items.add(
                        NetworkGroup(
                            label = "G$next",
                            kind = GroupKind.SERIES,
                            children = mutableStateListOf<NetworkItem>().apply {
                                add(NetworkResistor("R${next + 1}", "100"))
                            },
                        ),
                    )
                    onNext(next + 1)
                },
                icon = Icons.Filled.Add,
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                text = "Add parallel group",
                onClick = {
                    items.add(
                        NetworkGroup(
                            label = "G$next",
                            kind = GroupKind.PARALLEL,
                            children = mutableStateListOf<NetworkItem>().apply {
                                add(NetworkResistor("R${next + 1}", "100"))
                            },
                        ),
                    )
                    onNext(next + 1)
                },
                icon = Icons.Filled.Add,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One group of a mixed network, and the parts inside it.
 */
@Composable
private fun GroupBox(
    group: NetworkGroup,
    next: Int,
    onNext: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${group.label}: a group ${group.kind.label}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Take away ${group.label}",
                )
            }
        }
        NetworkItems(
            items = group.children,
            next = next,
            onNext = onNext,
        )
    }
}

/**
 * A copy of a resistor with a new value, so that the list notices the
 * change.
 */
private fun NetworkResistor.copyValue(value: String): NetworkResistor {
    this.value = value
    return this
}

/**
 * The list of parts, as the network the engine works with.
 */
private fun toNetwork(items: List<NetworkItem>): ResistorNetwork? {
    if (items.isEmpty()) return null
    val parts = items.map { item ->
        when (item) {
            is NetworkResistor -> {
                val value = Num.parseOrNull(item.value) ?: return null
                ResistorNetwork.Resistor(item.label, value as BigDecimal)
            }
            is NetworkGroup -> {
                val inside = toNetwork(item.children) ?: return null
                when (item.kind) {
                    GroupKind.SERIES -> ResistorNetwork.Series(listOf(inside))
                    GroupKind.PARALLEL -> ResistorNetwork.Parallel(listOf(inside))
                }
            }
        }
    }
    return ResistorNetwork.Series(parts)
}

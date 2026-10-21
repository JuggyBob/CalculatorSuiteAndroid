package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.electrical.CalculatorResult
import com.calcsuite.engine.electrical.ElectricalExtras
import com.calcsuite.engine.electrical.ElectricalUnits
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.SegmentedChoice

/**
 * The same value written in another prefix.
 */
@Composable
fun UnitConversionPanel() {
    var value by rememberSaveable { mutableStateOf("0.12") }
    var unit by rememberSaveable { mutableStateOf("CURRENT") }
    var fromPrefix by rememberSaveable { mutableStateOf("none") }
    var toPrefix by rememberSaveable { mutableStateOf("milli") }

    val quantity = ElectricalUnits.Unit.valueOf(unit)
    val parsed = Num.parseOrNull(value)
    val result = remember(parsed, unit, fromPrefix, toPrefix) {
        if (parsed == null) {
            null
        } else {
            ElectricalExtras.convert(parsed, quantity, fromPrefix, quantity, toPrefix)
        }
    }

    SectionCard(title = "Write a value in another prefix") {
        NumberField(
            label = "The value",
            value = value,
            onValueChange = { value = it },
        )
        SelectField(
            label = "The quantity",
            options = ElectricalUnits.Unit.entries.map {
                Option(it.name, it.symbol, group = "Electrical", symbol = it.base)
            },
            selected = unit,
            onSelect = { unit = it },
        )
        SelectField(
            label = "The prefix it is written in",
            options = ElectricalUnits.prefixes.map {
                Option(it.id, it.label, group = "Prefixes", symbol = it.symbol)
            },
            selected = fromPrefix,
            onSelect = { fromPrefix = it },
        )
        SelectField(
            label = "The prefix to write it in",
            options = ElectricalUnits.prefixes.map {
                Option(it.id, it.label, group = "Prefixes", symbol = it.symbol)
            },
            selected = toPrefix,
            onSelect = { toPrefix = it },
        )
    }

    when (result) {
        null -> InfoNote("Enter a value to see it in the other prefix.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

/**
 * A resistor read from its colours, or written in colours.
 */
@Composable
fun ColourCodePanel() {
    var first by rememberSaveable { mutableStateOf("Brown") }
    var second by rememberSaveable { mutableStateOf("Black") }
    var multiplier by rememberSaveable { mutableStateOf("Red") }
    var tolerance by rememberSaveable { mutableStateOf("Gold") }

    val band = { name: String ->
        ElectricalExtras.digitBands.firstOrNull { it.colour == name }
            ?: ElectricalExtras.multiplierBands.firstOrNull { it.colour == name }
    }
    val tolBand = { name: String ->
        ElectricalExtras.toleranceBands.firstOrNull { it.colour == name }
    }

    val result = remember(first, second, multiplier, tolerance) {
        val bands = listOfNotNull(
            band(first),
            band(second),
            band(multiplier),
            tolBand(tolerance),
        )
        if (bands.size < 4) null else ElectricalExtras.fromBands(bands)
    }

    SectionCard(title = "Read a resistor from its colours") {
        SelectField(
            label = "The first band",
            options = ElectricalExtras.digitBands.map {
                Option(it.colour, it.colour, group = "Digits", symbol = it.digit?.toString())
            },
            selected = first,
            onSelect = { first = it },
        )
        SelectField(
            label = "The second band",
            options = ElectricalExtras.digitBands.map {
                Option(it.colour, it.colour, group = "Digits", symbol = it.digit?.toString())
            },
            selected = second,
            onSelect = { second = it },
        )
        SelectField(
            label = "The multiplier band",
            options = ElectricalExtras.multiplierBands.map {
                Option(it.colour, it.colour, group = "Multipliers", symbol = "10^${it.multiplier}")
            },
            selected = multiplier,
            onSelect = { multiplier = it },
        )
        SelectField(
            label = "The tolerance band",
            options = ElectricalExtras.toleranceBands.map {
                Option(it.colour, it.colour, group = "Tolerance", symbol = "± ${it.tolerance?.stripTrailingZeros()?.toPlainString()} %")
            },
            selected = tolerance,
            onSelect = { tolerance = it },
        )
    }

    when (result) {
        null -> InfoNote("Choose the bands in the order they are printed.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }

    // The other way round: the bands for a value.
    var value by rememberSaveable { mutableStateOf("4700") }
    val toBands = remember(value) {
        Num.parseOrNull(value)?.let { ElectricalExtras.toBands(it) }
    }
    SectionCard(title = "Write a resistor in colours") {
        NumberField(
            label = "The resistance (Ω)",
            value = value,
            onValueChange = { value = it },
        )
    }
    when (toBands) {
        null -> InfoNote("Enter a resistance to see the bands.")
        is CalcResult.Err -> InfoNote(toBands.error.message)
        is CalcResult.Ok -> CalculatorResultCard(toBands.value)
    }
}

/**
 * A capacitor: the charge it holds, the energy in it, and how long it takes
 * to charge.
 */
@Composable
fun CapacitorPanel() {
    var capacitance by rememberSaveable { mutableStateOf("100") }
    var prefix by rememberSaveable { mutableStateOf("micro") }
    var voltage by rememberSaveable { mutableStateOf("12") }
    var resistance by rememberSaveable { mutableStateOf("1000") }
    var useResistance by rememberSaveable { mutableStateOf(true) }

    val c = ElectricalUnits.toBaseOrNull(Num.parseOrNull(capacitance), prefix)
    val v = Num.parseOrNull(voltage)
    val r = Num.parseOrNull(resistance)

    val result = remember(c, v, r, useResistance) {
        if (c == null || v == null) {
            null
        } else {
            ElectricalExtras.capacitor(c, v, if (useResistance) r else null)
        }
    }

    SectionCard(title = "A capacitor") {
        NumberField(
            label = "The capacitance",
            value = capacitance,
            onValueChange = { capacitance = it },
        )
        SegmentedChoice(
            label = "SI prefix",
            options = ElectricalUnits.prefixes.map { it.id to it.shortLabel },
            selected = prefix,
            onSelect = { prefix = it },
        )
        NumberField(
            label = "The voltage across it (V)",
            value = voltage,
            onValueChange = { voltage = it },
        )
        SegmentedChoice(
            label = "Charging through a resistance",
            options = listOf(true to "Yes", false to "No"),
            selected = useResistance,
            onSelect = { useResistance = it },
        )
        if (useResistance) {
            NumberField(
                label = "The resistance in series (Ω)",
                value = resistance,
                onValueChange = { resistance = it },
            )
        }
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

/**
 * The time constant of an RC or an RL circuit.
 */
@Composable
fun TimeConstantPanel() {
    var kind by rememberSaveable { mutableStateOf("capacitor") }
    var resistance by rememberSaveable { mutableStateOf("1000") }
    var capacitance by rememberSaveable { mutableStateOf("100") }
    var inductance by rememberSaveable { mutableStateOf("0.1") }

    val r = Num.parseOrNull(resistance)
    val c = Num.parseOrNull(capacitance)
    val l = Num.parseOrNull(inductance)

    val result = remember(kind, r, c, l) {
        val second = if (kind == "capacitor") c else l
        if (r == null || second == null) {
            null
        } else {
            ElectricalExtras.timeConstant(kind, r, second)
        }
    }

    SectionCard(title = "How long the circuit takes to settle") {
        SegmentedChoice(
            label = "The kind of circuit",
            options = listOf("capacitor" to "RC", "inductor" to "RL"),
            selected = kind,
            onSelect = { kind = it },
        )
        NumberField(
            label = "The resistance (Ω)",
            value = resistance,
            onValueChange = { resistance = it },
        )
        if (kind == "capacitor") {
            NumberField(
                label = "The capacitance (F)",
                value = capacitance,
                onValueChange = { capacitance = it },
            )
        } else {
            NumberField(
                label = "The inductance (H)",
                value = inductance,
                onValueChange = { inductance = it },
            )
        }
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

/**
 * An alternating circuit.
 */
@Composable
fun AcPanel() {
    var frequency by rememberSaveable { mutableStateOf("50") }
    var voltage by rememberSaveable { mutableStateOf("230") }
    var resistance by rememberSaveable { mutableStateOf("100") }
    var inductance by rememberSaveable { mutableStateOf("0.1") }
    var capacitance by rememberSaveable { mutableStateOf("0.000047") }
    var useInductance by rememberSaveable { mutableStateOf(true) }
    var useCapacitance by rememberSaveable { mutableStateOf(true) }

    val f = Num.parseOrNull(frequency)
    val v = Num.parseOrNull(voltage)
    val r = Num.parseOrNull(resistance)
    val l = Num.parseOrNull(inductance)
    val c = Num.parseOrNull(capacitance)

    val result = remember(f, v, r, l, c, useInductance, useCapacitance) {
        if (f == null || v == null || r == null) {
            null
        } else {
            ElectricalExtras.ac(
                frequency = f,
                rmsVoltage = v,
                resistance = r,
                inductance = if (useInductance) l else null,
                capacitance = if (useCapacitance) c else null,
            )
        }
    }

    SectionCard(title = "An alternating circuit") {
        NumberField(
            label = "The frequency (Hz)",
            value = frequency,
            onValueChange = { frequency = it },
        )
        NumberField(
            label = "The RMS voltage (V)",
            value = voltage,
            onValueChange = { voltage = it },
        )
        NumberField(
            label = "The resistance (Ω)",
            value = resistance,
            onValueChange = { resistance = it },
        )
        SegmentedChoice(
            label = "With an inductor",
            options = listOf(true to "Yes", false to "No"),
            selected = useInductance,
            onSelect = { useInductance = it },
        )
        if (useInductance) {
            NumberField(
                label = "The inductance (H)",
                value = inductance,
                onValueChange = { inductance = it },
            )
        }
        SegmentedChoice(
            label = "With a capacitor",
            options = listOf(true to "Yes", false to "No"),
            selected = useCapacitance,
            onSelect = { useCapacitance = it },
        )
        if (useCapacitance) {
            NumberField(
                label = "The capacitance (F)",
                value = capacitance,
                onValueChange = { capacitance = it },
            )
        }
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

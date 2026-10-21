package com.calcsuite.engine.electrical

import java.math.BigDecimal

/**
 * The sample circuits the tool offers.
 *
 * They are real circuits: loading one fills the model, so every part can be
 * edited and the circuit solved like any other. The answers are the ones a
 * textbook gives, and they are used as the reference cases in the tests.
 *
 * The parts are laid out on the canvas as they would be drawn by hand: the
 * source on the left, the parts of the circuit along the top, and the ground
 * below. The layout says nothing about the circuit itself - the solver only
 * ever sees the parts and the wires.
 */
object CircuitExamples {

    /** One of the sample circuits. */
    data class Example(
        val id: String,
        val title: String,
        val teaching: String,
        val circuit: Circuit,
    )

    /** The samples, in the order they are offered. */
    val all: List<Example> = listOf(
        batteryAndResistor(),
        twoInSeries(),
        twoInParallel(),
        mixedNetwork(),
        seriesWithSwitch(),
        parallelBranches(),
        voltageDivider(),
    )

    /** The sample with that name. */
    fun byId(id: String): Example? = all.firstOrNull { it.id == id }

    // ------------------------------------------------------------- helpers

    /**
     * Where the source of a sample is drawn.
     *
     * The world is measured with the up direction as positive, so the row of
     * parts that the source feeds is above it, and the ground is below.
     */
    private const val SOURCE_X = -120.0
    private const val SOURCE_Y = -40.0

    /** Where the top row of parts is drawn, level with the source's top end. */
    private const val TOP_Y = 5.0

    /** Where the ground is drawn, below the source. */
    private const val GROUND_X = -120.0
    private const val GROUND_Y = -140.0

    /** Where the lower row of parts is drawn, between the ends of the source. */
    private const val LOWER_Y = -40.0

    private fun resistor(
        id: String,
        label: String,
        ohms: String,
        x: Double,
        y: Double,
        rotation: Int = 0,
    ) = CircuitComponent(
        id = id,
        kind = ComponentKind.RESISTOR,
        label = label,
        resistance = BigDecimal(ohms),
        x = x,
        y = y,
        rotation = rotation,
    )

    private fun source(
        id: String,
        label: String,
        volts: String,
        x: Double = SOURCE_X,
        y: Double = SOURCE_Y,
        rotation: Int = 3,
    ) = CircuitComponent(
        id = id,
        kind = ComponentKind.VOLTAGE_SOURCE,
        label = label,
        voltage = BigDecimal(volts),
        x = x,
        y = y,
        rotation = rotation,
    )

    private fun ground(
        id: String = "GND",
        x: Double = GROUND_X,
        y: Double = GROUND_Y,
    ) = CircuitComponent(
        id = id,
        kind = ComponentKind.GROUND,
        label = "Ground",
        x = x,
        y = y,
    )

    private fun switch(
        id: String,
        label: String,
        closed: Boolean,
        x: Double,
        y: Double,
    ) = CircuitComponent(
        id = id,
        kind = ComponentKind.SWITCH,
        label = label,
        switchClosed = closed,
        x = x,
        y = y,
    )

    private fun wire(id: String, from: String, fromIndex: Int, to: String, toIndex: Int) =
        CircuitWire(id, TerminalRef(from, fromIndex), TerminalRef(to, toIndex))

    // -------------------------------------------------------------- samples

    private fun batteryAndResistor() = Example(
        id = "battery",
        title = "A battery and a resistor",
        teaching = "The simplest circuit there is. The whole of the source " +
            "voltage is across the resistor, so Ohm's law gives the current.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "100", x = -30.0, y = TOP_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "R1", 1, "V1", 1),
                wire("w3", "V1", 1, "GND", 0),
            ),
        ),
    )

    private fun twoInSeries() = Example(
        id = "series",
        title = "Two resistors in series",
        teaching = "The same current flows through both resistors, so their " +
            "resistances add and the voltage drops add up to the source.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "100", x = -30.0, y = TOP_Y),
                resistor("R2", "R2", "200", x = 70.0, y = TOP_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "R1", 1, "R2", 0),
                wire("w3", "R2", 1, "V1", 1),
                wire("w4", "V1", 1, "GND", 0),
            ),
        ),
    )

    private fun twoInParallel() = Example(
        id = "parallel",
        title = "Two resistors in parallel",
        teaching = "Each branch has the whole of the source voltage across it, " +
            "and the branch currents add up to the current the source delivers.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "100", x = 0.0, y = TOP_Y),
                resistor("R2", "R2", "200", x = 0.0, y = LOWER_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "V1", 0, "R2", 0),
                wire("w3", "R1", 1, "V1", 1),
                wire("w4", "R2", 1, "V1", 1),
                wire("w5", "V1", 1, "GND", 0),
            ),
        ),
    )

    private fun mixedNetwork() = Example(
        id = "mixed",
        title = "A resistor and a parallel pair",
        teaching = "The current goes through the first resistor and then splits " +
            "between the two branches, so the parallel pair is reduced first and " +
            "the series resistor is added to it.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "100", x = 0.0, y = TOP_Y),
                resistor("R2", "R2", "200", x = -20.0, y = LOWER_Y),
                resistor("R3", "R3", "300", x = 80.0, y = LOWER_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "R1", 1, "R2", 0),
                wire("w3", "R1", 1, "R3", 0),
                wire("w4", "R2", 1, "V1", 1),
                wire("w5", "R3", 1, "V1", 1),
                wire("w6", "V1", 1, "GND", 0),
            ),
        ),
    )

    private fun seriesWithSwitch() = Example(
        id = "switch",
        title = "A switch in the circuit",
        teaching = "Open the switch and the branch is broken, so no current " +
            "flows and the whole of the source voltage appears across the switch. " +
            "Close it and the current is the same as without the switch.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "100", x = -30.0, y = TOP_Y),
                switch("S1", "S1", closed = true, x = 70.0, y = TOP_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "R1", 1, "S1", 0),
                wire("w3", "S1", 1, "V1", 1),
                wire("w4", "V1", 1, "GND", 0),
            ),
        ),
    )

    private fun parallelBranches() = Example(
        id = "branches",
        title = "Three branches in parallel",
        teaching = "Every branch has the source voltage across it, so each one " +
            "takes its own current and the three add up.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "100", x = -30.0, y = TOP_Y),
                resistor("R2", "R2", "220", x = -20.0, y = LOWER_Y),
                resistor("R3", "R3", "470", x = 80.0, y = LOWER_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "V1", 0, "R2", 0),
                wire("w3", "V1", 0, "R3", 0),
                wire("w4", "R1", 1, "V1", 1),
                wire("w5", "R2", 1, "V1", 1),
                wire("w6", "R3", 1, "V1", 1),
                wire("w7", "V1", 1, "GND", 0),
            ),
        ),
    )

    private fun voltageDivider() = Example(
        id = "divider",
        title = "A voltage divider",
        teaching = "Two resistors in series across the source. The output is " +
            "the part of the source voltage that falls across the second one.",
        circuit = Circuit(
            components = listOf(
                source("V1", "V1", "12"),
                resistor("R1", "R1", "10000", x = -30.0, y = TOP_Y),
                resistor("R2", "R2", "20000", x = 70.0, y = TOP_Y),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "R1", 1, "R2", 0),
                wire("w3", "R2", 1, "V1", 1),
                wire("w4", "V1", 1, "GND", 0),
            ),
        ),
    )
}

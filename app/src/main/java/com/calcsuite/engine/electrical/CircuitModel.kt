package com.calcsuite.engine.electrical

import java.math.BigDecimal

/**
 * The circuit model.
 *
 * A circuit is a set of components and a set of wires. The components carry
 * their electrical properties; their position, size and orientation are
 * drawing information and are kept apart from them, so that moving a
 * resistor cannot change the circuit.
 *
 * The connectivity is **not** read from the drawing. It is built from the
 * terminals and the wires alone, so that two wires which merely cross are
 * never joined.
 */

/** The groups the component drawer offers. */
enum class ComponentGroup(val label: String) {
    SOURCE("Sources"),
    PASSIVE("Resistors and passive parts"),
    CONTROL("Switches and controls"),
    MEASUREMENT("Instruments"),
    REFERENCE("Ground and connections"),
}

/** The kinds of component the first release supports. */
enum class ComponentKind(
    val label: String,
    val group: ComponentGroup,
    val terminalCount: Int,
    val prefix: String,
) {
    RESISTOR("Resistor", ComponentGroup.PASSIVE, 2, "R"),
    VOLTAGE_SOURCE("Voltage source", ComponentGroup.SOURCE, 2, "V"),
    CURRENT_SOURCE("Current source", ComponentGroup.SOURCE, 2, "I"),
    SWITCH("Switch", ComponentGroup.CONTROL, 2, "S"),
    GROUND("Ground", ComponentGroup.REFERENCE, 1, "GND"),
    ;

    /** True when the component takes part in the equations as a resistance. */
    val isResistive: Boolean get() = this == RESISTOR

    /** True when the component imposes a voltage. */
    val isVoltageSource: Boolean get() = this == VOLTAGE_SOURCE

    /** True when the component imposes a current. */
    val isCurrentSource: Boolean get() = this == CURRENT_SOURCE

    /** True when the component can be open or closed. */
    val isSwitch: Boolean get() = this == SWITCH
}

/** A terminal of a component, named by the component and its index. */
data class TerminalRef(val componentId: String, val index: Int) {
    override fun toString(): String = "$componentId#$index"
}

/**
 * One component of a circuit.
 *
 * @param id a stable identity that does not change when the component is
 *   moved, renamed or rotated.
 * @param kind what the component is.
 * @param label the reference designator the user sees, such as `R1`.
 * @param x where the component is drawn.
 * @param y where the component is drawn.
 * @param rotation the turn of the symbol, in quarter turns.
 * @param resistance the resistance in ohms, for a resistor.
 * @param voltage the voltage in volts, for a voltage source.
 * @param current the current in amperes, for a current source.
 * @param switchClosed whether a switch is closed.
 * @param powerRating the power a resistor can stand, in watts, when the user
 *   has said so.
 */
data class CircuitComponent(
    val id: String,
    val kind: ComponentKind,
    val label: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val rotation: Int = 0,
    val resistance: BigDecimal? = null,
    val voltage: BigDecimal? = null,
    val current: BigDecimal? = null,
    val switchClosed: Boolean = true,
    val powerRating: BigDecimal? = null,
) {
    /** How many terminals the component has. */
    val terminalCount: Int get() = kind.terminalCount

    /** The terminals of the component. */
    val terminals: List<TerminalRef>
        get() = (0 until terminalCount).map { TerminalRef(id, it) }

    /** True when the component takes part in the equations. */
    val isActive: Boolean
        get() = when (kind) {
            ComponentKind.RESISTOR -> true
            ComponentKind.SWITCH -> switchClosed
            ComponentKind.VOLTAGE_SOURCE -> true
            ComponentKind.CURRENT_SOURCE -> true
            ComponentKind.GROUND -> true
        }

    /** A copy with the next free reference designator. */
    fun withLabel(newLabel: String): CircuitComponent = copy(label = newLabel)
}

/** A wire between two terminals. */
data class CircuitWire(
    val id: String,
    val from: TerminalRef,
    val to: TerminalRef,
)

/**
 * A whole circuit.
 *
 * The components and the wires are the whole of it. Everything the solver
 * needs is derived from them, and nothing is derived from the drawing.
 */
data class Circuit(
    val components: List<CircuitComponent> = emptyList(),
    val wires: List<CircuitWire> = emptyList(),
) {
    /** The component with that identity. */
    fun component(id: String): CircuitComponent? = components.firstOrNull { it.id == id }

    /** The wire with that identity. */
    fun wire(id: String): CircuitWire? = wires.firstOrNull { it.id == id }

    /** The components of one kind. */
    fun of(kind: ComponentKind): List<CircuitComponent> = components.filter { it.kind == kind }

    /** True when there is nothing in the circuit. */
    val isEmpty: Boolean get() = components.isEmpty()

    /** A copy with one more component. */
    fun withComponent(component: CircuitComponent): Circuit = copy(
        components = components + component,
    )

    /** A copy without that component, and without the wires that used it. */
    fun withoutComponent(id: String): Circuit = copy(
        components = components.filterNot { it.id == id },
        wires = wires.filterNot { it.from.componentId == id || it.to.componentId == id },
    )

    /** A copy with one more wire. */
    fun withWire(wire: CircuitWire): Circuit = copy(wires = wires + wire)

    /** A copy without that wire. */
    fun withoutWire(id: String): Circuit = copy(wires = wires.filterNot { it.id == id })

    /** The next free reference designator for a kind, such as `R2`. */
    fun nextLabel(kind: ComponentKind): String {
        val used = components.filter { it.kind == kind }.map { it.label }.toSet()
        var index = 1
        while (used.contains("${kind.prefix}$index")) index++
        return "${kind.prefix}$index"
    }
}

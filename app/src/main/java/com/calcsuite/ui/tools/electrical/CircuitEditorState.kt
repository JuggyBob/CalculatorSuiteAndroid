package com.calcsuite.ui.tools.electrical

import com.calcsuite.engine.electrical.Circuit
import com.calcsuite.engine.electrical.CircuitComponent
import com.calcsuite.engine.electrical.CircuitGeometry
import com.calcsuite.engine.electrical.CircuitWire
import com.calcsuite.engine.electrical.ComponentKind
import com.calcsuite.engine.electrical.TerminalRef

/**
 * What the editor is doing at the moment.
 */
sealed interface EditorAction {
    /** Nothing is happening. */
    data object Idle : EditorAction

    /** A part is being dragged. */
    data class Dragging(val componentId: String) : EditorAction

    /** A wire is being drawn from a terminal. */
    data class Wiring(val from: TerminalRef, val to: Double, val toY: Double) : EditorAction

    /**
     * A part is waiting to be put down.
     *
     * This is the alternative to dragging a part out of the drawer: the part
     * is chosen and then put down with a tap on the canvas.
     */
    data class Placing(val kind: ComponentKind) : EditorAction
}

/**
 * What the editor is showing.
 *
 * @param circuit the circuit as it is now.
 * @param selected the part that is selected, when there is one.
 * @param action what the editor is doing.
 * @param viewCentreX the middle of the picture.
 * @param viewCentreY the middle of the picture.
 * @param zoom how much the picture is magnified.
 * @param showValues whether the computed values are drawn beside the parts.
 * @param undo the states that can be undone to.
 * @param redo the states that can be redone to.
 */
data class CircuitEditorState(
    val circuit: Circuit = Circuit(),
    val selected: String? = null,
    val action: EditorAction = EditorAction.Idle,
    val viewCentreX: Double = 0.0,
    val viewCentreY: Double = 0.0,
    val zoom: Double = 0.5,
    val showValues: Boolean = false,
    val undo: List<Circuit> = emptyList(),
    val redo: List<Circuit> = emptyList(),
) {
    /** The component that is selected. */
    val selectedComponent: CircuitComponent? get() = selected?.let { circuit.component(it) }

    /** The part the user is about to place, when they are placing one. */
    val placing: ComponentKind? get() = (action as? EditorAction.Placing)?.kind
}

/**
 * The editor's actions.
 *
 * Every change to the circuit goes through here, so that the history of the
 * circuit is kept in one place and the undo can put it back exactly as it
 * was.
 */
object CircuitEditor {

    /** The next identity for a part. */
    fun nextId(circuit: Circuit, prefix: String): String {
        var index = 1
        val used = circuit.components.map { it.id }.toSet() +
            circuit.wires.map { it.id }.toSet()
        while (used.contains("$prefix$index")) index++
        return "$prefix$index"
    }

    /** Adds a part to the circuit. */
    fun addComponent(
        state: CircuitEditorState,
        kind: ComponentKind,
        x: Double,
        y: Double,
    ): CircuitEditorState {
        val id = nextId(state.circuit, kind.prefix)
        val component = CircuitComponent(
            id = id,
            kind = kind,
            label = state.circuit.nextLabel(kind),
            x = CircuitGeometry.snap(x),
            y = CircuitGeometry.snap(y),
            resistance = if (kind == ComponentKind.RESISTOR) java.math.BigDecimal("100") else null,
            voltage = if (kind == ComponentKind.VOLTAGE_SOURCE) java.math.BigDecimal("12") else null,
            current = if (kind == ComponentKind.CURRENT_SOURCE) java.math.BigDecimal("0.1") else null,
        )
        return state.copy(
            circuit = state.circuit.withComponent(component),
            selected = id,
            undo = state.undo + state.circuit,
            redo = emptyList(),
        )
    }

    /**
     * Moves a part.
     *
     * @param snap true to put the part on the grid, false while it is being
     *   dragged. A part that is being dragged follows the finger exactly, and
     *   is put on the grid when it is let go.
     */
    fun moveComponent(
        state: CircuitEditorState,
        id: String,
        x: Double,
        y: Double,
        record: Boolean,
        snap: Boolean = true,
    ): CircuitEditorState {
        val component = state.circuit.component(id) ?: return state
        val moved = if (snap) {
            component.copy(x = CircuitGeometry.snap(x), y = CircuitGeometry.snap(y))
        } else {
            component.copy(x = x, y = y)
        }
        return state.copy(
            circuit = state.circuit.copy(
                components = state.circuit.components.map { if (it.id == id) moved else it },
            ),
            undo = if (record) state.undo + state.circuit else state.undo,
            redo = if (record) emptyList() else state.redo,
        )
    }

    /** Turns a part. */
    fun rotateComponent(state: CircuitEditorState, id: String): CircuitEditorState {
        val component = state.circuit.component(id) ?: return state
        val turned = component.copy(rotation = (component.rotation + 1) % 4)
        return state.copy(
            circuit = state.circuit.copy(
                components = state.circuit.components.map { if (it.id == id) turned else it },
            ),
            undo = state.undo + state.circuit,
            redo = emptyList(),
        )
    }

    /** Removes a part and the wires that used it. */
    fun deleteComponent(state: CircuitEditorState, id: String): CircuitEditorState =
        state.copy(
            circuit = state.circuit.withoutComponent(id),
            selected = null,
            undo = state.undo + state.circuit,
            redo = emptyList(),
        )

    /** Removes a wire. */
    fun deleteWire(state: CircuitEditorState, id: String): CircuitEditorState = state.copy(
        circuit = state.circuit.withoutWire(id),
        undo = state.undo + state.circuit,
        redo = emptyList(),
    )

    /** Joins two terminals with a wire. */
    fun addWire(
        state: CircuitEditorState,
        from: TerminalRef,
        to: TerminalRef,
    ): CircuitEditorState {
        if (from == to) return state
        // A wire between two points that are already joined would make a
        // loop of nothing, so it is refused.
        if (state.circuit.wires.any { wire ->
            (wire.from == from && wire.to == to) || (wire.from == to && wire.to == from)
        }) {
            return state
        }
        val wire = CircuitWire(nextId(state.circuit, "w"), from, to)
        return state.copy(
            circuit = state.circuit.withWire(wire),
            undo = state.undo + state.circuit,
            redo = emptyList(),
        )
    }

    /** Changes a part's values. */
    fun updateComponent(
        state: CircuitEditorState,
        id: String,
        change: (CircuitComponent) -> CircuitComponent,
    ): CircuitEditorState {
        val component = state.circuit.component(id) ?: return state
        val updated = change(component)
        return state.copy(
            circuit = state.circuit.copy(
                components = state.circuit.components.map { if (it.id == id) updated else it },
            ),
            undo = state.undo + state.circuit,
            redo = emptyList(),
        )
    }

    /** Puts the last change back. */
    fun undo(state: CircuitEditorState): CircuitEditorState {
        val previous = state.undo.lastOrNull() ?: return state
        return state.copy(
            circuit = previous,
            undo = state.undo.dropLast(1),
            redo = state.redo + state.circuit,
            selected = null,
        )
    }

    /** Puts the last change back again. */
    fun redo(state: CircuitEditorState): CircuitEditorState {
        val next = state.redo.lastOrNull() ?: return state
        return state.copy(
            circuit = next,
            undo = state.undo + state.circuit,
            redo = state.redo.dropLast(1),
            selected = null,
        )
    }

    /** Throws the circuit away. */
    fun clear(state: CircuitEditorState): CircuitEditorState = state.copy(
        circuit = Circuit(),
        selected = null,
        action = EditorAction.Idle,
        undo = state.undo + state.circuit,
        redo = emptyList(),
    )
}

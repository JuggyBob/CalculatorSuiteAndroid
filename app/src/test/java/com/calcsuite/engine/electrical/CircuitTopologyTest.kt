package com.calcsuite.engine.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The topology of a circuit: which terminals are joined to which.
 *
 * The topology is what the solver's equations are built from, so it is
 * checked on its own, on the cases where it is easy to get wrong: a wire
 * that crosses another wire, a switch that is open, and a part that hangs.
 */
class CircuitTopologyTest {

    private fun component(
        id: String,
        kind: ComponentKind,
        switchClosed: Boolean = true,
    ) = CircuitComponent(
        id = id,
        kind = kind,
        label = id,
        resistance = if (kind == ComponentKind.RESISTOR) BigDecimal("100") else null,
        voltage = if (kind == ComponentKind.VOLTAGE_SOURCE) BigDecimal("12") else null,
        switchClosed = switchClosed,
    )

    private fun wire(id: String, from: String, fromIndex: Int, to: String, toIndex: Int) =
        CircuitWire(id, TerminalRef(from, fromIndex), TerminalRef(to, toIndex))

    @Test fun aWireJoinsItsTwoTerminals() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
            ),
            wires = listOf(wire("w1", "R1", 1, "R2", 0)),
        )
        val topology = CircuitTopology(circuit)
        assertTrue(
            "the two terminals are joined",
            topology.areJoined(TerminalRef("R1", 1), TerminalRef("R2", 0)),
        )
        assertFalse(
            "the other terminals are not",
            topology.areJoined(TerminalRef("R1", 0), TerminalRef("R2", 1)),
        )
    }

    @Test fun aChainOfWiresJoinsTheTwoEndsOfEachWire() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
                component("R3", ComponentKind.RESISTOR),
            ),
            wires = listOf(
                wire("w1", "R1", 1, "R2", 0),
                wire("w2", "R2", 1, "R3", 0),
            ),
        )
        val topology = CircuitTopology(circuit)
        assertTrue(
            "the first wire joins its two ends",
            topology.areJoined(TerminalRef("R1", 1), TerminalRef("R2", 0)),
        )
        assertTrue(
            "the second wire joins its two ends",
            topology.areJoined(TerminalRef("R2", 1), TerminalRef("R3", 0)),
        )
        // R2 is between them, and a part is not a wire, so the ends of the
        // chain are not joined to each other.
        assertFalse(
            "the ends of the chain are not joined",
            topology.areJoined(TerminalRef("R1", 1), TerminalRef("R3", 0)),
        )
    }

    @Test fun thePositionOnTheCanvasDoesNotJoinAnything() {
        // Two resistors whose wires cross on the screen, but which are not
        // joined by any wire.
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
            ),
            wires = listOf(
                wire("w1", "R1", 1, "R2", 0),
            ),
        )
        val topology = CircuitTopology(circuit)
        assertFalse(
            "the free ends are not joined",
            topology.areJoined(TerminalRef("R1", 0), TerminalRef("R2", 1)),
        )
        assertEquals("there are three nodes", 3, topology.nodeCount)
    }

    @Test fun aClosedSwitchJoinsItsTerminals() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("S1", ComponentKind.SWITCH, switchClosed = true),
            ),
            wires = listOf(),
        )
        val topology = CircuitTopology(circuit)
        assertTrue(
            "a closed switch is a conductor",
            topology.areJoined(TerminalRef("S1", 0), TerminalRef("S1", 1)),
        )
    }

    @Test fun anOpenSwitchJoinsNothing() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("S1", ComponentKind.SWITCH, switchClosed = false),
            ),
            wires = listOf(),
        )
        val topology = CircuitTopology(circuit)
        assertFalse(
            "an open switch is not a conductor",
            topology.areJoined(TerminalRef("S1", 0), TerminalRef("S1", 1)),
        )
    }

    @Test fun theGroundIsTheReferenceNode() {
        val circuit = Circuit(
            components = listOf(
                component("V1", ComponentKind.VOLTAGE_SOURCE),
                component("GND", ComponentKind.GROUND),
            ),
            wires = listOf(wire("w1", "V1", 1, "GND", 0)),
        )
        val topology = CircuitTopology(circuit)
        assertEquals("the ground is the node zero", 0, topology.groundNode)
        assertEquals(
            "the terminal of the ground is in that node",
            0,
            topology.nodeOf(TerminalRef("GND", 0)),
        )
    }

    @Test fun aPartWithNothingJoinedToItIsFound() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
            ),
            wires = listOf(wire("w1", "R1", 0, "R1", 1)),
        )
        val topology = CircuitTopology(circuit)
        val dangling = topology.danglingTerminals()
        assertEquals("the two ends of R2 hang", 2, dangling.size)
        assertTrue("they are the ends of R2", dangling.all { it.componentId == "R2" })
    }

    @Test fun aWireThatEndsOnNothingIsFound() {
        val circuit = Circuit(
            components = listOf(component("R1", ComponentKind.RESISTOR)),
            wires = listOf(wire("w1", "R1", 0, "R9", 0)),
        )
        val topology = CircuitTopology(circuit)
        assertEquals("the wire is broken", 1, topology.brokenWires().size)
    }

    @Test fun aNodeWithMoreThanTwoTerminalsIsAJunction() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
                component("R3", ComponentKind.RESISTOR),
            ),
            wires = listOf(
                wire("w1", "R1", 1, "R2", 0),
                wire("w2", "R1", 1, "R3", 0),
            ),
        )
        val topology = CircuitTopology(circuit)
        assertEquals("there is one junction", 1, topology.junctionNodes().size)
    }

    @Test fun aNodeWithTwoTerminalsIsNotAJunction() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
            ),
            wires = listOf(wire("w1", "R1", 1, "R2", 0)),
        )
        val topology = CircuitTopology(circuit)
        assertTrue("there is no junction", topology.junctionNodes().isEmpty())
    }

    @Test fun anEmptyCircuitHasNoNodes() {
        val topology = CircuitTopology(Circuit())
        assertEquals("there are no nodes", 0, topology.nodeCount)
        assertNull("there is no ground", topology.groundNode)
    }

    @Test fun theNeighboursOfANodeAreFound() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
            ),
            wires = listOf(wire("w1", "R1", 1, "R2", 0)),
        )
        val topology = CircuitTopology(circuit)
        val node = topology.nodeOf(TerminalRef("R1", 0))!!
        val neighbours = topology.neighbours(node)
        assertTrue("the other end of R1 is a neighbour", neighbours.isNotEmpty())
    }

    @Test fun theTerminalsOfANodeAreFound() {
        val circuit = Circuit(
            components = listOf(
                component("R1", ComponentKind.RESISTOR),
                component("R2", ComponentKind.RESISTOR),
            ),
            wires = listOf(wire("w1", "R1", 1, "R2", 0)),
        )
        val topology = CircuitTopology(circuit)
        val node = topology.nodeOf(TerminalRef("R1", 1))!!
        val terminals = topology.terminalsIn(node)
        assertEquals("two terminals meet there", 2, terminals.size)
        assertNotNull("the component of a terminal is found", topology.componentOf(terminals.first()))
    }
}

package com.calcsuite.engine.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the parts are drawn.
 *
 * The drawing must not change the circuit, so the geometry is checked on its
 * own: the terminals of a part stay with it when it is moved or turned, and
 * a part that is turned still has its terminals in the right places.
 */
class CircuitGeometryTest {

    private fun resistor(id: String, x: Double = 0.0, y: Double = 0.0, rotation: Int = 0) =
        CircuitComponent(
            id = id,
            kind = ComponentKind.RESISTOR,
            label = id,
            x = x,
            y = y,
            rotation = rotation,
            resistance = java.math.BigDecimal("100"),
        )

    @Test fun theTerminalsOfAPartAreAtItsTwoEnds() {
        val component = resistor("R1")
        val first = CircuitGeometry.terminalPosition(component, 0)
        val second = CircuitGeometry.terminalPosition(component, 1)
        assertEquals(
            "the first terminal is on the left",
            component.x - CircuitGeometry.TERMINAL_OFFSET,
            first.first,
            1e-9,
        )
        assertEquals(
            "the first terminal is in the middle",
            component.y,
            first.second,
            1e-9,
        )
        assertEquals(
            "the second terminal is on the right",
            component.x + CircuitGeometry.TERMINAL_OFFSET,
            second.first,
            1e-9,
        )
    }

    @Test fun aPartWithOneTerminalHasItInTheMiddle() {
        val ground = CircuitComponent(id = "GND", kind = ComponentKind.GROUND, label = "GND")
        val point = CircuitGeometry.terminalPosition(ground, 0)
        assertEquals("the terminal is in the middle", ground.x, point.first, 1e-9)
        assertEquals("the terminal is in the middle", ground.y, point.second, 1e-9)
    }

    @Test fun aTurnedPartKeepsItsTerminalsWithIt() {
        val component = resistor("R1", 100.0, 50.0, 1)
        val first = CircuitGeometry.terminalPosition(component, 0)
        val second = CircuitGeometry.terminalPosition(component, 1)
        // A quarter turn puts the terminals above and below the part.
        assertEquals("the first terminal is in the middle", component.x, first.first, 1e-6)
        assertEquals(
            "the first terminal is below",
            component.y - CircuitGeometry.TERMINAL_OFFSET,
            first.second,
            1e-6,
        )
        assertEquals(
            "the second terminal is above",
            component.y + CircuitGeometry.TERMINAL_OFFSET,
            second.second,
            1e-6,
        )
        assertTrue("the two ends are apart", first.second != second.second)
    }

    @Test fun theTerminalOfAPartIsFound() {
        val circuit = Circuit(components = listOf(resistor("R1", 100.0, 0.0)))
        val point = CircuitGeometry.terminalPoint(circuit, TerminalRef("R1", 1))!!
        assertEquals("the terminal is where the part is", 100.0 + CircuitGeometry.TERMINAL_OFFSET, point.first, 1e-9)
    }

    @Test fun aTerminalThatIsNotThereIsNotFound() {
        val circuit = Circuit(components = listOf(resistor("R1")))
        assertNull("there is no such terminal", CircuitGeometry.terminalPoint(circuit, TerminalRef("R9", 0)))
    }

    @Test fun aPartIsFoundUnderThePointItIsDrawnAt() {
        val circuit = Circuit(components = listOf(resistor("R1", 100.0, 50.0)))
        val found = CircuitGeometry.componentAt(circuit, 100.0, 50.0)
        assertNotNull("the part is found", found)
        assertEquals("it is the right part", "R1", found!!.id)
    }

    @Test fun aPartIsNotFoundWhereThereIsNothing() {
        val circuit = Circuit(components = listOf(resistor("R1", 100.0, 50.0)))
        assertNull("there is nothing there", CircuitGeometry.componentAt(circuit, 0.0, 0.0))
    }

    @Test fun thePartOnTopIsFoundFirst() {
        val circuit = Circuit(
            components = listOf(
                resistor("R1", 0.0, 0.0),
                resistor("R2", 0.0, 0.0),
            ),
        )
        val found = CircuitGeometry.componentAt(circuit, 0.0, 0.0)
        assertNotNull("a part is found", found)
        assertEquals("the one on top is found", "R2", found!!.id)
    }

    @Test fun aTerminalIsFoundNearItsPoint() {
        val circuit = Circuit(components = listOf(resistor("R1", 0.0, 0.0)))
        val terminal = CircuitGeometry.terminalAt(
            circuit,
            CircuitGeometry.TERMINAL_OFFSET + 2.0,
            0.0,
            10.0,
        )
        assertNotNull("the terminal is found", terminal)
        assertEquals("it is the right terminal", TerminalRef("R1", 1), terminal)
    }

    @Test fun aTerminalIsNotFoundFarAway() {
        val circuit = Circuit(components = listOf(resistor("R1", 0.0, 0.0)))
        val terminal = CircuitGeometry.terminalAt(circuit, 0.0, 500.0, 10.0)
        assertNull("there is no terminal there", terminal)
    }

    @Test fun aPartIsSnappedOntoTheGrid() {
        assertEquals(100.0, CircuitGeometry.snap(103.0), 0.0)
        assertEquals(100.0, CircuitGeometry.snap(97.0), 0.0)
        assertEquals(110.0, CircuitGeometry.snap(105.0), 0.0)
        assertEquals(100.0, CircuitGeometry.snap(104.0), 0.0)
    }

    @Test fun theBoundsOfAPartAreFound() {
        val component = resistor("R1", 0.0, 0.0)
        val bounds = CircuitGeometry.componentBounds(component)
        assertEquals("the left edge", -CircuitGeometry.COMPONENT_WIDTH / 2, bounds[0], 1e-9)
        assertEquals("the top edge", -CircuitGeometry.COMPONENT_HEIGHT / 2, bounds[1], 1e-9)
        assertEquals("the right edge", CircuitGeometry.COMPONENT_WIDTH / 2, bounds[2], 1e-9)
        assertEquals("the bottom edge", CircuitGeometry.COMPONENT_HEIGHT / 2, bounds[3], 1e-9)
    }

    @Test fun theMovementOfAPartDoesNotChangeItsCircuit() {
        // The same part, moved and turned, is still the same circuit.
        val circuit = CircuitExamples.byId("series")!!.circuit
        val moved = circuit.copy(
            components = circuit.components.map {
                it.copy(x = it.x + 37.0, y = it.y - 11.0, rotation = (it.rotation + 1) % 4)
            },
        )
        val before = DcSolver.solve(circuit)
        val after = DcSolver.solve(moved)
        assertTrue("both are solved", before is com.calcsuite.core.CalcResult.Ok)
        assertTrue("both are solved", after is com.calcsuite.core.CalcResult.Ok)
        assertEquals(
            "the answer is the same",
            (before as com.calcsuite.core.CalcResult.Ok).value.totalSourceCurrent,
            (after as com.calcsuite.core.CalcResult.Ok).value.totalSourceCurrent,
            1e-12,
        )
        assertFalse(
            "the drawing is different",
            circuit.components.map { it.x } == moved.components.map { it.x },
        )
    }
}

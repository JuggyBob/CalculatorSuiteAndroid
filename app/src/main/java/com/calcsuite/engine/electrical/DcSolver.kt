package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * What one component is doing.
 *
 * @param componentId the component this is about.
 * @param voltage the voltage across the component, from the first terminal to
 *   the second.
 * @param current the current through the component, from the first terminal
 *   to the second. A negative value means the current runs the other way.
 * @param power the power the component absorbs, in watts. A negative value
 *   means the component delivers power.
 * @param nodeA the node of the first terminal.
 * @param nodeB the node of the second terminal, when it has one.
 */
data class ComponentResult(
    val componentId: String,
    val voltage: Double,
    val current: Double,
    val power: Double,
    val nodeA: Int?,
    val nodeB: Int?,
)

/**
 * The answer of the DC solver.
 *
 * @param nodeVoltages the voltage of every node, against the ground.
 * @param components what every component is doing.
 * @param sourceCurrents the current each source delivers, in amperes.
 * @param totalSourceCurrent the current the sources deliver together.
 * @param totalPowerSupplied the power the sources deliver together.
 * @param totalPowerDissipated the power the passive parts take in.
 * @param equivalentResistance the resistance the source sees, when there is
 *   exactly one source and the circuit has a well defined answer.
 */
data class DcSolution(
    val nodeVoltages: Map<Int, Double>,
    val components: Map<String, ComponentResult>,
    val sourceCurrents: Map<String, Double>,
    val totalSourceCurrent: Double,
    val totalPowerSupplied: Double,
    val totalPowerDissipated: Double,
    val equivalentResistance: Double?,
    /** The residual of the power balance, which should be zero. */
    val powerBalance: Double,
)

/**
 * The DC solver.
 *
 * The circuit is solved by **modified nodal analysis**: one equation for the
 * current balance at every node, and one equation for the voltage of every
 * ideal voltage source. The unknowns are the node voltages and the currents
 * through the voltage sources.
 *
 * The system is solved by Gaussian elimination with partial pivoting, so a
 * singular system is noticed rather than producing a number full of
 * infinities.
 */
object DcSolver {

    /** The largest circuit the solver will take. */
    const val MAX_COMPONENTS = 200

    /**
     * Solves the circuit.
     *
     * The errors say what is wrong with the circuit in the words of somebody
     * who is looking at it for the first time.
     */
    fun solve(circuit: Circuit): CalcResult<DcSolution> {
        if (circuit.components.size > MAX_COMPONENTS) {
            return err(
                ErrorKind.InvalidInput,
                "This circuit has more than $MAX_COMPONENTS parts. Break it into smaller circuits.",
            )
        }
        val topology = CircuitTopology(circuit)
        val ground = topology.groundNode
            ?: return err(
                ErrorKind.Insufficient,
                "The circuit needs a ground. Add one so that the voltages have a reference.",
            )

        val resistors = circuit.of(ComponentKind.RESISTOR)
        for (resistor in resistors) {
            val value = resistor.resistance?.toDouble()
            if (value == null || !value.isFinite() || value <= 0.0) {
                return err(
                    ErrorKind.InvalidInput,
                    "The resistance of ${resistor.label} must be a positive number.",
                )
            }
        }
        val sources = circuit.of(ComponentKind.VOLTAGE_SOURCE)
        for (source in sources) {
            val value = source.voltage?.toDouble()
            if (value == null || !value.isFinite()) {
                return err(
                    ErrorKind.InvalidInput,
                    "The voltage of ${source.label} must be a number.",
                )
            }
        }
        val currentSources = circuit.of(ComponentKind.CURRENT_SOURCE)
        for (source in currentSources) {
            val value = source.current?.toDouble()
            if (value == null || !value.isFinite()) {
                return err(
                    ErrorKind.InvalidInput,
                    "The current of ${source.label} must be a number.",
                )
            }
        }

        // A voltage source whose two ends are joined cannot hold a voltage.
        for (source in sources) {
            val terminals = source.terminals
            if (terminals.size == 2) {
                val a = topology.nodeOf(terminals[0])
                val b = topology.nodeOf(terminals[1])
                if (a != null && a == b) {
                    val value = source.voltage?.toDouble() ?: 0.0
                    if (kotlin.math.abs(value) > 1e-12) {
                        return err(
                            ErrorKind.Inconsistent,
                            "${source.label} is short-circuited: its two ends are joined by a wire, " +
                                "so it cannot hold ${format(value)} V.",
                        )
                    }
                }
            }
        }

        val nodeCount = topology.nodeCount
        // The ground is node zero and is not an unknown.
        val unknownNodes = (0 until nodeCount).filter { it != ground }
        val nodeIndex = unknownNodes.withIndex().associate { (index, node) -> node to index }
        val sourceCount = sources.size
        val size = unknownNodes.size + sourceCount
        if (size == 0) {
            return err(
                ErrorKind.Insufficient,
                "The circuit has nothing to solve. Add a source and a part to work out.",
            )
        }

        // The matrix and the right-hand side.
        val matrix = Array(size) { DoubleArray(size) }
        val right = DoubleArray(size)

        // The resistors.
        for (resistor in resistors) {
            val terminals = resistor.terminals
            if (terminals.size < 2) continue
            val a = topology.nodeOf(terminals[0]) ?: continue
            val b = topology.nodeOf(terminals[1]) ?: continue
            val conductance = 1.0 / (resistor.resistance!!.toDouble())
            addConductance(matrix, nodeIndex, a, b, conductance)
        }

        // The current sources: the current runs from the first terminal to the
        // second inside the source, so it leaves the first node and enters
        // the second.
        for (source in currentSources) {
            val terminals = source.terminals
            if (terminals.size < 2) continue
            val a = topology.nodeOf(terminals[0]) ?: continue
            val b = topology.nodeOf(terminals[1]) ?: continue
            val value = source.current!!.toDouble()
            // It injects into b and takes out of a.
            if (b != ground) right[nodeIndex[b]!!] += value
            if (a != ground) right[nodeIndex[a]!!] -= value
        }

        // The voltage sources: an unknown current and a constraint.
        for ((index, source) in sources.withIndex()) {
            val column = unknownNodes.size + index
            val terminals = source.terminals
            if (terminals.size < 2) continue
            val a = topology.nodeOf(terminals[0]) ?: continue
            val b = topology.nodeOf(terminals[1]) ?: continue
            if (a != ground) matrix[nodeIndex[a]!!][column] += 1.0
            if (b != ground) matrix[nodeIndex[b]!!][column] -= 1.0
            // The constraint row: v(a) - v(b) = V.
            val row = column
            if (a != ground) matrix[row][nodeIndex[a]!!] += 1.0
            if (b != ground) matrix[row][nodeIndex[b]!!] -= 1.0
            right[row] = source.voltage!!.toDouble()
        }

        val solved = solveLinear(matrix, right)
            ?: return err(
                ErrorKind.Inconsistent,
                "The circuit cannot be solved as it stands. The most usual reasons are " +
                    "that part of it is not joined to the ground, or that two sources " +
                    "fight each other.",
            )

        // The node voltages.
        val nodeVoltages = mutableMapOf<Int, Double>()
        nodeVoltages[ground] = 0.0
        for ((node, index) in nodeIndex) {
            nodeVoltages[node] = solved[index]
        }

        // The component results.
        val results = mutableMapOf<String, ComponentResult>()
        for (component in circuit.components) {
            val terminals = component.terminals
            val nodeA = terminals.firstOrNull()?.let { topology.nodeOf(it) }
            val nodeB = terminals.getOrNull(1)?.let { topology.nodeOf(it) }
            val voltageA = nodeA?.let { nodeVoltages[it] } ?: 0.0
            val voltageB = nodeB?.let { nodeVoltages[it] } ?: 0.0
            val voltage = voltageA - voltageB
            val current = when (component.kind) {
                ComponentKind.RESISTOR -> {
                    val resistance = component.resistance?.toDouble() ?: 0.0
                    if (resistance > 0.0) voltage / resistance else 0.0
                }
                ComponentKind.VOLTAGE_SOURCE -> {
                    val index = sources.indexOfFirst { it.id == component.id }
                    if (index >= 0) solved[unknownNodes.size + index] else 0.0
                }
                ComponentKind.CURRENT_SOURCE -> component.current?.toDouble() ?: 0.0
                ComponentKind.SWITCH -> {
                    if (component.switchClosed) {
                        val resistance = 0.0
                        if (resistance > 0.0) voltage / resistance else 0.0
                    } else {
                        0.0
                    }
                }
                ComponentKind.GROUND -> 0.0
            }
            results[component.id] = ComponentResult(
                componentId = component.id,
                voltage = voltage,
                current = current,
                power = voltage * current,
                nodeA = nodeA,
                nodeB = nodeB,
            )
        }

        // The sources: the current they deliver is the current that leaves
        // their positive terminal.
        val sourceCurrents = mutableMapOf<String, Double>()
        var totalSourceCurrent = 0.0
        for (source in sources) {
            val result = results[source.id]
            val delivered = -(result?.current ?: 0.0)
            sourceCurrents[source.id] = delivered
            totalSourceCurrent += delivered
        }
        for (source in currentSources) {
            val delivered = source.current?.toDouble() ?: 0.0
            sourceCurrents[source.id] = delivered
            totalSourceCurrent += delivered
        }

        var supplied = 0.0
        for (source in sources) {
            val result = results[source.id] ?: continue
            supplied += -(result.voltage * result.current)
        }
        for (source in currentSources) {
            val result = results[source.id] ?: continue
            supplied += -(result.voltage * result.current)
        }
        var dissipated = 0.0
        for (component in circuit.components) {
            if (component.kind == ComponentKind.RESISTOR) {
                dissipated += results[component.id]?.power ?: 0.0
            }
        }

        // The equivalent resistance is only the resistance the source sees
        // when there is one source to ask.
        val equivalent = if (sources.size == 1 && currentSources.isEmpty()) {
            val voltage = sources.first().voltage?.toDouble() ?: 0.0
            val current = totalSourceCurrent
            if (kotlin.math.abs(current) > 1e-15) voltage / current else null
        } else {
            null
        }

        return ok(
            DcSolution(
                nodeVoltages = nodeVoltages,
                components = results,
                sourceCurrents = sourceCurrents,
                totalSourceCurrent = totalSourceCurrent,
                totalPowerSupplied = supplied,
                totalPowerDissipated = dissipated,
                equivalentResistance = equivalent,
                // The power the sources deliver is the power the parts take
                // in, so the balance is the difference between them and it
                // has to come out at nothing.
                powerBalance = supplied - dissipated,
            ),
        )
    }

    /** Adds the conductance of a resistor between two nodes. */
    private fun addConductance(
        matrix: Array<DoubleArray>,
        nodeIndex: Map<Int, Int>,
        a: Int,
        b: Int,
        conductance: Double,
    ) {
        if (a != b) {
            val ia = nodeIndex[a]
            val ib = nodeIndex[b]
            if (ia != null) matrix[ia][ia] += conductance
            if (ib != null) matrix[ib][ib] += conductance
            if (ia != null && ib != null) {
                matrix[ia][ib] -= conductance
                matrix[ib][ia] -= conductance
            }
        }
    }

    /**
     * Solves a linear system by Gaussian elimination with partial pivoting.
     *
     * Returns null when the system is singular, which is what happens when
     * part of the circuit floats or when two sources fight each other.
     */
    fun solveLinear(matrix: Array<DoubleArray>, right: DoubleArray): DoubleArray? {
        val n = right.size
        if (n == 0) return DoubleArray(0)
        val a = Array(n) { matrix[it].copyOf() }
        val b = right.copyOf()
        for (column in 0 until n) {
            // The pivot is the largest entry in the column, so that the
            // arithmetic stays as exact as it can.
            var pivotRow = column
            var best = kotlin.math.abs(a[column][column])
            for (row in column + 1 until n) {
                val candidate = kotlin.math.abs(a[row][column])
                if (candidate > best) {
                    best = candidate
                    pivotRow = row
                }
            }
            if (best < 1e-14) return null
            if (pivotRow != column) {
                val swap = a[column]
                a[column] = a[pivotRow]
                a[pivotRow] = swap
                val swapB = b[column]
                b[column] = b[pivotRow]
                b[pivotRow] = swapB
            }
            for (row in column + 1 until n) {
                val factor = a[row][column] / a[column][column]
                if (factor == 0.0) continue
                for (k in column until n) {
                    a[row][k] -= factor * a[column][k]
                }
                b[row] -= factor * b[column]
            }
        }
        val solution = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = b[row]
            for (k in row + 1 until n) {
                sum -= a[row][k] * solution[k]
            }
            solution[row] = sum / a[row][row]
        }
        for (value in solution) {
            if (!value.isFinite()) return null
        }
        return solution
    }

    /** A number written for a message. */
    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) {
            "${value.toLong()}"
        } else {
            "%.4f".format(value)
        }
}

/** The value of a result as a decimal, for the display and the history. */
fun Double.toDecimal(): BigDecimal = BigDecimal.valueOf(this)

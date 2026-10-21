package com.calcsuite.engine.electrical

/**
 * The electrical topology of a circuit.
 *
 * The nodes are found from the terminals and the wires alone. Two terminals
 * are in the same node when a chain of wires joins them, so a wire that
 * merely crosses another wire on the screen joins nothing.
 *
 * The ground is the reference node: every voltage is measured against it.
 */
class CircuitTopology(private val circuit: Circuit) {

    /** The node each terminal belongs to. */
    private val nodeOfTerminal: Map<TerminalRef, Int> = buildNodes(circuit)

    /** The nodes, in the order they were found. */
    val nodeCount: Int = nodeOfTerminal.values.toSet().size

    /** The reference node, which is the ground. */
    val groundNode: Int? = circuit.components
        .filter { it.kind == ComponentKind.GROUND }
        .flatMap { it.terminals }
        .mapNotNull { nodeOfTerminal[it] }
        .firstOrNull()

    /** The node a terminal is in, or null when the terminal is unknown. */
    fun nodeOf(terminal: TerminalRef): Int? = nodeOfTerminal[terminal]

    /** The terminals in one node. */
    fun terminalsIn(node: Int): List<TerminalRef> =
        nodeOfTerminal.filterValues { it == node }.keys.toList()

    /** The nodes that are joined to a node, in the direction of the current. */
    fun neighbours(node: Int): Set<Int> = buildSet {
        for (terminal in terminalsIn(node)) {
            val component = componentOf(terminal) ?: continue
            for (other in component.terminals) {
                if (other == terminal) continue
                val otherNode = nodeOfTerminal[other] ?: continue
                if (otherNode != node) add(otherNode)
            }
        }
    }

    /** The component a terminal belongs to. */
    fun componentOf(terminal: TerminalRef): CircuitComponent? =
        circuit.component(terminal.componentId)

    /**
     * The groups of terminals that are joined by a wire, for the drawing.
     *
     * A junction is a node where more than two terminals meet; it is drawn
     * with a dot so that the reader can see that the wires are joined.
     */
    fun junctionNodes(): Set<Int> {
        val counts = mutableMapOf<Int, Int>()
        for (node in nodeOfTerminal.values) {
            counts[node] = (counts[node] ?: 0) + 1
        }
        return counts.filterValues { it > 2 }.keys
    }

    /** The terminals that are joined to a node but are not the only ones. */
    fun connectedTerminals(): Set<TerminalRef> = nodeOfTerminal.keys

    /** The terminals that are not joined to anything. */
    fun danglingTerminals(): List<TerminalRef> {
        val used = mutableSetOf<TerminalRef>()
        for (wire in circuit.wires) {
            used.add(wire.from)
            used.add(wire.to)
        }
        return circuit.components
            .flatMap { it.terminals }
            .filterNot { used.contains(it) }
    }

    /** The wires that join a terminal to itself or to a missing terminal. */
    fun brokenWires(): List<CircuitWire> = circuit.wires.filter { wire ->
        val from = nodeOfTerminal[wire.from]
        val to = nodeOfTerminal[wire.to]
        from == null || to == null
    }

    /** True when the two terminals are in the same node. */
    fun areJoined(first: TerminalRef, second: TerminalRef): Boolean {
        val a = nodeOfTerminal[first] ?: return false
        val b = nodeOfTerminal[second] ?: return false
        return a == b
    }

    companion object {
        /**
         * The nodes of a circuit.
         *
         * A union-find over the terminals: each wire joins its two
         * terminals, and the ground is put in the node zero so that the
         * reference is fixed.
         */
        private fun buildNodes(circuit: Circuit): Map<TerminalRef, Int> {
            val parent = mutableMapOf<TerminalRef, TerminalRef>()

            fun find(terminal: TerminalRef): TerminalRef {
                val current = parent.getOrPut(terminal) { terminal }
                if (current == terminal) return current
                val root = find(current)
                parent[terminal] = root
                return root
            }

            fun join(a: TerminalRef, b: TerminalRef) {
                val rootA = find(a)
                val rootB = find(b)
                if (rootA != rootB) parent[rootB] = rootA
            }

            // Every terminal of every component starts on its own.
            for (component in circuit.components) {
                for (terminal in component.terminals) parent[terminal] = terminal
            }
            // Each wire joins its two terminals.
            for (wire in circuit.wires) {
                if (parent.containsKey(wire.from) && parent.containsKey(wire.to)) {
                    join(wire.from, wire.to)
                }
            }
            // A closed switch is a conductor, so it joins its two terminals
            // exactly as a wire would. An open switch joins nothing.
            for (component in circuit.components) {
                if (!component.kind.isSwitch || !component.switchClosed) continue
                val terminals = component.terminals
                if (terminals.size == 2 &&
                    parent.containsKey(terminals[0]) &&
                    parent.containsKey(terminals[1])
                ) {
                    join(terminals[0], terminals[1])
                }
            }
            // The ground is the reference node.
            val grounds = circuit.components
                .filter { it.kind == ComponentKind.GROUND }
                .flatMap { it.terminals }
            if (grounds.isNotEmpty()) {
                val reference = find(grounds.first())
                for (terminal in grounds.drop(1)) join(reference, terminal)
            }

            // The nodes are numbered, and the ground, when there is one, is
            // the node zero.
            val roots = parent.keys.map { find(it) }.distinct()
            val ordered = if (roots.isEmpty()) {
                emptyList()
            } else {
                val groundRoot = grounds.firstOrNull()?.let { find(it) }
                val rest = roots.filter { it != groundRoot }
                (if (groundRoot != null) listOf(groundRoot) else emptyList()) + rest
            }
            val number = ordered.withIndex().associate { (index, root) -> root to index }
            return parent.keys.associateWith { terminal ->
                number[find(terminal)] ?: 0
            }
        }
    }
}

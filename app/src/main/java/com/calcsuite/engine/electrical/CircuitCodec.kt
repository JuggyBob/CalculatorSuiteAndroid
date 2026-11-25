package com.calcsuite.engine.electrical

import java.math.BigDecimal

/**
 * How a circuit is written down so that it can be saved and read back.
 *
 * The format is plain text, one part per line, so that a saved circuit can
 * be kept in the history, sent anywhere as a string, and read back without
 * needing anything from the phone itself. A circuit that cannot be read back
 * is returned as null rather than as a broken circuit, so that a damaged
 * save never turns into a wrong answer.
 */
object CircuitCodec {

    /** The version of the format this writes. */
    const val VERSION = 1

    /** The largest circuit this format will read, matching the solver's limit. */
    const val MAX_COMPONENTS = DcSolver.MAX_COMPONENTS

    /** The largest number of wires this format will read. */
    const val MAX_WIRES = 800

    /** The largest absolute scale of a number this format will read. */
    private const val MAX_ABS_SCALE = 10_000

    /** The largest number of significant digits of a number this format will read. */
    private const val MAX_PRECISION = 10_000

    /** The longest numeric field accepted, long enough for the limits above. */
    private const val MAX_NUMBER_LENGTH = 20_100

    private const val FIELD = '|'
    private const val ESCAPE = '\\'

    /** The circuit, written as text. */
    fun encode(circuit: Circuit): String {
        val lines = mutableListOf("v$VERSION")
        for (component in circuit.components) {
            lines.add(
                listOf(
                    "c",
                    component.id,
                    component.kind.name,
                    component.label,
                    component.x.toString(),
                    component.y.toString(),
                    component.rotation.toString(),
                    component.resistance?.forStorage().orEmpty(),
                    component.voltage?.forStorage().orEmpty(),
                    component.current?.forStorage().orEmpty(),
                    component.switchClosed.toString(),
                    component.powerRating?.forStorage().orEmpty(),
                ).joinToString(FIELD.toString()) { escape(it) },
            )
        }
        for (wire in circuit.wires) {
            lines.add(
                listOf(
                    "w",
                    wire.id,
                    wire.from.componentId,
                    wire.from.index.toString(),
                    wire.to.componentId,
                    wire.to.index.toString(),
                ).joinToString(FIELD.toString()) { escape(it) },
            )
        }
        return lines.joinToString("\n")
    }

    /** The circuit, read back from text. */
    fun decode(text: String): Circuit? {
        if (text.isBlank()) return null
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val first = lines.first().trim()
        if (!first.startsWith("v")) return null
        val version = first.drop(1).toIntOrNull() ?: return null
        if (version > VERSION) return null

        val components = mutableListOf<CircuitComponent>()
        val wires = mutableListOf<CircuitWire>()
        for (line in lines.drop(1)) {
            val parts = split(line)
            when (parts.firstOrNull()) {
                "c" -> {
                    if (components.size >= MAX_COMPONENTS) return null
                    if (parts.size < 12) return null
                    val kind = ComponentKind.entries.firstOrNull { it.name == parts[2] }
                        ?: return null
                    // A blank field is a part that has not been valued yet
                    // and is read back as null. A non-blank field that is not
                    // a sane number is a damaged save: refuse it all rather
                    // than quietly turning the value into null.
                    val resistance = parts[7].decodeNumberField()
                    val voltage = parts[8].decodeNumberField()
                    val current = parts[9].decodeNumberField()
                    val rating = parts[11].decodeNumberField()
                    if (!resistance.valid || !voltage.valid || !current.valid || !rating.valid) {
                        return null
                    }
                    val component = CircuitComponent(
                        id = parts[1],
                        kind = kind,
                        label = parts[3],
                        x = parts[4].toDoubleOrNull() ?: return null,
                        y = parts[5].toDoubleOrNull() ?: return null,
                        rotation = parts[6].toIntOrNull() ?: 0,
                        resistance = resistance.value,
                        voltage = voltage.value,
                        current = current.value,
                        switchClosed = parts[10].toBooleanStrictOrNull() ?: true,
                        powerRating = rating.value,
                    )
                    components.add(component)
                }
                "w" -> {
                    if (wires.size >= MAX_WIRES) return null
                    if (parts.size < 6) return null
                    val from = parts[3].toIntOrNull() ?: return null
                    val to = parts[5].toIntOrNull() ?: return null
                    wires.add(
                        CircuitWire(
                            id = parts[1],
                            from = TerminalRef(parts[2], from),
                            to = TerminalRef(parts[4], to),
                        ),
                    )
                }
                else -> return null
            }
        }
        return Circuit(components = components, wires = wires)
    }

    /** A short description of a circuit, for the history. */
    fun summary(circuit: Circuit): String {
        val resistors = circuit.of(ComponentKind.RESISTOR).size
        val sources = circuit.of(ComponentKind.VOLTAGE_SOURCE).size +
            circuit.of(ComponentKind.CURRENT_SOURCE).size
        val switches = circuit.of(ComponentKind.SWITCH).size
        return buildString {
            append("$resistors resistor")
            if (resistors != 1) append("s")
            append(", $sources source")
            if (sources != 1) append("s")
            if (switches > 0) {
                append(", $switches switch")
                if (switches != 1) append("es")
            }
        }
    }

    // ------------------------------------------------------------ helpers

    /** Escapes the separator so that a label can hold anything. */
    private fun escape(text: String): String =
        text.replace(ESCAPE.toString(), "$ESCAPE$ESCAPE")
            .replace(FIELD.toString(), "$ESCAPE" + "p")
            .replace("\n", "$ESCAPE" + "n")

    /** Undoes the escaping. */
    private fun unescape(text: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character == ESCAPE && index + 1 < text.length) {
                when (text[index + 1]) {
                    'p' -> {
                        out.append(FIELD)
                        index += 2
                    }
                    'n' -> {
                        out.append('\n')
                        index += 2
                    }
                    ESCAPE -> {
                        out.append(ESCAPE)
                        index += 2
                    }
                    else -> {
                        out.append(character)
                        index += 1
                    }
                }
            } else {
                out.append(character)
                index += 1
            }
        }
        return out.toString()
    }

    /** Splits a line into its parts, keeping the separators that were escaped. */
    private fun split(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == ESCAPE && index + 1 < line.length -> {
                    val next = line[index + 1]
                    current.append(
                        when (next) {
                            'p' -> FIELD.toString()
                            'n' -> "\n"
                            ESCAPE -> ESCAPE.toString()
                            else -> next.toString()
                        },
                    )
                    index += 2
                }
                character == FIELD -> {
                    parts.add(current.toString())
                    current.setLength(0)
                    index += 1
                }
                else -> {
                    current.append(character)
                    index += 1
                }
            }
        }
        parts.add(current.toString())
        return parts
    }

    /**
     * A number written for saving.
     *
     * Plain decimal notation keeps ordinary values readable and round-trips
     * them exactly. A value with an extreme scale would take an enormous
     * amount of memory to write out in full, so those values are written in
     * scientific notation instead. The reader rejects them in any case.
     */
    private fun BigDecimal.forStorage(): String =
        if (scale() in -MAX_ABS_SCALE..MAX_ABS_SCALE &&
            precision() <= MAX_PRECISION &&
            toDouble().isFinite()
        ) {
            toPlainString()
        } else {
            toString()
        }

    /**
     * One numeric field of a saved part.
     *
     * A blank field is legitimate: a part can be put on the canvas before it
     * has a value, and the writer stores that as an empty field. Bad text or
     * an out-of-range number is not legitimate and has to fail the whole
     * save, so it is told apart from a blank field here.
     */
    private data class NumberField(val value: BigDecimal?, val valid: Boolean)

    /** Reads one numeric field, never downgrading a hostile value to null. */
    private fun String.decodeNumberField(): NumberField {
        if (isBlank()) return NumberField(null, true)
        if (length > MAX_NUMBER_LENGTH) return NumberField(null, false)
        val value = try {
            BigDecimal(this)
        } catch (_: NumberFormatException) {
            return NumberField(null, false)
        }
        // A tiny string such as 1E+1000000000 would make toPlainString try
        // to write a billion characters, so extreme scales, long numbers and
        // magnitudes the solver cannot use are all refused.
        if (value.scale() !in -MAX_ABS_SCALE..MAX_ABS_SCALE) return NumberField(null, false)
        if (value.precision() > MAX_PRECISION) return NumberField(null, false)
        if (!value.toDouble().isFinite()) return NumberField(null, false)
        return NumberField(value, true)
    }
}

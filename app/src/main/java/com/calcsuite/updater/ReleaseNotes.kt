package com.calcsuite.updater

/**
 * One piece of styled text inside a release note.
 */
data class NoteSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

/**
 * A block of a release note.
 */
sealed interface NoteBlock {
    /** A heading, such as "### Fixed". */
    data class Heading(val level: Int, val spans: List<NoteSpan>) : NoteBlock

    /** A list item. [level] is 0 for the first level of the list. */
    data class Bullet(val level: Int, val spans: List<NoteSpan>) : NoteBlock

    /** A run of ordinary text. */
    data class Paragraph(val spans: List<NoteSpan>) : NoteBlock
}

/**
 * Reads the release notes of a release as a small, safe subset of Markdown.
 *
 * Only the parts a release note actually uses are read: headings, list items,
 * paragraphs, bold, italic, code and links. No HTML is understood and nothing
 * in the notes can run: the result is plain text with a few flags, which the
 * screen draws as ordinary text.
 */
object ReleaseNotes {

    /** The notes of a release, as the blocks the screen draws. */
    fun parse(markdown: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        val paragraph = mutableListOf<String>()
        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString(" ").trim()
            paragraph.clear()
            if (text.isNotEmpty()) blocks.add(NoteBlock.Paragraph(inline(text)))
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }
            val heading = headingLevel(trimmed)
            if (heading != null) {
                flushParagraph()
                blocks.add(
                    NoteBlock.Heading(
                        heading.first,
                        inline(trimmed.drop(heading.second.length).trim()),
                    ),
                )
                continue
            }
            val bullet = bulletOf(line)
            if (bullet != null) {
                flushParagraph()
                blocks.add(NoteBlock.Bullet(bullet.first, inline(bullet.second)))
                continue
            }
            paragraph.add(trimmed)
        }
        flushParagraph()
        return blocks
    }

    /** The level of the heading and the text after the marker, when there is one. */
    private fun headingLevel(line: String): Pair<Int, String>? {
        if (!line.startsWith("#")) return null
        var level = 0
        while (level < line.length && line[level] == '#') level++
        if (level > 6) return null
        if (level >= line.length || line[level] != ' ') return null
        return level to "#".repeat(level)
    }

    /** The level of the list item and its text, when the line is one. */
    private fun bulletOf(line: String): Pair<Int, String>? {
        val trimmed = line.trimStart()
        val indent = (line.length - trimmed.length) / 2
        val marker = when {
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> 2
            else -> {
                val dot = trimmed.indexOf(". ")
                if (dot > 0 && dot <= 3 && trimmed.take(dot).all { it.isDigit() }) dot + 2 else 0
            }
        }
        if (marker == 0) return null
        return indent to trimmed.substring(marker).trim()
    }

    /**
     * Reads the styled text of one line.
     *
     * The markers are removed and the words between them keep their flags. A
     * link is shown as its text, with the address after it, so that no address
     * is silently swallowed.
     */
    fun inline(text: String): List<NoteSpan> {
        val spans = mutableListOf<NoteSpan>()
        val current = StringBuilder()
        var bold = false
        var italic = false
        var code = false
        var index = 0
        fun flush() {
            if (current.isEmpty()) return
            spans.add(NoteSpan(current.toString(), bold, italic, code))
            current.clear()
        }
        while (index < text.length) {
            val rest = text.substring(index)
            when {
                rest.startsWith("**") -> {
                    flush()
                    bold = !bold
                    index += 2
                }
                rest.startsWith("`") -> {
                    flush()
                    code = !code
                    index += 1
                }
                rest.startsWith("*") || rest.startsWith("_") -> {
                    flush()
                    italic = !italic
                    index += 1
                }
                rest.startsWith("[") -> {
                    val close = rest.indexOf(']')
                    val open = if (close >= 0) rest.indexOf('(', close) else -1
                    val end = if (open >= 0) rest.indexOf(')', open) else -1
                    if (close > 0 && open > close && end > open) {
                        flush()
                        val label = rest.substring(1, close)
                        val address = rest.substring(open + 1, end)
                        spans.add(NoteSpan(label, bold, italic, code))
                        if (address.isNotEmpty()) {
                            spans.add(NoteSpan(" ($address)", bold, italic, code))
                        }
                        index += end + 1
                    } else {
                        current.append('[')
                        index++
                    }
                }
                else -> {
                    current.append(text[index])
                    index++
                }
            }
        }
        flush()
        return spans
    }
}

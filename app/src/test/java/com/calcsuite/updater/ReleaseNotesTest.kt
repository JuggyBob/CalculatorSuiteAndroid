package com.calcsuite.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the notes of a release as a small, safe subset of Markdown.
 */
class ReleaseNotesTest {

    @Test fun aHeadingIsRead() {
        val blocks = ReleaseNotes.parse("## What changed")
        assertEquals("there is one block", 1, blocks.size)
        val heading = blocks.first() as NoteBlock.Heading
        assertEquals("the level of the heading", 2, heading.level)
        assertEquals("the text of the heading", "What changed", heading.spans.first().text)
    }

    @Test fun aListItemIsRead() {
        val blocks = ReleaseNotes.parse("- the first thing\n- the second thing")
        assertEquals("there are two items", 2, blocks.size)
        val bullet = blocks.first() as NoteBlock.Bullet
        assertEquals("the text of the item", "the first thing", bullet.spans.first().text)
    }

    @Test fun anOrdinaryLineIsAParagraph() {
        val blocks = ReleaseNotes.parse("Just a sentence.")
        assertTrue("it is a paragraph", blocks.first() is NoteBlock.Paragraph)
    }

    @Test fun wordsOnSeveralLinesMakeOneParagraph() {
        val blocks = ReleaseNotes.parse("The first line\nand the second one")
        assertEquals("there is one paragraph", 1, blocks.size)
        val paragraph = blocks.first() as NoteBlock.Paragraph
        assertEquals("the lines are joined", "The first line and the second one", paragraph.spans.first().text)
    }

    @Test fun boldAndItalicAreRead() {
        val spans = ReleaseNotes.inline("a **bold** and an *italic* word")
        assertTrue("there is a bold part", spans.any { it.bold && it.text == "bold" })
        assertTrue("there is an italic part", spans.any { it.italic && it.text == "italic" })
    }

    @Test fun codeIsRead() {
        val spans = ReleaseNotes.inline("the `zoom` value")
        assertTrue("there is a code part", spans.any { it.code && it.text == "zoom" })
    }

    @Test fun aLinkShowsItsTextAndItsAddress() {
        val spans = ReleaseNotes.inline("see [the release](https://example.invalid/release)")
        assertTrue("the text of the link is shown", spans.any { it.text == "the release" })
        assertTrue("the address is shown as well", spans.any { it.text.contains("example.invalid") })
    }

    @Test fun htmlIsOnlyText() {
        val blocks = ReleaseNotes.parse("<script>alert(1)</script>")
        val text = blocks.flatMap { block ->
            when (block) {
                is NoteBlock.Paragraph -> block.spans.map { it.text }
                is NoteBlock.Heading -> block.spans.map { it.text }
                is NoteBlock.Bullet -> block.spans.map { it.text }
            }
        }.joinToString(" ")
        assertTrue("the text is kept as it is", text.contains("<script>"))
    }

    @Test fun anEmptyNoteHasNoBlocks() {
        assertTrue("there is nothing to show", ReleaseNotes.parse("").isEmpty())
    }
}

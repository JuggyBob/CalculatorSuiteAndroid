package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    /** The exact 49 tool ids the app must ship. */
    private val expectedIds = setOf(
        // Algebra (10)
        "algebra.percentage", "algebra.average", "algebra.proportion", "algebra.ratio",
        "algebra.equations", "algebra.gcf_lcm", "algebra.combinations", "algebra.primes",
        "algebra.random", "algebra.calculus",
        // Geometry (2)
        "geometry.shapes", "geometry.bodies",
        // Converters (23)
        "convert.acceleration", "convert.angle", "convert.area", "convert.cooking",
        "convert.data_storage", "convert.data_transfer", "convert.energy", "convert.force",
        "convert.fuel", "convert.length", "convert.numeric_base", "convert.power",
        "convert.pressure", "convert.ring_size", "convert.roman", "convert.shoe_size",
        "convert.speed", "convert.temperature", "convert.time", "convert.torque",
        "convert.volume", "convert.flow", "convert.weight",
        // Finance (6)
        "finance.currency", "finance.unit_price", "finance.tax", "finance.tip",
        "finance.loan", "finance.interest",
        // Health (3)
        "health.bmi", "health.calories", "health.body_fat",
        // Date & time (3)
        "datetime.age", "datetime.add_subtract", "datetime.interval",
        // Other (2)
        "other.mileage", "other.ohms_law",
    )

    @Test fun registryContainsExactlyTheFortyNineTools() {
        assertEquals(49, ToolRegistry.all.size)
        assertEquals(expectedIds, ToolRegistry.all.map { it.id }.toSet())
    }

    @Test fun idsAreUniqueAndTitlesNonBlank() {
        val ids = ToolRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (tool in ToolRegistry.all) {
            assertTrue("Blank title for ${tool.id}", tool.title.isNotBlank())
            assertTrue("Blank description for ${tool.id}", tool.description.isNotBlank())
            assertTrue("No synonyms for ${tool.id}", tool.synonyms.isNotEmpty())
        }
    }

    @Test fun categoryCounts() {
        assertEquals(10, ToolRegistry.inCategory(ToolCategory.ALGEBRA).size)
        assertEquals(2, ToolRegistry.inCategory(ToolCategory.GEOMETRY).size)
        assertEquals(23, ToolRegistry.inCategory(ToolCategory.CONVERT).size)
        assertEquals(6, ToolRegistry.inCategory(ToolCategory.FINANCE).size)
        assertEquals(3, ToolRegistry.inCategory(ToolCategory.HEALTH).size)
        assertEquals(3, ToolRegistry.inCategory(ToolCategory.DATETIME).size)
        assertEquals(2, ToolRegistry.inCategory(ToolCategory.OTHER).size)
        assertEquals(49, ToolCategory.entries.sumOf { ToolRegistry.inCategory(it).size })
    }

    @Test fun byIdResolvesEveryToolAndRejectsUnknown() {
        for (id in expectedIds) {
            assertEquals(id, ToolRegistry.byId(id)?.id)
        }
        assertNull(ToolRegistry.byId("algebra.nope"))
        assertNull(ToolRegistry.byId(""))
    }

    // ------------------------------------------------------------------ search

    @Test fun shortcutQueriesFindTheRightTool() {
        // The master prompt calls these synonyms out explicitly.
        assertEquals("convert.numeric_base", ToolRegistry.search("hex").first().id)
        assertEquals("convert.fuel", ToolRegistry.search("fuel economy").first().id)
        assertEquals("algebra.percentage", ToolRegistry.search("%").first().id)
        assertEquals("health.bmi", ToolRegistry.search("bmi").first().id)
        assertEquals("algebra.combinations", ToolRegistry.search("ncr").first().id)
        assertEquals("convert.roman", ToolRegistry.search("mcmxciv").first().id)
        assertEquals("other.ohms_law", ToolRegistry.search("ohms law").first().id)
    }

    @Test fun titleMatchesRankAboveSynonymMatches() {
        // "Average" is a title; nothing else ranks higher for the exact query.
        assertEquals("algebra.average", ToolRegistry.search("average").first().id)
        // "Angle" is an exact title; shapes only match via "triangle".
        assertEquals("convert.angle", ToolRegistry.search("angle").first().id)
        assertEquals("convert.time", ToolRegistry.search("time").first().id)
    }

    @Test fun multiWordQueriesRequireEveryWord() {
        val results = ToolRegistry.search("fuel economy")
        assertTrue(results.any { it.id == "convert.fuel" })
        assertTrue(results.any { it.id == "other.mileage" })
        assertTrue("Unrelated combination must not match", ToolRegistry.search("prime hex").isEmpty())
        assertTrue(ToolRegistry.search("zzyzx quux").isEmpty())
    }

    @Test fun blankQueriesReturnNothing() {
        assertTrue(ToolRegistry.search("").isEmpty())
        assertTrue(ToolRegistry.search("   ").isEmpty())
    }

    @Test fun searchIsCaseInsensitive() {
        assertEquals(ToolRegistry.search("hex").map { it.id }, ToolRegistry.search("HEX").map { it.id })
        assertEquals(ToolRegistry.search("Tip").first().id, ToolRegistry.search("tip").first().id)
    }

    @Test fun categoryNameMatchesTools() {
        val finance = ToolRegistry.search("finance").map { it.id }
        assertTrue(finance.contains("finance.loan"))
        assertTrue(finance.contains("finance.tip"))
    }
}

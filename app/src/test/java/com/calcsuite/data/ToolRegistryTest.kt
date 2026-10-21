package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    /**
     * The exact tool ids the app must ship.
     *
     * The counts are derived from this list rather than written down, so adding
     * a tool only means adding its id here.
     */
    private val expectedIds = setOf(
        // Algebra
        "algebra.percentage", "algebra.average", "algebra.proportion", "algebra.ratio",
        "algebra.equations", "algebra.gcf_lcm", "algebra.combinations", "algebra.primes",
        "algebra.random", "algebra.inequalities",
        // Geometry
        "geometry.shapes", "geometry.bodies",
        // Converters
        "convert.acceleration", "convert.angle", "convert.area", "convert.cooking",
        "convert.data_storage", "convert.data_transfer", "convert.energy", "convert.force",
        "convert.fuel", "convert.length", "convert.numeric_base", "convert.power",
        "convert.pressure", "convert.ring_size", "convert.roman", "convert.shoe_size",
        "convert.speed", "convert.temperature", "convert.time", "convert.torque",
        "convert.volume", "convert.flow", "convert.weight",
        // Finance
        "finance.currency", "finance.unit_price", "finance.tax", "finance.tip",
        "finance.loan", "finance.interest",
        // Health
        "health.bmi", "health.calories", "health.body_fat",
        // Date & time
        "datetime.age", "datetime.add_subtract", "datetime.interval",
        // Calculus (14)
        "calculus.derivatives", "calculus.integrals", "calculus.limits",
        "calculus.taylor", "calculus.summations", "calculus.series",
        "calculus.roots", "calculus.extrema", "calculus.graph",
        "calculus.differential_equations", "calculus.multivariable",
        "calculus.fourier", "calculus.complex", "calculus.pde",
        // Advanced mathematics (3)
        "advanced.measure", "advanced.topology", "advanced.functional",
        // Other
        "other.mileage", "other.electrical_circuits",
    )

    @Test fun registryContainsExactlyTheExpectedTools() {
        assertEquals(expectedIds.size, ToolRegistry.all.size)
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
        // Each tool belongs to the category its id names, and every category
        // holds what the catalogue says it holds.
        for (tool in ToolRegistry.all) {
            val expected = ToolCategory.entries.first { it.id == tool.id.substringBefore('.') }
            assertEquals(
                "${tool.id} is in the wrong category",
                expected,
                tool.category,
            )
            assertTrue(
                "${tool.id} is not listed under its category",
                ToolRegistry.inCategory(expected).any { it.id == tool.id },
            )
        }
        // Every tool is counted exactly once.
        assertEquals(
            expectedIds.size,
            ToolCategory.entries.sumOf { ToolRegistry.inCategory(it).size },
        )
    }

    @Test fun byIdResolvesEveryToolAndRejectsUnknown() {
        for (id in expectedIds) {
            assertEquals(id, ToolRegistry.byId(id)?.id)
        }
        assertNull(ToolRegistry.byId("algebra.nope"))
        assertNull(ToolRegistry.byId(""))
    }

    @Test fun theOldCalculusIdStillResolves() {
        // Favourites, the recently-used list and the history keep the id of the
        // tool they were created from, so the pre-category id has to map to
        // something rather than disappear.
        val legacy = ToolRegistry.byId("algebra.calculus")
        assertNotNull("the legacy id must still resolve", legacy)
        assertEquals("calculus.derivatives", legacy?.id)
        // It must not be listed twice: the alias is a lookup, not an entry.
        assertEquals(1, ToolRegistry.all.count { it.id == "calculus.derivatives" })
        assertTrue(ToolRegistry.all.none { it.id == "algebra.calculus" })
    }

    @Test fun theOldOhmsLawIdStillResolves() {
        // The tool was called Ohm's law before it became Electrical Circuits.
        // The history entries made with it have to open the tool that is
        // there now, and the tool must not be listed twice.
        val legacy = ToolRegistry.byId("other.ohms_law")
        assertNotNull("the old id must still resolve", legacy)
        assertEquals("other.electrical_circuits", legacy?.id)
        assertEquals("other.electrical_circuits", ToolRegistry.canonicalId("other.ohms_law"))
        assertEquals(1, ToolRegistry.all.count { it.id == "other.electrical_circuits" })
        assertTrue(ToolRegistry.all.none { it.id == "other.ohms_law" })
    }

    @Test fun theElectricalToolIsInTheOtherCategory() {
        val tool = ToolRegistry.byId("other.electrical_circuits")
        assertNotNull("the tool is there", tool)
        assertEquals("it is in Other", ToolCategory.OTHER, tool?.category)
        assertEquals("it is called Electrical Circuits", "Electrical Circuits", tool?.title)
        assertTrue(
            "it is found under the old name",
            ToolRegistry.search("ohm's law").any { it.id == "other.electrical_circuits" },
        )
    }

    @Test fun theCalculusCategoryHoldsTheCalculusTools() {
        val calculus = ToolRegistry.inCategory(ToolCategory.CALCULUS)
        assertEquals(14, calculus.size)
        assertTrue(calculus.any { it.id == "calculus.differential_equations" })
        assertTrue(calculus.any { it.id == "calculus.multivariable" })
        assertTrue(calculus.any { it.id == "calculus.fourier" })
        assertTrue(calculus.any { it.id == "calculus.complex" })
        assertTrue(calculus.any { it.id == "calculus.pde" })
        // Every tool sits in the category its id names.
        for (tool in ToolRegistry.all) {
            val expected = ToolCategory.entries.first { it.id == tool.id.substringBefore('.') }
            assertEquals("${tool.id} is in the wrong category", expected, tool.category)
        }
    }

    // ------------------------------------------------------------------ search

    @Test fun theAdvancedMathematicsCategoryHoldsItsTools() {
        val advanced = ToolRegistry.inCategory(ToolCategory.ADVANCED)
        assertEquals(3, advanced.size)
        assertTrue(advanced.any { it.id == "advanced.measure" })
        assertTrue(advanced.any { it.id == "advanced.topology" })
        assertTrue(advanced.any { it.id == "advanced.functional" })
        for (tool in advanced) {
            assertTrue("${tool.id} should have synonyms", tool.synonyms.isNotEmpty())
        }
    }

    @Test fun theNewToolsAreFoundBySearch() {
        assertTrue(ToolRegistry.search("fourier").any { it.id == "calculus.fourier" })
        assertTrue(ToolRegistry.search("residue").any { it.id == "calculus.complex" })
        assertTrue(ToolRegistry.search("heat equation").any { it.id == "calculus.pde" })
        assertTrue(ToolRegistry.search("homeomorphism").any { it.id == "advanced.topology" })
        assertTrue(ToolRegistry.search("eigenvalue").any { it.id == "advanced.functional" })
        assertTrue(ToolRegistry.search("countably additive").any { it.id == "advanced.measure" })
        assertTrue(ToolRegistry.search("gradient").any { it.id == "calculus.multivariable" })
    }

    @Test fun shortcutQueriesFindTheRightTool() {
        // The master prompt calls these synonyms out explicitly.
        assertEquals("convert.numeric_base", ToolRegistry.search("hex").first().id)
        assertEquals("convert.fuel", ToolRegistry.search("fuel economy").first().id)
        assertEquals("algebra.percentage", ToolRegistry.search("%").first().id)
        assertEquals("health.bmi", ToolRegistry.search("bmi").first().id)
        assertEquals("algebra.combinations", ToolRegistry.search("ncr").first().id)
        assertEquals("convert.roman", ToolRegistry.search("mcmxciv").first().id)
        assertEquals("other.electrical_circuits", ToolRegistry.search("ohms law").first().id)
        assertEquals("other.electrical_circuits", ToolRegistry.search("ohm law").first().id)
        assertEquals("other.electrical_circuits", ToolRegistry.search("circuit").first().id)
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

package com.calcsuite.engine.algebra.inequality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the inequality parser: what it accepts, and how it complains. */
class InequalityParserTest {

    private fun parse(input: String): ParsedInequality =
        InequalityParser.parse(input, expectedVariables = 1)

    private fun error(input: String): String {
        try {
            InequalityParser.parse(input, expectedVariables = 1)
        } catch (e: InequalityParseException) {
            return e.message ?: "the input was rejected"
        }
        throw AssertionError("expected '$input' to be rejected")
    }

    @Test fun parsesALinearInequality() {
        val p = parse("2x + 3 < 11")
        val relation = p.relation as Rel.Simple
        assertEquals(CompareOp.LT, relation.op)
        assertEquals("x", p.variable)
        assertEquals(1, p.relationCount)
        assertEquals(1, p.chainCount)
    }

    @Test fun parsesEveryComparison() {
        assertEquals(CompareOp.LT, (parse("x < 1").relation as Rel.Simple).op)
        assertEquals(CompareOp.GT, (parse("x > 1").relation as Rel.Simple).op)
        assertEquals(CompareOp.LE, (parse("x <= 1").relation as Rel.Simple).op)
        assertEquals(CompareOp.GE, (parse("x >= 1").relation as Rel.Simple).op)
        assertEquals(CompareOp.EQ, (parse("x = 1").relation as Rel.Simple).op)
    }

    @Test fun parsesAPolynomial() {
        val p = parse("x^2 - 5x + 6 <= 0")
        assertEquals("x", p.variable)
        // The right-hand side folds into the difference, so the tree is small.
        val relation = p.relation as Rel.Simple
        assertNotNull(relation.lhs)
        assertNotNull(relation.rhs)
    }

    @Test fun parsesARationalExpression() {
        val p = parse("(x - 2)/(x + 1) > 0")
        assertEquals("x", p.variable)
    }

    @Test fun parsesAnAbsoluteValue() {
        val p = parse("|2x - 3| <= 5")
        val relation = p.relation as Rel.Simple
        val call = relation.lhs as IExpr.Call
        assertEquals("abs", call.name)
    }

    @Test fun parsesACompoundChainAsTwoRelations() {
        val p = parse("-2 < 3x + 1 <= 10")
        assertEquals(2, p.relationCount)
        assertEquals(1, p.chainCount)
        val relation = p.relation
        assertTrue("a chain becomes an And", relation is Rel.And)
        assertEquals(2, (relation as Rel.And).parts.size)
    }

    @Test fun parsesASystemOfSeveralLines() {
        val p = InequalityParser.parse("x > 5\nx < 0", expectedVariables = 1)
        assertEquals(2, p.chainCount)
        assertEquals(2, p.relationCount)
    }

    @Test fun parsesTheWordAndAsASeparator() {
        val p = InequalityParser.parse("x > 1 and x < 5", expectedVariables = 1)
        assertEquals(2, p.relationCount)
    }

    @Test fun parsesTheWordOrAsASeparator() {
        val p = InequalityParser.parse("x < -3 or x > 3", expectedVariables = 1)
        assertEquals(2, p.relationCount)
        assertTrue("or becomes an Or", p.relation is Rel.Or)
    }

    @Test fun parsesTwoVariables() {
        val p = InequalityParser.parse("y > 2x - 1", expectedVariables = 2)
        assertEquals(listOf("x", "y"), p.variables)
    }

    @Test fun parsesFunctionsAndConstants() {
        assertNotNull(parse("sqrt(x) < 2"))
        assertNotNull(parse("log(x) > 1"))
        assertNotNull(parse("x + pi > 3"))
        assertNotNull(parse("x + e > 3"))
    }

    @Test fun parsesImplicitMultiplication() {
        assertNotNull(parse("2x < 4"))
        assertNotNull(parse("3(x + 1) < 4"))
        assertNotNull(parse("2pi < x"))
    }

    @Test fun parsesDecimalNumbers() {
        assertNotNull(parse("0.5x < 2"))
        assertNotNull(parse("1.5e2 > x"))
    }

    // ------------------------------------------------------------- rejections

    @Test fun rejectsAMissingComparison() {
        assertTrue(error("2x + 3").contains("comparison"))
    }

    @Test fun rejectsAnUnclosedBracket() {
        assertTrue(error("(2x + 3 < 11").isNotEmpty())
    }

    @Test fun rejectsAnUnknownFunction() {
        assertTrue(error("frobnicate(x) < 2").contains("Unknown function"))
    }

    @Test fun rejectsAnUnknownCharacter() {
        assertTrue(error("2x + 3 # 11").isNotEmpty())
    }

    @Test fun rejectsAnUnclosedAbsoluteValue() {
        assertTrue(error("|2x - 3 <= 5").contains("closing |"))
    }

    @Test fun rejectsTooManyVariables() {
        try {
            InequalityParser.parse("x + y + z < 1", expectedVariables = 1)
            throw AssertionError("expected the input to be rejected")
        } catch (e: InequalityParseException) {
            assertTrue(e.message ?: "", e.message?.contains("one variable") == true)
        }
    }

    @Test fun rejectsAnInputWithNoVariable() {
        assertTrue(error("2 < 5").contains("variable"))
    }

    @Test fun anEmptyInputIsRejected() {
        assertTrue(error("   ").isNotEmpty())
    }
}

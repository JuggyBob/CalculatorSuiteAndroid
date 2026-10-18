package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.WeightFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

private fun assertClose(expected: String, actual: BigDecimal, tol: String) {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

class WeightFamilyTest {

    @Test
    fun onePoundIsZeroPointFourFiveThreeFiveNineTwoThreeSevenKilograms() {
        assertExact("0.45359237", WeightFamily.convert(BigDecimal.ONE, "lb", "kg").valueOrNull()!!)
    }

    @Test
    fun oneKilogramIsOneThousandGrams() {
        assertExact("1000", WeightFamily.convert(BigDecimal.ONE, "kg", "g").valueOrNull()!!)
    }

    @Test
    fun oneGramIsOneThousandMilligrams() {
        assertExact("1000", WeightFamily.convert(BigDecimal.ONE, "g", "mg").valueOrNull()!!)
    }

    @Test
    fun oneOunceIsTwentyEightPointThreeFourNineFiveTwoThreeOneTwoFiveGrams() {
        assertExact("28.349523125", WeightFamily.convert(BigDecimal.ONE, "oz", "g").valueOrNull()!!)
    }

    @Test
    fun onePoundIsSixteenOunces() {
        assertExact("16", WeightFamily.convert(BigDecimal.ONE, "lb", "oz").valueOrNull()!!)
    }

    @Test
    fun oneStoneIsSixPointThreeFiveZeroTwoNineThreeOneEightKilograms() {
        assertExact("6.35029318", WeightFamily.convert(BigDecimal.ONE, "st", "kg").valueOrNull()!!)
    }

    @Test
    fun oneStoneIsFourteenPounds() {
        assertExact("14", WeightFamily.convert(BigDecimal.ONE, "st", "lb").valueOrNull()!!)
    }

    @Test
    fun oneTonneIsOneThousandKilograms() {
        assertExact("1000", WeightFamily.convert(BigDecimal.ONE, "t", "kg").valueOrNull()!!)
    }

    @Test
    fun oneShortTonIsTwoThousandPounds() {
        assertExact("2000", WeightFamily.convert(BigDecimal.ONE, "ton_us", "lb").valueOrNull()!!)
    }

    @Test
    fun oneShortTonIsNineZeroSevenPointOneEightFourSevenFourKilograms() {
        assertExact("907.18474", WeightFamily.convert(BigDecimal.ONE, "ton_us", "kg").valueOrNull()!!)
    }

    @Test
    fun oneLongTonIsTwentyTwoHundredAndFortyPounds() {
        assertExact("2240", WeightFamily.convert(BigDecimal.ONE, "ton_uk", "lb").valueOrNull()!!)
    }

    @Test
    fun oneLongTonIsTenSixteenPointZeroFourSixNineZeroEightEightKilograms() {
        assertExact("1016.0469088", WeightFamily.convert(BigDecimal.ONE, "ton_uk", "kg").valueOrNull()!!)
    }

    @Test
    fun seventyKilogramsInPounds() {
        assertClose("154.3235835", WeightFamily.convert(BigDecimal("70"), "kg", "lb").valueOrNull()!!, "1e-6")
    }

    @Test
    fun elevenStoneInKilograms() {
        assertExact("69.85322498", WeightFamily.convert(BigDecimal("11"), "st", "kg").valueOrNull()!!)
    }

    @Test
    fun oneKilogramInOunces() {
        assertClose("35.27396195", WeightFamily.convert(BigDecimal.ONE, "kg", "oz").valueOrNull()!!, "1e-6")
    }

    @Test
    fun massIsNotForce() {
        val notes = WeightFamily.notes.joinToString(" ")
        assertTrue("notes should contain the mass-not-force disclaimer", notes.contains("mass, not force"))
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(WeightFamily.convert(BigDecimal.ONE, "kg", "carat") is com.calcsuite.core.CalcResult.Err)
    }
}

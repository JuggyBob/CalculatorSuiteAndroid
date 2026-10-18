package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.DataStorageFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

class DataStorageFamilyTest {

    @Test
    fun oneByteIsEightBits() {
        assertExact("8", DataStorageFamily.convert(BigDecimal.ONE, "byte", "bit").valueOrNull()!!)
    }

    @Test
    fun oneMegabyteIsOneMillionBytes() {
        assertExact("1000000", DataStorageFamily.convert(BigDecimal.ONE, "MB", "byte").valueOrNull()!!)
    }

    @Test
    fun oneMebibyteIsOneMillionFortyEightThousandFiveHundredSeventySixBytes() {
        assertExact("1048576", DataStorageFamily.convert(BigDecimal.ONE, "MiB", "byte").valueOrNull()!!)
    }

    @Test
    fun oneGibibyteIsOneThousandAndTwentyFourMebibytes() {
        assertExact("1024", DataStorageFamily.convert(BigDecimal.ONE, "GiB", "MiB").valueOrNull()!!)
    }

    @Test
    fun oneKilobyteIsOneThousandBytes() {
        assertExact("1000", DataStorageFamily.convert(BigDecimal.ONE, "kB", "byte").valueOrNull()!!)
    }

    @Test
    fun oneKibibyteIsOneThousandAndTwentyFourBytes() {
        assertExact("1024", DataStorageFamily.convert(BigDecimal.ONE, "KiB", "byte").valueOrNull()!!)
    }

    @Test
    fun oneKilobitIsOneThousandBits() {
        assertExact("1000", DataStorageFamily.convert(BigDecimal.ONE, "kbit", "bit").valueOrNull()!!)
    }

    @Test
    fun oneMebibyteIsEightMillionThreeHundredEightyEightThousandSixHundredAndEightBits() {
        assertExact("8388608", DataStorageFamily.convert(BigDecimal.ONE, "MiB", "bit").valueOrNull()!!)
    }

    @Test
    fun oneGigabyteIsOneThousandMegabytes() {
        assertExact("1000", DataStorageFamily.convert(BigDecimal.ONE, "GB", "MB").valueOrNull()!!)
    }

    @Test
    fun oneGibibyteIsOnePointZeroSevenThreeSevenFourOneEightTwoFourGigabytes() {
        assertExact("1.073741824", DataStorageFamily.convert(BigDecimal.ONE, "GiB", "GB").valueOrNull()!!)
    }

    @Test
    fun onePebibyteInPetabytes() {
        assertExact("1.125899906842624", DataStorageFamily.convert(BigDecimal.ONE, "PiB", "PB").valueOrNull()!!)
    }

    @Test
    fun oneTerabyteIsOneThousandGigabytes() {
        assertExact("1000", DataStorageFamily.convert(BigDecimal.ONE, "TB", "GB").valueOrNull()!!)
    }

    @Test
    fun oneHundredMegabytesIsEightHundredMegabits() {
        assertExact("800", DataStorageFamily.convert(BigDecimal("100"), "MB", "Mbit").valueOrNull()!!)
    }

    @Test
    fun megabytesToMebibytesIsNotOne() {
        val result = DataStorageFamily.convert(BigDecimal.ONE, "MB", "MiB").valueOrNull()!!
        assertTrue((result - BigDecimal("0.95367431640625")).abs() < BigDecimal("1e-15"))
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(DataStorageFamily.convert(BigDecimal.ONE, "MB", "wibble") is com.calcsuite.core.CalcResult.Err)
    }
}

package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10
import java.math.BigDecimal

private fun bitsPerByte(multiplier: BigDecimal): BigDecimal = bd("8").multiply(multiplier)

/**
 * Digital data storage. Base unit: bit.
 *
 * Decimal (SI) prefixes step by 1000 (kB = 1000 B), binary (IEC) prefixes step
 * by 1024 (KiB = 1024 B). One byte is exactly 8 bits everywhere: 1 MB is
 * 8 000 000 bits and 1 MiB is 8 388 608 bits.
 */
val DataStorageFamily = UnitFamily(
    id = "data_storage",
    name = "Data storage",
    baseUnitId = "bit",
    units = listOf(
        // Bits
        linear("bit", "Bits", "bit", bd("1"), "Bits"),
        linear("kbit", "Kilobits", "kbit", pow10(3), "Bits"),
        linear("Mbit", "Megabits", "Mbit", pow10(6), "Bits"),
        linear("Gbit", "Gigabits", "Gbit", pow10(9), "Bits"),
        // Decimal byte units (SI)
        linear("byte", "Bytes", "B", bd("8"), "Decimal (SI)"),
        linear("kB", "Kilobytes", "kB", bitsPerByte(pow10(3)), "Decimal (SI)"),
        linear("MB", "Megabytes", "MB", bitsPerByte(pow10(6)), "Decimal (SI)"),
        linear("GB", "Gigabytes", "GB", bitsPerByte(pow10(9)), "Decimal (SI)"),
        linear("TB", "Terabytes", "TB", bitsPerByte(pow10(12)), "Decimal (SI)"),
        linear("PB", "Petabytes", "PB", bitsPerByte(pow10(15)), "Decimal (SI)"),
        // Binary byte units (IEC)
        linear("KiB", "Kibibytes", "KiB", bd("8192"), "Binary (IEC)"),
        linear("MiB", "Mebibytes", "MiB", bd("8388608"), "Binary (IEC)"),
        linear("GiB", "Gibibytes", "GiB", bd("8589934592"), "Binary (IEC)"),
        linear("TiB", "Tebibytes", "TiB", bd("8796093022208"), "Binary (IEC)"),
        linear("PiB", "Pebibytes", "PiB", bd("9007199254740992"), "Binary (IEC)"),
    ),
    notes = listOf(
        "1 byte = 8 bits exactly: 1 MB = 8 000 000 bits and 1 MiB = 8 388 608 bits.",
        "Decimal prefixes (kB, MB, GB) step by 1000 as required by SI; binary prefixes (KiB, MiB, GiB) step by 1024.",
        "Operating systems often show binary sizes with decimal labels — check which convention a figure follows.",
    ),
)

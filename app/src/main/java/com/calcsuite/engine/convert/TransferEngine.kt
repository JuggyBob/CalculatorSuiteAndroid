package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * Data transfer rates. Base unit: bit per second.
 *
 * Network rates use **decimal** prefixes (1 Mbit/s = 10⁶ bit/s,
 * 1 MB/s = 8×10⁶ bit/s) following the SI / ITU convention for link speeds,
 * while file sizes shown by operating systems are often **binary**
 * (1 MiB = 2²⁰ B). The [binaryFileSize] flags let callers choose how the
 * ambiguous labels kB/MB/GB/TB on a file size are read; the unambiguous
 * KiB/MiB/GiB/TiB units are always binary.
 *
 * ## Theoretical vs actual
 * Every duration computed here is a *theoretical* transfer time: it ignores
 * protocol overhead (Ethernet, IP, TCP/TLS headers), ACK latency, contention
 * and storage speed. Real throughput is typically 60–90% of the link rate,
 * so treat the result as a best case.
 */
object TransferEngine {

    /** One rate unit. `bitsPerSecond` is the factor to the base unit. */
    data class TransferUnit(
        val id: String,
        val label: String,
        val symbol: String,
        val group: String,
        val bitsPerSecond: BigDecimal,
    )

    private val units = listOf(
        TransferUnit("bit_s", "Bits per second", "bit/s", "Bits", bd("1")),
        TransferUnit("kbit_s", "Kilobits per second", "kbit/s", "Bits", bd("1000")),
        TransferUnit("Mbit_s", "Megabits per second", "Mbit/s", "Bits", bd("1000000")),
        TransferUnit("Gbit_s", "Gigabits per second", "Gbit/s", "Bits", bd("1000000000")),
        TransferUnit("B_s", "Bytes per second", "B/s", "Bytes (decimal)", bd("8")),
        TransferUnit("kB_s", "Kilobytes per second", "kB/s", "Bytes (decimal)", bd("8000")),
        TransferUnit("MB_s", "Megabytes per second", "MB/s", "Bytes (decimal)", bd("8000000")),
        TransferUnit("GB_s", "Gigabytes per second", "GB/s", "Bytes (decimal)", bd("8000000000")),
        TransferUnit("KiB_s", "Kibibytes per second", "KiB/s", "Bytes (binary)", bd("8192")),
        TransferUnit("MiB_s", "Mebibytes per second", "MiB/s", "Bytes (binary)", bd("8388608")),
        TransferUnit("GiB_s", "Gibibytes per second", "GiB/s", "Bytes (binary)", bd("8589934592")),
    )

    val allUnits: List<TransferUnit> get() = units

    /**
     * The same units as a linear family so the standard converter screen can be
     * reused; the base unit is bit/s.
     */
    val family: UnitFamily = UnitFamily(
        id = "data_transfer",
        name = "Data transfer",
        baseUnitId = "bit_s",
        units = units.map { linear(it.id, it.label, it.symbol, it.bitsPerSecond, it.group) },
    )

    val notes: List<String> = listOf(
        "Transfer times are theoretical: headers, latency and congestion reduce real throughput to roughly 60–90% of the link rate.",
        "Network speeds use decimal prefixes (1 Mbit/s = 1 000 000 bit/s); 8 bits = 1 byte, so 100 Mbit/s ≈ 12.5 MB/s.",
        "File sizes labelled MB by operating systems are often MiB (binary, ×1024). Choose the interpretation that matches your file.",
    )

    fun unit(id: String): TransferUnit? = units.firstOrNull { it.id == id }

    private val megabyte = BigDecimal(1_000_000)
    private val mebibyte = BigDecimal(1_048_576)
    private val gibibyte = BigDecimal(1_073_741_824)
    private val kibibyte = BigDecimal(1024)
    private val gigabyte = BigDecimal(1_000_000_000)
    private val terabyte = BigDecimal(1_000_000_000_000)
    private val tebibyte = BigDecimal("1099511627776")

    /** Byte size of one display unit, honouring the decimal/binary choice. */
    private fun byteUnitSize(unitId: String, binaryFileSize: Boolean): BigDecimal? = when (unitId) {
        "B" -> BigDecimal.ONE
        "kB" -> if (binaryFileSize) kibibyte else BigDecimal(1000)
        "MB" -> if (binaryFileSize) mebibyte else megabyte
        "GB" -> if (binaryFileSize) gibibyte else gigabyte
        "TB" -> if (binaryFileSize) tebibyte else terabyte
        "KiB" -> kibibyte
        "MiB" -> mebibyte
        "GiB" -> gibibyte
        "TiB" -> tebibyte
        else -> null
    }

    /** Unit ids accepted by the file-size helpers, for pickers. */
    val fileSizeUnits: List<Pair<String, String>> = listOf(
        "B" to "Bytes (B)",
        "kB" to "kB / KiB",
        "MB" to "MB / MiB",
        "GB" to "GB / GiB",
        "TB" to "TB / TiB",
        "KiB" to "KiB (binary)",
        "MiB" to "MiB (binary)",
        "GiB" to "GiB (binary)",
        "TiB" to "TiB (binary)",
    )

    /** Converts a rate between two rate units. */
    fun convert(value: BigDecimal, fromId: String, toId: String): CalcResult<BigDecimal> {
        val from = unit(fromId) ?: return err(ErrorKind.InvalidInput, "Unknown source unit")
        val to = unit(toId) ?: return err(ErrorKind.InvalidInput, "Unknown target unit")
        if (value.signum() < 0) return err(ErrorKind.Domain, "Transfer rate must not be negative")
        val bits = value.multiply(from.bitsPerSecond, UnitFamily.CTX)
        return ok(bits.divide(to.bitsPerSecond, UnitFamily.CTX))
    }

    /** Bytes represented by [size] in a file-size unit. */
    fun sizeInBytes(size: BigDecimal, sizeUnitId: String, binaryFileSize: Boolean = true): CalcResult<BigDecimal> {
        if (size.signum() < 0) return err(ErrorKind.Domain, "File size must not be negative")
        val unitSize = byteUnitSize(sizeUnitId, binaryFileSize)
            ?: return err(ErrorKind.InvalidInput, "Unknown file size unit")
        return ok(size.multiply(unitSize, UnitFamily.CTX))
    }

    /** Seconds to move [sizeBytes] over a link of [rateBitsPerSecond]. */
    fun transferTime(sizeBytes: BigDecimal, rateBitsPerSecond: BigDecimal): CalcResult<BigDecimal> {
        if (sizeBytes.signum() < 0) return err(ErrorKind.Domain, "File size must not be negative")
        if (rateBitsPerSecond.signum() <= 0) {
            return err(ErrorKind.DivisionByZero, "Transfer rate must be greater than zero")
        }
        val bits = sizeBytes.multiply(bd("8"), UnitFamily.CTX)
        return ok(bits.divide(rateBitsPerSecond, UnitFamily.CTX))
    }

    /**
     * Seconds to move a file of [size] [sizeUnitId] over [rate] [rateUnitId].
     * Set [binaryFileSize] to false to read kB/MB/GB/TB as powers of 1000.
     */
    fun transferTime(
        size: BigDecimal,
        sizeUnitId: String,
        rate: BigDecimal,
        rateUnitId: String,
        binaryFileSize: Boolean = true,
    ): CalcResult<BigDecimal> {
        val sizeBytes = when (val sizeResult = sizeInBytes(size, sizeUnitId, binaryFileSize)) {
            is CalcResult.Ok -> sizeResult.value
            is CalcResult.Err -> return err(sizeResult.error.kind, sizeResult.error.message)
        }
        val rateUnit = unit(rateUnitId) ?: return err(ErrorKind.InvalidInput, "Unknown rate unit")
        return transferTime(sizeBytes, rate.multiply(rateUnit.bitsPerSecond, UnitFamily.CTX))
    }

    /** Bits per second needed to move [sizeBytes] in [durationSeconds]. */
    fun requiredRate(sizeBytes: BigDecimal, durationSeconds: BigDecimal): CalcResult<BigDecimal> {
        if (sizeBytes.signum() < 0) return err(ErrorKind.Domain, "File size must not be negative")
        if (durationSeconds.signum() <= 0) {
            return err(ErrorKind.DivisionByZero, "Duration must be greater than zero")
        }
        return ok(sizeBytes.multiply(bd("8"), UnitFamily.CTX).divide(durationSeconds, UnitFamily.CTX))
    }

    /** Bits per second needed to move [size] [sizeUnitId] in [durationSeconds]. */
    fun requiredRate(
        size: BigDecimal,
        sizeUnitId: String,
        durationSeconds: BigDecimal,
        binaryFileSize: Boolean = true,
    ): CalcResult<BigDecimal> {
        return when (val sizeResult = sizeInBytes(size, sizeUnitId, binaryFileSize)) {
            is CalcResult.Ok -> requiredRate(sizeResult.value, durationSeconds)
            is CalcResult.Err -> err(sizeResult.error.kind, sizeResult.error.message)
        }
    }

    /** Bytes transferred at [rateBitsPerSecond] for [durationSeconds]. */
    fun sizeFor(rateBitsPerSecond: BigDecimal, durationSeconds: BigDecimal): CalcResult<BigDecimal> {
        if (rateBitsPerSecond.signum() < 0) {
            return err(ErrorKind.Domain, "Transfer rate must not be negative")
        }
        if (durationSeconds.signum() < 0) return err(ErrorKind.Domain, "Duration must not be negative")
        return ok(
            rateBitsPerSecond.multiply(durationSeconds, UnitFamily.CTX)
                .divide(bd("8"), UnitFamily.CTX),
        )
    }

    /**
     * Size transferred at [rate] [rateUnitId] in [durationSeconds], returned in
     * [displayUnitId] using the same decimal/binary interpretation.
     */
    fun sizeFor(
        rate: BigDecimal,
        rateUnitId: String,
        durationSeconds: BigDecimal,
        displayUnitId: String,
        binaryFileSize: Boolean = true,
    ): CalcResult<BigDecimal> {
        val rateUnit = unit(rateUnitId) ?: return err(ErrorKind.InvalidInput, "Unknown rate unit")
        val bytes = when (val bytesResult = sizeFor(rate.multiply(rateUnit.bitsPerSecond, UnitFamily.CTX), durationSeconds)) {
            is CalcResult.Ok -> bytesResult.value
            is CalcResult.Err -> return err(bytesResult.error.kind, bytesResult.error.message)
        }
        val displaySize = byteUnitSize(displayUnitId, binaryFileSize)
            ?: return err(ErrorKind.InvalidInput, "Unknown file size unit")
        return ok(bytes.divide(displaySize, UnitFamily.CTX))
    }
}

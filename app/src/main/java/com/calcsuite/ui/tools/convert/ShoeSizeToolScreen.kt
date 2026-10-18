package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.engine.convert.ShoeSizeEngine
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.tools.ToolArgs

private const val MODE_SIZE = "size"
private const val MODE_LENGTH = "length"

@Composable
fun ShoeSizeToolScreen(args: ToolArgs) {
    val copy = rememberCopyAction()

    var audience by rememberSaveable { mutableStateOf(ShoeSizeEngine.Audience.MEN) }
    var mode by rememberSaveable { mutableStateOf(MODE_SIZE) }
    var system by rememberSaveable { mutableStateOf("eu") }
    var sizeText by rememberSaveable { mutableStateOf("42") }
    var lengthText by rememberSaveable { mutableStateOf("265") }

    val attempt: CalcResult<ShoeSizeEngine.ShoeMatch>? = when {
        mode == MODE_SIZE && sizeText.isBlank() -> null
        mode == MODE_LENGTH && lengthText.isBlank() -> null
        mode == MODE_SIZE -> ShoeSizeEngine.convert(audience, system, sizeText)
        else -> Num.parseOrNull(lengthText)?.let { ShoeSizeEngine.byFootLength(audience, it) }
            ?: err(ErrorKind.InvalidInput, "Enter a foot length in millimetres")
    }
    val match = (attempt as? CalcResult.Ok)?.value
    val error = (attempt as? CalcResult.Err)?.error?.message

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = listOf(
                ShoeSizeEngine.Audience.MEN to "Men",
                ShoeSizeEngine.Audience.WOMEN to "Women",
                ShoeSizeEngine.Audience.CHILDREN to "Children",
            ),
            selected = audience,
            onSelect = { audience = it },
            label = "Audience",
        )
        SegmentedChoice(
            options = listOf(MODE_SIZE to "Known size", MODE_LENGTH to "Foot length"),
            selected = mode,
            onSelect = { mode = it },
            label = "Find by",
        )

        if (mode == MODE_SIZE) {
            SegmentedChoice(
                options = ShoeSizeEngine.systems.map { it.first to it.second },
                selected = system,
                onSelect = { system = it },
                label = "Size system",
            )
            NumberField(
                label = "${audience.label} shoe size",
                value = sizeText,
                onValueChange = { sizeText = it },
                unit = ShoeSizeEngine.systems.first { it.first == system }.second,
                error = error,
            )
        } else {
            NumberField(
                label = "Foot length",
                value = lengthText,
                onValueChange = { lengthText = it },
                unit = "mm",
                error = error,
            )
        }

        if (match != null) {
            val row = match.row
            SectionCard(title = "${audience.label} ${if (mode == MODE_SIZE) "size" else "foot length"} conversion") {
                ResultRow(label = "EU", value = row.eu, emphasis = true)
                ResultRow(label = "US", value = row.us, emphasis = true)
                ResultRow(label = "UK", value = row.uk, emphasis = true)
                ResultRow(label = "JP", value = row.jp, unit = "cm", emphasis = true)
                ResultRow(label = "Foot length", value = row.footLengthMm.toString(), unit = "mm")
            }
            match.note?.let { InfoNote(it) }

            ResultCard(
                label = "Shoe size",
                value = "EU ${row.eu} · US ${row.us} · UK ${row.uk}",
                caption = "foot length ${row.footLengthMm} mm · JP ${row.jp} cm",
                onCopy = { copy(it, "Shoe size") },
                copyValue = "EU ${row.eu}, US ${row.us}, UK ${row.uk}, JP ${row.jp}",
                onSave = {
                    args.onRecord(
                        if (mode == MODE_SIZE) {
                            "${audience.label} $system ${sizeText.trim()}"
                        } else {
                            "${audience.label} foot ${lengthText.trim()} mm"
                        },
                        "EU ${row.eu} / US ${row.us} / UK ${row.uk} / JP ${row.jp}",
                    )
                },
            )
        }

        val table = ShoeSizeEngine.rowsFor(audience)
        ResultRow(
            label = "Listed range (${audience.label.lowercase()})",
            value = "EU ${table.first().eu}–${table.last().eu}",
        )
        ResultRow(
            label = "Foot lengths",
            value = "${table.first().footLengthMm}–${table.last().footLengthMm} mm",
        )

        InfoNote(
            "Shoe lasts differ between brands and models and published tables disagree by half a size at " +
                "several rows, so treat these equivalents as approximations.",
        )
        InfoNote(
            "Measure a foot in the evening while standing, heel against a wall, and add 5–10 mm of toe room; " +
                "for children re-measure every few months.",
        )
    }
}

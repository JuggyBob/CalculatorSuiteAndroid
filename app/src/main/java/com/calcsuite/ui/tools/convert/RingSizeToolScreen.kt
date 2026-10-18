package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.convert.RingSizeEngine
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun RingSizeToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var system by rememberSaveable { mutableStateOf(RingSizeEngine.SYSTEM_CIRCUMFERENCE) }
    var input by rememberSaveable { mutableStateOf("54.4") }

    val options = remember {
        RingSizeEngine.systems.map { Option(it.first, it.second) }
    }
    val parseAttempt = if (input.isBlank()) null else RingSizeEngine.convert(system, input)
    val match = (parseAttempt as? CalcResult.Ok)?.value
    val error = when {
        input.isBlank() -> null
        parseAttempt is CalcResult.Err -> parseAttempt.error.message
        else -> null
    }
    val placeholder = when (system) {
        RingSizeEngine.SYSTEM_CIRCUMFERENCE, RingSizeEngine.SYSTEM_DIAMETER -> "54.4"
        RingSizeEngine.SYSTEM_UK -> "N½"
        else -> "7"
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SelectField(
            label = "Input",
            options = options,
            selected = system,
            onSelect = { system = it },
        )
        PlainField(
            label = when (system) {
                RingSizeEngine.SYSTEM_CIRCUMFERENCE -> "Finger circumference"
                RingSizeEngine.SYSTEM_DIAMETER -> "Ring inner diameter"
                else -> "Ring size"
            },
            value = input,
            onValueChange = { input = it },
            error = error,
            placeholder = placeholder,
        )

        if (match != null) {
            val row = match.row
            SectionCard(title = if (match.nearest) "Closest chart row" else "Chart row") {
                ResultRow(label = "US", value = row.us, emphasis = true)
                ResultRow(label = "UK", value = row.uk, emphasis = true)
                ResultRow(label = "EU", value = row.eu, emphasis = true)
                ResultRow(label = "JP", value = row.jp, emphasis = true)
                ResultRow(
                    label = "Inner diameter",
                    value = fmt.num(row.diameterMm, precision = maxOf(fmt.precision, 8)),
                    unit = "mm",
                )
                ResultRow(
                    label = "Circumference",
                    value = fmt.num(row.circumferenceMm, precision = maxOf(fmt.precision, 8)),
                    unit = "mm",
                )
            }
            match.note?.let { InfoNote(it) }

            ResultCard(
                label = "Ring size",
                value = "US ${row.us} · UK ${row.uk} · EU ${row.eu}",
                caption = "diameter ${fmt.num(row.diameterMm, precision = 8)} mm · " +
                    "circumference ${fmt.num(row.circumferenceMm, precision = 8)} mm",
                onCopy = { copy(it, "Ring size") },
                copyValue = "US ${row.us}, UK ${row.uk}, EU ${row.eu}, JP ${row.jp}",
                onSave = {
                    args.onRecord(
                        "${if (input.isNotBlank()) input.trim() else system} ($system)",
                        "US ${row.us} / UK ${row.uk} / EU ${row.eu} / JP ${row.jp}",
                    )
                },
            )
        }

        InfoNote(
            "Measure the inside diameter of a ring that fits (in mm) or wrap a strip of paper around the " +
                "finger, mark it and measure the length for the circumference.",
        )
        InfoNote(
            "The chart is a published jeweller reference table and the cross-system equivalents are " +
                "approximate: fingers change size with temperature and time of day, bands wider than 6 mm " +
                "fit tighter, and the published circumferences are rounded (π × diameter may differ slightly).",
        )
    }
}

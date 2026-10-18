package com.calcsuite.ui.tools.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.finance.Measure
import com.calcsuite.engine.finance.ProductInput
import com.calcsuite.engine.finance.PriceUnit
import com.calcsuite.engine.finance.UnitPriceEngine
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.Pill
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/** One editable product line, serialised for [rememberSaveable]. */
private data class ProductForm(
    val name: String = "",
    val price: String = "",
    val quantity: String = "1",
    val unitId: String = PriceUnit.KILOGRAM.id,
    val discount: String = "",
) {
    fun encode(): String = listOf(name, price, quantity, unitId, discount)
        .joinToString("\u001F") { it.replace('\u001F', ' ') }

    companion object {
        fun decode(raw: String): ProductForm {
            val parts = raw.split('\u001F')
            return ProductForm(
                name = parts.getOrElse(0) { "" },
                price = parts.getOrElse(1) { "" },
                quantity = parts.getOrElse(2) { "1" },
                unitId = parts.getOrElse(3) { PriceUnit.KILOGRAM.id },
                discount = parts.getOrElse(4) { "" },
            )
        }
    }
}

private val ProductListSaver = listSaver<List<ProductForm>, String>(
    save = { forms -> forms.map { it.encode() } },
    restore = { encoded -> encoded.map { ProductForm.decode(it) } },
)

/** A parsed product or the field-level reason it could not be used yet. */
private data class PreparedProduct(
    val input: ProductInput?,
    val priceError: String? = null,
    val quantityError: String? = null,
    val discountError: String? = null,
)

private fun defaultUnitFor(dimension: Measure): PriceUnit = when (dimension) {
    Measure.MASS -> PriceUnit.KILOGRAM
    Measure.VOLUME -> PriceUnit.LITRE
    Measure.COUNT -> PriceUnit.ITEM
    Measure.LENGTH -> PriceUnit.METRE
}

/**
 * "Which pack is actually cheaper?" — several products are normalised to the
 * same comparison unit (per kg, per L, per item, per m) and ranked by unit
 * price. The cheapest flag always follows the normalised price, never the
 * package price.
 */
@Composable
fun UnitPriceToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var dimensionId by rememberSaveable { mutableStateOf(Measure.MASS.id) }
    var forms by rememberSaveable(stateSaver = ProductListSaver) {
        mutableStateOf(listOf(ProductForm(), ProductForm()))
    }
    val dimension = Measure.byId(dimensionId) ?: Measure.MASS

    val prepared: List<PreparedProduct> = forms.mapIndexed { index, form ->
        val unit = PriceUnit.byId(form.unitId) ?: PriceUnit.KILOGRAM
        val price = Num.parseOrNull(form.price)
        val quantity = Num.parseOrNull(form.quantity)
        val discountText = form.discount.trim()
        val discount = if (discountText.isEmpty()) BigDecimal.ZERO else Num.parseOrNull(discountText)
        val priceError = if (form.price.isNotBlank() && price == null) "Enter a valid price" else null
        val quantityError = if (form.quantity.isNotBlank() && quantity == null) "Enter a valid quantity" else null
        val discountError = if (discountText.isNotEmpty() && discount == null) "Enter a valid percentage" else null
        val input = if (
            price != null && quantity != null && discount != null &&
            priceError == null && quantityError == null && discountError == null
        ) {
            ProductInput(
                name = form.name.trim().ifBlank { "Product ${index + 1}" },
                price = price,
                quantity = quantity,
                unit = unit,
                discountPercent = discount,
            )
        } else {
            null
        }
        PreparedProduct(input, priceError, quantityError, discountError)
    }
    val inputs = prepared.mapNotNull { it.input }

    val comparison = if (inputs.isEmpty()) null else UnitPriceEngine.compare(dimension, inputs)
    val unitOptions = remember {
        PriceUnit.entries.map { unit ->
            Option(unit.id, unit.label, group = unit.measure.label, symbol = unit.symbol)
        }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = Measure.entries.map { it.id to it.label },
            selected = dimensionId,
            onSelect = { dimensionId = it },
            label = "Compare prices by",
        )
        InfoNote(
            "Only rank products with the same dimension: mass (per kg), volume (per L), " +
                "count (per item) or length (per m).",
        )

        forms.forEachIndexed { index, form ->
            SectionCard(title = "Product ${index + 1}") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlainField(
                        label = "Name (optional)",
                        value = form.name,
                        onValueChange = { value ->
                            forms = forms.toMutableList().also { it[index] = form.copy(name = value) }
                        },
                        placeholder = "e.g. Flour",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            forms = forms.filterIndexed { i, _ -> i != index }
                        },
                        enabled = forms.size > 1,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove product ${index + 1}",
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NumberField(
                        label = "Price",
                        value = form.price,
                        onValueChange = { value ->
                            forms = forms.toMutableList().also { it[index] = form.copy(price = value) }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = "2.50",
                        error = prepared[index].priceError,
                    )
                    NumberField(
                        label = "Quantity",
                        value = form.quantity,
                        onValueChange = { value ->
                            forms = forms.toMutableList().also { it[index] = form.copy(quantity = value) }
                        },
                        modifier = Modifier.weight(1f),
                        unit = PriceUnit.byId(form.unitId)?.symbol,
                        error = prepared[index].quantityError,
                    )
                }
                SelectField(
                    label = "Unit",
                    options = unitOptions,
                    selected = form.unitId,
                    onSelect = { value ->
                        forms = forms.toMutableList().also { it[index] = form.copy(unitId = value) }
                    },
                )
                NumberField(
                    label = "Discount (optional)",
                    value = form.discount,
                    onValueChange = { value ->
                        forms = forms.toMutableList().also { it[index] = form.copy(discount = value) }
                    },
                    unit = "%",
                    placeholder = "0",
                    error = prepared[index].discountError,
                )
            }
        }

        OutlinedButton(
            onClick = {
                forms = forms + ProductForm(unitId = defaultUnitFor(dimension).id)
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add product")
        }

        when (comparison) {
            null -> InfoNote("Enter a price and quantity for at least one product.")

            is CalcResult.Err -> ErrorNotice(comparison.error.message)

            is CalcResult.Ok -> {
                val value = comparison.value
                val best = value.best
                if (best != null) {
                    ResultCard(
                        label = "Best value",
                        value = fmt.num(best.unitPrice, precision = maxOf(fmt.precision, 8)),
                        unit = "per ${value.dimension.baseSymbol}",
                        caption = "${best.product.name}: ${fmt.num(best.effectivePrice)} for " +
                            "${fmt.num(best.product.quantity)} ${best.product.unit.symbol} " +
                            "= ${fmt.num(best.quantityInBase, precision = maxOf(fmt.precision, 8))} " +
                            "${value.dimension.baseSymbol}",
                        onCopy = { copy(it, "Unit price") },
                        copyValue = Num.formatForCopy(best.unitPrice),
                        onSave = {
                            args.onRecord(
                                "${value.rows.size} products compared by ${value.dimension.label.lowercase()}",
                                "Best: ${best.product.name} at " +
                                    "${fmt.num(best.unitPrice, precision = maxOf(fmt.precision, 8))} " +
                                    "per ${value.dimension.baseSymbol}",
                            )
                        },
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(
                        text = "Cheapest: ${best?.product?.name ?: "—"}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (value.rows.size > 1) {
                        Pill("${value.rows.size} comparable products")
                    }
                }

                value.rows.forEach { row ->
                    ResultRow(
                        label = "#${row.rank} ${row.product.name} — " +
                            "${fmt.num(row.effectivePrice)} for " +
                            "${fmt.num(row.product.quantity)} ${row.product.unit.symbol}" +
                            (if (row.product.discountPercent.signum() > 0) {
                                " (after ${fmt.num(row.product.discountPercent)}% discount)"
                            } else {
                                ""
                            }),
                        value = fmt.num(row.unitPrice, precision = maxOf(fmt.precision, 8)),
                        unit = "per ${value.dimension.baseSymbol}",
                        emphasis = row.bestValue,
                        onCopy = { copy(Num.formatForCopy(row.unitPrice), "Unit price") },
                    )
                }

                if (value.rejected.isNotEmpty()) {
                    value.rejected.forEach { rejected ->
                        ErrorNotice(rejected.message)
                    }
                }
            }
        }

        InfoNote(UnitPriceEngine.CROSS_DIMENSION_NOTE)
        InfoNote(
            "Discounts are applied to the price before normalising, and the ranking always uses " +
                "the normalised unit price — a bigger pack is not automatically cheaper.",
        )
    }
}

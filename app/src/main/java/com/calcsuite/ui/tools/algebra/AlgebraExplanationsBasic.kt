package com.calcsuite.ui.tools.algebra

import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.algebra.ProportionMode
import com.calcsuite.engine.algebra.ProportionSlot

/**
 * The explanations for the four elementary algebra tools.
 *
 * A screen passes the mode that is in use and the working that its calculation
 * already produced. Nothing is invented here: the figures belong to the
 * caller, and the prose describes the arithmetic that the app really does.
 * The sections follow the same order as the other tools.
 */
object AlgebraExplanationsBasic {

    private val percentageScope =
        "Eight percentage forms are supported: percent of, find whole, what %, " +
            "change, original, successive, points and relative difference."
    private val averageScope =
        "Five averages are supported: mean, weighted mean, median, mode and " +
            "geometric mean."
    private val proportionScope =
        "Two forms are supported: direct a/b = c/d and inverse a·b = c·d. " +
            "Exactly one of the four slots may be unknown."
    private val ratioScope =
        "Six ratio forms are supported: simplify, scale, split total, unknown " +
            "part, combine and compare."

    // --------------------------------------------------------------- percentage

    /** The explanation for the percentage mode named [modeName]. */
    fun percentageExplanation(
        modeName: String?,
        steps: List<String>? = null,
    ): Explanation = when (modeName ?: "") {
        "PercentOf" -> Explanation(
            definition = "A percentage is a fraction with a denominator of 100. " +
                "Taking p% of a value means splitting the value into 100 equal " +
                "parts and taking p of them.",
            formula = "amount = value × percentage ÷ 100",
            symbols = listOf(
                "value" to "the amount the percentage is taken from",
                "p" to "the percentage, e.g. 15 for 15%",
                "amount" to "the part of the value that p% picks out",
            ),
            conditions = listOf(
                "Both numbers must be entered; the tool reads a percentage as a " +
                    "plain number, so 15 means 15%.",
                "The value may be zero or negative: multiplying by a percentage " +
                    "is defined for every value.",
            ),
            method = "The percentage is divided by 100 and multiplied by the " +
                "value at the app's evaluation precision. The working shows " +
                "the fraction and the multiplication.",
            steps = steps.orEmpty(),
            interpretation = "The result is the part of the value that the " +
                "percentage names. For example, 15% of 200 is 30.",
            verification = "The screen shows the percentage written as a " +
                "fraction and the base value next to the result, so the " +
                "multiplication can be checked directly.",
            limitations = percentageScope +
                " This mode takes a plain percentage of one value; phrases such " +
                "as '15% more than' belong to Change or Original.",
        )

        "FindWhole" -> Explanation(
            definition = "Finding the whole reverses a percentage: the part is " +
                "known together with the percentage it represents, and the " +
                "amount it came from is recovered.",
            formula = "whole = part × 100 ÷ percentage",
            symbols = listOf(
                "part" to "the known portion of the whole",
                "p" to "the percentage that the part represents",
                "whole" to "the amount being recovered",
            ),
            conditions = listOf(
                "The percentage must not be zero. The screen reports 'A part " +
                    "can never be 0% of a value, so the whole is undefined' " +
                    "and gives no result.",
                "Both the part and the percentage must be entered as numbers.",
            ),
            method = "The screen rearranges part = whole × p ÷ 100 into whole " +
                "= part × 100 ÷ p, and divides at the app's evaluation precision.",
            steps = steps.orEmpty(),
            interpretation = "The result is the amount that the part came " +
                "from. A part of 50 at 25% comes from a whole of 200.",
            verification = "The screen shows the percentage as a fraction; " +
                "multiplying the recovered whole by that fraction returns the " +
                "part that was entered.",
            limitations = percentageScope +
                " A zero percentage has no whole and is rejected. Negative " +
                "values are not rejected by this mode, so their sign is left " +
                "to the reader to interpret.",
        )

        "WhatPercent" -> Explanation(
            definition = "This mode expresses one value as a share of a " +
                "reference value, scaled to 100.",
            formula = "percentage = part ÷ whole × 100",
            symbols = listOf(
                "part" to "the amount being expressed",
                "whole" to "the reference value",
                "percentage" to "the share measured in parts per hundred",
            ),
            conditions = listOf(
                "The reference value must not be zero. PercentageEngine " +
                    "reports 'The reference value is zero, so a percentage of " +
                    "it is undefined'.",
                "Both the part and the whole must be entered as numbers.",
            ),
            method = "PercentageEngine divides the part by the reference value " +
                "and multiplies by 100 at the app's evaluation precision.",
            steps = steps.orEmpty(),
            interpretation = "The result says how many parts per hundred the " +
                "part is of the whole. It can exceed 100% when the part is " +
                "larger than the reference.",
            verification = "The screen shows the ratio part ÷ whole and the " +
                "fraction part / whole beside the result, so the scaling by " +
                "100 can be checked.",
            limitations = percentageScope +
                " The reference value must not be zero, and the entered numbers " +
                "are taken as exact — no allowance is made for their rounding.",
        )

        "Change" -> Explanation(
            definition = "A percentage change compares the difference between " +
                "two values with the starting value, and scales that " +
                "comparison to 100.",
            formula = "change % = (end − start) ÷ start × 100",
            symbols = listOf(
                "start" to "the value before the change",
                "end" to "the value after the change",
                "change %" to "the signed change measured against the start",
            ),
            conditions = listOf(
                "The starting value must not be zero. PercentageEngine reports " +
                    "'The starting value is zero, so a percentage change is " +
                    "undefined'.",
                "Both the starting and the final value must be entered.",
            ),
            method = "PercentageEngine.percentIncrease subtracts the starting " +
                "value from the final value, divides by the starting value and " +
                "multiplies by 100. A positive answer is an increase and a " +
                "negative answer a decrease.",
            steps = steps.orEmpty(),
            interpretation = "The size of the answer is the change measured " +
                "against the starting value, not against the final value. " +
                "The screen also names the direction.",
            verification = "The screen shows the absolute change and the ratio " +
                "end ÷ start; taking that ratio away from 1 and multiplying by " +
                "100 reproduces the percentage.",
            limitations = percentageScope +
                " The starting value must not be zero. Negative values are " +
                "allowed, but the sign of the answer then depends on both " +
                "signs and needs care when read.",
        )

        "Original" -> Explanation(
            definition = "This mode undoes a percentage increase or decrease " +
                "to find the value that was there before the change.",
            formula = "original = final ÷ (1 ± percentage ÷ 100)",
            symbols = listOf(
                "final" to "the value after the change",
                "p" to "the percentage increase or decrease",
                "original" to "the value before the change",
            ),
            conditions = listOf(
                "A decrease of exactly 100% cannot be undone. " +
                    "PercentageEngine.recoverOriginal reports 'A decrease of " +
                    "exactly 100% leaves nothing, so the original value cannot " +
                    "be recovered'.",
                "The toggle chooses the multiplier: plus for an increase, " +
                    "minus for a decrease.",
            ),
            method = "The screen builds the multiplier 1 + p ÷ 100 or " +
                "1 − p ÷ 100 from the toggle, then divides the final value by " +
                "it at the app's evaluation precision.",
            steps = steps.orEmpty(),
            interpretation = "The result is the value that, after the chosen " +
                "percentage change, gives the final value that was entered.",
            verification = "The screen's 'Check: original × multiplier' row " +
                "multiplies the recovered value back by the multiplier, so it " +
                "can be compared with the final value directly.",
            limitations = percentageScope +
                " Only one percentage change is undone; a chain of changes " +
                "belongs to Successive. A 100% decrease has no recoverable " +
                "original.",
        )

        "Successive" -> Explanation(
            definition = "Successive percentage changes are applied one after " +
                "another, each to the result of the previous one, so they " +
                "compound rather than add.",
            formula = "final = base × Π(1 + changeᵢ ÷ 100)",
            symbols = listOf(
                "base" to "the starting value",
                "changeᵢ" to "each signed percentage, positive up and negative down",
                "final" to "the value after every change",
            ),
            conditions = listOf(
                "At least one change is needed. PercentageEngine reports " +
                    "'Enter at least one percentage change' for an empty list.",
                "The starting value must not be zero. PercentageEngine reports " +
                    "'The starting value is zero, so a percentage change is " +
                    "undefined'.",
                "The changes are entered as numbers separated by commas, " +
                    "semicolons or new lines.",
            ),
            method = "PercentageEngine multiplies the base by each factor " +
                "1 + changeᵢ ÷ 100 in turn, and reports the final value and the " +
                "effective percentage change.",
            steps = steps.orEmpty(),
            interpretation = "Each change acts on the running value, so the " +
                "effective change is not the sum of the percentages. The " +
                "screen shows that simple sum alongside it for comparison.",
            verification = "The screen lists the running value after each " +
                "change, and sets the simple sum of the percentages against " +
                "the effective change, so the compounding can be seen.",
            limitations = percentageScope +
                " The list is parsed as plain numbers only, and the starting " +
                "value must not be zero. Negative percentages are decreases.",
        )

        "Points" -> Explanation(
            definition = "A percentage point is the unit for comparing two " +
                "percentages directly: it is the plain difference between them.",
            formula = "percentage points = p₁ − p₂",
            symbols = listOf(
                "p₁" to "the later percentage",
                "p₂" to "the earlier percentage",
                "percentage points" to "the direct gap between the two",
            ),
            conditions = listOf(
                "Both entries must be percentages; the screen reads them as " +
                    "plain numbers.",
                "The earlier percentage may be zero. The screen then shows a " +
                    "relative change of 0% rather than dividing by zero.",
            ),
            method = "PercentageEngine.percentagePoints subtracts the second " +
                "percentage from the first. The screen also shows the reversed " +
                "difference and the relative change from the earlier percentage.",
            steps = steps.orEmpty(),
            interpretation = "Going from 5% to 7% is +2 percentage points. " +
                "The relative change divides that gap by the earlier " +
                "percentage, so the same gap is +40% there.",
            verification = "The screen shows the reversed difference and the " +
                "relative change, so the direction and the size of the gap can " +
                "be cross-checked.",
            limitations = percentageScope +
                " This compares percentages with each other, not a change in " +
                "a value; use Change for that.",
        )

        "Relative" -> Explanation(
            definition = "The relative percent difference measures the gap " +
                "between two values against their mean, so swapping the two " +
                "values gives the same answer.",
            formula = "relative difference = |a − b| ÷ |(a + b) ÷ 2| × 100",
            symbols = listOf(
                "a" to "the first value",
                "b" to "the second value",
                "mean" to "(a + b) ÷ 2, the reference for the gap",
            ),
            conditions = listOf(
                "The two values must not both be zero. PercentageEngine " +
                    "reports 'Both values are zero, so the relative difference " +
                    "is undefined'.",
                "The values must not cancel each other. PercentageEngine " +
                    "reports 'The two values cancel out, so the relative " +
                    "difference is undefined' when their mean is zero.",
            ),
            method = "PercentageEngine takes the absolute difference, divides " +
                "it by the absolute value of the mean and multiplies by 100.",
            steps = steps.orEmpty(),
            interpretation = "The result is the gap as a percentage of the " +
                "average of the two values. It has no direction, because the " +
                "mean treats both values alike.",
            verification = "The screen shows the absolute difference and the " +
                "mean used as the reference, so the division can be checked.",
            limitations = percentageScope +
                " A pair that is both zero or cancels exactly is rejected, " +
                "and no numerical tolerance is applied to that check.",
        )

        else -> Explanation(
            definition = "Percentage arithmetic writes a share as a number " +
                "out of 100, either taking a percentage of a value or " +
                "comparing two values on that scale.",
            formula = "percentage = part ÷ whole × 100",
            symbols = listOf(
                "part" to "the amount being compared",
                "whole" to "the reference amount",
                "percentage" to "the share measured in parts per hundred",
            ),
            conditions = listOf(
                "The percentage tool must be given one of its eight modes; " +
                    "an unrecognised mode falls back to this general form.",
            ),
            method = "The screen divides the part by the reference and " +
                "multiplies by 100 at the app's evaluation precision.",
            steps = steps.orEmpty(),
            interpretation = "The result states the share in parts per " +
                "hundred, whatever values were entered.",
            verification = "The working supplied by the screen is shown " +
                "unchanged, so the arithmetic can be checked from it.",
            limitations = percentageScope +
                " An unrecognised mode is shown with this general formula " +
                "rather than a mode-specific one.",
        )
    }

    // ----------------------------------------------------------------- average

    /** The explanation for the average mode named [modeName]. */
    fun averageExplanation(
        modeName: String?,
        steps: List<String>? = null,
    ): Explanation = when (modeName ?: "") {
        "Mean" -> Explanation(
            definition = "The arithmetic mean is the total of a data set " +
                "shared equally between its values.",
            formula = "mean = Σxᵢ ÷ n",
            symbols = listOf(
                "Σxᵢ" to "sum of all values",
                "n" to "how many values there are",
                "mean" to "the evenly shared value",
            ),
            conditions = listOf(
                "At least one number is needed. AverageEngine.parseDataset " +
                    "reports 'Enter at least one number' for an empty set.",
                "Numbers are separated by commas, semicolons or new lines; a " +
                    "space inside a number is digit grouping (1 234 = 1234).",
            ),
            method = "AverageEngine adds every value and divides the sum by " +
                "the count at the app's evaluation precision.",
            steps = steps.orEmpty(),
            interpretation = "The mean is the value each member would have if " +
                "the total were spread evenly. A single extreme value can " +
                "move it a long way.",
            verification = "The screen's Data summary shows the sum and the " +
                "count that produced the mean, so the division can be checked.",
            limitations = averageScope +
                " This mode reads a flat list of numbers; it does not read " +
                "expressions and it does not group the data.",
        )

        "Weighted" -> Explanation(
            definition = "A weighted mean lets each value contribute by a " +
                "stated weight instead of contributing equally.",
            formula = "weighted mean = Σ(xᵢ·wᵢ) ÷ Σwᵢ",
            symbols = listOf(
                "xᵢ" to "a value in the data set",
                "wᵢ" to "the weight belonging to that value",
                "Σwᵢ" to "the total weight",
            ),
            conditions = listOf(
                "There must be one weight per value. The screen reports how " +
                    "many values and how many weights were given when the two " +
                    "counts differ.",
                "Weights must not be negative. AverageEngine.weightedMean " +
                    "reports 'Weights must not be negative' and names the value.",
                "The weights must not add to zero. AverageEngine reports " +
                    "'The weights add up to zero, so the weighted mean is " +
                    "undefined'.",
            ),
            method = "AverageEngine multiplies each value by its weight, adds " +
                "the products and divides by the total weight. The screen " +
                "lists each product on its own row.",
            steps = steps.orEmpty(),
            interpretation = "A value with a larger weight pulls the mean " +
                "towards it. A zero weight leaves that value out of the " +
                "average altogether.",
            verification = "The screen shows every value × weight and the " +
                "total weight next to the result, so the division can be " +
                "checked term by term.",
            limitations = averageScope +
                " Weights must be non-negative and must not all be zero. " +
                "Only the division by their sum is used to scale them.",
        )

        "Median" -> Explanation(
            definition = "The median is the middle value of a data set once " +
                "it has been sorted.",
            formula = "median = middle value of the sorted list (mean of the two middles when n is even)",
            symbols = listOf(
                "sorted list" to "the values placed in order",
                "n" to "how many values there are",
                "two middles" to "the pair averaged when n is even",
            ),
            conditions = listOf(
                "At least one number is needed; an empty set is rejected.",
                "For an even count the two middle values are averaged, and " +
                    "the screen names the pair that it used.",
            ),
            method = "AverageEngine sorts the values and takes the middle " +
                "one, or the mean of the two middle values when the count is " +
                "even.",
            steps = steps.orEmpty(),
            interpretation = "Half the values lie at or below the median and " +
                "half at or above it, so an extreme value does not move it " +
                "as much as it moves the mean.",
            verification = "The screen shows the sorted list and says whether " +
                "it took the middle value or averaged the two middles.",
            limitations = averageScope +
                " The median uses only the order of the values: it ignores " +
                "how far from the middle the other values lie.",
        )

        "Mode" -> Explanation(
            definition = "The mode is the value that occurs most often in a " +
                "data set.",
            formula = "mode = value(s) with the highest frequency",
            symbols = listOf(
                "frequency" to "how often a value occurs",
                "mode(s)" to "every value with the greatest frequency",
            ),
            conditions = listOf(
                "At least one number is needed; an empty set is rejected.",
                "When every value occurs exactly once, the screen reports " +
                    "'Every value appears exactly once, so there is no mode'. " +
                    "Several values may share the highest frequency.",
            ),
            method = "AverageEngine counts how often each value appears and " +
                "returns every value with the greatest count.",
            steps = steps.orEmpty(),
            interpretation = "One mode names the most common value. Several " +
                "modes mean the data has more than one peak, and no mode " +
                "means that all values are different.",
            verification = "The working says how often the winning value or " +
                "values appear, and the Data summary repeats the modes.",
            limitations = averageScope +
                " Values are compared exactly after parsing, so numbers " +
                "that are close but not equal are not grouped together.",
        )

        "Geometric" -> Explanation(
            definition = "The geometric mean is the nth root of the product " +
                "of n positive values. It is used for growth rates and ratios.",
            formula = "geometric mean = (x₁ × x₂ × … × xₙ)^(1/n)",
            symbols = listOf(
                "xᵢ" to "a strictly positive value",
                "n" to "how many values there are",
                "product" to "all values multiplied together",
            ),
            conditions = listOf(
                "Every value must be strictly positive. AverageEngine." +
                    "geometricMean reports 'The geometric mean needs positive " +
                    "values only' and names the offending value for zero or " +
                    "negative entries.",
                "At least one number is needed; an empty set is rejected.",
            ),
            method = "AverageEngine multiplies the values and takes the nth " +
                "root. A single value is returned unchanged.",
            steps = steps.orEmpty(),
            interpretation = "The geometric mean is the typical multiplicative " +
                "factor. For positive values it is never larger than the " +
                "arithmetic mean.",
            verification = "The screen shows the product and the root that " +
                "was taken, and marks the answer as approximate when the " +
                "root is not exact.",
            limitations = averageScope +
                " Zero and negative values are rejected. Roots that are not " +
                "exact are shown to the app's precision and flagged as " +
                "approximate.",
        )

        else -> Explanation(
            definition = "An average is a single value that stands for a " +
                "whole data set. The tool offers several ways of choosing it.",
            formula = "mean = Σxᵢ ÷ n",
            symbols = listOf(
                "Σxᵢ" to "sum of all values",
                "n" to "how many values there are",
            ),
            conditions = listOf(
                "The average tool must be given one of its five modes; an " +
                    "unrecognised mode falls back to the arithmetic mean.",
            ),
            method = "The screen adds the entered values and divides by their " +
                "count at the app's evaluation precision.",
            steps = steps.orEmpty(),
            interpretation = "The result is one number standing for the data " +
                "that was entered.",
            verification = "The working supplied by the screen is shown " +
                "unchanged, so the arithmetic can be checked from it.",
            limitations = averageScope +
                " An unrecognised mode is shown with the arithmetic mean " +
                "formula rather than a mode-specific one.",
        )
    }

    // --------------------------------------------------------------- proportion

    /** The explanation for a proportion solved in [mode]. */
    fun proportionExplanation(
        mode: ProportionMode?,
        steps: List<String>? = null,
        verification: String? = null,
        unknown: ProportionSlot? = null,
    ): Explanation {
        val target = unknown?.label?.let { " Solving for $it." } ?: ""
        return when (mode ?: ProportionMode.Direct) {
            ProportionMode.Direct -> Explanation(
                definition = "A direct proportion states that two ratios are " +
                    "equal, a/b = c/d. Three of the four values fix the fourth." +
                    target,
                formula = "a / b = c / d  ⟺  a × d = b × c",
                symbols = listOf(
                    "a, b" to "the first pair, a over b",
                    "c, d" to "the second pair, c over d",
                    "cross products" to "a × d and b × c, which must agree",
                ),
                conditions = listOf(
                    "Three of the four values are needed. ProportionEngine " +
                        "reports how many were given when fewer are supplied.",
                    "The slot that the division needs must not be zero. The " +
                        "engine reports that the slot is zero and the unknown " +
                        "cannot be found by division.",
                ),
                method = "ProportionEngine cross multiplies, isolates the " +
                    "unknown slot by division and substitutes the answer back " +
                    "into the proportion.",
                steps = steps.orEmpty(),
                interpretation = "The fourth value is the one that keeps the " +
                    "two ratios equal, so the two pairs scale together." + target,
                verification = verification?.takeIf { it.isNotBlank() }
                    ?: "When a solution exists, the screen shows a Verification " +
                        "row from ProportionEngine comparing the two cross " +
                        "products after substituting the answer.",
                limitations = proportionScope +
                    " A zero in the position that must be divided by is " +
                    "rejected; a system of proportions is not handled.",
            )

            ProportionMode.Inverse -> Explanation(
                definition = "An inverse proportion keeps the product of two " +
                    "paired quantities constant: when one grows, the other " +
                    "shrinks." + target,
                formula = "a × b = c × d  (the product stays constant)",
                symbols = listOf(
                    "a, b" to "the first pair of paired quantities",
                    "c, d" to "the second pair of paired quantities",
                    "product" to "a × b, equal to c × d",
                ),
                conditions = listOf(
                    "Three of the four values are needed; the engine reports " +
                        "how many were given otherwise.",
                    "The partner of the unknown in its product must not be " +
                        "zero. The engine reports that the partner is zero and " +
                        "the unknown cannot be found by division.",
                ),
                method = "ProportionEngine works out the known product, " +
                    "isolates the unknown by dividing by its partner and " +
                    "checks that the two products agree.",
                steps = steps.orEmpty(),
                interpretation = "The answer is the pairing that makes both " +
                    "products equal, so an increase in one quantity is matched " +
                    "by a decrease in the other." + target,
                verification = verification?.takeIf { it.isNotBlank() }
                    ?: "When a solution exists, the screen shows a Verification " +
                        "row from ProportionEngine comparing the two products " +
                        "after substituting the answer.",
                limitations = proportionScope +
                    " A zero partner in the unknown's product is rejected; " +
                    "relations other than a constant product are not handled.",
            )
        }
    }

    // --------------------------------------------------------------------- ratio

    /** The explanation for the ratio mode named [modeName]. */
    fun ratioExplanation(
        modeName: String?,
        steps: List<String>? = null,
        verification: String? = null,
    ): Explanation = when (modeName ?: "") {
        "Simplify" -> Explanation(
            definition = "Simplifying a ratio divides every term by their " +
                "greatest common divisor, leaving the smallest whole numbers " +
                "with the same relationship.",
            formula = "a : b = (a ÷ g) : (b ÷ g),  g = gcd(a, b)",
            symbols = listOf(
                "a, b" to "the terms that were entered",
                "g" to "the greatest common divisor of the terms",
                "simplified terms" to "the smallest integers with the same ratio",
            ),
            conditions = listOf(
                "RatioEngine.parse needs at least two terms separated by ':'.",
                "Terms must be greater than zero: negative terms are rejected " +
                    "with 'Ratio terms must not be negative' and zero with " +
                    "'Ratio terms must be greater than zero'.",
                "Terms with more than 30 decimal places cannot be simplified " +
                    "exactly and are rejected.",
            ),
            method = "RatioEngine clears decimals by multiplying by a power " +
                "of ten, finds the greatest common divisor of the integer " +
                "terms and divides through by it.",
            steps = steps.orEmpty(),
            interpretation = "The simplified terms are the smallest integers " +
                "with the same ratio as the terms that were entered.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The working shows the scaled integers, their greatest " +
                    "common divisor and the division, so the result can be " +
                    "checked by multiplying back.",
            limitations = ratioScope +
                " Terms must be positive and no more than 30 decimal places " +
                "may be simplified exactly. Fractions such as 3/4 are read.",
        )

        "Scale" -> Explanation(
            definition = "Scaling a ratio multiplies every term by the same " +
                "positive factor, giving an equivalent ratio.",
            formula = "a : b → (k · a) : (k · b)",
            symbols = listOf(
                "a, b" to "the terms that were entered",
                "k" to "the scale factor",
                "scaled terms" to "the terms after multiplication",
            ),
            conditions = listOf(
                "Terms must be greater than zero; RatioEngine rejects " +
                    "negative and zero terms.",
                "The scale factor must be greater than zero. RatioEngine.scale " +
                    "reports 'The scale factor must be greater than zero'.",
            ),
            method = "RatioEngine multiplies each term by the factor. The " +
                "screen also shows the ×10 and ÷10 forms and the simplified " +
                "form for comparison.",
            steps = steps.orEmpty(),
            interpretation = "The scaled ratio has different terms but the " +
                "same relationship, because every term grew or shrank by the " +
                "same factor.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The screen shows the ×10 and ÷10 equivalents and the " +
                    "simplified form, so equivalence can be checked by " +
                    "scaling a term back.",
            limitations = ratioScope +
                " The factor and every term must be positive; a factor of " +
                "zero or less is rejected.",
        )

        "Split" -> Explanation(
            definition = "Splitting a total in a ratio gives each term a " +
                "share proportional to its size.",
            formula = "shareᵢ = total × termᵢ ÷ (Σ of all terms)",
            symbols = listOf(
                "total" to "the amount being split",
                "termᵢ" to "the term that determines one share",
                "shareᵢ" to "the amount given to that term",
                "decimal places" to "the precision chosen for the shares",
            ),
            conditions = listOf(
                "The total must not be negative. RatioEngine.splitTotal " +
                    "reports 'The total must not be negative'.",
                "Terms must be greater than zero; negative and zero terms are " +
                    "rejected.",
                "Decimal places must be between 0 and 12. The screen reports " +
                    "'Decimal places must be between 0 and 12'.",
            ),
            method = "RatioEngine works out exact shares, rounds each one down " +
                "to the chosen decimal places, then hands the leftover out one " +
                "unit at a time to the largest remainders.",
            steps = steps.orEmpty(),
            interpretation = "Each share matches its term's fraction of the " +
                "total. When the total cannot be split exactly at the chosen " +
                "precision, the screen says which shares received the leftover.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The screen shows the sum of the shares and says whether " +
                    "the division was exact, how much remainder was handed " +
                    "out, or how much could not be shown at that precision.",
            limitations = ratioScope +
                " The total must be non-negative, terms must be positive and " +
                "decimal places must lie between 0 and 12. The rounding is by " +
                "largest remainder, not banker's rounding.",
        )

        "UnknownPart" -> Explanation(
            definition = "When one share of a ratio split is known, the value " +
                "of one ratio unit can be found and used for any other term.",
            formula = "unknown share = known share × its term ÷ the known term",
            symbols = listOf(
                "known share" to "the share that was entered",
                "known term" to "the 1-based term it belongs to",
                "unknown term" to "the 1-based term being found",
                "unit" to "the value of one ratio unit",
            ),
            conditions = listOf(
                "Terms must be greater than zero; negative and zero terms are " +
                    "rejected.",
                "The known share must be greater than zero. RatioEngine " +
                    "reports 'The known share must be greater than zero'.",
                "Term numbers must lie between 1 and the number of terms, and " +
                    "the two terms must differ. The engine says so when either " +
                    "check fails, including 'Choose two different terms'.",
            ),
            method = "RatioEngine divides the known share by its term to get " +
                "one ratio unit, then multiplies that unit by the unknown term.",
            steps = steps.orEmpty(),
            interpretation = "The answer is the share belonging to the chosen " +
                "term, assuming the same ratio unit applies to every term.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The screen shows the engine's check that the known share " +
                    "and the computed share keep the entered ratio.",
            limitations = ratioScope +
                " Term numbers are 1-based and must name two different terms; " +
                "the known share must be positive.",
        )

        "Combine" -> Explanation(
            definition = "Combining two ratios that share a linking quantity " +
                "gives one chained ratio across all the quantities.",
            formula = "a : b  and  b : c  →  a : b : c",
            symbols = listOf(
                "a : b" to "the first ratio",
                "b : c" to "the second ratio",
                "linking term" to "the last term of the first and first term " +
                    "of the second",
            ),
            conditions = listOf(
                "Both ratios must have positive terms; negative and zero " +
                    "terms are rejected.",
                "The linking terms must represent the same quantity. The " +
                    "screen notes that combining needs a shared linking " +
                    "quantity.",
                "Terms with more than 30 decimal places cannot be simplified " +
                    "exactly, and both ratios are simplified before merging.",
            ),
            method = "RatioEngine simplifies both ratios, rescales them so " +
                "the linking term matches at its least common multiple, merges " +
                "them and divides out any common divisor.",
            steps = steps.orEmpty(),
            interpretation = "The chained ratio puts all the quantities on " +
                "one scale, with the linking quantity appearing once.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The working shows both rescaled ratios and the linking " +
                    "value, so the merge can be checked term by term.",
            limitations = ratioScope +
                " Only a two-term chain is offered: the last term of the " +
                "first ratio links to the first term of the second. Longer " +
                "chains and unrelated link terms are not handled.",
        )

        "Compare" -> Explanation(
            definition = "Comparing two ratios means deciding which of the " +
                "two fractions is larger.",
            formula = "a : b ⋛ c : d  ⟺  a × d ⋛ c × b",
            symbols = listOf(
                "a : b" to "the first ratio, read as a ÷ b",
                "c : d" to "the second ratio, read as c ÷ d",
                "cross products" to "a × d and c × b, which decide the verdict",
            ),
            conditions = listOf(
                "Both ratios must have exactly two terms. RatioEngine reports " +
                    "'Comparison needs two-term ratios, for example 2 : 3 and " +
                    "4 : 6'.",
                "Terms must be greater than zero; negative and zero terms are " +
                    "rejected.",
            ),
            method = "RatioEngine compares the cross products exactly. The " +
                "decimal values shown are rounded to 12 significant digits " +
                "for display only.",
            steps = steps.orEmpty(),
            interpretation = "The verdict says which ratio is smaller, equal " +
                "or greater. The decimal values help to see the sizes, but " +
                "the verdict does not depend on them.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The working shows both cross products and states that the " +
                    "comparison is exact with no tolerance; the screen's note " +
                    "says the same.",
            limitations = ratioScope +
                " Only two-term ratios can be compared, and no numerical " +
                "tolerance is used in the verdict.",
        )

        else -> Explanation(
            definition = "A ratio compares quantities by division. The tool " +
                "can simplify, scale, split, find an unknown term, combine or " +
                "compare ratios.",
            formula = "a : b = (a ÷ g) : (b ÷ g),  g = gcd(a, b)",
            symbols = listOf(
                "a, b" to "the terms of the ratio",
                "g" to "the greatest common divisor of the terms",
            ),
            conditions = listOf(
                "The ratio tool must be given one of its six modes; an " +
                    "unrecognised mode falls back to simplification.",
            ),
            method = "The terms are read as positive numbers and the " +
                "engine's simplification is used for the working.",
            steps = steps.orEmpty(),
            interpretation = "The result keeps the same relationship as the " +
                "ratio that was entered.",
            verification = verification?.takeIf { it.isNotBlank() }
                ?: "The working supplied by the screen is shown unchanged, " +
                    "so the arithmetic can be checked from it.",
            limitations = ratioScope +
                " An unrecognised mode is shown with the simplification " +
                "formula rather than a mode-specific one.",
        )
    }
}

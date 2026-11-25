package com.calcsuite.ui.tools.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.algebra.Factorization
import com.calcsuite.engine.algebra.GcfLcmResult
import com.calcsuite.engine.algebra.PrimeCheck
import com.calcsuite.engine.algebra.ProbabilityResult
import com.calcsuite.engine.algebra.SelectionKind
import com.calcsuite.engine.algebra.SelectionResult
import java.math.BigInteger

/**
 * Explanations for the four number tools in this batch.
 *
 * The builders are pure: they read the values the screens already hold and
 * return the shared explanation model. They never run or change a
 * calculation, so the words cannot disagree with the numbers on screen.
 */
object AlgebraExplanationsNumber {

    // ------------------------------------------------------------- GCF & LCM

    /** The explanation of a greatest common factor / least common multiple report. */
    fun gcfLcmExplanation(report: GcfLcmResult?): Explanation {
        val values = report?.values.orEmpty()
        val nonZero = values.filter { it.signum() != 0 }
        val absolute = nonZero.map { it.abs() }
        val gcf = report?.gcf
        val lcm = report?.lcm

        val definition =
            "The greatest common factor is the largest whole number that divides " +
                "every value in the set. The least common multiple is the smallest " +
                "positive whole number that every value divides. The two ideas are " +
                "normally stated for two or more positive whole numbers."
        val formula =
            "gcd(a₁, …, aₖ) = gcd(gcd(a₁, a₂), …, aₖ)   ·   " +
                "lcm(a, b) = |a × b| ÷ gcd(a, b)   ·   " +
                "lcm(a₁, …, aₖ) = lcm(lcm(a₁, a₂), …, aₖ)"
        val symbols = listOf(
            "aᵢ" to "one of the values; a negative value is read as its absolute value",
            "gcd" to "the greatest common factor, also called the highest common factor",
            "lcm" to "the least common multiple; lcm(x, 0) = 0 by convention",
            "|a × b|" to "the product of the absolute values",
        )
        val conditions = listOf(
            "The classical definition uses two or more positive whole numbers; this " +
                "tool accepts one or more whole numbers and reports the single value " +
                "as both its own factor and its own multiple.",
            "Negative values are used as their absolute values, so neither result is negative.",
            "A zero makes the LCM zero by convention and is skipped when finding the " +
                "GCF; a set containing only zeros has no GCF.",
            "Prime factorisations are attempted only when there are at most four " +
                "non-zero values and each has at most 18 digits.",
        )
        val method =
            "Euclid's algorithm replaces the larger value by the remainder until " +
                "the remainder is zero, and the last non-zero remainder is the factor. " +
                "For more than two values the pairwise result is folded in one value at " +
                "a time. When factorisations are available, the common prime powers " +
                "take the smallest exponent of each prime for the GCF, and all prime " +
                "powers take the largest exponent for the LCM."

        val steps = if (report != null && report.steps.isNotEmpty()) {
            report.steps
        } else {
            listOf(
                "Replace each value by its absolute value and set zeros aside for the GCF",
                "Apply Euclid's algorithm to two values and fold the result in for the rest",
                "Build the LCM from lcm(a, b) = |a × b| ÷ gcd(a, b), again folding in one value at a time",
            )
        }

        val interpretation = if (report != null && gcf != null && lcm != null) {
            if (lcm.signum() == 0) {
                "$gcf is the largest number that divides every non-zero value. The LCM " +
                    "is 0 because a zero is present; this is the tool's convention, not " +
                    "a positive multiple."
            } else {
                "$gcf is the largest number that divides every value, and $lcm is the " +
                    "smallest number that each value divides."
            }
        } else {
            "The two quantities being sought are the largest shared divisor and the " +
                "smallest shared multiple of the values entered."
        }

        val verification = when {
            report == null || gcf == null || lcm == null ->
                "For two values the identity gcd × lcm = |a × b| is the check. For " +
                    "larger sets each reported factor is checked against every value " +
                    "and the reported multiple is checked against each value."
            values.size == 2 -> {
                val product = values[0].abs().multiply(values[1].abs())
                val reconstructed = gcf.multiply(lcm)
                "For two values the identity gcd × lcm = |a × b| applies: " +
                    "$gcf × $lcm = $reconstructed and " +
                    "${values[0].abs()} × ${values[1].abs()} = $product."
            }
            else -> {
                val factorValue = gcf!!
                val multipleValue = lcm!!
                val factorDivides = absolute.all { it.mod(factorValue).signum() == 0 }
                val multipleContains = multipleValue.signum() == 0 ||
                    absolute.all { multipleValue.mod(it).signum() == 0 }
                if (factorDivides && multipleContains) {
                    "The pairwise product identity applies only to two values. For these " +
                        "${absolute.size} non-zero values the check is that every value " +
                        "is divisible by $factorValue and divides $multipleValue."
                } else {
                    "The pairwise product identity applies only to two values, and a " +
                        "simple divisibility check did not confirm this report."
                }
            }
        }

        val limitations =
            "Both results use exact BigInteger arithmetic, so they stay exact even for " +
                "very large inputs. The prime-power view is limited: factorisations are " +
                "attempted for at most four values of at most 18 digits, and are skipped " +
                "otherwise. Only whole numbers are read; fractions and decimals are not."
        return Explanation(
            definition = definition,
            formula = formula,
            symbols = symbols,
            conditions = conditions,
            method = method,
            steps = steps,
            interpretation = interpretation,
            verification = verification,
            limitations = limitations,
        )
    }

    // --------------------------------------------------------- combinatorics

    /** The explanation of the selection or probability currently on screen. */
    fun combinatoricsExplanation(
        mode: String,
        selection: SelectionResult?,
        probability: ProbabilityResult?,
    ): Explanation {
        val probabilityMode = mode.equals("Probability", ignoreCase = true) ||
            (selection == null && probability != null)
        val permutationMode = !probabilityMode &&
            (mode.equals("Permutation", ignoreCase = true) ||
                selection?.kind == SelectionKind.Permutation)

        val definition = when {
            probabilityMode ->
                "The probability mode uses the hypergeometric model. A population of N " +
                    "items contains K special ones; n items are drawn without replacement, " +
                    "and the result is the chance that exactly k of them are special. " +
                    "Every subset of n items is equally likely."
            permutationMode ->
                "A permutation is an arrangement of r items taken from a pool of n in " +
                    "which the order matters: the same r items in a different order count " +
                    "as another arrangement. Each item is used at most once; the tool has " +
                    "no with-repetition mode."
            else ->
                "A combination is a selection of r items from a pool of n in which the " +
                    "order does not matter: (a, b) and (b, a) are the same selection. " +
                    "Each item is used at most once; the tool has no with-repetition mode."
        }
        val formula = when {
            probabilityMode -> buildString {
                append("P(exactly k) = C(K, k) · C(N − K, n − k) ÷ C(N, n)")
                if (probability != null) append("   ·   ${probability.formula}")
            }
            permutationMode -> buildString {
                append("P(n, r) = n! ÷ (n − r)!")
                if (selection != null) append("   ·   ${selection.formula}")
            }
            else -> buildString {
                append("C(n, r) = n! ÷ (r! · (n − r)!)")
                if (selection != null) append("   ·   ${selection.formula}")
            }
        }
        val symbols = when {
            probabilityMode -> listOf(
                "N" to "the size of the population",
                "K" to "the number of special items in the population",
                "n" to "the number of items drawn",
                "k" to "the exact number of matches among the drawn items",
                "C(a, b)" to "the number of ways to choose b items from a",
            )
            else -> listOf(
                "n" to "the size of the pool",
                "r" to "how many items are selected",
                "!" to "factorial, the product of the whole numbers from 1 up to that number",
                "C(n, r)" to "combinations, where order does not matter",
                "P(n, r)" to "permutations, where order does matter",
            )
        }
        val conditions = listOf(
            "n and r must be whole numbers with n ≥ r ≥ 0.",
            "In the probability mode 0 ≤ K ≤ N, 0 ≤ n ≤ N and " +
                "0 ≤ k ≤ min(K, n).",
            "Any success probability p in a binomial calculation has to lie between " +
                "0 and 1; the probability shown here is a ratio of counts and also lies " +
                "between 0 and 1.",
            "Selections and draws are without replacement; each item is used at most once.",
        )
        val method = when {
            probabilityMode ->
                "The number of favourable selections C(K, k) · C(N − K, n − k) is " +
                    "divided by the number of all selections C(N, n). Every count is an " +
                    "exact BigInteger and the division is carried out in the evaluation " +
                    "context."
            permutationMode ->
                "The count is built as an exact product of r descending factors, from " +
                    "n down to n − r + 1. No factorial of n is ever materialised."
            else ->
                "The count is built as an exact product of min(r, n − r) factors, with " +
                    "an exact division after each step, so no factorial of n is ever " +
                    "materialised."
        }
        val steps = when {
            probability != null && probability.steps.isNotEmpty() -> probability.steps
            selection != null && selection.steps.isNotEmpty() -> selection.steps
            probabilityMode -> listOf(
                "Count the favourable draws: C(K, k) · C(N − K, n − k)",
                "Count every possible draw: C(N, n)",
                "Divide the favourable count by the total count to get the probability",
            )
            permutationMode -> listOf(
                "Set up P(n, r) = n! ÷ (n − r)!",
                "Multiply the descending factors n × (n − 1) × … × (n − r + 1)",
                "Count each ordering separately, because order matters",
            )
            else -> listOf(
                "Set up C(n, r) = n! ÷ (r! · (n − r)!)",
                "Cancel the factorials to a product of r factors divided by r!",
                "Count each set once, because order does not matter",
            )
        }
        val interpretation = when {
            probabilityMode ->
                "The result is the chance of exactly k matches in n draws without " +
                    "replacement, and the 'one in' row is its reciprocal when the chance " +
                    "is positive. This is not the binomial model: binomial probability " +
                    "C(n, k) · p^k · (1 − p)^(n − k) assumes independent trials with a " +
                    "constant probability p and replacement, while here each draw changes " +
                    "the pool."
            permutationMode -> {
                val value = selection?.value?.toString()
                "The result is the number of ordered arrangements" +
                    (if (value != null) ", $value here" else "") +
                    ". Here order matters, so the same r items in a different order count " +
                    "separately. For the same n and r, P(n, r) = C(n, r) × r!, which is " +
                    "why this count is larger than the combination count."
            }
            else -> {
                val value = selection?.value?.toString()
                "The result is the number of different sets of r items" +
                    (if (value != null) ", $value here" else "") +
                    ". Here the order does not matter, so (a, b) and (b, a) are " +
                    "the same selection and it is counted once."
            }
        }
        val verification = if (probabilityMode) {
            "The favourable count and the total count are computed separately as exact " +
                "integers before the ratio is taken. The result stays within 0 and 1, " +
                "and the 'one in' figure is one divided by that ratio."
        } else {
            "The two counts are linked by P(n, r) = C(n, r) × r!. Comparing the nCr " +
                "and nPr modes for the same n and r checks whether the r! factor has " +
                "been applied: permutations are the larger count."
        }
        val limitations =
            "Counts are exact integers, but a printed value can be thousands of digits " +
                "long. A combination evaluates at most min(r, n − r) factors and a " +
                "permutation at most r, with a guard of 1 000 000 steps; factorials above " +
                "100 000! and probability calculations with n above 100 000 are refused. " +
                "Probabilities are rounded to ${Num.EVAL_CONTEXT.precision} significant " +
                "digits for display. With-repetition counts and the binomial " +
                "with-replacement model are not calculated by this screen."
        return Explanation(
            definition = definition,
            formula = formula,
            symbols = symbols,
            conditions = conditions,
            method = method,
            steps = steps,
            interpretation = interpretation,
            verification = verification,
            limitations = limitations,
        )
    }

    // ----------------------------------------------------------------- primes

    /** The explanation of a primality verdict and any factorisation shown with it. */
    fun primeExplanation(
        check: PrimeCheck?,
        factorization: CalcResult<Factorization>?,
    ): Explanation {
        val definition =
            "A prime is a whole number greater than 1 with exactly two divisors, 1 and " +
                "the number itself. A composite has a further divisor. The number 1 is " +
                "neither prime nor composite, and numbers below 2 are not prime."
        val formula =
            "n is prime ⇔ no whole number d with 1 < d < n divides n   ·   " +
                "n − 1 = 2^s · d with d odd, then test a^d mod n for each Miller-Rabin base a"
        val symbols = listOf(
            "n" to "the whole number being tested",
            "d" to "the odd part of n − 1 used by the Miller-Rabin test",
            "s" to "the number of times 2 divides n − 1",
            "a" to "a Miller-Rabin base; below 3.3 × 10^24 the twelve fixed bases prove the answer",
            "proven" to "true for a certain verdict, false for a strong probable prime",
        )
        val conditions = listOf(
            "Any whole number is accepted; negative numbers, 0 and 1 are reported as " +
                "not prime.",
            "Trial division runs over the primes up to 1000, obtained by a sieve; larger " +
                "candidates go to Miller-Rabin.",
            "Below 3 317 044 064 679 887 385 961 981 the twelve fixed bases 2, 3, 5, 7, " +
                "11, 13, 17, 19, 23, 29, 31 and 37 give a proof. Above it, 40 fixed bases " +
                "give a strong probable prime, not a proof.",
            "Factorisation is shown only for values up to 20 digits on this screen; the " +
                "engine itself refuses more than 27 digits.",
        )
        val method =
            "Small candidates are matched against the sieved primes and divided by every " +
                "prime up to 1000. For larger candidates, n − 1 is written as 2^s · d " +
                "with d odd and each base is tested by modular exponentiation; one " +
                "witness proves that the number is composite. Factorisation, when shown, " +
                "repeats the small-prime division and then uses Pollard's rho with exact " +
                "checks."

        val steps = buildList {
            if (check == null) {
                add("A prime has exactly two divisors: 1 and the number itself")
                add("The primes up to 1000 are tried by division first")
                add("Miller-Rabin witnesses are then tested on the odd part of n − 1")
            } else {
                add("Number tested: ${check.n}")
                if (check.n < BigInteger.valueOf(2L)) {
                    add("Values below 2 fail the definition of a prime")
                } else {
                    add("Small candidates are checked against the primes up to 1000")
                }
                when {
                    !check.isPrime ->
                        add("A divisor or a Miller-Rabin witness proves the number composite")
                    check.proven ->
                        add("The deterministic bases found no witness, which proves primality below 3.3 × 10^24")
                    else ->
                        add("40 fixed Miller-Rabin bases found no witness, so this is a strong probable prime")
                }
                when (factorization) {
                    is CalcResult.Ok -> add("Prime powers found: ${factorization.value.text}")
                    is CalcResult.Err -> add("Factorisation: ${factorization.error.message}")
                    null -> Unit
                }
            }
        }

        val interpretation = check?.explanation
            ?: "A prime verdict means exactly two divisors; a composite verdict means " +
                "another divisor was found."
        val verification = when (factorization) {
            is CalcResult.Ok ->
                "The reported prime factors are multiplied back together and compared " +
                    "with the input; ${factorization.value.text} is shown only when that " +
                    "check passes."
            is CalcResult.Err ->
                "No factorisation is shown: ${factorization.error.message}. The primality " +
                    "verdict is independent of it."
            null ->
                "The engine multiplies its prime factors back together and checks the " +
                    "product against the input before showing them."
        }
        val limitations =
            "The verdict is exact only below 3 317 044 064 679 887 385 961 981, where " +
                "the twelve fixed Miller-Rabin bases prove it. Larger inputs receive 40 " +
                "fixed Miller-Rabin bases, so a prime verdict there is a strong probable " +
                "prime with no known counterexample, not a proof. Factorisation is far " +
                "more limited: " +
                "this screen attempts it only up to 20 digits and the engine refuses more " +
                "than 27 digits, so large semiprimes may be left unfactored. The nearest " +
                "primes use the same primality test."
        return Explanation(
            definition = definition,
            formula = formula,
            symbols = symbols,
            conditions = conditions,
            method = method,
            steps = steps,
            interpretation = interpretation,
            verification = verification,
            limitations = limitations,
        )
    }

    // ----------------------------------------------------------------- random

    /** The explanation of the generated list that is currently on screen. */
    fun randomExplanation(
        mode: String,
        generated: List<String>,
        notes: List<String>,
        summary: List<Pair<String, String>>,
        lastAction: String,
    ): Explanation {
        val decimalMode = mode.equals("Decimal", ignoreCase = true)
        val noteText = notes.joinToString(" ")
        val secure = noteText.contains("SecureRandom", ignoreCase = true)
        val seeded = !secure && noteText.contains("Seeded with", ignoreCase = true)
        val unique = noteText.contains("distinct", ignoreCase = true) ||
            lastAction.contains("unique", ignoreCase = true)
        val parity = when {
            lastAction.contains("even", ignoreCase = true) -> "even"
            lastAction.contains("odd", ignoreCase = true) -> "odd"
            else -> null
        }

        val definition = if (decimalMode) {
            "A decimal draw picks one point from the grid of values with the chosen " +
                "number of decimal places; every grid point has the same chance. It is " +
                "not a draw from the continuous interval. A seed makes java.util.Random " +
                "deterministic; the secure option switches to java.security.SecureRandom " +
                "and ignores the seed."
        } else {
            "A uniform integer draw gives every whole number in the range the same " +
                "chance; with an even or odd filter the chance is uniform over the " +
                "values of that parity in the range. A seed makes java.util.Random " +
                "deterministic; the secure option switches to java.security.SecureRandom " +
                "and ignores the seed."
        }
        val formula = if (decimalMode) {
            "step = 10^−p   ·   low = ceil(min × 10^p), high = floor(max × 10^p)   ·   " +
                "P(each grid point) = 1/(high − low + 1)"
        } else {
            "value = min + U, U uniform in [0, max − min]   ·   " +
                "P(value = v) = 1/(b − a + 1) for the inclusive range [a, b]" +
                (if (parity == "even") "   ·   even values only"
                else if (parity == "odd") "   ·   odd values only"
                else "")
        }
        val symbols = listOf(
            "a, b" to "the inclusive lower and upper bounds of the range",
            "U" to "a uniform whole-number draw from the size of the domain",
            "p" to "the number of decimal places, from 0 to 12",
            "seed" to "a whole number that makes java.util.Random repeat the same list",
            "unique" to "draws without replacement, so every value appears at most once",
        )
        val conditions = listOf(
            "The count must be a whole number from 1 to 1000, and both endpoints are " +
                "inclusive. If the minimum exceeds the maximum the bounds are swapped " +
                "and a note is shown.",
            "For unique values the domain must contain at least as many values as the " +
                "count; an even or odd filter must leave at least one value in the range.",
            "Decimal places must be between 0 and 12 and the range must contain at " +
                "least one grid point.",
            "The seed must be a whole number that fits a Long; the secure option ignores it.",
        )
        val method =
            "Random bits are taken for the size of the domain and candidates outside it " +
                "are discarded, so the accepted values are uniform. Unique integer draws " +
                "either shuffle the whole pool when it has at most 20 000 values or draw " +
                "into a set with an attempt limit. Decimal draws choose an unscaled " +
                "integer on the grid and then apply the chosen scale."

        val steps = buildList {
            addAll(notes)
            if (lastAction.isNotBlank()) add("Action recorded: $lastAction")
            if (summary.isNotEmpty()) {
                add("Summary for this run: " + summary.joinToString("; ") { "${it.first} = ${it.second}" })
            }
            if (generated.isNotEmpty()) {
                add("${generated.size} value(s) were drawn; the first few are " +
                    generated.take(3).joinToString(", "))
            }
            if (isEmpty()) add("Press Generate to draw values from the selected range")
        }
        val interpretation =
            "The list is one sample from the chosen rule, and the summary rows show its " +
                "smallest value, largest value, sum and number of distinct values" +
                (if (generated.isNotEmpty()) " for this run" else "") +
                ". Equal chance describes the rule, not a promise that a short list " +
                "looks evenly spread" +
                (if (unique) "; every value here is distinct" else "") + "."
        val verification = when {
            secure ->
                "No seed can reproduce a secure list: java.security.SecureRandom ignores " +
                    "the seed, so another Generate press gives a fresh list. The " +
                    "smallest and largest rows can still be checked against the range."
            seeded ->
                "Keep the same mode, bounds, count, parity, uniqueness and seed and " +
                    "press Generate again to reproduce the list: java.util.Random is " +
                    "deterministic, so the same values return. The generation note " +
                    "records the seed used."
            else ->
                "There is no fixed seed, so another Generate press gives a fresh " +
                    "java.util.Random draw. Type a seed to make the list reproducible."
        }
        val limitations =
            "These are statistical samples, not a proof of randomness, and a short list " +
                "can look uneven. java.util.Random, seeded or not, is not " +
                "cryptographically secure; only the secure option uses " +
                "java.security.SecureRandom. At most 1000 values can be generated at " +
                "once. Unique draws over a very large domain use an attempt limit and " +
                "can fail, and decimal values are points on a fixed grid rather than " +
                "continuous values."
        return Explanation(
            definition = definition,
            formula = formula,
            symbols = symbols,
            conditions = conditions,
            method = method,
            steps = steps,
            interpretation = interpretation,
            verification = verification,
            limitations = limitations,
        )
    }
}

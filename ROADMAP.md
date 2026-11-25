# Roadmap

This document separates three things that are easy to conflate:

1. **Implemented and verified** — shipping through 3.1.0.
2. **Outstanding work** — known gaps or defects, verified to exist, not yet fixed.
3. **Possible future ideas** — plausible, not committed to, not scheduled.

Nothing in sections 2 or 3 is on a timeline. Section 2 is a list of facts about the current code, not a promise.

---

## 1. Implemented and verified

### Calculators

- **66 tools across 9 categories** (Algebra 10, Geometry 2, Converters 23, Finance 6, Health 3, Date & Time 3, Other 2, Calculus 14, Advanced Mathematics 3), registered in one catalogue that drives the menu, categories and search.
- **Everyday keypad** with live preview, memory keys and optional full-screen expansion.
- **Scientific calculator** with angle modes, the full function keypad, and reference panels that match the keypad.
- **Fourteen calculus tools**: derivatives, integrals, limits, Taylor and Maclaurin, summations and products, infinite series, roots, extrema, the interactive function graph, differential equations, multivariable calculus, Fourier analysis, complex analysis and partial differential equations.
- **Indefinite integration** for constants, linear/quadratic/higher powers, reciprocals, sines, cosines, exponentials, and sums and differences of those.
- **Definite integration** by adaptive Simpson, labelled approximate.
- **Limits** (two-sided, one-sided, infinite), **infinite series** with convergence verdicts, **Taylor/Maclaurin** expansions to order 12.
- **Σ and Π notation**, with the index inferred from the expression.
- **Root finding** and **extrema** by sign scan plus bisection; **first-order ODEs** by RK4.
- **Interactive graph** with pan, pinch-zoom, an optional `f′` layer, a shaded area between limits, and reset, on a shared viewport whose scales are the same on both axes.
- **Algebra**: 8-mode percentage, five averages, proportions, six-mode ratios, linear/quadratic/2×2 equations, GCF/LCM, combinations/permutations/hypergeometric probability, Miller–Rabin primality with Poll's-rho factorisation, inequalities with their solution sets and regions, and a seeded/cryptographic random generator.
- **Geometry**: 11 plane figures and 9 solids.
- **23 converters** spanning 286 distinct units, including reciprocal families (fuel), lookup tables (ring and shoe sizes), affine conversion (temperature) and text transforms (numeric bases, Roman numerals).
- **Finance**: loan amortisation with extra payments, compound interest with effective annual rate, sales tax, tip splitting with cent allocation, unit-price comparison, and currency with reference rates plus an offline cache.
- **Health**: BMI, MET-based caloric burn, US Navy body fat.
- **Date & time**: exact age, date arithmetic with month-end clamping, interval calculation.
- **Other**: mileage and trip cost, and the Electrical Circuits tool — a circuit you draw, wire and work out, with eleven electrical calculators beside it.
- **Advanced mathematics**: measure theory, a topology explorer and functional analysis, each computing real values and stating what it does not reach.
- **Eight analysis panels across 17 tools** — Definition, Formula, Conditions, Method, Step-by-step work, Interpretation, Verification and Limitations — in eight algebra tools (percentage, average, proportion, ratio, GCF/LCM, combinations, primes, random) and nine calculus tools (derivatives, integrals, limits, Taylor, summations, series, roots, extrema, graph).
- **Partial differential equations** render more smoothly: on the same API 36 emulator, janky frames went from 29.91% to 22.22%, the 90th percentile from 34 ms to 22 ms and the worst frame from 101 ms to 34 ms, with the numerical output unchanged.

### Platform

- First-launch onboarding (four pages, animated, skippable, remembered).
- Thousands separator setting — automatic, comma, period, space, apostrophe — applied display-only.
- **Settings** is a hub of four pages — Appearance, Calculation, History and data, and About CalcSuite — with leading icons on every setting row, covering theme, dynamic colour, **accent colour**, angle unit, precision (3–15 digits), grouping, separator style and haptics.
- **Accent colour**: seven presets and a custom picker, reaching every surface, button, highlight and curve in the app while keeping the contrast the app already had, and reproducing the original green exactly.
- Favourites, recently used, and a 500-entry local history.
- Portrait-only lock across every screen, including dialogs and fullscreen layouts.
- Hidden system bars, edge-to-edge, adaptive themed launcher icon.
- **Four themes**: System, Light, Dark and **Amoled** — true black (`#000000`), bypassing dynamic colour, with dark system bars and a black first composed frame. System mode follows the device and can use Android 12+ wallpaper colours. On stock Android with the system in light mode, the platform's pre-app splash is white (~1.2 s) before the Amoled UI; MIUI launches dark.
- **Fullscreen expansion** animates over 200 ms: `=` geometry is preserved (Poco 1631↔2421, emulator 1501↔2322), the display value survives, and the calculus plot grows (660→968 Poco, 630→939 emulator).
- **Update check**: after onboarding, the app checks GitHub's releases page on start, at most every three hours, network-aware with a cached fallback. Notes come from `releases/latest`, with an explicit **Download & install now** action and a per-version **Never show again** option; Android still confirms the install.
- **Network use** is limited to currency rates, the optional AI provider and the GitHub update check. The permissions are `INTERNET`, `ACCESS_NETWORK_STATE` and `REQUEST_INSTALL_PACKAGES`, and DataStore is excluded from Android backup.
- **AI problem solver**: a photograph of a mathematical problem is read by the AI provider the user chooses, which names the tool, and the app's own engine does the mathematics. Ten providers, a keystore-encrypted key, a typed model name, and a registry of the tools that may be chosen.

### Quality

- 1779 JVM unit tests, 0 failures; Lint: 0 errors, 35 warnings, 8 hints; `assembleDebug` verified, and the signed release APK was built and verified (versionCode 8, certificate 55071A…).
- Every engine is pure Kotlin with no Android dependencies, which is what makes the whole suite testable without an emulator.
- The AI feature keeps its transport behind an interface, so the whole path from the picture to the answer is testable with a mocked provider and no key.

---

## 2. Outstanding work (verified to exist)

These are real, confirmed by reading the code. They are documented rather than hidden.

**What 3.1.0 changed here.** The catalogue now spans nine categories, with eight analysis panels across 17 tools and smoother partial-differential-equation field rendering. Settings is a hub of four pages with leading icons, there are four themes including Amoled, and fullscreen expansion animates over 200 ms. The update check runs on start after onboarding, at most every three hours, network-aware with a cached fallback and release notes from `releases/latest`; network use is currency rates, the optional AI provider and that check, with permissions `INTERNET`, `ACCESS_NETWORK_STATE` and `REQUEST_INSTALL_PACKAGES`. Verification is 1779 JVM tests with 0 failures and Lint at 0 errors, 35 warnings and 8 hints. On stock Android in light system mode the pre-app splash is still white (~1.2 s) before an Amoled UI; MIUI launches dark. Most items below remain open; the ODE variable binding, the Algebra dispatcher comment and the fuel comment constants were fixed in 3.1 and have been removed from this list.

**What 3.0.0 changed here.** The Electrical Circuits tool is new: a circuit you draw, wire and work out, with eleven electrical calculators beside it. The Ohm's law calculator is unchanged and is still the first of them, under the new tool name; the old `other.ohms_law` id still opens the tool, so nothing that points at it breaks. The AI problem solver now works on exactly the problem it shows you, and a saved calculation opens its tool with the problem in it. The items below are still open and are still true.

**What 2.0.0 changed here.** The sign in the calculus parser used to bind tighter than the power, so `-x^2` meant `(-x)^2`; it is now looser, as the parser’s own grammar says. A negative factor in a product used to be printed without brackets, so `x * -2` was shown as `x-2`; it is now bracketed. The root finder is polished with Newton’s method, so the text no longer overstates the method. The rest of the items below are still open and are still true.

### Calculus — highest priority

| Issue | Detail |
|---|---|
| **Indefinite integral can return a wrong answer labelled "exact"** | The `u^n → u^(n+1)/(n+1)` rule applies the power rule to the power's base **as written**, ignoring the chain factor. `∫sin(x)²dx` returns `sin(x)³ ÷ 3 + C`, which is wrong, yet the card says "Antiderivative — found exactly". Fix: restrict the rule to a base that is the integration variable (or the variable times a constant), and otherwise report that no elementary antiderivative was recognised. |
| **`∫2/x dx` is rejected** | The logarithm rule only fires when the numerator is literally `1`. `2/x` should give `2·ln\|x\| + C`. |
| **Roots, extrema and the graph ignore the chosen variable** | All three evaluate with a hard-coded `mapOf("x" to x)`, so changing "Differentiate with respect to" to another letter silently breaks them. |
| ~~**Root-finding text overstates the method**~~ **Fixed in 2.0.0** | The roots are now polished with Newton’s method, so the text and the method agree. |
| **ODE numerical table still uses x/y headers** | The solver infers the variables, but the numerical table's headers are still `x` and `y`. |

### Catalogue text

| Issue | Detail |
|---|---|
| **Time tool description promises duration formatting that does not exist** | The "1 h 2 min 3 s" formatter lives in the Data-transfer tool, not the Time tool. |
| **Angle converter carries a `dms` search keyword but cannot parse degrees/minutes/seconds** | `arcmin` and `arcsec` are plain linear units only. |
| **Speed converter carries a `pace` keyword but has no pace unit** | |
| **Volume description mentions cups; only the US cup exists** in that family (the Imperial cup exists only in Cooking). |
| **GCF & LCM silently swallows one error** | When every input is zero the engine returns `Undefined`, but the screen only reads the success path, so nothing is rendered at all. |
| **Unreachable engine functions** | Two engine functions (`percentDecrease`, `probabilityOfExactlySuccesses`) are unreachable from the UI. |

### Electrical Circuits

| Issue | Detail |
|---|---|
| **The circuit builder is steady direct current only** | There is no transient analysis, no frequency sweep and no non-ohmic component. The alternating-current calculator is a separate calculation at one frequency, not a simulation of a circuit over time. |
| **A resistor's power rating is kept but not checked against** | The value is stored and shown in the editor, but the app does not yet warn when a part is being worked harder than its rating. |
| **A circuit needs one ground** | The voltages are measured against it, and a circuit without one is refused with that reason. |
| **The switch is ideal** | Closed it is a conductor and open it is a break; there is no contact resistance and no switching time. |

### Smaller items

- Prime factorisation is capped at 20 digits in the UI though the engine allows 27, for responsiveness.
- A dead note string in the data-transfer family that is never rendered.
- Unit-family descriptions are illustrative rather than exhaustive, so some units (milligrams, astronomical units, watt-hours) appear in the app but not in the one-line description.
- The Combinations probability mode is hypergeometric only; a binomial mode exists in the engine but is not exposed.

### Not verifiable in this environment

- Pinch-zoom on the Calculus graph could not be exercised by automation, because `adb` injects only single-pointer gestures. The pan/zoom maths is covered by unit tests on the pure transform, and panning is exercised on-device.
- Live device rotation could not be exercised on the available emulator, which stays at `ROTATION_0` regardless of the rotation requested. The portrait lock is verified through the built manifest and the runtime requested orientation on every destination instead.

---

## 3. Possible future ideas

Not commitments. Ordered roughly by how much they would improve the app.

### Mathematics

- A proper symbolic simplifier with a normal form, so `2x + 2x` becomes `4x` rather than staying as a sum.
- General antiderivatives via pattern matching for `tan`, `sec`, `csc`, `cot`, and substitution recognition (`u`-substitution with a guessed inner derivative).
- Symbolic root finding for polynomials of degree 3 and 4, alongside the numeric scan.
- Complex-number support beyond what the Complex Analysis tool now does, and complex roots in the other tools.
- Taylor expansion in the second variable, and series arithmetic (adding, multiplying and composing two series).
- Higher-order ODEs beyond the constant-coefficient ones the solver already handles.

### Converters

- Degrees/minutes/seconds input and output in the angle converter.
- A duration formatter on the time tool, and cooking/temperature-aware conversion.
- More density entries in the cooking tool, and per-brand shoe/ring charts.
- Mass-flow units for the flow converter.

### Product

- Room for unit-conversion favourites ("always convert miles → km").
- Export or share a result card as an image.
- Batch conversion — a list of values through one converter.
- A landscape/tablet-adapted two-pane layout for larger screens, which would mean revisiting the portrait lock.
- Wear OS or home-screen widgets for a quick keypad.
- Optional end-to-end encrypted sync of history across the user's own devices, with no server operated by the project.

### Engineering

- Instrumentation (Espresso/Compose UI) tests for a handful of critical flows, to complement the 1779 JVM tests.
- A screenshot-testing baseline, so UI regressions are caught automatically.
- Reproducibility of the release build — currently signed with a locally held key.

---

## Principles for any future change

- **Never regress correctness.** A wrong number is a worse bug than a missing feature.
- **Keep the offline guarantee.** Any new feature must not require the network, with the possible exception of clearly-labelled reference data such as exchange rates.
- **Keep engines pure.** It is what makes 1779 tests possible without an emulator, and it should stay that way.
- **Keep documentation honest.** If a tool cannot do something its description implies, fix one or the other. Section 2 of this document exists because that promise has to be kept in both directions.

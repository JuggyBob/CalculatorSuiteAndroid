# Changelog

All notable changes to Calculator Suite are recorded here.

This project uses a plain, human-written changelog. **Version 1.0.0 is the first public release** — there are no earlier published releases to record.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for the version number.

---

## [1.0.0] — 2026-10-18

First public release. `versionName = 1.0.0`, `versionCode = 1`, `minSdk 26`, `targetSdk 36`.

### Added — core

- **49 registered tools across 7 categories**, all driven by a single tool registry that feeds the main menu, category screens and search.
- **Everyday calculator** on the landing screen with live result preview, four memory keys (`MC`/`MR`/`M+`/`M−`), sign toggle, percent, and an optional full-screen expansion.
- **Scientific calculator** with a DEG/RAD/GRAD switch, trigonometric and inverse trigonometric functions, logarithms, exponentials, powers, roots, factorial, `mod`, memory keys, and collapsible reference panels that document exactly the keys present on the keypad.
- **Search** across every tool by name *or* by the words people actually use (`mortgage`, `kcal`, `octal`, `tip`, …).
- **Favourites** (starred tools) and **recently used** tools, plus a local **calculation history** capped at 500 entries with copy and delete.

### Added — tools

- **Algebra (10):** Percentage (8 modes), Average (mean/weighted/median/mode/geometric), Proportion, Ratio (6 modes), Equations (linear, quadratic, 2×2 systems), GCF & LCM, Combinations (nCr/nPr/hypergeometric probability), Prime checker, Number generator, **Calculus**.
- **Geometry (2):** 2D shapes (11 plane figures), 3D bodies (9 solids).
- **Converters (23):** acceleration, angle, area, cooking, data storage, data transfer, energy, force, fuel, length, numeric base, power, pressure, ring size, Roman numerals, shoe size, speed, temperature, time, torque, volume, volumetric flow, weight & mass.
- **Finance (6):** Currency converter, Unit price, Sales tax, Tip, Loan (full amortisation with extra payments), Interest (simple/compound with EAR).
- **Health (3):** Body mass index, Caloric burn (MET values), Body fat (US Navy method).
- **Date & time (3):** Age calculator, Add & subtract time, Time interval.
- **Other (2):** Mileage & trips, Ohm's law.

### Added — Calculus tool

Ten operations from a single expression field, each showing its working:

- Symbolic differentiation (sum, product, quotient, power and chain rules) plus partial derivatives and numerical differentiation.
- Indefinite integration (exact, pattern-based) and definite integration (adaptive Simpson, reported as approximate).
- Limits, one-sided or two-sided, including infinite limits.
- Infinite series with a convergence verdict and a closed form where one exists.
- Taylor and Maclaurin expansions to order 12, with every coefficient shown.
- Σ summation and Π product notation with an automatically inferred index.
- Root finding and extrema by sign scan and bisection.
- First-order differential equations solved with RK4.
- An **interactive graph**: drag to pan, pinch to zoom about the point you touch, optional `f′` overlay, a shaded area between two limits, and a reset.
- A **Conventions & Help** panel documenting the symbol table, syntax, every operation and worked examples.

### Added — platform

- **First-launch onboarding**: four pages with Previous/Next, Skip, a page indicator, animated transitions, and a persisted "don't show again" flag. The main menu is unchanged by it.
- **Thousands separator setting**: automatic, comma, period, space or apostrophe. Display-only — parsing is deliberately independent, so the choice can never reinterpret a typed expression.
- **Settings**: theme (system/light/dark), Android 12+ dynamic colour, default angle unit, result precision 3–15 significant digits, grouping toggle, separator style, haptics, and history management.
- **Portrait-only lock** applied app-wide, covering the onboarding pages, every tool, fullscreen layouts and dialogs.
- Edge-to-edge layout with hidden system bars, a theme-matched window background (no white flash on launch) and a matching adaptive launcher icon.
- Light and dark themes throughout, verified on device.

### Correctness

Exact `BigDecimal` arithmetic with a 34-significant-digit evaluation context for decimal work, and `BigInteger` for integer mathematics (factorials, GCD/LCM, primality, permutations). Display rounding happens only at output. Numerical results are prefixed `≈` and name their method.

Notable bugs found and fixed during development, each covered by a regression test:

- **Taylor/Maclaurin reported a false "undefined derivative".** Differentiating a constant such as `(5/2^1)/10` through the quotient rule built a removable singularity that evaluated as undefined. Each derivative is now simplified before the next is taken, so a derivative of exactly `0` is reported as `0`. `x^3-5/2^1/10` now yields `-0.25 + x³` with all higher coefficients zero.
- **Indefinite integration rejected differences.** The integrator handled sums but not differences, so `x^3 - 0.25` was reported as having no elementary antiderivative. Added difference and negation handling, constant × power, and a general constant-factor rule. `∫(x^3-0.25)dx` now returns `x⁴ ÷ 4 - 0.25x + C`.
- **Symbolic differentiation left removable singularities** in the tree, so second and higher derivatives of polynomials could not simplify. Differentiation now simplifies its result.
- **`extrema` solved `f(x)=0` instead of `f′(x)=0`**, so stationary points were never found.
- **The second derivative measured the third** — `x²-4x` at `x=2` was reported as an inflection point.
- **Σ and Π used the differentiation variable as the index**, so `∑k` with variable `x` reported "the term at x = 1 is undefined".
- **`NodePrinter.format` truncated every decimal**, so `0.5` printed as `0`.
- **`String.format` used the device locale**, printing `0,5` on comma-decimal locales.
- **The expression printer did not round-trip**, silently corrupting any result that re-parsed its own output.
- **The Calculus graph painted over surrounding text** — `Canvas` does not clip by default.
- **Expanding the Calculus tool made its graph smaller**, because `weight()` handed the plot whatever space remained.
- **Proportions and several other tools wrapped one character per line** in their result rows.

### Known limitations in 1.0.0

Documented in full in [ROADMAP.md](ROADMAP.md) and [docs/CALCULUS_GUIDE.md](docs/CALCULUS_GUIDE.md); summarised here because they matter when reading the docs:

- The indefinite integral pattern-matcher can return a result labelled "exact" for a power whose base is not the integration variable (for example `∫sin(x)²dx`). Prefer the definite integral for such expressions.
- `∫2/x dx` is reported as having no elementary antiderivative; only a numerator of exactly `1` is recognised as a logarithm.
- Calculus root finding, extrema and the graph are bound to the variable `x`.
- Angle conversion has no degrees/minutes/seconds input, and the Time tool has no duration formatter, despite the wording of their catalogue descriptions.
- Above 3.3 × 10²⁴ the prime checker reports a *strong probable prime*, not a proof.

### Documentation

- `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `SUPPORT.md`, `PRIVACY.md`, `ROADMAP.md`, `CHANGELOG.md`, `LICENSE`.
- `docs/`: `FEATURES_AND_TOOLS.md`, `USER_GUIDE.md`, `CALCULUS_GUIDE.md`, `ARCHITECTURE.md`, `BUILDING.md`, `DEPENDENCIES.md`, `FAQ.md`.

### Verification

- 910 JVM unit tests, 0 failures.
- Android Lint: 0 errors.
- `assembleDebug` and the signed release build both succeed.
- On-device checks covering onboarding (first launch, navigation, skip, finish, restart persistence), the separator setting (all five styles, live update, persistence), portrait lock across 13 destinations, light and dark themes, and 14 assertions on the two calculus fixes.

[1.0.0]: https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v1.0.0

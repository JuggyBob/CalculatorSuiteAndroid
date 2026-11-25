# Architecture

Calculator Suite is a **single-activity, fully-Compose Android app**. All calculation logic lives in pure Kotlin engines that have no Android dependencies, which is what allows the entire test suite to run on a JVM without an emulator.

```
UI (Compose)  ──►  ToolRegistry (dispatch)  ──►  Screen (ToolArgs)
                                                          │
                                                          ▼
                                                      Engine
                                                          │
                                                          ▼
                                              CalcResult<Ok | Err>
```

## Package structure

Source root: `app/src/main/java/com/calcsuite/`

| Package | Responsibility | Android deps? |
|---|---|---|
| `core` | `CalcResult`/`CalcError`/`ErrorKind`, and `Num` — parsing, formatting, precision | No |
| `expr` | The scientific calculator's expression language: lexer, parser, evaluator, `Functions` catalogue | No |
| `engine.calculus` | The calculus engines: `Node`, printer, parser, evaluator, differentiation, integration, calculus and equation engines | No |
| `engine.calculus.ode` | The differential equation engine: the parser, the classifier, the separable, linear and constant-coefficient solvers, and the numerical one | No |
| `engine.calculus.multivariable` | The multivariable operations, built on the same expression engine | No |
| `engine.algebra.inequality` | The inequality engine: the parser, the solver, the solution set and the numerical scan | No |
| `engine.algebra` | Percentage, average, proportion, ratio, equations, GCF/LCM, combinatorics, primes, random | No |
| `engine.convert` | `UnitFamily` machinery plus one file per unit family, and the bespoke cooking/fuel/base/roman/ring/shoe/transfer engines | No |
| `engine.finance` | Currency catalog and engine, loan, interest, tax, tip, unit price | No (HTTP via `HttpURLConnection`) |
| `engine.geometry` | Shapes, bodies, shared geometry maths | No |
| `engine.health` | BMI, calories, body fat | No |
| `engine.datetime` | Age, date maths, intervals | No (`java.time`) |
| `engine.other` | Mileage and trip cost | No |
| `engine.electrical` | The circuit model, the topology, the DC solver (modified nodal analysis), the validator, the explainer, the series/parallel reducer, the units and the electrical calculators | No |
| `engine.advanced` | The advanced mathematics: complex numbers and analysis, matrices and linear algebra, the Fourier engine, the finite-difference PDE engine, the measure and topology models, and the shared `Explanation` model | No |
| `data` | `ToolRegistry` (the catalogue), `SettingsRepository`, `HistoryRepository`, `CurrencyRepository`, `CircuitRepository`, `ToolDestinations` (which AI tool opens which screen), icon definitions | Yes |
| `updater` | The in-app update: the version rules, the prompt policy, the release model and its JSON reader, the GitHub release client, safe-link checking, the download, the package checks and the notes reader | Partly (HTTP, the package manager) |
| `ai` | The AI feature: the ten providers, the HTTP client, the credential store, the image loader, the tool registry and the pipeline | Yes (keystore, HTTP) |
| `ui.components` | Shared Compose widgets: scaffolding, forms, result cards, inputs, formatting | Yes |
| `ui.screens` | Main menu, categories, search, history, settings (a hub plus four pages), scientific, onboarding, the calculator panel | Yes |
| `ui.theme` | Colour, typography, shape, `CalcSuiteTheme`, the four theme modes and `AppNightMode` | Yes |
| `ui.tools` | One screen per tool, grouped by category, plus per-domain dispatchers | Yes |
| `ui.ai` | The AI problem solver: its screen, its settings screen and its view model | Yes |
| `ui.update` | The startup `UpdateGate`, the update card on the About page and the About dialog, and the view model that drives them | Yes |

**The rule:** `core`, `expr` and every `engine.*` package must compile and run without the Android framework. `java.math`, `java.time` and the rest of the JDK are fine; `android.*` is not.

The `ai` package is the one part that needs the framework, because it holds the keystore and the HTTP client. Its mathematics — the tool registry, the argument checking and the execution — is kept apart from the transport, so that it can be tested without a network.

## The result type

Every engine returns `CalcResult`, and never throws on user input:

```kotlin
sealed interface CalcResult<out T> {
    data class Ok<T>(val value: T) : CalcResult<T>
    data class Err(val error: CalcError) : CalcResult<Nothing>
}

data class CalcError(val kind: ErrorKind, val message: String)
enum class ErrorKind { InvalidInput, Syntax, Domain, DivisionByZero,
                       Overflow, Undefined, Inconsistent, Unavailable, Insufficient }

fun <T> ok(value: T): CalcResult<T>
fun <T> err(kind: ErrorKind, message: String): CalcResult<T>
```

`message` is written for the user: short, no trailing punctuation, no stack traces. The UI renders it next to the offending field or inside the result card.

## Numerics

Three numeric regimes, deliberately separated:

| Regime | Types | Where |
|---|---|---|
| Exact decimal | `BigDecimal`, `MathContext(34, HALF_UP)` | Money, general algebra, converters |
| Exact integer | `BigInteger` | Factorials, GCD/LCM, primes, combinations, ratio simplification |
| Approximate | `Double` | Numerical calculus only — always labelled `≈` |

Rounding happens **only at display time**, at 3–15 significant digits. Engines carry full precision internally, so `0.1 + 0.2` does not become `0.30000000000000004` in the middle of a chain.

`Num` is the single entry point for parsing and formatting:

- `parseOrNull` / `parseIntOrNull` — accept `.` and `,` decimal marks, grouping separators, and `e` notation; return `null` rather than guessing
- `format` / `formatForCopy` — locale-aware display, and a full-precision copy value
- `sepsFor(locale, useGrouping, separator)` — the display separators

The string stored in history is the **formatted** display string, while the copy action uses `Num.formatForCopy` so nothing is silently truncated.

## The tool registry

`data/ToolRegistry.kt` is the single source of truth for all 66 tools, spread across nine categories: Algebra (10), Geometry (2), Converters (23), Finance (6), Health (3), Date & Time (3), Other (2), Calculus (14) and Advanced Math (3).

```kotlin
ToolDef(
    id = "algebra.percentage",
    title = "Percentage",
    category = ToolCategory.ALGEBRA,
    description = "Percent of a number, percent change, percentage points",
    synonyms = listOf("percent", "%", "increase", "decrease", "discount", "change"),
    icon = PercentIcon,
)
```

The main menu, the category screens and search all read from this list — adding a tool means adding one `ToolDef` and one screen, with no separate registration step.

Dispatch goes through a per-domain file, `ui/tools/<domain>/<Domain>Tools.kt`:

```kotlin
@Composable
fun AlgebraTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "algebra.percentage" -> PercentageToolScreen(args)
        // …
        else -> return false
    }
    return true
}
```

`ToolHostScreen` tries each domain dispatcher until one returns `true`, so a tool belongs entirely to its domain folder.

## Navigation

One activity (`MainActivity`), one `NavHost` (`ui/CalcSuiteApp.kt`).

```
Routes.MAIN ──▶ CategoryScreen ──▶ ToolHostScreen ──▶ <Domain>Tools ──▶ ToolScreen
     │              ▲                    │
     ├──▶ ScientificScreen              └── onOpenTool ──▶ another tool
     ├──▶ SearchScreen ───────────────────────┘
     ├──▶ HistoryScreen
     └──▶ SettingsScreen
```

Routes are string constants in `Routes`. `onOpenTool` marks the tool as recently used and pushes `tool/{toolId}`; `ToolHostScreen` resolves the id through `ToolRegistry.byId`, and an id that is not in this build gets a screen that says so instead of an empty page.

A tool may be **renamed** without breaking the things that point at it. `ToolRegistry` keeps a table of legacy ids as aliases — `algebra.calculus` resolves to `calculus.derivatives`, and `other.ohms_law` to `other.electrical_circuits`, both dating from before the Calculus category existed. These are aliases rather than extra tools: `byId` maps them to the current tool, so a history entry, a favourite or a recently-used item made with an old id still opens the tool that is there now.

Above the `NavHost`, `CalcSuiteApp` shows the onboarding flow while `settings.onboardingComplete` is false. Settings are collected as a **nullable** value so the welcome pages cannot flash for a user who has already been through them.

## State management

There is no architecture framework — no ViewModel for the tool screens, no reducer, no DI container. State is Compose state, and it is deliberately local:

- **User input** — `rememberSaveable`, so it survives configuration changes and process death.
- **Results** — computed with `remember(inputs) { … }`, so they recompute when an input changes. Converters and percentage tools update live; the rest have an explicit **Calculate** button.
- **Settings, history, favourites, recents** — `StateFlow`s from DataStore, collected with `collectAsStateWithLifecycle`. One flow drives the theme, the whole app's formatting and every tool's number display at once.
- **The one exception** is the random number generator's generated list, which uses plain `remember` and is therefore lost on rotation — a deliberate trade for holding up to 1000 values.

The container itself is four lines:

```kotlin
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val history = HistoryRepository(context)
    val currency = CurrencyRepository(context)
}
```

reached through a `compositionLocalOf`. There is no DI framework because there is no graph to wire.

## Number formatting

`ui/components/Formatting.kt` exposes `Fmt`, built from the current `Settings` and the current locale:

```kotlin
val seps = Num.sepsFor(locale, settings.grouping, settings.thousandsSeparator)
fun num(value: BigDecimal, precision: Int = settings.precision): String
```

Every screen calls `rememberFmt()` and formats with `precision = maxOf(fmt.precision, 10)`, so results show at least ten significant digits even when the global precision is set to three. Changing the thousands separator therefore updates **every** tool at once, because they all route through this one class.

Parsing is deliberately independent of display settings — `Num.parseOrNull` always accepts `.`, `,`, `'` and spaces as grouping, whatever the user has chosen — so a display preference can never reinterpret a typed expression.

## Calculation engines

### Unit conversion

Most converters are data, not code. A `UnitFamily` is:

```kotlin
UnitFamily(
    id = "length",
    name = "Length",
    baseUnitId = "m",
    units = listOf(
        UnitDef("km", "Kilometres", "km", bd("1000"), group = "Metric (SI)"),
        UnitDef("mi", "Miles", "mi", bd("1609.344"), group = "Imperial / US"),
    ),
    notes = listOf("…"),
)
```

Conversion is affine: `base = value * from.factor + from.offset`, then `result = (base - to.offset) / to.factor`. `LinearConverterScreen` turns a family into a complete, working screen — From/To pickers, a swap button that copies the result back, factor rows, a formula card and the family's notes.

That covers 16 of the 23 converters. The rest need custom engines because they are not linear:

| Case | Engine | Approach |
|---|---|---|
| Fuel consumption | `FuelEngine` | `ReciprocalFamily` — canonicalised to L/100 km |
| Temperature | `TemperatureFamily` | The only affine family, with an absolute-zero validator |
| Cooking | `CookingEngine` | Two dimensions (volume, mass) bridged by an ingredient-density table |
| Ring size, shoe size | Lookup tables | Nearest-row matching from a measurement, exact size lookup otherwise |
| Numeric base | `NumericBaseEngine` | `BigInteger` text transform, bases 2–36, two's complement |
| Roman numerals | `RomanEngine` | Canonical-only text transform, 1–3999 |

### Calculus

A separate subsystem with its own expression language:

```
CalculusParser → Node tree → NodePrinter → NodeEvaluator
                                   ↓
              DifferentiationEngine / IntegrationEngine / CalculusEngine / EquationEngine
```

- **`Node`** — a sealed tree (`Num`, `Const`, `Var`, `Neg`, `Add`, `Sub`, `Mul`, `Div`, `Pow`, `Fun`), plus `Simplifier` (constant folding, zero/one elimination) and `NodePrinter` with a precedence table.
- **The printer round-trips.** Re-parsing a printed expression yields the same tree. This is load-bearing: extrema, series and Taylor all re-parse their own output, so a printer that loses parentheses silently corrupts them.
- **Differentiation** is symbolic over the tree — sum, product, quotient, power and chain rules — and **simplifies its result**. That simplification is not cosmetic: differentiating a constant through the quotient rule produces a removable singularity which, left unsimplified, evaluates as "undefined" and reports a derivative of `0` as nonexistent.
- **Integration** is pattern-matched (`antiderivative`) against a fixed set of forms, with sums and differences decomposed. Anything unrecognised returns `null` and the tool says so rather than guessing.
- **Numerical operations** are explicitly labelled approximate: adaptive Simpson for definite integrals, RK4 for ODEs, bisection for roots, one-sided evaluation for limits.

The graph is a Compose `Canvas` with `clipToBounds()` — necessary, because `Canvas` does not clip by default and curve sampling reaches far outside the visible window. Its pan/zoom maths is factored into a pure `GraphViewport.transform` so it can be unit-tested without multi-touch injection.

## Persistence

Three repositories over one shared DataStore instance (`Context.appDataStore`, name `calcsuite`):

| Repository | Keys | Shape |
|---|---|---|
| `SettingsRepository` | `theme_mode`, `accent`, `angle_mode`, `precision`, `grouping`, `thousands_separator`, `haptics`, `dynamic_color`, `onboarding_complete`, `favorites`, `recents` | Scalars and small collections |
| `HistoryRepository` | `history` | A JSON array of entries, capped at 500, oldest dropped first |
| `CurrencyRepository` | `currency_rates` | Rates plus the fetch timestamp, so results survive a restart and work offline |
| `ReleaseCache` | `last_release`, `last_release_at`, `suppressed_release_tags`, `last_auto_check` | The last release that was read, so the About screen can show its notes offline; the tags the user chose not to see again; and the time of the last automatic check |

History is a JSON array rather than a database because it is a bounded, flat list — a database would be more machinery than the problem needs.

## Networking

Three places, and no more.

**The currency converter.** `CurrencyRepository.refresh()` issues a single HTTPS `GET` to `open.er-api.com` on `Dispatchers.IO`, with 15-second timeouts, two fixed headers and no analytics parameters. The response is parsed into a `RatesSnapshot`, cached, and re-used offline with a staleness warning past 24 hours.

**The AI problem solver.** `ai/AiClient` sends one HTTPS `POST` to the provider the user chose, on `Dispatchers.IO`, with the picture as base64 in that provider's own image format, the question, the list of eligible tools and the user's API key in the provider's own authentication header. The transport is behind the `AiTransport` interface, so the whole path can be exercised with a fake transport and no network at all. The credential is held by `ai/AiConfigRepository`, which encrypts it with AES/GCM under a key in the Android keystore and never writes it in plain text.

**The update check.** `updater/GitHubReleaseClient` reads the project's own releases from `api.github.com` with a single unauthenticated HTTPS `GET`, on `Dispatchers.IO`. The endpoint is the public one for the latest published release, so no token is embedded in the app and none is needed. The reply is parsed into a `ReleaseInfo`; the version of the release is compared with the installed one; and a release that publishes exactly one installable APK can be downloaded into the app's own cache and handed to Android's installer. The file is checked before it is offered — the digest the release publishes when it publishes one, and then the package name, the version code and the signing certificate. The app never installs anything by itself: Android shows its own confirmation. Release-page and download links are restricted to the project's GitHub host by `updater/SafeLinks.kt`.

A startup gate, `ui/update/UpdateGate.kt`, drives the flow: it checks for a release when the app starts, but throttles automatic checks to one every three hours, recorded as `last_auto_check`. The decision of whether a found release becomes a popup is a pure rule in `ui/update/UpdatePromptPolicy.kt`, which considers the version rules, the throttle, cached notes and suppressed tags. The dialog appears only for a newer, installable release that the user has not asked to hide; only that release's tag is suppressed, so a later one can prompt again.

There is no other network code in the project: no telemetry, no crash reporting and no font or icon download.

## Theme

`CalcSuiteTheme` is the single theme entry point, resolving a `ThemeMode` plus an optional Android 12+ dynamic palette into a Material 3 `ColorScheme`. There are four modes: **System**, **Light**, **Dark** and **Amoled**. Amoled uses true black (`#000000`) for its background and surfaces, so an AMOLED panel can switch those pixels off; it is a distinct scheme, not another name for Dark. `ui/theme/AppNightMode.kt` maps the chosen mode to an app-level night mode (`Auto`, `No` or `Yes`), which `MainActivity` applies alongside the window background and system-bar appearance so the bars match the app. The bars are hidden app-wide.

One platform caveat: the system draws the *starting window* before the process exists, so the very first cold start cannot read the saved mode from DataStore. Applying the saved choice once at app level requests the matching night mode, but the platform starting window is resolved from the system configuration: on stock Android with the system in light mode the splash is still white (~1.2 s) even for Amoled, while MIUI launches dark. Only the app's first composed frame is guaranteed dark. The platform may recreate the activity once when the night mode changes; a guard, together with the helper's unchanged-value check, stops that from repeating.

## Orientation

Locked to portrait twice over: `android:screenOrientation="portrait"` in the manifest (verified in the built APK) and a `requestedOrientation` re-assertion in `MainActivity.onCreate`, so a dialog or library cannot unlock landscape. The activity declares `configChanges` including `orientation`, so a rotation would not recreate it — the lock means the user never sees one.

## Testing

1779 JUnit 4 tests, all JVM — no emulator, no instrumentation, no Robolectric. That is possible precisely because engines are pure Kotlin.

```
app/src/test/java/com/calcsuite/engine/
    advanced/    algebra/     calculus/     convert/
    datetime/    electrical/  finance/      geometry/
    health/      other/
app/src/test/java/com/calcsuite/ui/       # UI logic: graph viewport, formatting, prompt policy
app/src/test/java/com/calcsuite/updater/  # version rules, release reader, update decision, notes
app/src/test/java/com/calcsuite/ai/       # tool registry and argument checking
```

plus `core/` and `expr/` for the shared numerics and the expression language.

Conventions:

- Exact expectations compare with `BigDecimal.compareTo`, not `equals`, so scale differences do not fail a test.
- Approximate expectations compare with a tolerance.
- Every mode, every invalid-input path and the zero/negative edges are covered.
- Every bug fix ships with a test that fails before it — including the Taylor "undefined derivative" and the missing subtraction rule in the integrator.

## Things deliberately not done

- **No ViewModels in tool screens.** With one field each and results computed from them, Compose state plus `rememberSaveable` is sufficient; a ViewModel per tool would be ceremony.
- **No DI framework.** One container object is the whole graph.
- **No database.** A bounded JSON array in DataStore is the right size for the problem.
- **No state-management library.** No Redux, no Molecule; `remember(inputs) { … }` covers it.

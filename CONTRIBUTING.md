# Contributing to Calculator Suite

Thanks for considering a contribution. Bug reports, documentation fixes and code are all welcome.

By participating you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md). The project is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE) — contributions are accepted under the same terms.

## What is useful

The most valuable contributions, in rough order:

1. **A wrong number.** If a calculator returns an incorrect result, that is a serious bug. Open an issue with the exact input, what it produced, and what you expected — or send a failing unit test.
2. **A missing unit, constant or unit test.** The engines are pure Kotlin and easy to test.
3. **Documentation that does not match the code.** Docs drifting from behaviour is a real defect here.
4. **A new tool.** Open an issue first so we can agree on scope before you write it.

## Development setup

### Prerequisites

| | |
|---|---|
| JDK | 17 or newer (CI uses Temurin 21) |
| Android SDK | Platform **API 36** + Build-Tools 36 |
| Device/emulator | Android 8.0 (API 26) or newer for manual testing |

### Getting set up

```bash
git clone https://github.com/JuggyBob/CalculatorSuiteAndroid.git
cd CalculatorSuiteAndroid

# Point the build at your SDK (this file is git-ignored)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# or export ANDROID_HOME=/path/to/Android/sdk

./gradlew :app:testDebugUnitTest     # run the suite
./gradlew :app:assembleDebug         # build the APK
./gradlew :app:installDebug          # install onto a connected device
```

There is also a `.editorconfig`; IntelliJ IDEA and Android Studio pick it up automatically.

### Before you push

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

All three must pass. CI runs exactly this command.

## Project layout

```
app/src/main/java/com/calcsuite/
├── core/            # shared numerics (Num, CalcResult) — no Android dependencies
├── expr/            # the scientific calculator's expression language
├── engine/          # pure calculation engines, one package per domain
├── data/            # tool registry, settings/history repositories, icons
├── ui/
│   ├── components/  # shared Compose widgets (forms, result cards, scaffolding)
│   ├── screens/     # main menu, search, settings, history, scientific, onboarding
│   ├── theme/       # colour, type, shape
│   └── tools/       # one screen per tool, grouped by category
└── MainActivity.kt  # single activity
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for detail.

## Coding conventions

These are the rules the existing code follows. Please match them so diffs stay readable.

### Hard rules

1. **No placeholder or non-functional UI.** Every visible control must do something real. If you cannot implement it, do not ship the button.
2. **Mathematically correct, with validation.** Never divide by zero silently, never emit `NaN` or `Infinity` as a normal result, never claim exactness you do not have.
3. **Engines are pure.** No Android framework types in `engine/` or `core/`. `java.time`, `java.math` and the JDK are fine. An engine function must be independently testable on the JVM.
4. **Return `CalcResult`, never throw** on user input:

   ```kotlin
   sealed interface CalcResult<out T> {
       data class Ok<T>(val value: T) : CalcResult<T>
       data class Err(val error: CalcError) : CalcResult<Nothing>
   }

   fun <T> ok(value: T): CalcResult<T>
   fun <T> err(kind: ErrorKind, message: String): CalcResult<T>
   ```

   `ErrorKind` values: `InvalidInput`, `Syntax`, `Domain`, `DivisionByZero`, `Overflow`, `Undefined`, `Inconsistent`, `Unavailable`, `Insufficient`.

5. **Error messages are user-facing.** Short sentences, no trailing punctuation: `"Height must be greater than zero"`, `"r must not exceed n"`.

### Numbers

- Money and general decimals: `BigDecimal` with an explicit `MathContext` (`ALG_CTX` = 34 significant digits, `HALF_UP`).
- Exact integers: `BigInteger`.
- Never `Double` for money. Only use `Double` where you label the result approximate (numerical calculus).
- Parse user text with `Num.parseOrNull` / `Num.parseIntOrNull`, and display with `rememberFmt()`. Copy values use `Num.formatForCopy` for full precision.

### Unit families

A converter is usually a `UnitFamily` in `engine/convert/families/`, defined as base-unit id, explicit `UnitDef(id, label, symbol, factor, offset, group)`, plus `convert` / `factorBetween`. Conversion is affine: `valueInBase = value * factor + offset`. Copy `LengthFamily` as the shape to follow.

Write a custom engine only when a family is *not* linear/affine — fuel consumption is reciprocal, ring and shoe sizes are lookup tables, and numeric-base and Roman numerals are text transformations.

### Screens

Tool screens live in `ui/tools/<domain>/` and take a single `ToolArgs`. Route them through the domain dispatcher (`ui/tools/<domain>/<Domain>Tools.kt`). Reuse the shared components in `ui/components/` rather than inventing new ones — do not add a component unless three or more screens need it.

- Screen state goes in `rememberSaveable`.
- Recompute live where sensible (`remember(inputs) { … }`); otherwise use a primary **Calculate** button.
- Record to history exactly once per user action via `args.onRecord(inputSummary, resultText)`.
- Validation errors go next to the field or in the result card — never a crash, never a bare "Error".
- Explain formulas with `FormulaCard` and intermediate steps with `StepsCard`; keep advanced controls inside `ExpandablePanel`.
- Units belong in labels; distinct quantities get their own `ResultRow`.
- Every icon button needs a `contentDescription`.
- Make sure long labels degrade gracefully — put the label above the value rather than letting a `ResultRow` wrap one character per line.

### Tests

Tests live in `app/src/test/java/com/calcsuite/engine/<domain>/<X>Test.kt` using JUnit 4 (`org.junit.Test`, `org.junit.Assert.*`). They must run on the JVM with no Android dependency or emulator.

```kotlin
// exact expectations
assertEquals(0, actual.compareTo(BigDecimal("36")))

// approximations — compare with a tolerance
assertTrue(abs(actual - expected) < 1e-9)
```

Cover every mode, invalid input, and the zero/negative edges. Every bug fix should come with a test that failed before it.

## Branches and commits

- Branch from `main` with a descriptive name: `fix/calculus-negative-exponent`, `docs/roadmap`, `tool/spin-decay`.
- Write commit messages in the imperative: *"Fix Taylor series for constants"*, not *"fixed taylor"*.
- Keep a commit focused; unrelated changes in the same commit make review harder.
- Formatting churn and refactoring in a bug-fix PR are unwelcome — extract them first.

## Pull requests

1. Branch from `main`.
2. Make your change, with tests.
3. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` locally.
4. Open the PR against `main` and fill in the template: what changed, why, how you verified it.
5. Link any related issue with `Fixes #123`.

A PR that changes behaviour should also update the affected documentation in `docs/`. If you change a tool's inputs, modes or errors, update `docs/FEATURES_AND_TOOLS.md` in the same PR.

### Adding a new tool

1. Open an issue first to agree on scope, inputs and outputs.
2. Add the `ToolDef` to `data/ToolRegistry.kt` — id, title, category, description, search synonyms, icon. The registry is the single source of truth; the menu, search and category screens all read from it.
3. Write the engine in `engine/<domain>/`, pure and returning `CalcResult`.
4. Write the screen in `ui/tools/<domain>/` and route it in `<Domain>Tools.kt`.
5. Add the icon to `data/ToolIcons.kt` if it needs a custom glyph.
6. Add unit tests and a `docs/FEATURES_AND_TOOLS.md` entry.
7. Verify on a device or emulator before opening the PR.

## Issues

Use the templates under `.github/ISSUE_TEMPLATE/`. When reporting a calculation problem, always include:

- the tool,
- the exact input(s) and units you entered,
- the result shown,
- the result you expected, and why.

That is usually enough for a bug to be reproduced and fixed without a follow-up question.

## Security

Do not report security vulnerabilities through public issues. See [SECURITY.md](SECURITY.md).

## Licence

By contributing you agree that your contribution is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE), the same terms as the rest of the project.

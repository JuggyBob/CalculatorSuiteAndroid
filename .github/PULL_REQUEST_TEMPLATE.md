## What this changes

<!-- What the PR does, in one or two sentences. -->

Fixes #

## Why

<!-- The problem this solves. Link the issue. -->

## How it was verified

<!--
Be specific. "Ran the new unit test and it fails without the fix" is useful.
"This should work" is not.
-->

- [ ] Added or updated unit tests (`./gradlew :app:testDebugUnitTest`)
- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passes locally
- [ ] Checked on a device or emulator (Android 8.0+)

<!-- Device/emulator used, and what you saw: -->

## Checklist

- [ ] No placeholder or non-functional UI — every control does something real
- [ ] No unrelated changes, refactoring or formatting churn in this branch
- [ ] Engines stay pure Kotlin (no Android types in `core/`, `expr/`, `engine/`)
- [ ] User-facing error messages are short sentences without trailing punctuation
- [ ] Money uses `BigDecimal`; integer maths uses `BigInteger`; `Double` only for results labelled `≈`
- [ ] Every new mode/field/error is documented in `docs/FEATURES_AND_TOOLS.md`
- [ ] Tests cover each mode, invalid input, and the zero/negative edges
- [ ] Accessibility: every new icon button has a `contentDescription`
- [ ] Long labels degrade gracefully rather than wrapping one character per line

## Breaking changes

<!-- Any change to existing behaviour, inputs, units or stored data. -->

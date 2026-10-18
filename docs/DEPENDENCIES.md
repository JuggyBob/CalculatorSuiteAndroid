# Dependencies

Every direct dependency, as declared in `app/build.gradle.kts`, with the purpose it serves and its licence. All are open-source; none are under a noncommercial or copyleft restriction, and none conflict with the project's PolyForm Noncommercial 1.0.0 licence.

> **Licensing note.** The project's own [LICENSE](../LICENSE) covers this repository's code only. Third-party libraries keep their own terms, and their notices apply wherever their code is redistributed. The list below is the complete set of direct dependencies; each brings its own transitive set.

## Build tooling

| Dependency | Version | Licence | Purpose |
|---|---|---|---|
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | Android build system |
| Kotlin Android plugin | 2.2.10 | Apache-2.0 | Kotlin support |
| Kotlin Compose compiler plugin | 2.2.10 | Apache-2.0 | Compile-time support for `@Composable` (bundled with Kotlin 2.x; no separate Compose compiler version to manage) |
| Gradle | 8.13 | Apache-2.0 | Build execution, via the committed wrapper |

## Android runtime

| Dependency | Version | Licence | Purpose |
|---|---|---|---|
| `androidx.core:core-ktx` | 1.16.0 | Apache-2.0 | Kotlin extensions for the Android platform |
| `androidx.activity:activity-compose` | 1.10.1 | Apache-2.0 | `ComponentActivity` + `setContent`, the single-activity host |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.4 | Apache-2.0 | `collectAsStateWithLifecycle`, so flows are collected with lifecycle awareness |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | Apache-2.0 | ViewModel support for Compose |
| `androidx.navigation:navigation-compose` | 2.8.9 | Apache-2.0 | The single-activity navigation graph (`NavHost`, typed routes) |
| `androidx.datastore:datastore-preferences` | 1.1.7 | Apache-2.0 | Typed, transactional key/value persistence for settings, history and cached rates |

## Jetpack Compose

Pinned through the Compose BOM, currently **2026.05.01**.

| Dependency | Version | Licence | Purpose |
|---|---|---|---|
| Compose BOM | 2026.05.01 | Apache-2.0 | Aligns every `androidx.compose.*` artifact to a compatible set |
| `androidx.compose.ui:ui` | from BOM | Apache-2.0 | Core Compose runtime, layout and drawing primitives |
| `androidx.compose.ui:ui-graphics` | from BOM | Apache-2.0 | `Color`, `ImageVector`, `Canvas`, `Path` — used by the diagrams and the graph |
| `androidx.compose.ui:ui-tooling-preview` | from BOM | Apache-2.0 | `@Preview` support |
| `androidx.compose.foundation:foundation` | from BOM | Apache-2.0 | `LazyRow`, gesture detectors, `pager`, shape/clip modifiers |
| `androidx.compose.material3:material3` | from BOM | Apache-2.0 | Material 3 components: `Scaffold`, `TopAppBar`, `TextField`, `Slider`, `AlertDialog`, `Surface` |
| `androidx.compose.material:material-icons-extended` | from BOM | Apache-2.0 | The extended icon set used by the tool registry (category and tool glyphs) |

`androidx.compose.foundation:foundation` supplies `androidx.compose.foundation.pager.HorizontalPager`, used by the onboarding flow's horizontal sliding animation.

## Test-only

| Dependency | Version | Licence | Purpose |
|---|---|---|---|
| `junit:junit` | 4.13.2 | EPL-1.0 | The test framework — all 910 tests are JVM unit tests |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.7.3 | Apache-2.0 | Coroutine test dispatchers |
| `org.json:json` | 20250107 | Public Domain | A real `org.json` implementation, because the `android.jar` stub throws at runtime in JVM unit tests. Used to test currency-rate JSON parsing. |

## Platform APIs used directly

These are part of the Android platform, not third-party dependencies, but they are load-bearing enough to name:

| API | Used for |
|---|---|
| `java.math.BigDecimal` / `BigInteger` / `MathContext` | Exact decimal and integer arithmetic throughout every engine |
| `java.time` | All date and time arithmetic (Age, Date math, Interval) |
| `java.net.HttpURLConnection` | The single currency-rate HTTPS request |
| `org.json` | Parsing the currency-rate response (real implementation supplied by the test-only dependency above) |
| `java.security.SecureRandom` | The optional cryptographically secure mode of the Number generator |
| `android.content.pm.ActivityInfo` | The portrait orientation constant |

## Deliberately absent

Worth stating, because their absence is a design decision:

- **No analytics** (Firebase, Amplitude, Sentry, Crashlytics)
- **No advertising** (AdMob or otherwise)
- **No ad mediation**
- **No crash reporting / diagnostics upload**
- **No remote configuration**
- **No HTTP client library** (OkHttp, Retrofit) — `HttpURLConnection` covers the one request
- **No image-loading library** (Coil, Glide) — the app has a launcher icon and inline diagrams, nothing remote
- **No DI framework** (Hilt, Koin, Dagger) — `AppContainer` is a small manual holder in `CalculatorApp.kt`
- **No database** (Room, SQLite) — DataStore Preferences holds settings, history and cached rates
- **No web view** and no dynamic code loading

## Assets

| Asset | Origin | Notes |
|---|---|---|
| `ic_launcher_image.png`, `ic_launcher_monochrome.png` | Original artwork supplied by the project author | Adaptive-icon background, foreground and monochrome layers. Authored for this project, not third-party. |
| `CalculusIcon` (`data/ToolIcons.kt`) | Hand-authored `ImageVector` | Drawn from stroked centrelines as an inline Compose vector — no external asset, no third-party licence. |
| All other tool and category icons | `androidx.compose.material.icons` | From AndroidX, Apache-2.0. |

There are no bundled fonts, no copied source from other projects, and no third-party images.

## Updating a dependency

Bump the version in `app/build.gradle.kts`, then:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The engines are pure Kotlin, so a breaking change in an AndroidX artifact rarely reaches them — but the unit tests will catch any behavioural change in `Num`, the expression parser or the calculus engines, which is the main reason they are so numerous.

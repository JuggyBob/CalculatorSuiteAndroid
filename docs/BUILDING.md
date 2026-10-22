# Building Calculator Suite

Everything below was run on the versions stated here. Commands are cross-platform unless noted.

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| **JDK** | 17 or newer | CI uses Temurin **21**. The Gradle toolchain targets JVM 17 bytecode. |
| **Android SDK Platform** | **API 36** | `compileSdk = 36`, `targetSdk = 36`. |
| **Android SDK Build-Tools** | 36.x | Installed automatically by the SDK Manager. |
| **Gradle** | 8.13 | Supplied by the wrapper — do not install it separately. |
| **Android Gradle Plugin** | 8.13.2 | |
| **Kotlin** | 2.2.10 | Includes the Compose compiler plugin. |
| Device / emulator | Android 8.0 (API 26)+ | Only needed for installing and manual testing. |

### Pointing the build at your SDK

The build finds the SDK in one of two ways. Either is fine; **both are machine-specific and neither is committed** (`local.properties` is git-ignored).

```bash
# Option A — local.properties
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# Option B — environment variable
export ANDROID_HOME=/path/to/Android/sdk
export ANDROID_SDK_ROOT=/path/to/Android/sdk
```

> **Note for contributors:** an absolute `org.gradle.java.home=` line in `gradle.properties` will break the build on any machine but yours. Do not commit one — set `JAVA_HOME` in your shell instead.

## Getting the source

```bash
git clone https://github.com/JuggyBob/CalculatorSuiteAndroid.git
cd CalculatorSuiteAndroid
```

## Everyday commands

```bash
# Run the 1410 JVM unit tests
./gradlew :app:testDebugUnitTest

# Run Android Lint
./gradlew :app:lintDebug

# Build the debug APK
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Install onto a connected device or running emulator
./gradlew :app:installDebug

# Everything CI runs, in one go
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`. In PowerShell the JDK must be visible first:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Reading the test report

```bash
./gradlew :app:testDebugUnitTest
```

- Human-readable: `app/build/reports/tests/testDebugUnitTest/index.html`
- Machine-readable: `app/build/test-results/testDebugUnitTest/*.xml`

Run a single suite or a single test:

```bash
./gradlew :app:testDebugUnitTest --tests "com.calcsuite.engine.calculus.*"
./gradlew :app:testDebugUnitTest --tests "com.calcsuite.core.NumTest.separatorStyle*"
```

## Build variants

| Variant | Command | Output |
|---|---|---|
| `debug` | `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| `release` | `:app:assembleRelease` | `app/build/outputs/apk/release/app-release.apk` (signed when the signing properties are present, `app-release-unsigned.apk` otherwise) |

The release build is **not minified** (`isMinifyEnabled = false`) and `proguard-rules.pro` is intentionally empty — the app uses no reflection beyond platform APIs, so nothing needs keeping.

### Producing a signed release

The signed APKs published in the 1.0.0, 2.0.0, 3.0.0, 3.0.1, 3.0.2 and 3.0.3 releases were produced with a locally held key, and all of them were signed with the **same key**, so each release installs straight over the one before it.

The signing configuration is already in `app/build.gradle.kts`, and it is used only when the properties are present, so an ordinary checkout still builds an unsigned release and the continuous build is unaffected. Create a keystore and reference it from `~/.gradle/gradle.properties` (never from a file in the repository):

```properties
RELEASE_STORE_FILE=/absolute/path/to/your/release.jks
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=…
RELEASE_KEY_PASSWORD=…
```

Then build the release:

```bash
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

The four properties can also be given in the environment, which keeps them out of the shell history and out of any file:

```powershell
$env:ORG_GRADLE_PROJECT_RELEASE_STORE_FILE = "C:\path\to\release.p12"
$env:ORG_GRADLE_PROJECT_RELEASE_STORE_PASSWORD = $password
$env:ORG_GRADLE_PROJECT_RELEASE_KEY_ALIAS = "key0"
$env:ORG_GRADLE_PROJECT_RELEASE_KEY_PASSWORD = $password
.\gradlew.bat :app:assembleRelease
```

> Never commit a keystore, its passwords, or a properties file containing them. `.gitignore` already excludes `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `keystore.properties` and `.env*`, but a guard rail is not a substitute for care.

## Verifying an APK

```bash
# Package, version and SDK levels
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging app-release.apk | head -3

# Manifest internals (screen orientation, permissions)
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump xmltree app-release.apk --file AndroidManifest.xml

# Signature scheme and signer certificate
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app-release.apk

# Checksum
sha256sum app-release.apk          # Linux / macOS / Git Bash
Get-FileHash app-release.apk -Algorithm SHA256   # PowerShell
```

The published 3.0.1 APK verifies under **APK Signature Scheme v2** and reports `versionName='3.0.1'`, `versionCode='5'`, `minSdkVersion='26'`, `targetSdkVersion='36'`. The 3.0.0, 2.0.0 and 1.0.0 APKs report the same signer, which is what lets 3.0.1 upgrade any of them in place.

## Running the app

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.calcsuite/.MainActivity
```

The app is **portrait-locked** by design, so it will not rotate. That is intentional — see [ARCHITECTURE.md](ARCHITECTURE.md).

On first launch a four-page onboarding flow appears; use **Skip** or **Get started** to reach the main menu.

## Cleaning

```bash
./gradlew clean            # delete build outputs
./gradlew clean --dry-run  # show what would be deleted
```

To clear user data on a device:

```bash
adb shell pm clear com.calcsuite   # wipes history, favourites, settings, cached rates
```

## Troubleshooting

### `SDK location not found`

`local.properties` is missing or wrong. Create it with `sdk.dir=/absolute/path/to/Android/sdk`, or set `ANDROID_HOME`. Verify the directory contains `platforms/android-36`.

### `Unsupported class file major version` / JVM target errors

An old JDK is on the path. Confirm with `java -version` and point at JDK 17 or 21:

```bash
export JAVA_HOME=/path/to/jdk-21
```

### `Failed to find Build Tools revision 36.x`

Install it via the SDK Manager, or set `ANDROID_SDK_ROOT` so AGP finds the right SDK root.

### The build fails on a machine that is not yours

Look for an absolute path committed by accident:

- `org.gradle.java.home=` in `gradle.properties` — should not be there at all
- `sdk.dir=` in `local.properties` — should never be committed

### Tests fail only on CI

Usually a locale dependency. The suite pins `Locale.ROOT` where formatting could drift, but if you add a formatter, make its locale explicit rather than relying on the machine's default.

### Gradle daemon out of memory

`gradle.properties` sets `-Xmx4096m`. If a build still fails, raise it, or run with `--no-daemon` to see the real error.

### The build seems to ignore my changes

```bash
./gradlew --stop          # stop daemons
./gradlew :app:clean
./gradlew :app:assembleDebug
```

Android Studio caches aggressively; **File → Invalidate Caches** is the last resort.

### A tool shows a wrong number

Run the engine's unit test directly and add a failing case:

```bash
./gradlew :app:testDebugUnitTest --tests "com.calcsuite.engine.<domain>.<Engine>Test"
```

Engines are pure Kotlin, so a JVM test is all you need — no emulator.

## Continuous integration

`.github/workflows/android-ci.yml` runs on every push and pull request:

```yaml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4      # Temurin 21
- uses: android-actions/setup-android@v3
- run: gradle/actions/setup-gradle@v4
- run: ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

It runs the real Gradle wrapper from the repository — the workflow never installs a different Gradle version.

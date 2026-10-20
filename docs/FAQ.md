# Frequently Asked Questions

## Installation

### Which Android versions are supported?

Android **8.0 Oreo (API 26)** and newer, up to and including Android 16 (API 36). The release is built against API 36.

### Where do I get the app?

From the [GitHub Releases page](https://github.com/JuggyBob/CalculatorSuiteAndroid/releases). There is no Play Store listing.

### Why does Android warn me about installing it?

Because the APK is not from the Play Store, Android shows a "blocked by Play Protect" or "unknown sources" warning the first time. This is expected for any sideloaded app. Verify the SHA-256 checksum published alongside the APK first, then allow the installation.

### How do I verify the download is genuine?

Each release includes a `.sha256` file.

```bash
sha256sum CalculatorSuite-v2.0.0.apk
# compare the output with the contents of CalculatorSuite-v2.0.0.apk.sha256
```

The APK is signed with a release key, so Android also verifies its integrity during installation.

### Can I install it on more than one device?

Yes. There is no activation, no device limit and no account.

### Will it conflict with the stock calculator or another app?

No. The package is `com.calcsuite` and it installs alongside anything else.

---

## Using it

### Does it need internet?

Only the **Currency converter** does. Every other tool — all 48 of them — works with no network at all. Currency also works offline once rates have been cached, with a warning if the cache is more than 24 hours old.

### Does it work offline / in aeroplane mode?

Yes, with the two exceptions above — the currency converter, and the AI problem solver if you turn it on. There is no telemetry, no update check and no "are you online?" ping, so every other part of the app behaves identically offline.

### Why won't my phone rotate?

Because the app is **portrait-locked by design**. No screen, dialog or fullscreen calculator can rotate. This was a deliberate choice so nothing is ever caught mid-rotation.

### Where did my system bars go?

They are hidden app-wide so the content uses the whole screen. Swipe in from a screen edge to bring them back temporarily.

### What is the difference between `=` and the live preview?

The preview shows what `=` *would* produce, updating as you type. `=` commits the calculation and saves it to history.

### What do MC, MR, M+, M− do?

One stored number. `M+` adds the display to it, `M−` subtracts the display from it, `MR` recalls it into the display, `MC` clears it. Memory is not saved between app launches.

### Why does `sin(30)` give 0.5 and not 30?

Because the angle unit is **DEG** by default. Tap the `DEG` chip to cycle to RAD or GRAD.

### Does the AI problem solver need an internet connection?

Only that one feature does, and only once you have turned it on yourself. Everything else — all 66 tools, the keypad, the history, the settings — works in aeroplane mode.

The AI feature needs a connection because the picture is read by the provider you chose. The key is kept by the phone's keystore, encrypted, and the picture goes to that provider and nowhere else.

### Does the AI problem solver work with any model?

No. The model has to be one that can **read pictures**. The app cannot tell for certain which models can, so it says plainly what happened: if the provider refuses the picture, you get *"This model cannot read images. Choose a model that supports pictures."* It never moves you to another model on its own, because that would cost you money you did not agree to spend.

### Will the AI answer my homework for me?

It reads the problem and works it out with Calculator Suite's own engines, so the working it shows is the app's working and can be checked. It will also tell you when it is **not sure** — if a symbol in the picture could be read in two ways, it says so and asks you which you meant rather than choosing for you. And if the reading is wrong, you can correct the inputs and have the tool run again.

### Does the Calculus tool use degrees?

No — it always works in **radians**. That is the convention for calculus. Use `deg(x)` and `rad(x)` if you need to convert explicitly.

### What does `≈` mean?

That the result was computed numerically rather than symbolically. The panel underneath names the method — adaptive Simpson, RK4, bisection, one-sided evaluation. An exact result has no prefix.

### Why does a Taylor expansion say a derivative is "undefined"?

It should not, for a polynomial. If the 4th or higher derivative of a polynomial is reported as undefined, that is a bug — please [report it](https://github.com/JuggyBob/CalculatorSuiteAndroid/issues/new/choose). A derivative that is genuinely undefined at the centre (like the derivative of `1/x` at `x = 0`) *is* correctly reported as undefined.

### The Calculator says "No elementary antiderivative" for something simple

See the [Calculus guide's limitations section](CALCULUS_GUIDE.md#8-limitations--read-this). The indefinite integrator is a pattern matcher: it recognises `1/x` but not `2/x`, and for powers it does not apply the chain factor. **For anything it does not recognise, use the definite integral** — that path is numerical and always works.

### How do I type a superscript or a special character?

You do not have to. `sin(30)` and `x^2` work with ordinary ASCII. The app also accepts `−` (U+2212), `–`, `×`, `÷` and `·` if you paste them from a word processor.

### Can I copy a result?

Yes — every result card has a copy action, and it copies a **full-precision** value rather than the rounded one you see.

---

## Accuracy and results

### How accurate are the results?

Decimal arithmetic is exact internally (34 significant digits) and rounded only for display, at 3–15 significant digits depending on your setting. Integer mathematics — factorials, GCD/LCM, primes, permutations — uses arbitrary-precision integers and is exact.

Numerical methods (definite integrals, ODEs, root scans) are approximate and always labelled `≈`. Their tolerances are stated in the [Calculus guide](CALCULUS_GUIDE.md#2-symbolic-or-numerical).

### Is the prime checker always sure?

Up to 3.3 × 10²⁴, yes — that range is covered by deterministic Miller-Rabin bases. Above it, the tool reports a **strong probable prime**, which is not a proof, and the app labels it that way rather than claiming primality.

### Why does the ellipse perimeter look slightly off?

It uses Ramanujan's second approximation, which is accurate to about 1e-5 relative — far below the display precision, but it is an approximation and not an exact integral.

### Why is the fuel number 7.84 L/100 km not exactly what my car manual says?

Fuel consumption is a reciprocal relationship, not a linear one, so the tool converts through a canonical L/100 km form using exact reciprocals. The constant for one imperial mile per gallon is a 34-digit reciprocal, so no rounding is introduced by the conversion itself. Your car's actual consumption varies with driving conditions.

### Are the exchange rates my bank uses?

No. They are **reference mid-market rates** from a free public provider. Banks, card issuers and exchange services add fees and spread. The app says so in its own data-source note.

### Why does the same number show differently in two tools?

Different tools round for display according to their own significance needs, and all of them use at least ten significant digits. If you see fewer, it is because the value is genuinely short, not because precision was lost.

---

## Compatibility

### Does it work on tablets?

Yes — it is portrait-only and scales to any screen, but there is no tablet-specific two-pane layout. That is on the [roadmap](../ROADMAP.md).

### What are the CPU requirements?

Standard Android arm64-v8a. No unusual permissions, no background services and no battery impact beyond normal use.

### Will it run on a Chromebook?

Through Android-app support, yes.

### Can I translate the app?

Not yet — there is no localisation infrastructure. All strings are in English in the source. If you want to help with this, an issue would be a good start.

---

## Privacy and data

### What data does it collect?

None. There is no analytics, no advertising, no crash reporting, no tracking and no account. Full detail in [PRIVACY.md](../PRIVACY.md).

### What leaves my phone?

Two things, and only those two:

- The **currency converter** makes one request to `open.er-api.com` for reference exchange rates, and sends only the base currency code you picked.
- The **AI problem solver**, if you have turned it on, sends the picture and a short question to the AI provider **you chose**, along with your API key. Nothing else goes with it — no device identifier, no account, no history. The picture is not stored in your history.

There is no telemetry, no analytics, no crash reporting and no advertising anywhere in the app. The full disclosure, including the ten providers and where each one is contacted, is in [Privacy](../PRIVACY.md).

### Is my API key safe?

It is kept by the phone's own **Android keystore**, encrypted with AES/GCM, and never written in plain text, logged, or included in an error message — including when the provider itself repeats the key in its error, which the app rewrites. You can remove it with one button.

### Can I change the colour of the app?

Yes — **Settings → Accent colour**. There are seven presets and a custom colour picker. The accent reaches the buttons, the selection, the icons, the surfaces and the curves in the graphs, while every colour keeps the lightness the app already had, so the contrast is the same whatever you choose. Green is the app's original colour exactly.

Only one thing: if you use the Currency converter, the base currency code you select is sent to `open.er-api.com` in the request URL. Nothing else — no device identifier, no analytics parameters, no data about your other calculations.

### Where is my history stored?

On your device, in the app's private storage, capped at 500 entries. Delete it with **Settings → Clear all history**, or by clearing app storage or uninstalling.

### Is this open source?

**Source-available, but not open source.** It is licensed under the [PolyForm Noncommercial License 1.0.0](../LICENSE), which is a source-available licence, not an OSI-approved one. You can read, run, modify and redistribute it for noncommercial purposes; you cannot use it commercially. See the [licensing section of the README](../README.md#licensing).

### Can I use it in my company's app?

No — that would be a commercial use, which the licence does not permit. The exact terms are in the [LICENSE](../LICENSE); if you need broader rights, open an issue and we can discuss it.

### Can I fork and publish my own version?

Yes, for noncommercial purposes, provided you keep the same licence and notices.

---

## Contributing

### How do I report a wrong result?

[Open a bug report](https://github.com/JuggyBob/CalculatorSuiteAndroid/issues/new/choose) with the tool name, the exact inputs and units, the result shown, and the result you expected. That is usually enough to reproduce it.

### Can I add a tool?

Yes, and it is a well-trodden path — see [CONTRIBUTING.md](../CONTRIBUTING.md). Please open an issue first to agree on scope.

### Do I need a Mac or an Android phone to contribute?

No. The engines are pure Kotlin and the entire test suite runs on a JVM — `./gradlew :app:testDebugUnitTest`. You only need a device or emulator for UI work.

### What is the build command?

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

That is exactly what CI runs. Details in [BUILDING.md](BUILDING.md).

---

## Still stuck?

Read [SUPPORT.md](../SUPPORT.md).

# User Guide

How Calculator Suite is put together, what to type, what the messages mean, and how to get the common jobs done.

## Contents

1. [First launch](#1-first-launch)
2. [The main screen](#2-the-main-screen)
3. [Finding a tool](#3-finding-a-tool)
4. [Entering numbers](#4-entering-numbers)
5. [The scientific calculator](#5-the-scientific-calculator)
6. [Using a tool](#6-using-a-tool)
7. [Settings](#7-settings)
8. [History and favourites](#8-history-and-favourites)
9. [The AI problem solver](#9-the-ai-problem-solver)
10. [Error messages](#10-error-messages)
11. [Common workflows](#11-common-workflows)
12. [Things to know](#12-things-to-know)

---

## 1. First launch

The first time you open the app you get four welcome pages:

1. **Welcome** — what the app is
2. **Organised** — the eight categories and how many tools each holds
3. **Mathematics** — the calculus workbench
4. **Make it yours** — the settings

Use **Next** to move forward, **Previous** to go back, **Get started** on the last page to enter the app, or **Skip** at any point. Pressing the system Back button walks the pages backwards, and on the first page it skips onboarding.

You only see these once. If you skip and later want to see them again, clear the app's storage in Android settings (this also clears history and settings).

## 2. The main screen

From top to bottom:

| Element | What it does |
|---|---|
| **Calc Suite** | Title, and a count of the available tools |
| **History icon** | Your saved calculations |
| **Brain icon** | The AI problem solver — read a mathematical problem from a picture |
| **Gear icon** | Settings |
| **Fullscreen icon** | Expand the keypad to fill the screen; tap again to restore |
| **Search field** | Tap and type keywords |
| **DEG** | The current angle unit — tap to cycle DEG → RAD → GRAD |
| **Σ / fx** | Toggle the live preview of the current expression |
| **Display** | The expression on top, the result beneath |
| **MC MR M+ M−** | Memory: clear, recall, add, subtract |
| **Keypad** | Digits, operators, `%`, `±`, `C`, backspace, `=` |
| **Recently used** | The last tools you opened, if any |
| **Categories** | The eight category cards |

The result previews live as you type. Press `=` to commit it to history.

## 3. Finding a tool

Two ways:

- **Search.** Tap the search field and type what you want. Every tool carries synonyms beyond its name, so `mortgage` finds Loan, `kcal` finds Caloric burn, `octal` finds Numeric base, `tip` finds Tip. Matching is case-insensitive.
- **Browse.** Tap a category card, then a tool. Each category shows its tool count.

From a tool screen you can jump straight to a related one using the suggestions some tools provide.

## 4. Entering numbers

The app accepts the number formats people actually type.

| You can type | Read as |
|---|---|
| `1234` | 1234 |
| `1,234` or `1 234` or `1'234` | 1234 (grouped) |
| `1.5` or `1,5` | 1.5 |
| `1,234.56` or `1.234,56` or `1 234,56` | 1234.56 |
| `1.5e-9` | 0.0000000015 |

**The decimal mark follows your device's region**, and is not configurable — that is deliberate, so nothing you type is reinterpreted. If both `.` and `,` appear, the **last** one is the decimal mark. If a separator repeats, it is digit grouping and every group after the first must be three digits.

What the app will **not** do is guess. `12,345,67` is rejected rather than read as 12345.67, because a malformed grouping separator is far more likely to be a typo than an intention. See [the note in Settings](#thousands-separator) for the one case where this matters.

Results are shown rounded to your chosen precision (3–15 significant digits), but the maths underneath keeps full precision, and the copy action always copies a full-precision value.

## 5. The scientific calculator

Open it from the **Scientific calculator** card on the main screen.

### Controls

| Control | Effect |
|---|---|
| **DEG / RAD / GRAD** | Angle unit. Tap to cycle. It affects trigonometric input and inverse-trigonometric output. |
| **Σ fx** | Toggle the live preview |
| **History icon** | Copy or clear the display |
| **Fullscreen icon** | Expand to fill the screen |
| **MC MR M+ M−** | Memory keys, shared with the main keypad |
| **2nd** | Switches the top two function rows to their inverse/hyperbolic forms |
| **C**, **⌫** | Clear, backspace |
| **`%`** | Percent of the left-hand term: `200 + 10%` → `220` |
| **`±`** | Toggle the sign of the number at the cursor |

### Keypad

| Row | Keys |
|---|---|
| 1 | `sin` `cos` `tan` `π` `e` |
| 2 | `x²` `xʸ` `ln` `log` `√` |
| 3 | `x⁻¹` `(` `)` `n!` `mod` |
| 4 | `2nd` (toggles the two rows above) |

With `2nd` active, rows 1 and 2 become `sin⁻¹ cos⁻¹ tan⁻¹ π e`, `x³ xʸ eˣ 10ˣ ∛`.

### Operators

| Operator | Meaning |
|---|---|
| `+ - × ÷` | Arithmetic. `×` may be typed as `*`, `÷` as `/` |
| `^` | Power, **right-associative**: `2^3^2` = `2^9` = 512 |
| `mod` | Remainder of integer division: `17 mod 5` → `2` |
| `!` | Postfix factorial, exact to 10000! |
| `%` | Percent of the left term |
| `±` | Sign toggle |
| juxtaposition | Implicit multiplication: `2π`, `3(4+5)`, `2sin(30)` |

`−` (U+2212) and `–` (en dash) are accepted anywhere a minus sign is, so pasted text works.

### Constants

| Type | Names |
|---|---|
| π | `pi`, `π`, `p` |
| e | `e` |
| τ (2π) | `tau`, `τ`, `t` |
| Golden ratio φ | `phi`, `φ`, `f` |

They are carried to 36 significant digits.

### Functions you can type (38)

Trigonometric: `sin` `cos` `tan` `csc` `sec` `cot` `asin` `acos` `atan` `atan2(y,x)`
Hyperbolic: `sinh` `cosh` `tanh` `asinh` `acosh` `atanh`
Logarithms and exponentials: `ln` `log(x)` `log(x, base)` `log2` `exp`
Roots: `sqrt` `cbrt` `root(x, n)`
Rounding and sign: `abs` `sign` `floor` `ceil` `round(x)` `round(x, digits)`
Integer maths: `fact(n)` `nCr(n,r)` `nPr(n,r)` `gcd(a,b)` `lcm(a,b)`
Selection: `min(a,b,…)` `max(a,b,…)` (1–8 arguments)
Geometry and angles: `hypot(x,y)` `deg(x)` `rad(x)`

`2nd` is only a shortcut — `asin(30)` and the `sin⁻¹` key produce exactly the same result.

### Reference panels

Below the keypad are three collapsible panels: **Functions on the keypad**, **Functions you can type**, and **Operators & syntax**. They are generated from the same catalogue the evaluator uses, so they cannot drift out of date.

### Memory

`M+` and `M−` add to and subtract from a single stored value; `MR` recalls it; `MC` clears it. The memory is a single number, not four registers, and it is not persisted between app launches.

## 6. Using a tool

Every tool screen has the same shape:

- **Top bar** — title, the tool's one-line description, a favourite star, and a **Reset** action that restores that tool's defaults.
- **Input fields** — clearly labelled, with the unit in the label where relevant.
- **Results** — a headline value in a card, then additional rows for each distinct quantity, then any working steps.
- **Formula card** — the formula used, with each symbol explained.
- **Notes** — assumptions, caveats, and the exact definitions of any constants used.

Two interaction patterns:

- **Live** — converters, Percentage, Average and similar recompute as you change a field.
- **Explicit** — a **Calculate** button where an operation is expensive or the inputs are long.

Results can be **copied** at full precision or **saved** to history from the result card.

## 7. Settings

Open with the gear icon on the main screen.

### Appearance

The **Accent colour** row is where the colour of the app comes from. There are seven preset swatches — green, blue, teal, purple, rose, orange and slate — and a **custom colour** button that opens a picker with a colour square, a hue slider, a hexadecimal field and a preview. **Back to the default green** puts the original colour back.

The accent reaches the buttons, the selection, the icons, the surfaces and the curves in the graphs, so a blue accent gives a blue app rather than blue buttons on green cards. Every colour keeps the lightness the app already had, so the contrast between the text and the background is the same whatever you choose. The green is the app's original colour exactly, so if you never touch this row nothing changes.

The choice is kept between restarts.

| Setting | Options |
|---|---|
| Theme | System, Light, Dark |
| Use system colours | Follows the Android 12+ wallpaper palette |

### Calculation

| Setting | Effect |
|---|---|
| Default angle unit | Degrees, Radians or Gradians — used by the calculators |
| Result precision | 3–15 significant digits, via the slider |
| Thousands separators | Master on/off for digit grouping |
| Separator style | Auto, Comma, Period, Space, Apostrophe |
| Haptic feedback | A short vibration when a calculator key is pressed |

#### Thousands separator

**Separator style** changes how large numbers are *displayed* across the whole app.

`Auto` (the default) keeps the locale-derived behaviour: a comma in most of the world, a space where the decimal mark is a comma. Choosing Comma, Period, Space (a non-breaking space, so a number never wraps mid-value) or Apostrophe overrides that.

The setting is **display-only**. Parsing never consults it — `1,234.56` and `1 234,56` are both always accepted, whatever you choose — so you can never change how a typed expression is understood by changing a display preference. A live preview line under the control shows exactly what the current setting produces.

**The one ambiguous case:** if you pick a separator that is the *same character* as your locale's decimal mark (a period, in a region where the decimal mark is also a period), then a displayed decimal such as `1.234.56` cannot be typed back in unambiguously. The app rejects that rather than guessing, and Settings warns you when you make that combination.

### History

Shows how many calculations are saved, with a **Clear all history** button and a confirmation dialog.

### Data sources

A note explaining that currency conversion uses daily reference rates from a public provider, cached on the device, and that reference rates differ from what banks actually charge. Everything else runs entirely on the device.

## 8. History and favourites

**History** records a calculation when you press `=` on a calculator or save a result from a tool. Each entry stores the tool, a summary of the input, the result, and a timestamp. Up to **500** entries are kept; the oldest are dropped.

Tap an entry to reopen its tool.

**Favourites** — star a tool from its top bar and it appears in your favourites on the main screen.

**Recently used** — the last eight tools you opened are listed on the main screen.

All three are local. Nothing is uploaded.

## 9. The AI problem solver

The **brain icon** on the main screen opens a feature that reads a mathematical problem from a picture and works it out.

### What it actually does

1. You take a photograph of the problem, or pick a picture of one.
2. The picture is shown to you, so you can check it is the right one before anything is sent.
3. The app sends the picture to **the AI provider you chose**, together with the list of the mathematical tools that may be used and the arguments each one takes.
4. The model reads the problem, names the tool it wants, and fills in that tool's arguments.
5. The app **checks the choice** against the registry and the arguments against that tool's own list, and then **runs the engine that tool already uses**.

The mathematics is done by Calculator Suite, not by the model. If the model picks a tool, the answer is that tool's answer, with that tool's working — and a number that was worked out numerically is marked `≈` and says how it was found.

### Setting it up

The feature is off until you turn it on. Open the brain icon, then the **gear icon** beside it.

| Field | What to put |
|---|---|
| **Who answers** | The provider: OpenAI, Anthropic, Google Gemini, OpenRouter, DeepSeek, Groq, Mistral AI, xAI, Together AI or Fireworks AI |
| **Model name** | The model you want to use, typed exactly as the provider names it |
| **API key** | Your key from that provider. It is hidden by default; tap the eye to show it |
| **Save the key** | Stores it, encrypted, in the phone's keystore |
| **Remove the key** | Deletes it |
| **Test the connection** | Sends one short message and tells you whether the key and the model work |

The **model name** is used exactly as you type it, so a model released today can be used today without waiting for an app update.

> **The model must be one that reads pictures.** If the model you chose cannot read images, the request is refused and the app says so. It never moves you to a different model on its own, and it never makes a request you did not ask for.

### Using it

1. Tap **Take a photo**, or **Pick a picture**.
2. Check the preview. **Choose another** throws it away.
3. Tap **Read the problem**.
4. The app shows **what the model read** — the problem in words and the tool it chose — and then the answer, with the working.

If the model was not sure, it says so instead of guessing: the reading is marked **ambiguous** and the question that has to be answered is put to you. If no tool can work the problem out, that is said too.

### When the reading is wrong

Tap **Correct the inputs**. The arguments the model produced are shown in a dialog, one field each, and the tool runs again with what you write. The answer updates, and the expression shown is the one that was actually used.

### Your key and your privacy

- The key is kept by the phone's **keystore**, encrypted. It is never written in plain text, never logged, and never shown inside an error message.
- The picture goes to **the provider you chose** and to nobody else, and only when you press **Read the problem**. It is not stored in your history.
- The request is billed by that provider using your key. **Test the connection** sends one very short message.
- The full disclosure is in [Privacy](../PRIVACY.md).

## 10. Error messages

Errors appear next to the offending field or in the result card, in plain language. There are no error codes.

| Message pattern | What it means |
|---|---|
| *Enter a value* / *Enter a valid number* | The field is empty, or the text is not a number |
| *… must be greater than zero* | A physical quantity (distance, price, count) cannot be zero or negative |
| *… must not be negative* | A magnitude was negative |
| *… must be greater than / less than …* | A value is outside its valid range |
| *… is zero, so … is undefined* | You asked for something divided by zero |
| *r must not exceed n* | A combinatorial bound was violated |
| *Provide one weight per value* | Two lists of different lengths |
| *Temperature cannot be below absolute zero* | The Kelvin equivalent is negative |
| *The function is not defined at x = …* | A Taylor/series centre is outside the function's domain |
| *The limit could not be determined* | No consistent limit exists at that point |
| *… is too large to compute here* | The result would exceed what can be computed exactly |
| *Exchange-rate service returned HTTP …* | The currency request failed; cached rates are used instead |
| *The weights add up to zero* | A weighted mean would divide by zero |

**`≈` means approximate.** A result prefixed with `≈` was computed numerically rather than symbolically, and the panel names the method (adaptive Simpson, RK4, bisection, one-sided evaluation). An exact result has no prefix.

## 11. Common workflows

### "What is 15% of 2,000?"

Open **Percentage** from Algebra, keep the **Percent of** mode, enter `15` and `2000`. The answer is `300`. For a reverse question use **% of** (find the whole) or **What %** (find the share).

### "A price went from 80 to 100 — was that a good deal?"

**Percentage → Change**: starting value `80`, final value `100`. You get `+25%`, captioned "A 25% increase". The tool also reports the absolute change.

### "Split a bill of 84 across 3 people and work out a 15% tip"

**Tip** takes the bill, the tip percentage, the number of people and how to round. It allocates the tip down to whole cents and distributes the remainder, so the shares always add up exactly to the bill — which is what makes it usable in a restaurant.

### "How much is 250,000 at 4.5% over 30 years?"

**Loan**. Enter the principal, the annual rate and the term. You get the monthly payment, the total paid and the total interest, plus an amortisation breakdown and the effect of an extra monthly payment. Money arithmetic here is exact decimal, so the final balance reaches exactly zero rather than leaving a rounding crumb.

### "How fast do I need to walk to catch a train?"

**Speed**, or the **Mileage & trips** tool for distance, fuel and cost together.

### "What's the derivative of x²+3x?"

**Calculus**, leave the default expression, and the `d/dx` operation is already selected. It reports `2x + 3` and cross-checks it numerically at a point you choose. See the [Calculus guide](CALCULUS_GUIDE.md).

### "Convert 100 USD to EUR"

**Currency converter**. It fetches reference rates the first time and caches them, so it keeps working offline; a warning appears if the cached rates are more than 24 hours old.

### "What's C(52, 5)?"

**Combinations**, nCr mode, `n = 52`, `r = 5`. Large results are shown in full — the exact integer is available to copy, not rounded.

## 12. Things to know

- **The app is portrait-only.** It will not rotate. This is deliberate, so no screen or dialog can be caught mid-rotation.
- **System bars are hidden.** Swipe from the screen edge to bring them back temporarily.
- **Everything is local except currency rates.** No analytics, no ads, no account, no tracking. See [PRIVACY.md](../PRIVACY.md).
- **Reference rates are not bank rates.** The currency tool shows reference mid-market rates. Your bank, card issuer or exchange service will differ by fees and spread.
- **Some tools are approximations, and say so.** Notably: the ellipse perimeter uses Ramanujan's second approximation; body fat uses the US Navy circumference method; caloric burn uses MET values; the fuel constant for one imperial mpg is a 34-digit reciprocal. Each is documented in [Features & tools](FEATURES_AND_TOOLS.md).
- **The prime checker is exact up to 3.3 × 10²⁴.** Above that it reports a *strong probable prime*, which is not a proof, and the app labels it accordingly.
- **Not every tool does everything its name suggests.** Where a catalogue description overstates the implementation, it is listed in [ROADMAP.md](../ROADMAP.md) rather than hidden.

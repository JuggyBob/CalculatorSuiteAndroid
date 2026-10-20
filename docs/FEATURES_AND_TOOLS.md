# Features and Tools

Every one of the **66 registered tools** across **8 categories**, with its inputs, the mathematics it actually uses, its results, how it validates input, its limits, and a worked example.

| Category | Tools |
|---|---|
| [Algebra](#1-algebra--10-tools) | 10 |
| [Geometry](#2-geometry--2-tools) | 2 |
| [Converters](#3-converters--23-tools) | 23 |
| [Finance](#4-finance--6-tools) | 6 |
| [Health](#5-health--3-tools) | 3 |
| [Date & Time](#6-date--time--3-tools) | 3 |
| [Other](#7-other--2-tools) | 2 |
| [Calculus](#8-calculus--14-tools) | 14 |
| [Advanced Mathematics](#9-advanced-mathematics--3-tools) | 3 |

**Not a tool, but a feature.** The **AI problem solver** (the brain button beside the history button on the main screen) reads a photograph of a mathematical problem, has a chosen AI provider pick the right tool below, and lets that tool's own engine do the mathematics. It is described in the [User Guide](USER_GUIDE.md#the-ai-problem-solver).

**Notation used throughout.** *Exact* means the result is computed with arbitrary-precision arithmetic (`BigDecimal` at 34 significant digits, or `BigInteger`) and is not rounded until display. *Numerical* means a numeric method was used, the result is prefixed `≈`, and the panel names the method.

**Engine coverage.** In each tool's *Limitations*, the wording is drawn from the source. Where the in-app catalogue description promises more than the implementation delivers, that is called out explicitly and tracked in the [Roadmap](../ROADMAP.md).

---

## 1. Algebra — 10 tools

### Percentage (`algebra.percentage`)

**Purpose.** Everyday percentage arithmetic across eight distinct question types.

**Inputs.** Two mode rows sharing one state:

| Mode | Fields |
|---|---|
| **Percent of** | `Percentage` (unit %), `Value` |
| **% of** (find whole) | `Part`, `Percentage` (unit %) |
| **What %** | `Part`, `Whole (reference)` |
| **Change** | `Starting value`, `Final value` |
| **Original** (reverse a change) | `Final value`, `Percentage change` (%), toggle *The change was an increase* |
| **Successive** | `Starting value`, `Percentage changes` (a list, e.g. `+10, -10`) |
| **Points** | `First percentage` (%), `Second percentage` (%) |
| **Relative** | `First value`, `Second value` |

The `%` on a field is a display suffix only — no unit conversion happens. `15` means 15.

**Method.** Exact decimal. `amount = value × p ÷ 100`; `percentage = part ÷ whole × 100`; `change % = (end − start) ÷ start × 100`; `original = final ÷ (1 ± p ÷ 100)`; `final = base × Π(1 + cᵢ/100)`; `points = p₁ − p₂`; `relative difference = |a − b| ÷ |(a + b) ÷ 2| × 100`.

**Results.** Headline value with a unit, then rows and a steps card. Change mode captions the direction (*"A 25% increase"*). Points mode explains that 5% → 7% is +2 percentage points **and** a +40% relative change — the distinction most people get wrong.

**Validation.** Reference value zero → *"The reference value is zero, so a percentage of it is undefined."* Starting value zero → *"The starting value is zero, so a percentage change is undefined."* A decrease of exactly 100% → *"A decrease of exactly 100% leaves nothing, so the original value cannot be recovered."* Two zero values in Relative → *"Both values are zero…"*; values that cancel → *"The two values cancel out…"*

**Limitations.** No discount, compound-interest or percentage-base mode. The relative-change row in Points mode returns `0` rather than erroring when the second percentage is zero.

**Examples.** 15 % of 200 → **30**. 120 → 150 → change **+30**, percent **25 %**, *"A 25% increase"*. Base 100 with `+10, -10` → 110 then **99**. Relative difference of 10 and 12 → **≈ 18.1818 %**.

**Related.** Average (its dataset parser is reused for the successive list), Ratio, Calculus (Σ/Π).

---

### Average (`algebra.average`)

**Purpose.** Five ways to summarise a data set.

**Inputs.** Mode selector: **Mean**, **Weighted**, **Median**, **Mode**, **Geometric**. Always a `Data set` field (`2, 4, 6`); weighted mode adds `Weights`. Numbers may be separated by **commas, semicolons, tabs or new lines**, and spaces inside a number are treated as digit grouping (`1 234` = 1234).

**Method.** Exact decimal. Mean `Σxᵢ ÷ n`; weighted `Σ(xᵢ·wᵢ) ÷ Σwᵢ`; median is the middle value, or the mean of the two middles when *n* is even; mode is every value with the highest frequency (ascending); geometric `(Πxᵢ)^(1/n)` via a Newton *n*-th root.

**Results.** Headline value plus a **Data summary** card: Count, Sum, Minimum, Maximum, Sorted, Modes. Weighted mode adds one row per `value × weight`. Geometric mean is captioned *"Approximate — irrational root"* when the result needs all 15 digits.

**Validation.** Empty list → *"Enter at least one number."* Unreadable token → *"Could not read \"x\" as a number."* Mismatched lengths → *"Provide one weight per value: N values but M weights."* Negative weights, weights summing to zero, and any non-positive value in Geometric are all rejected with specific messages.

**Limitations.** No trimmed mean, standard deviation, variance, range or percentile. `Mode` returns nothing (rather than an error) when every value occurs once.

**Examples.** `2, 4, 6` → mean **4**. `2, 4, 6, 9` → median **5**. Weights `1, 2, 3` → **≈ 4.6667**. Geometric mean of `2, 4, 8` → **4**.

**Related.** Prime checker, Number generator (paste a generated list in).

---

### Proportion (`algebra.proportion`)

**Purpose.** Solve `a/b = c/d` — or the inverse `a·b = c·d` — for whichever term is unknown.

**Inputs.** Mode: **Direct a/b = c/d** or **Inverse a·b = c·d**. An **Unknown** selector (`a`, `b`, `c`, `d`). Four fields, labelled `a (numerator, left)`, `b (denominator, left)`, `c (numerator, right)`, `d (denominator, right)` in direct mode and `x₁/y₁/x₂/y₂` in inverse. The unknown field is disabled and displays the solved value.

**Method.** Exact decimal via cross multiplication. The tool then **verifies** by comparing the two cross products — exactly, or *"agree at 34 significant digits"* if the difference is below 10⁻²⁸ relative.

**Results.** The solved equation (`2/3 = 8/12`), a Verification row, and a six-step card.

**Validation.** Fewer than three knowns → *"Three of the four values are needed; only 2 given."* A zero divisor → *"d is zero, so d cannot be found by division."*

**Limitations.** Exactly one unknown at a time. Numbers only — no expressions, units, or fractional text like `1/3` (use the decimal form). The formula card's variable labels read `a, b`/`c, d` even in inverse mode.

**Examples.** Direct, unknown `d`, with a=2, b=3, c=8 → **12**; *"Both cross products are 24 — the proportion holds exactly."* Inverse with the same values → **0.75**.

**Related.** Ratio, Equations (Linear).

---

### Ratio (`algebra.ratio`)

**Purpose.** Simplify, scale, split totals and compare ratios.

**Inputs.** Six modes. Terms are separated by `:` and may be decimals or fractions (`3/4`). **Simplify** takes `Ratio`; **Scale** adds `Scale factor`; **Split total** adds `Total to split` and `Decimal places for money`; **Unknown part** adds `Known term number (1-based)`, `Known share` and `Unknown term number (1-based)`; **Combine** and **Compare** take `First ratio` and `Second ratio`.

**Method.** Simplification clears decimals to integers and divides by the GCD — exact. Scaling multiplies. **Split uses a largest-remainder allocation**: each share is floored to the requested precision and the leftover is handed out one unit at a time, largest fractional part first, so the shares sum to the total exactly. Combine links two ratios through the LCM of the shared term. Compare cross-multiplies exactly.

**Results.** Headline ratio plus mode-specific rows: Terms, the GCD used, `× 10`/`÷ 10` equivalents, one row per share, the rescaled intermediates, or a comparison verdict (*"First ratio is smaller"* / *"The ratios are equal"* / *"First ratio is greater"*).

**Validation.** Fewer than two terms; unreadable term; a zero denominator within a term; negative or zero terms (depending on mode); a non-positive scale factor; term numbers outside range or equal; decimal places outside 0–12; more than 30 decimal places in Simplify.

**Limitations.** No unit conversion. Split is capped at 12 decimal places, and at low precision the shares are no longer exactly proportional — the caption says so. Compare handles two-term ratios only.

**Examples.** Simplify `12 : 18` → **2 : 3** (*"Divided by the greatest common divisor 6"*). Split 100 in `2 : 3` → **40** and **60**. Combine `2 : 3` and `2 : 3` → **4 : 6 : 9**.

**Related.** Proportion, GCF & LCM.

---

### Equations (`algebra.equations`)

**Purpose.** Solve linear, quadratic and 2×2 systems exactly, with steps.

**Inputs.** Modes **Linear**, **Quadratic**, **System 2×2**.

- Linear: `Equation` (`2x + 4 = 10`) and `Variable` (default `x`, matched case-insensitively; one letter or word).
- Quadratic: `Equation` (`x^2 - 3x + 2 = 0`) and `Variable`.
- System: six numeric fields `a₁ b₁ c₁ a₂ b₂ c₂` for `a₁x + b₁y = c₁`, `a₂x + b₂y = c₂`.

**Syntax.** Decimals with `.` or `,`; `+ − × * ÷ / ^`; parentheses and brackets; implicit multiplication (`2x`, `3(x+1)`, `x(x−1)`); fractional coefficients (`1/2 x + 3 = 4`); exactly one `=` sign, with the variable allowed on both sides. A **Syntax help** panel states all of this.

**Method.** Both sides are parsed into degree-≤2 polynomials and subtracted. Linear: `x = −b ÷ a`. Quadratic: `x = (−b ± √(b² − 4ac)) ÷ 2a`; Δ > 0 gives two real roots sorted ascending, Δ = 0 one double root, Δ < 0 complex roots `a ± bi`. Square roots use a Newton iteration at 34 digits, so `√` is exact where it should be. Systems use Cramer's rule.

**Results.** `x = …` / `x₁ = …, x₂ = …` / `x = …, y = …`, plus rows for the standard form, the coefficients, the discriminant, and the real/complex roots. Degenerate cases are named explicitly: *"Every value of x"*, *"No solution"*, *"Infinitely many solutions"*. A substitution check confirms the root satisfies the original equation.

**Validation.** Missing or duplicate `=` sign; unbalanced brackets; *"This equation is quadratic; switch to the Quadratic mode"*; *"Only exponents 0, 1 and 2 are supported"*; *"Dividing by an expression containing x is not supported"*; *"The product is above degree 2, which this tool does not solve"*; an unknown identifier.

**Limitations.** Degree is capped at 2 — cubics and quartics are rejected, not approximated. One variable name per equation. Division by a constant only. No factoring, no Vieta, no rational-root theorem, no 3×3 systems.

**Examples.** `2x + 4 = 10` → **x = 3** (*"Substitute back: left side 10, right side 10"*). `x^2 - 3x + 2 = 0` → **x₁ = 1, x₂ = 2**. `x^2 + 1 = 0` → **x = 0 ± 1i**.

**Related.** Proportion; Calculus (numeric root scanning for arbitrary functions).

---

### GCF & LCM (`algebra.gcf_lcm`)

**Purpose.** Greatest common factor and least common multiple of a set of whole numbers.

**Inputs.** A single `Numbers` field (`18, 24, 36`), separated by commas, semicolons, tabs or new lines. Whole numbers only; negatives are used as their absolute values.

**Method.** Exact `BigInteger`. GCF by Euclid; LCM folded pairwise as `acc ÷ gcd(acc, v) × v`. Zeros are skipped for the GCF; if any input is zero the LCM is `0`. **Prime factorisations** are added for up to four values of at most 18 digits, together with the common prime powers (minimum exponent) and the union of prime powers (maximum exponent).

**Results.** GCF headline, LCM row (emphasised), Count, Values, then a **Prime factorizations** card, then a steps card showing the Euclid chain and `LCM = |a × b| ÷ gcd`.

**Validation.** Empty input; a non-integer token; all-zero input.

**Limitations.** Whole numbers only. Factorisation is suppressed above four values or above 18 digits (*"Prime factorizations are skipped because a value is too large to factor quickly"*).

> **Known defect.** When every input is zero the engine returns an `Undefined` error, but the screen reads only the success path — so nothing at all is rendered. Tracked in the [Roadmap](../ROADMAP.md#smaller-items).

**Examples.** `18, 24` → GCF **6**, LCM **72**; factorisations `2 × 3²` and `2³ × 3`; steps *"24 = 1 × 18 + 6"*, *"18 = 3 × 6 + 0"*. `0, 5` → GCF **5**, LCM **0** (*"LCM is 0 (a zero is present)"*).

**Related.** Prime checker (supplies the factorisations), Ratio.

---

### Combinations (`algebra.combinations`)

**Purpose.** nCr combinations, nPr permutations, and a drawing probability.

**Inputs.** Modes **nCr**, **nPr**, **Probability**. The first two need `n (pool size)` and `r (chosen)`, both integers. Probability needs `Population N`, `Special items K (e.g. drawn winners)`, `Items drawn n` and `Exact matches k` — the **hypergeometric** model, drawing without replacement.

**Method.** Exact `BigInteger`. nCr uses a multiplicative product with exact interim divisions — `n!` is never materialised, so `C(1000, 500)` is still exact. nPr is a running product. Probability is `C(K,k) · C(N−K, n−k) ÷ C(N,n)` at 34 digits.

**Results.** Headline value; a **Formula** row; and an **Exact value** card holding the full integer — selectable and copyable — with a digit count. Large results are captioned *"Exact value — scroll the box below to read or copy every digit."* Probability is captioned *"Rounded to 34 significant digits"* and offers a *"One in"* row.

**Validation.** Negative *n* or *r*; `r > n`; K outside 0…N; n outside 0…N; k outside 0…min(n,K); results beyond a size cap (*"The selection is too large to compute here"*); factorials above 100 000.

**Limitations.** Integers only. Probability is hypergeometric only — the engine contains a binomial function but the UI does not expose it. *"One in"* derives from the already-rounded probability, so it is an approximation.

**Examples.** `C(5,2)` = **10**. `P(5,2)` = **20**. The default lottery case N=49, K=6, n=6, k=6 → **≈ 7.1511238 × 10⁻⁸**, i.e. about one in 13 983 816.

**Related.** GCF & LCM, Number generator.

---

### Prime checker (`algebra.primes`)

**Purpose.** Primality test, factorisation and neighbouring primes.

**Inputs.** A single `Number` field. Whole numbers of any size.

**Method.** Exact `BigInteger`. Primality: trial division by all primes ≤ 1000, then Miller–Rabin. Below **3.317 × 10²⁴** the twelve deterministic bases are used and the result is a **proof**; above it, forty fixed bases give a **strong probable prime** — the app labels it as such rather than claiming primality. Factorisation: trial division by primes ≤ 1000, then **Pollard's rho** with Floyd cycle detection, then a verification that the reported factors multiply back to the input.

**Results.** A verdict, rows for Number, digit count, next prime and previous prime, and a **Prime factorization** card. Each verdict carries its justification — a found divisor, or the specific Miller–Rabin witness.

**Validation.** Empty or non-integer input; 0 (which every prime divides); inputs the tool is too large to factor quickly.

**Limitations.** The **UI** caps live factorisation at 20 digits so the screen stays responsive (the engine allows 27). Above the deterministic bound the primality answer is explicitly not a proof. No twin/Sophie-Germain primes, no Fermat/Mersenne tests, no list input.

**Examples.** `97` → **Prime**, *"Proven prime"*, next **101**, previous **89**. `360` → **Composite**, factorisation `2³ × 3² × 5`.

**Related.** GCF & LCM, Combinations.

---

### Number generator (`algebra.random`)

**Purpose.** Random integers or decimals under explicit constraints.

**Inputs.** Mode **Integers** or **Decimals**; `Minimum (inclusive)`, `Maximum (inclusive)`, `How many`. Decimals adds `Decimal places`. Integers adds a **Parity** selector (Any / Even / Odd). A **Generation options** panel holds `Unique values`, an optional `Seed`, and `Cryptographically secure`. Generation is explicit via a **Generate** button.

**Method.** Both endpoints are inclusive. Parity filters the domain by halving it and shifting back. Unique values materialise and Fisher–Yates shuffle a pool up to 20 000 entries, then fall back to rejection sampling. Decimal mode snaps the range onto the decimal grid (`min` rounded **up**, `max` rounded **down**) and draws on the integer grid, so the endpoints stay reachable. A seed gives `java.util.Random(seed)` and is reproducible; the secure toggle switches to `java.security.SecureRandom` and ignores the seed.

**Results.** The first six values, a scrollable **All values** card, and rows for Count, Smallest, Largest, Sum and Distinct. The caption always states the randomness source (*"Secure randomness"*, *"Seeded with 42 — reproducible"*, or *"not cryptographically secure"*).

**Validation.** Count below 1 or above 1000; decimal places outside 0–12; a range containing no value of the requested parity; asking for more unique values than the range holds. Swapped bounds are **not** an error — they are silently corrected with a note.

**Limitations.** At most 1000 values per run and 12 decimal places. No weighted picks, no pick-one-from-a-list, no dice notation. The generated list uses plain `remember`, so it is lost on rotation.

**Examples.** Seed `42`, 1…100, ten values → the identical list every time. Even-only range `1…1` → *"There is no even number in the selected range."*

**Related.** Average, Combinations.

---

### Inequalities (`algebra.inequalities`)

**Purpose.** Solve an inequality, or a system of them, and show the answer as a set and as a picture.

**Inputs.** An **Expression** field, a **Mode** selector — *Single*, *Compound*, *System* or *Two vars* — and, in the two-variable mode, the **Feasible region** drawn on a plane with the point `(x, y)` marked.

**Method.** The expression is parsed into a relation over the variables, the boundary is found exactly where it can be, and the sign of the expression is then scanned either side of every critical point. The two-variable mode draws the half-planes the inequalities describe.

**Results.** The solution in interval notation, the set-builder form, the critical points, a sign chart, and — in the two-variable mode — the region itself.

**Validation.** An expression that cannot be read, a relation with no variable, and a system whose inequalities contradict each other are each reported in words rather than guessed at.

**Limitations.** The single-variable mode is exact for linear and quadratic expressions and numerical beyond that; the scanning is over a bounded interval, so a solution far from the origin can be missed. The two-variable mode handles half-planes and their intersections, not curved boundaries.

**Examples.** `2x + 3 < 11` → `x < 4`. `x^2 - 4 >= 0` → `(-∞, -2] ∪ [2, ∞)`.

**Related.** Equations, Extrema & Optimization.

---

## 2. Geometry — 2 tools

### 2D shapes (`geometry.shapes`)

**Purpose.** Area, perimeter and diagonals of 11 plane figures.

**Shapes.** Rectangle, square, triangle (with several input modes), circle, ellipse, trapezium, parallelogram, rhombus, regular polygon, sector, and more — each with its own field set and formula card. A diagram is drawn to match the selected shape.

**Method.** `GeometryMath` computes square roots by Newton iteration and forward trigonometry by Taylor series with argument reduction, so π-derived quantities keep 34-digit precision. **Inverse** trigonometry falls back to `kotlin.math` doubles and is reported as approximate — the only place in this tool where that happens.

**Validation.** Missing sides for the chosen shape; a negative length; a radius of zero; a triangle whose sides do not close.

**Limitations.** No compound shapes, no arcs-on-shapes, no coordinate geometry.

**Example.** A rectangle of 5 m × 3 m → area **15 m²**, perimeter **16 m**.

**Related.** 3D bodies.

---

### 3D bodies (`geometry.bodies`)

**Purpose.** Volume and surface areas of 9 solids.

**Solids.** Cube, cuboid, sphere, hemisphere, cone, cylinder, triangular prism, rectangular prism, pyramid — each with a diagram and the usual formulas.

**Method.** As above: exact square roots and forward trigonometry, approximate inverse.

**Validation.** Non-positive dimensions; a negative radius.

**Limitations.** No frustums, no prisms with a general polygonal base, no volume by integration.

**Example.** A sphere of radius 3 cm → volume **≈ 113.097 cm³**.

**Related.** 2D shapes.

---

## 3. Converters — 23 tools

Sixteen of these are driven by one generic `UnitFamily` screen; the rest need custom engines. See [Architecture](ARCHITECTURE.md#unit-conversion) for how that works.

### Length (`convert.length`)

14 units, base metre: `nm` `µm` `mm` `cm` `m` `km` `in` `ft` `yd` `mi` `nmi` `au` `ly` `pc`.
All linear, using exact definitions (1 in = 25.4 mm; 1 nmi = 1852 m; IAU 2012 astronomical units).
*Example.* 1 km = **0.6213711922373339696 mi**.
*Limitations.* No mil, furlong, chain, rod, fathom, or US survey foot.

### Area (`convert.area`)

10 units, base m²: `mm²` `cm²` `m²` `km²` `ha` `ac` `in²` `ft²` `yd²` `mi²`. Squared factors are exact squares of the length factors; the hectare and international acre are exact.
*Example.* 1 km² = **≈ 247.1053815 acres**. *Limitations.* No acre-foot, barn, or cubic-to-area shortcuts.

### Volume (`convert.volume`)

17 units, base litre: metric `mm³` `cm³` `mL` `L` `m³`; US `fl oz` `cup` `pt` `qt` `gal`; Imperial `fl oz (Imp)` `pt (Imp)` `qt (Imp)` `gal (Imp)`; and `in³` `ft³` `yd³`. US and Imperial are kept separate because the gallon differs by about 20%.
*Example.* 1 L = **≈ 0.2641720523581484 US gal**.
*Limitations.* Only the US cup exists here — the Imperial cup is in the Cooking converter. No tablespoons or teaspoons (also Cooking), no barrels or bushels.

### Weight & mass (`convert.weight`)

9 units, base kilogram: `mg` `g` `kg` `t` `oz` `lb` `st` `ton (US)` `ton (UK)`. Exact (1 oz = 1/16 lb; 1 st = 14 lb; short ton = 2000 lb; long ton = 2240 lb).
*Example.* 1 kg = **≈ 2.204622621848775807 lb**.
*Limitations.* Masses, not forces — lbf and kgf live in the Force converter and are not interchangeable with lb and kg. No troy ounces, carats or grains.

### Temperature (`convert.temperature`)

3 scales: °C, °F, K — **the only affine converter in the app**. `K = °C + 273.15`, `°F = °C × 9/5 + 32`, computed with a 5/9 factor at 34-digit working precision. The screen shows all three scales at once rather than offering From/To pickers, because the answer to "what is it in the other units?" is rarely just one.
*Example.* 20 °C = **68 °F** = **293.15 K**; −40 °C = −40 °F.
*Validation.* Anything below absolute zero is rejected: *"Temperature cannot be below absolute zero (-273.15 °C / 0 K)"* — including −300 °C, −500 °F and −1 K. −273.15 °C itself is accepted.
*Limitations.* Three scales only; no Rankine, Réaumur or Delisle.

### Speed (`convert.speed`)

5 units, base m/s: `m/s` `km/h` `ft/s` `mph` `kn`. Exact (1 mph = 1609.344/3600; 1 kn = 1852/3600).
*Example.* 100 km/h = **≈ 27.7777777778 m/s**.
*Limitations.* No Mach, no speed of light, no walking/running pace — although the tool is searchable by the keyword `pace`, it has no pace unit.

### Acceleration (`convert.acceleration`)

5 units, base m/s²: `cm/s²` `m/s²` `km/s²` `ft/s²` `g₀`. Standard gravity uses the exact CGPM value 9.80665.
*Example.* 1 m/s² = **≈ 3.280839895013123 ft/s²**.

### Force (`convert.force`)

5 units, base newton: `N` `kN` `dyn` `lbf` `kgf`. Exact (1 lbf = 0.45359237 × 9.80665).
*Example.* 1 kgf = **9.80665 N**.
*Limitations.* No poundal, kip or ton-force.

### Pressure (`convert.pressure`)

10 units, base pascal: `Pa` `hPa` `kPa` `MPa` `bar` `mbar` `atm` `Torr` `mmHg` `psi`. `Torr` and `mmHg` are deliberately listed as separate units sharing one exact factor.
*Example.* 1 bar = **≈ 14.503773773 psi**; 1 atm = 101 325 Pa = 760 Torr.
*Limitations.* No inHg, mmH₂O, kgf/cm² or ksi; no gauge/absolute mode.

### Energy (`convert.energy`)

10 units, base joule: `J` `kJ` `MJ` `cal` `kcal` `Cal` (food) `Wh` `kWh` `eV` `BTU`. The food Calorie is a distinct id sharing kcal's factor. The electronvolt uses the exact 2019 SI elementary charge.
*Example.* 1 kWh = **3 600 000 J** = **≈ 860.420650 kcal**.
*Limitations.* Only the IT BTU; no therm, quad, or erg.

### Power (`convert.power`)

6 units, base watt: `W` `kW` `MW` `hp` (mechanical) `PS` (metric) `BTU/h`. Exact (1 hp = 550 × 0.3048 × 4.4482216152605).
*Example.* 1 kW = **≈ 1.341022089595028 hp**.
*Limitations.* No GW, no BTU/min, no refrigeration-ton equivalent.

### Torque (`convert.torque`)

4 units, base N·m: `N·m` `lbf·ft` `lbf·in` `kgf·m`. Exact (1 lbf·ft = 4.4482216152605 × 0.3048).
*Example.* 1 lbf·ft = **1.3558179483314004 N·m**.
A note stresses that torque is **not** convertible to joules despite the shared dimension.
*Limitations.* No N·mm, kN·m or kgf·mm.

### Angle (`convert.angle`)

7 units, base radian: `°` `rad` `grad` `turn` `mrad` `′` (arcminutes) `″` (arcseconds). The π-derived factors are computed from a 36-digit π, so degrees are not rounded decimals.
*Example.* 1 turn = **6.28318530717958647692528676655900576 rad**; 1° = 60′ = 3600″.
*Limitations.* **No degrees-minutes-seconds input or output**, although the tool is searchable by the keyword `dms`. Arcminutes and arcseconds are plain linear units only.

### Time (`convert.time`)

10 units, base second: `ns` `µs` `ms` `s` `min` `h` `d` `wk` `mo` `yr`.
The calendar units are **convention-based averages**, flagged as such: 1 month = 2 629 746 s (30.436875 days), 1 year = 31 555 952 s (365.2425 days).
*Example.* 1 h = **3600 s** = **60 min**.
*Limitations.* No leap-year handling or per-month lengths. **The catalogue description promises "duration formatting" that does not exist here** — the `1 h 2 min 3 s` formatter lives in the Data-transfer tool. Tracked in the [Roadmap](../ROADMAP.md).

### Volumetric flow (`convert.flow`)

8 rate units, base m³/s: `m³/s` `m³/h` `L/s` `L/min` `mL/s` `gpm (US)` `gpm (Imp)` `ft³/s`.
Two extra panels: **volume from flow rate and time** (the output volume unit is paired automatically with the flow unit) and **time to deliver a volume** (in seconds, minutes and hours).
*Example.* 10 L/min for 60 s → **10 L**. 100 L at 10 L/min → **600 s**.
*Limitations.* No mass flow; the volume-from-flow panel cannot choose an arbitrary output volume unit.

### Data storage (`convert.data_storage`)

16 units, base **bit**: bits `bit` `kbit` `Mbit` `Gbit`; decimal `B` `kB` `MB` `GB` `TB` `PB`; binary `KiB` `MiB` `GiB` `TiB` `PiB`. Both families share the bit base, so mixed conversions are exact and 1 byte is always 8 bits.
*Example.* 1 MB = **8 000 000 bits** = **0.95367431640625 MiB**.
A note warns that operating systems often label binary sizes with decimal names.
*Limitations.* No nibble, word, block or E-prefixed units; deliberately no ambiguous "KB = 1024".

### Data transfer (`convert.data_transfer`)

11 rate units, base bit/s: `bit/s` `kbit/s` `Mbit/s` `Gbit/s` `B/s` `kB/s` `MB/s` `GB/s` `KiB/s` `MiB/s` `GiB/s`.
Two extra panels: **transfer time** for a file size (with a decimal/binary toggle) and **required rate for a deadline**. Durations are rendered as `1 h 2 min 3 s`.
*Example.* 100 Mbit/s = **12.5 MB/s**. A 10 GB file over 100 Mbit/s → **800 s**. 10 GB in 120 s → **≈ 666.667 Mbit/s**.
*Limitations.* Theoretical — a note states 60–90 % of link rate is realistic. No Tbit/s or TB/s.

### Fuel (`convert.fuel`)

5 units, a **reciprocal** family (not linear): `L/100km` `km/L` `mi/L` `mpg (US)` `mpg (Imp)`. Everything is canonicalised through L/100 km using exact reciprocals computed at 34 digits.
A **Trip fuel and cost** panel computes the fuel needed for a distance and its cost at a price per litre, and states which gallon definition it used.
*Example.* 7.84 L/100 km = **≈ 30.00186011 mpg (US)** = **≈ 36.03073167 mpg (Imp)**. A 450 km trip → **35.28 L**, **61.74** at 1.75/L.
*Validation.* Zero consumption and negative values are rejected with distinct messages; distance must be positive.
*Limitations.* Trip cost assumes a price per **litre**; no currency handling, no cost per mile or gallon, no emissions.

### Cooking (`convert.cooking`)

**21 units across two dimensions**, plus a **19-entry ingredient density table**.

- Volume (base mL): metric `mL` `L` `tsp (metric)` `tbsp (metric)` `cup (metric)`; US `tsp` `tbsp` `fl oz` `cup` `pt` `qt` `gal`; Imperial `fl oz (Imp)` `tsp (Imp)` `tbsp (Imp)` `cup (Imp)` `pt (Imp)`.
- Mass (base g): `g` `kg` `oz` `lb`.

The density panel bridges the two dimensions via `mass = volume × density` / `volume = mass ÷ density`, with an ingredient picker that autofills the density and an editable field for a custom value. Densities are sourced from USDA and King Arthur Baking charts.
*Example.* 1 US cup = **236.5882365 mL**. 236.5882365 mL of all-purpose flour at 0.53 g/mL → **≈ 125.391765 g**.
*Limitations.* Fixed density approximations — not per-brand, with no temperature or packing correction. Only four mass units (no mg, stone or ton).
*Validation.* Crossing dimensions without a density → *"Converting between volume and mass requires an ingredient density."*

### Numeric base (`convert.numeric_base`)

Bases **2 through 36**, via `BigInteger` text transformation. Accepts an optional `0b`/`0o`/`0x` prefix (which must match the selected base), spaces and underscores as grouping, and case-insensitive digits.
An always-on card shows binary, octal, decimal and hexadecimal simultaneously. Display toggles offer uppercase letters, prefixes and 4-digit binary grouping (on by default).
A **fixed-width** panel covers 8/16/32/64-bit two's complement, signed or unsigned, with the range spelled out.
*Example.* 255 base 10 → binary `1111 1111`, octal `377`, hex `ff`. −1 in 8-bit signed → `11111111` (range −128 to 127).
*Validation.* An invalid digit names the character, the base and the valid range: *"Character 'z' is not a valid digit in base 2 (valid digits: 0–1)."* Out-of-width values are an overflow error.
*Limitations.* **Integers only** — no fractional or radix-point parsing, no binary-coded decimal, no float bit patterns. Widths are limited to 8/16/32/64.

### Roman numerals (`convert.roman`)

1–3999 in both directions, **strictly canonical**. Writing is a greedy descent over the standard 13 steps; parsing enforces the canonical pattern, so `IIII`, `VX` and `IC` are all rejected. A symbol legend panel documents which symbols may subtract.
*Example.* 1994 → **MCMXCIV**; `MCMXCIV` → **1994**; `IIII` → *"IIII is not standard Roman notation; 4 is written IV"*.
*Limitations.* No overline bars (so no 4000), no apostrophus, no medieval variants, no zero. Negative numbers are rejected by definition.

### Ring size (`convert.ring_size`)

A **21-row chart** (US 3–13 in half sizes) with circumference and diameter in millimetres and US/UK/EU/JP sizes. Six input systems: circumference (mm), diameter (mm), or a size in any of the four scales.
Matching is nearest-row from a measurement, with an exact tolerance of 0.05 mm; a non-exact hit always states the millimetre delta.
*Example.* 54.4 mm circumference → US **7**, UK **N½**, EU **54**, JP **14**, diameter 17.3 mm.
*Limitations.* Fixed range. **Out-of-range measurements are not rejected** — any positive millimetre value silently returns the nearest row. No children's scales, no band-width adjustment.

### Shoe size (`convert.shoe_size`)

**47 rows across three audiences** — 15 men's, 17 women's, 15 children's — each with foot length in mm and EU/US/UK/JP sizes. Two modes: convert a known size, or find a size from a measured foot length (tolerance 0.5 mm).
*Example.* Men's EU 42 → US 8.5, UK 8, JP 26.5 cm, foot length 265 mm.
*Limitations.** Unlike ring sizes, an **off-table size is an error rather than a nearest match**, and it names the two nearest rows. Rows are fixed, so EU 38.5–48 (men), 35–44 (women) and 20–34 (children) cannot be entered.

---

## 4. Finance — 6 tools

### Currency converter (`finance.currency`)

**Purpose.** Live reference exchange rates with an offline cache.

**Inputs.** An amount, a base currency, and a target currency.

**Method.** A single HTTPS request to `open.er-api.com/v6/latest/<base>` — no API key. The JSON is parsed into a rate table; cross rates are derived locally so any pair works from one fetch. The snapshot is cached in DataStore with its timestamp, and the app warns when the cache is more than 24 hours old.

**Results.** The converted amount, the rate used, and a freshness note.

> **These are reference mid-market rates.** They are not the rates your bank, card issuer or exchange service applies. The app says so in its own data-source note.

**Limitations.** No rate is fetched for any other tool — the converter is the app's only network feature. Reference rates have no buy/sell spread and no fees. See [PRIVACY.md](../PRIVACY.md) for exactly what is transmitted.

**Related.** All finance tools are money-exact (`BigDecimal`).

---

### Loan (`finance.loan`)

**Purpose.** Full amortisation for a fixed-rate loan, with optional extra payments.

**Inputs.** Principal, annual rate, and either a term in years or an explicit number of payments, plus payments per year. Extras: an amount added to every payment, and/or a one-off lump sum in a chosen period. An option to include the schedule.

**Method.** Money is tracked in **whole cents** (scale 2, HALF_UP) throughout. `i = rate/100/paymentsPerYear`; `payment = P·i / (1 − (1+i)^(−n))`. A **zero rate is valid** — the payment is then `P / n` with no interest. When the level payment rounds *below* the exact annuity payment, the residue is cleared on the final instalment, so the balance reaches exactly zero at the contractual term. Because balances move by exact decimal subtraction, the principal repayments sum to exactly the principal.

The schedule is recomputed period by period when extras are used, so the payoff date and the interest saved follow from the loop rather than a shortcut formula.

**Results.** Scheduled payment, contractual and actual payoff counts, total paid, total interest, a flag for zero-interest loans, and the full schedule: period, payment, interest, balance and cumulative interest. A payoff label such as *"28 years 4 months"* is given, and schedules are truncated beyond a display limit with a note.

**Validation.** A term that resolves to zero periods; a payment count above a hard cap (100 years of monthly payments); a non-positive step.

**Limitations.** Fixed rate only — no variable or adjustable rates, no refinancing, no prepayment penalty modelling. Very long schedules are truncated for display, though the totals use every period. A disclaimer is shown in-app.

**Example.** 250 000 at 4.5 % over 30 years monthly → payment **1 265.85**, with the balance reaching exactly **0.00**.

---

### Interest (`finance.interest`)

**Purpose.** Simple and compound interest, effective annual rate, and regular contributions.

**Inputs.** Principal, rate, duration, compounding frequency (annual through continuous), and an optional contribution plan — amount, frequency, and whether contributions are made at the beginning or end of the period.

**Method.** Exact where the exponent is an integer. Simple: `A = P(1 + rt)`. Compound: `A = P(1 + r/n)^(nt)`; continuous `A = P·e^(rt)`. Contributions (end of period): `FV = PMT·((1+j)^m − 1)/j`; beginning of period multiplies by `(1+j)`, where `j = r/k`. EAR: `(1 + r/n)^n − 1`.

**Precision is handled honestly.** A *fractional* exponent — for example 0.7 years compounded monthly — has no exact decimal method, so that final step alone falls back to double precision and the result is flagged approximate. Continuous compounding is always flagged.

**Results.** Final amount, interest earned, contributions total, effective annual rate, period count, and a side-by-side simple-versus-compound comparison with the difference.

**Validation.** A duration that is not positive; a contribution frequency incompatible with continuous compounding; a non-representable EAR.

**Limitations.** No taxation, no inflation adjustment, no bond pricing, no yield curve.

**Example.** 1000 at 5 % for 10 years compounded annually → **1 628.89**, EAR **5.00 %**.

---

### Sales tax (`finance.tax`)

**Purpose.** Add or extract tax, including multi-component invoices.

**Inputs.** Amount, rate, and a direction. Multi-component mode takes named components (state, city, special district) each with its own rate.

**Method.** Exact at 20 significant digits internally; rounding to cents only for settlement. Add: `tax = net · r`. Extract: `net = gross / (1 + r)`.

**Multiple components apply every rate to the same pre-tax base and add the results** — `gross = net + Σ net·rᵢ`. A component is never charged on another component's tax. This is how split invoice lines are normally stated; it is explicitly **not** the same as compounding rates, and the app says so. Extraction treats the components as one combined rate and then attributes the total tax proportionally.

**Results.** net / tax / gross, plus a per-component breakdown and the combined rate actually applied.

**Validation.** Negative amounts; negative or out-of-range rates.

**Limitations.** No jurisdiction lookup, no exemption handling, no rounding-rule configuration (only HALF_UP).

**Example.** 100 at 7.25 % → tax **7.25**, gross **107.25**.

---

### Tip (`finance.tip`)

**Purpose.** Tip calculation and bill splitting that actually adds up.

**Inputs.** Bill, optional tax, tip percentage, number of people, a choice of whether the tip is on pre-tax or post-tax, and a rounding option.

**Method.** The grand total is split by the **largest-remainder method in whole cents**, so the shares sum to the total exactly — no cent is lost or invented. Rounding is applied to the grand total only, and the tip absorbs the difference (recomputed as `total − bill − tax`), which is how restaurants actually settle a bill. The tip base is shown explicitly.

**Results.** Tip base, tip, total, the rounding adjustment, the per-person shares, and the per-person tip shares.

**Validation.** A people count of zero or above the display cap; negative amounts.

**Limitations.** No currency handling, no service-charge logic beyond the simple post/pre-tax choice, no itemised splitting.

**Example.** 115.00 split 3 ways → **38.34 + 38.33 + 38.33** — exactly 115.00.

---

### Unit price (`finance.unit_price`)

**Purpose.** "Which package is actually cheaper?"

**Inputs.** A list of products, each with a name, price, quantity, unit, and an optional discount percentage. A dimension selector (mass, volume, count, length) scopes the comparison.

**Method.** Every product is normalised to a common comparison base — per kg, per L, per item or per m — using exact unit factors. Discounts are applied to the price *before* normalising. **A product is never ranked by its package price**; a bigger pack only wins when its normalised price is genuinely lower.

**Results.** Products ranked ascending by unit price, with rank 1 flagged as best value, plus the effective price and the normalised quantity. Any product that could not take part is listed separately with the reason.

**Validation.** Negative prices or quantities; a discount outside 0–99.x.

**Limitations.** Products of different dimensions are **rejected with an explicit message rather than silently converted** — mass, volume, count and length are not comparable. No currency conversion, no tax or shipping.

**Example.** Comparing 2 L at €3.00 against 500 g at €1.40 ranks by €/L, so the smaller pack can win.

---

## 5. Health — 3 tools

> **These tools are informational estimates, not medical advice**, and each one says so on screen.

### Body mass index (`health.bmi`)

**Purpose.** BMI with the WHO adult classification.

**Inputs.** Metric or imperial — weight in kg or lb, height in m, or feet and inches.

**Method.** Metric `BMI = mass(kg) / height(m)²`. Imperial `BMI = 703 × mass(lb) / height(in)²`, where the 703 factor folds in the exact definitions of 453.59237 g/lb and 2.54 cm/in so both routes agree exactly.

**Results.** The BMI, the WHO category, and the **healthy adult weight range for your height** (BMI 18.5–24.9 converted to kilograms) — often the more actionable number.

**Validation.** Height and mass outside physiological bounds.

**Limitations.** BMI is a screening measure from height and mass only; it says nothing about body composition. **The adult classification is deliberately never applied to anyone under 18** — for children, BMI must be read against age- and sex-specific growth charts, which this tool does not implement. The tool says so.

**Example.** 70 kg at 1.75 m → **22.86**, *Normal*.

---

### Caloric burn (`health.calories`)

**Purpose.** Energy expenditure for an activity, from a MET table.

**Inputs.** Body mass, an activity from a table of ~30 common activities grouped by class, and a duration in minutes.

**Method.** MET-based. `gross kcal = MET × 3.5 × mass(kg) / 200 × minutes`, and `net kcal = gross − resting`, where resting is the same expression at 1 MET. The 3.5 mL O₂/kg/min resting rate and the ~5 kcal/litre O₂ conversion are folded into the standard 1 MET ≈ 1 kcal/kg/h approximation — so this is an **estimate, not a measurement**.

**Results.** Gross expenditure (including resting metabolism), net expenditure (the activity's own cost), and the rate per minute. Gross and net are labelled separately because the difference is the number most people actually want.

**Limitations.** MET values are population averages; real expenditure varies with intensity, fitness, body composition and thermoregulation. No heart-rate estimate, no BMR calculator, no macronutrients.

**Example.** 70 kg running for 30 minutes (MET ≈ 9.8) → gross **≈ 360 kcal**, net **≈ 300 kcal**.

---

### Body fat (`health.body_fat`)

**Purpose.** A US Navy circumference estimate of body-fat percentage.

**Inputs.** The formula variant the measurements were taken for — **men** (height, neck, abdomen) or **women** (height, neck, waist, hip) — all in centimetres. Body mass is optional and enables a lean/fat mass split.

**Method.** The Hodgdon–Beckett regressions, published for **inches**:
- men: `%BF = 86.010·log10(abdomen − neck) − 70.041·log10(height) + 36.76`
- women: `%BF = 163.205·log10(waist + hip − neck) − 97.684·log10(height) − 78.387`

The screen works in centimetres, so inputs are converted at exactly 2.54 cm/inch first. Applying these constants to centimetre numerals directly would misplace the logarithm and overstate the estimate by several percentage points — a real and common bug in tools of this kind.

**Results.** The percentage (rounded to 0.1 pp), the method name, and optionally fat and lean mass. If the raw estimate falls below the reliability floor it is clamped and the result says so.

**Limitations.** **Circumference measurements only**, carrying roughly **±3–4 percentage points** of measurement error. It is not a diagnosis. The two variants are separate regressions fitted to different populations, which is why the tool asks which one applies rather than inferring it.

**Example.** Men, 180 cm, neck 38 cm, abdomen 88 cm → **≈ 17.6 %**.

---

## 6. Date & Time — 3 tools

### Age calculator (`datetime.age`)

**Purpose.** Exact age in years, months, days and totals.

**Inputs.** Birth date, and a reference date — either today or one you choose, optionally with a time of day.

**Method.** Two distinct quantities, kept separate. The **completed period** (years/months/days) is a calendar count matching how a birthday count reads. The **totals** (days, weeks, months) are exact elapsed counts. When the reference time of day precedes the birth time of day, the last day is not yet complete and the period is measured one day earlier.

**Results.** Years/months/days, total days, total weeks, total months, days until the next birthday, the next birthday's date, and the weekday you were born on.

**Leap-year convention.** Someone born on **29 February** has no 29 February in a common year; their birthday is counted on **1 March** in common years. This is the convention many civil registries use, and the app repeats it in its notes.

**Validation.** Impossible dates (30 February, month 13, 29 February in a common year) are reported rather than throwing.

**Example.** Born 1 January 2000, reference 18 October 2026 → **26 years, 9 months, 17 days**.

---

### Add & subtract time (`datetime.add_subtract`)

**Purpose.** Calendar-aware date and time arithmetic.

**Inputs.** A start date (and time), a shift expressed in years, months, weeks, days, hours, minutes and seconds, and a direction.

**Method.** Two modes are produced, because they genuinely differ:

1. **Calendar mode** — the main result. Adds to the local wall clock and **clamps month arithmetic** to the last valid day: 31 January + 1 month is 29 February in a leap year and 28 February otherwise. Because it keeps the wall clock, one calendar day across a daylight-saving transition is 23 or 25 real hours.
2. **Fixed-duration mode** — adds exact seconds to the timeline (a week is exactly 7 × 24 h). It is only defined when the shift contains no years or months, which have no fixed length.

When a time zone is supplied, the engine reports both and the **real elapsed time** between them, which differs exactly when a DST transition lies in between.

**Results.** The calendar result, a note when a clamp occurred (*"31 January + 1 month lands on 28 February"*), the fixed duration and its result, and the elapsed time.

**Limitations.** No business-day arithmetic and no holiday calendar. Date-only mode requires a start time before hour/minute/second shifts can be used.

**Example.** 31 January 2024 + 1 month → **29 February 2024**, with the clamp noted.

---

### Time interval (`datetime.interval`)

**Purpose.** The distance between two dates or date-times.

**Inputs.** A start and an end, either date-only or with times.

**Method.** Again two distinct quantities, deliberately labelled separately:

- the **calendar interval** (years / months / days) counts whole calendar units, matching how people speak about dates;
- the **total elapsed** figures (days / hours / minutes / seconds) count the exact time between the two moments.

They differ by design: 1 January 23:00 → 2 January 01:00 is a calendar interval of **1 day** but only **2 hours** (0 complete days) of elapsed time.

**Business days** count Monday–Friday dates from the start (inclusive) to the end (exclusive). **There is no holiday calendar** — public and company holidays are not modelled, so the count is an **upper bound**, and the app says so.

**Ordering.** When the end precedes the start, a signed flag is set and every magnitude is reported as an absolute difference, rather than silently reordering the inputs.

**Results.** The calendar interval, the elapsed totals, weeks and remaining days, business days, and notes explaining any difference.

**Limitations.** No time-zone handling (date-times are treated as wall clock), no holiday calendar, no working-hours calculation.

**Example.** 1 Jan 2024 → 1 Mar 2024 → **2 months 0 days** (60 total days), **44** business days.

---

## 7. Other — 2 tools

### Mileage & trips (`other.mileage`)

**Purpose.** Trip distance, fuel economy and trip cost together.

**Inputs.** A distance (or start and end odometer readings), fuel used with its volume unit, and optionally a fuel price per unit.

**Method.** Fuel economy (L/100 km, mpg) and fuel consumption (km/L) are **reciprocal views of the same measurement**, and the trip cost is a separate money quantity — the result keeps all three apart so each can be labelled precisely. Exact definitions: 1 mile = 1.609 344 km, 1 US gallon = 3.785 411 784 L, 1 imperial gallon = 4.546 09 L.

**Results.** Distance in km and miles, fuel in litres and both gallon measures, L/100 km, km/L, mpg US, mpg Imp, total cost, and cost per km and per mile.

**Validation.** An end odometer reading below the start is rejected. Negative distance, fuel or price are rejected.

**Limitations.** No fuel-type density or energy content, no combined city/highway cycle, no running total across multiple trips.

**Example.** 300 km on 20 L → **35.28 mpg US** / **42.37 mpg UK**.

---

### Ohm's law (`other.ohms_law`)

**Purpose.** Solve for voltage, current, resistance or power.

**Inputs.** Any three of `V = I × R`, `I = V / R`, `R = V / I`, and `P = V × I = I²R = V²/R`. The tool infers the missing quantity from the three you supply.

**Method.** Exact decimal — these are algebraic identities, not approximations.

**Results.** The solved value plus the derived quantities and the formula used.

**Validation.** A resistance of zero, which would make `I = V/R` undefined — reported rather than producing an infinity.

**Limitations.** DC only; no AC, impedance, phase or power factor. No parallel/series network solving. A skin-depth-aware or non-ohmic component is out of scope.

**Example.** 12 V across 4 Ω → I = **3 A**, P = **36 W**.

---

## 8. Calculus — 14 tools

The old catch-all Calculus tool has been split into fourteen focused ones. They share one expression engine, so the syntax is the same everywhere, and the guide that explains it in depth is the **[Calculus Guide](CALCULUS_GUIDE.md)**.

### Derivatives (`calculus.derivatives`)

**Purpose.** Differentiate an expression, to any order, with the working shown.

**Inputs.** `f(x) =`, the variable to differentiate with respect to, an optional **Second variable** for a mixed partial, and the **Order** of the derivative.

**Method.** Symbolic differentiation by the sum, product, quotient, power and chain rules, with the result simplified after each step so that higher orders can be taken. A numerical derivative is offered alongside for comparison.

**Results.** The derivative, the steps that produced it, and the value at a chosen point.

**Validation.** An expression that cannot be parsed, an order below one, and a function that is not differentiable at the point are each reported in words.

**Limitations.** The derivative is with respect to one variable at a time; a mixed partial takes the second variable in the field. Expressions are read as one function of one variable, so `3xy` needs the multiplication signs putting in.

**Examples.** `x^3 - 4x + 1` → `3x^2 - 4`. `sin(x)*x` → `sin(x) + x*cos(x)`.

**Related.** Integrals, Taylor & Maclaurin, Multivariable Calculus.

---

### Integrals (`calculus.integrals`)

**Purpose.** Antiderivatives and definite integrals.

**Inputs.** `f(x) =`, the variable of integration, and the **Lower limit a** and **Upper limit b** for a definite integral.

**Method.** Indefinite integration is exact where a pattern applies and is reported as such; where no elementary antiderivative exists, the tool says so rather than inventing one. Definite integrals use adaptive Simpson's rule.

**Results.** The antiderivative with its constant, or the value of the definite integral. A numerical answer is prefixed `≈` and names its method.

**Validation.** Limits that are not numbers, and an expression that cannot be parsed.

**Limitations.** The pattern-matcher can return a result labelled "exact" for a power whose base is not the integration variable (`∫sin(x)²dx`); prefer the definite integral for such expressions. `∫2/x dx` is reported as having no elementary antiderivative, because only a numerator of exactly `1` is recognised as a logarithm. The definite integral is a numerical value, not a closed form.

**Examples.** `x^2` from 0 to 1 → ≈ 0.333333333 (adaptive Simpson). `3x + 2` → `1.5x^2 + 2x + C`, *"Antiderivative — found exactly"*.

**Related.** Derivatives, Function Graphing.

---

### Limits (`calculus.limits`)

**Purpose.** Find the value a function approaches.

**Inputs.** `f(x) =`, the variable, the point it approaches, and the **Approach** — both sides, from the right or from the left.

**Method.** Direct substitution where the function is continuous there; otherwise the limit is taken numerically from the chosen side, and when the two sides disagree the limit is reported as not existing.

**Results.** The limit, the method used, and the steps.

**Validation.** A point that is not a number, and an expression that cannot be parsed.

**Limitations.** The numerical route is a one-sided scan, so a limit that needs a path rather than a direction is not found. Infinite limits are reported as such.

**Examples.** `(x^2 - 1)/(x - 1)` at 1 → `2`. `sin(x)/x` at 0 → `1`.

**Related.** Derivatives, Function Graphing.

---

### Taylor & Maclaurin (`calculus.taylor`)

**Purpose.** Expand a function as a power series about a point.

**Inputs.** `f(x) =`, the variable, the **Centre a**, and the **Order**.

**Method.** The derivatives at the centre are found exactly, up to the order asked for, and the series is assembled from them.

**Results.** The series, term by term, with the coefficients shown, and the value at the centre.

**Validation.** An order below zero or above twelve, and a centre that is not a number.

**Limitations.** The expansion is about one point and is useful only near it. A function that is not differentiable enough at the centre is reported as such.

**Examples.** `sin(x)` about 0, order 6 → `x - x^3/6 + x^5/120`.

**Related.** Derivatives, Infinite Series.

---

### Summations & Products (`calculus.summations`)

**Purpose.** Add up or multiply out the terms of a sequence.

**Inputs.** The term, the index (`k`), and the **From k** and **To k** bounds.

**Method.** The index is taken from the term itself, so `∑k` with the variable `x` still sums over `k`. The sum is exact where the terms are exact.

**Results.** The value, and the step-by-step accumulation.

**Validation.** A range that is empty or runs backwards, an index that does not appear in the term, and a range that is too long are each reported.

**Limitations.** The terms are evaluated one at a time, so a very long range is slow. The index has to be the variable of the term; a term in another variable is refused.

**Examples.** `n^2` from 1 to 10 → `385`. `n` from 1 to 10 → `55`.

**Related.** Infinite Series.

---

### Infinite Series (`calculus.series`)

**Purpose.** Decide whether a series with general terms converges.

**Inputs.** The general term, the index, and the **First index k** and **Last index k**.

**Method.** The behaviour of the term is examined, and for a geometric series the ratio decides; the partial sums are computed to show what is happening.

**Results.** A verdict — convergent or divergent, with the reason — and the partial sums.

**Validation.** A range that is empty or runs backwards, and an index that does not appear in the term.

**Limitations.** The verdict is a numerical one; a series whose convergence needs a subtlety the tests do not cover may be judged from its partial sums alone.

**Examples.** `1/n^2` from 1 to 1000 → converges. `(-1)^n` from 1 to 100 → the partial sums do not settle.

**Related.** Summations & Products, Taylor & Maclaurin.

---

### Equation Solving & Roots (`calculus.roots`)

**Purpose.** Find the real roots of an expression over an interval.

**Inputs.** `f(x) =`, and the **Interval from** and **Interval to** to search.

**Method.** The interval is sampled for sign changes, and each candidate is polished with Newton's method.

**Results.** The roots, and the steps that found them.

**Validation.** An interval that is not increasing, an interval that is not a pair of numbers, and an expression that cannot be parsed.

**Limitations.** The search is by sampling, so a root that touches zero without crossing it may be missed. Only real roots are found.

**Examples.** `x^2 - 4` from -10 to 10 → `-2`, `2`.

**Related.** Extrema & Optimization, Equations.

---

### Extrema & Optimization (`calculus.extrema`)

**Purpose.** Find the stationary points of a function over an interval.

**Inputs.** `f(x) =`, and the **Interval from** and **Interval to**.

**Method.** The derivative is found and its roots are located, and each is classified as a maximum or a minimum by the second derivative.

**Results.** The stationary points with their values and their kind, and the steps.

**Validation.** An interval that is not increasing, and an expression that cannot be parsed.

**Limitations.** The interval is sampled, so a very narrow feature may be missed. Endpoints of the interval are not reported as extrema.

**Examples.** `x^3 - 3x` from -5 to 5 → a local maximum at `x = -1` and a local minimum at `x = 1`.

**Related.** Equation Solving & Roots, Multivariable Calculus.

---

### Function Graphing (`calculus.graph`)

**Purpose.** Plot a function and read the mathematics off it.

**Inputs.** `f(x) =`, the variable, and an optional **Area from a** and **Area to b** to shade.

**Method.** The function is sampled on the visible window and drawn. The picture is a shared viewport: a drag moves it with the finger, a pinch spreads it about the point you touch, and the scales are the same on both axes, so a slope of one is drawn at forty-five degrees.

**Results.** The plot, with the axes, the grid, the optional `f′` overlay, the shaded area, and the view controls.

**Validation.** An expression that cannot be parsed, and a shading range that is not a pair of numbers.

**Limitations.** The graph is a picture of the function, not a proof of anything: the accuracy of the curve is the accuracy of the sampling. The view is bound to the variable `x`.

**Examples.** `sin(x)` with the area from 0 to π shaded.

**Related.** Limits, Derivatives, Inequalities.

---

### Differential Equations (`calculus.differential_equations`)

**Purpose.** Solve a first-order differential equation.

**Inputs.** The **Differential equation**, its **Order**, **How to solve it** (symbolic, initial value, or numerical), the **Condition** and **Second condition**, and for the numerical route the **From x**, **To x**, **Step size** and **Method**.

**Method.** The equation is classified first — separable, linear, or with constant coefficients — and then solved by the method that fits it. The numerical route is Runge–Kutta, and the stability condition is checked before anything is computed.

**Results.** The general or the particular solution, the constants and the values they took, the derivation step by step, and for the numerical route the points of the solution and the method that would have applied.

**Validation.** An equation that cannot be read, a condition for an equation of the wrong order, and a step size that the scheme cannot take are each reported in words.

**Limitations.** The symbolic route covers separable and first-order linear equations, and constant-coefficient equations of higher order; anything else is refused with the reason. The numerical route is an approximation.

**Examples.** `dy/dx = 2y` → `y = C·e^(2x)`. `y' = x - y` with `y(0) = 1` → `y = x - 1 + 2e^(-x)`.

**Related.** Function Graphing, Partial Differential Equations.

---

### Multivariable Calculus (`calculus.multivariable`)

**Purpose.** The calculus of functions of more than one variable.

**Inputs.** `f(x, y) =`, the **Variables**, the **Point** to evaluate at, the **Order** of the derivative, the **Second variable** for a mixed partial, a **Direction vector** for the directional derivative, the **Components** of a vector field, the **Bounds** of a region, and a **parametric curve** `x(t), y(t)` for a line integral.

**Method.** The operations run on the same expression engine as the rest of the calculus, so the partial derivatives are exact; the integrals reuse the adaptive rule. The critical-point search looks at all eight neighbours of a point.

**Results.** The requested quantity, the gradient, the Hessian, the classification of a critical point, the integrals, and a picture: a rotatable surface and a contour map with the gradient vectors, both drawn from the same expression. The level curves are traced with the marching-squares rule, so they follow the true contours, and the arrows have a fixed length on the screen.

**Validation.** A function of the wrong number of variables, a direction vector of the wrong length, bounds that are not in pairs, and a curve whose bounds run backwards are each reported.

**Limitations.** The integrals are numerical, over a rectangle, a box or a parametric curve. The critical points are found by scanning, so a saddle in a narrow region may be missed. The expression parser reads a run of letters as one name, so the multivariable tool puts the multiplication signs in itself, because it knows the variables.

**Examples.** The gradient of `x^2 + 3xy + y^2` is `(2x + 3y, 3x + 2y)`.

**Related.** Derivatives, Fourier Analysis.

---

### Fourier Analysis (`calculus.fourier`)

**Purpose.** The Fourier series of a function, and the discrete transform of a list of samples.

**Inputs.** For the series: `f(x) =`, the **Variable**, the **Period T** and the **Harmonics**. For the transform: the **Sample values**, in order, and the **Length of the record**.

**Method.** The coefficients are found by numerical integration with a fixed rule and enough panels, so a fast oscillation is not missed; the number of panels follows the number of harmonics. The constant term is the mean of the function, even when no harmonics are asked for.

**Results.** The series term by term, the constant term, the difference from the function, and the discrete transform with each bin's frequency. The function and its approximation are drawn together.

**Validation.** A period that is not positive, a negative number of harmonics, and a list of samples that is empty or of the wrong length.

**Limitations.** The coefficients are estimates, not exact values. The two routes are kept apart: the series takes a function, the transform takes a list.

**Examples.** The first sine coefficient of `sin(x)` is one.

**Related.** Multivariable Calculus, Complex Analysis.

---

### Complex Analysis (`calculus.complex`)

**Purpose.** Work with complex numbers and complex functions.

**Inputs.** A complex number as its real and imaginary parts, or a function `f(z) =` with `z` as the variable, a point, a contour `x(t), y(t)` with its bounds, and the **Order of the pole**.

**Method.** The complex arithmetic is exact in the polar and exponential forms; the principal square root and the principal logarithm take the principal branch, and the principal cube root is chosen by argument rather than by its imaginary part. The Cauchy–Riemann check is a local numerical check of the four partial derivatives.

**Results.** The real part, the imaginary part, the modulus, the argument, the polar form, the conjugate, the powers, the roots, the value of `f(z)`, the Cauchy–Riemann residual, a contour integral, and the residue.

**Validation.** A point that is not a pair of numbers, a contour whose bounds run backwards, and a pole of an order the tool does not handle.

**Limitations.** The contour integral and the residue are numerical estimates: the limit is taken along a few directions. The Cauchy–Riemann check is a local numerical check, not a proof of analyticity.

**Examples.** `z^2` at `1 + i` → `2i`.

**Related.** Fourier Analysis, Derivatives.

---

### Partial Differential Equations (`calculus.pde`)

**Purpose.** The heat and wave equations along a rod, and Laplace's and Poisson's equations over a square.

**Inputs.** The **equation** — heat, wave, Laplace or Poisson — the **Initial condition u(x, 0)**, the **Initial velocity u_t(x, 0)**, the two end values, the **Number of points in space**, the **Number of steps in time**, the **Time to reach**, the **Diffusivity α** or the **Speed c**, and for the two-dimensional route the **Size of the side**, the **Boundary values**, the **source f(x, y)**, the **Points on each side** and the **Maximum sweeps**. The coefficients **A**, **B** and **C** classify the equation as elliptic, parabolic or hyperbolic.

**Method.** Finite differences. The stability condition is checked before anything is computed, and the tool refuses a step that would make the numbers grow. The two-dimensional route relaxes the grid until the values stop changing.

**Results.** The solution, the stability number and the limit it must satisfy, the steady state, the number of sweeps used, and a colour map of the field. The colours follow the accent colour you have chosen, and a flat field is explained rather than left looking like a failure.

**Validation.** A step that breaks the stability condition, a grid or sweep count outside the allowed range, an expression that cannot be read, and a side length that is not positive are each reported in words.

**Limitations.** The boundary of the two-dimensional equations is a single expression used on all four sides, so the textbook "hot along the top, cold elsewhere" cannot be written directly. The answers are numerical approximations, and the wave equation is a one-dimensional string.

**Examples.** The heat equation with the initial value `sin(pi*x)` on a rod of length 1 spreads the peak and lowers it.

**Related.** Differential Equations, Function Graphing.

---

## 9. Advanced Mathematics — 3 tools

Three learning environments. Each computes real values for the example in front of it and states what it does not reach.

### Measure Theory (`advanced.measure`)

**Purpose.** Measure, unions, overlaps and the integral.

**Inputs.** What to explore — the measure of a set, the union and the overlap of two sets, a geometric series of intervals, or the Riemann integral of a function — and the sets, the series, or the function itself.

**Method.** The measure of a finite union of intervals is the total length; the union and the overlap are computed from the intervals themselves. Countable additivity is shown through a geometric series. The lower and upper Riemann sums are computed and compared, which is what says whether the integral of a function exists.

**Results.** The measure, the union, the overlap, the sum of the measures and the ratio between them; the partial sums and the total length; the lower and upper sums, the gap between them, and whether the integral exists; and where two functions differ and whether they are equal almost everywhere.

**Validation.** A set that is not a list of intervals, bounds that are the wrong way round, and a function that cannot be read are each reported in words.

**Limitations.** Finite unions of intervals only. The Riemann sums are numerical, and the answer they give about existence is the answer for that function on that interval.

**Examples.** The measure of `[0, 2] ∪ [1, 3]` is three.

**Related.** Topology Explorer, Functional Analysis.

---

### Topology Explorer (`advanced.topology`)

**Purpose.** The open sets, the closed sets and the shapes of a topological space.

**Inputs.** What to explore — a set of real numbers, a finite topological space, a region of the plane, or a homeomorphism — and the intervals, the points, the centre and radius, or the map itself.

**Method.** The intervals carry their open and closed ends, so an open interval and a closed one are different sets. The interior, the closure, the boundary, the isolated points and the limit points are computed from those ends. A finite space is given by its open sets, and the plane regions are compared by the map between them.

**Results.** Whether the set is open, closed, compact and connected; its interior, closure and boundary; its isolated and limit points; the open sets of a finite space; the area of a region; and the homeomorphisms between two spaces.

**Validation.** An interval whose ends are the wrong way round, a point that is not a number, a radius that is not positive, and a set of open sets that is not closed under intersection are each reported.

**Limitations.** Intervals, finite spaces and a few plane regions. The homeomorphisms are the ones that can be checked on those.

**Examples.** `(0, 1)` is open and not closed; `[0, 1]` is closed and compact.

**Related.** Measure Theory, Functional Analysis.

---

### Functional Analysis (`advanced.functional`)

**Purpose.** Vectors, inner products, projections and the operators between them.

**Inputs.** What to do — the inner product, the norms, the distance, the angle, the projection, the Gram–Schmidt process, the least-squares fit, the eigenvalues and eigenvectors of a matrix, or the spectral radius — and the vectors or the matrix.

**Method.** The inner products and the norms are exact in the usual arithmetic; the Gram–Schmidt process and the least-squares fit are checked for shape first, so a fit that does not exist is refused with the reason rather than crashing. The eigenvalues of a symmetric matrix are found with the Jacobi method.

**Results.** The inner product, the norms, the distance, the angle, whether two vectors are orthogonal, the projection and its residual, the orthonormal basis and whether it is orthonormal, the least-squares coefficients and the length of the residual, the eigenvalues, the eigenvectors, whether the matrix is symmetric, its spectral radius, and whether it is a contraction. The infinite-dimensional case is described rather than faked.

**Validation.** Vectors of different lengths, a matrix that is not square, a fit with more rows than columns and a projection onto a zero vector are each reported in words.

**Limitations.** The eigenvalues are found numerically, so they are estimates. The infinite-dimensional spaces are described, not computed in.

**Examples.** The inner product of `(1, 2, 3)` and `(0, 1, 4)` is fourteen.

**Related.** Measure Theory, Topology Explorer.

---

## Appendix: category index

| Category | Tool | Id |
|---|---|---|
| Algebra | [Percentage](#percentage-algebrapercentage) | `algebra.percentage` |
| | [Average](#average-algebraaverage) | `algebra.average` |
| | [Proportion](#proportion-algebraproportion) | `algebra.proportion` |
| | [Ratio](#ratio-algebraratio) | `algebra.ratio` |
| | [Equations](#equations-algebraequations) | `algebra.equations` |
| | [GCF & LCM](#gcf--lcm-algebragcf_lcm) | `algebra.gcf_lcm` |
| | [Combinations](#combinations-algebracombinations) | `algebra.combinations` |
| | [Prime checker](#prime-checker-algebraprimes) | `algebra.primes` |
| | [Number generator](#number-generator-algebrarandom) | `algebra.random` |
| | [Inequalities](#inequalities-algebrainequalities) | `algebra.inequalities` |
| Geometry | [2D shapes](#2d-shapes-geometryshapes) | `geometry.shapes` |
| | [3D bodies](#3d-bodies-geometrybodies) | `geometry.bodies` |
| Converters | [Length](#length-convertlength) | `convert.length` |
| | [Area](#area-convertarea) | `convert.area` |
| | [Volume](#volume-convertvolume) | `convert.volume` |
| | [Weight & mass](#weight--mass-convertweight) | `convert.weight` |
| | [Temperature](#temperature-converttemperature) | `convert.temperature` |
| | [Speed](#speed-convertspeed) | `convert.speed` |
| | [Acceleration](#acceleration-convertacceleration) | `convert.acceleration` |
| | [Force](#force-convertforce) | `convert.force` |
| | [Pressure](#pressure-convertpressure) | `convert.pressure` |
| | [Energy](#energy-convertenergy) | `convert.energy` |
| | [Power](#power-convertpower) | `convert.power` |
| | [Torque](#torque-converttorque) | `convert.torque` |
| | [Angle](#angle-convertangle) | `convert.angle` |
| | [Time](#time-converttime) | `convert.time` |
| | [Volumetric flow](#volumetric-flow-convertflow) | `convert.flow` |
| | [Data storage](#data-storage-convertdata_storage) | `convert.data_storage` |
| | [Data transfer](#data-transfer-convertdata_transfer) | `convert.data_transfer` |
| | [Fuel](#fuel-convertfuel) | `convert.fuel` |
| | [Cooking](#cooking-convertcooking) | `convert.cooking` |
| | [Numeric base](#numeric-base-convertnumeric_base) | `convert.numeric_base` |
| | [Roman numerals](#roman-numerals-convertroman) | `convert.roman` |
| | [Ring size](#ring-size-convertring_size) | `convert.ring_size` |
| | [Shoe size](#shoe-size-convertshoe_size) | `convert.shoe_size` |
| Finance | [Currency converter](#currency-converter-financecurrency) | `finance.currency` |
| | [Loan](#loan-financeloan) | `finance.loan` |
| | [Interest](#interest-financeinterest) | `finance.interest` |
| | [Sales tax](#sales-tax-financetax) | `finance.tax` |
| | [Tip](#tip-financetip) | `finance.tip` |
| | [Unit price](#unit-price-financeunit_price) | `finance.unit_price` |
| Health | [Body mass index](#body-mass-index-healthbmi) | `health.bmi` |
| | [Caloric burn](#caloric-burn-healthcalories) | `health.calories` |
| | [Body fat](#body-fat-healthbody_fat) | `health.body_fat` |
| Date & Time | [Age calculator](#age-calculator-datetimeage) | `datetime.age` |
| | [Add & subtract time](#add--subtract-time-datetimeadd_subtract) | `datetime.add_subtract` |
| | [Time interval](#time-interval-datetimeinterval) | `datetime.interval` |
| Other | [Mileage & trips](#mileage--trips-othermileage) | `other.mileage` |
| | [Ohm's law](#ohms-law-otherohms_law) | `other.ohms_law` |
| Calculus | [Derivatives](#derivatives-calculusderivatives) | `calculus.derivatives` |
| | [Integrals](#integrals-calculusintegrals) | `calculus.integrals` |
| | [Limits](#limits-calculuslimits) | `calculus.limits` |
| | [Taylor & Maclaurin](#taylor--maclaurin-calculustaylor) | `calculus.taylor` |
| | [Summations & Products](#summations--products-calculussummations) | `calculus.summations` |
| | [Infinite Series](#infinite-series-calculusseries) | `calculus.series` |
| | [Equation Solving & Roots](#equation-solving--roots-calculusroots) | `calculus.roots` |
| | [Extrema & Optimization](#extrema--optimization-calculusextrema) | `calculus.extrema` |
| | [Function Graphing](#function-graphing-calculusgraph) | `calculus.graph` |
| | [Differential Equations](#differential-equations-calculusdifferential_equations) | `calculus.differential_equations` |
| | [Multivariable Calculus](#multivariable-calculus-calculusmultivariable) | `calculus.multivariable` |
| | [Fourier Analysis](#fourier-analysis-calculusfourier) | `calculus.fourier` |
| | [Complex Analysis](#complex-analysis-calculuscomplex) | `calculus.complex` |
| | [Partial Differential Equations](#partial-differential-equations-calculuspde) | `calculus.pde` |
| Advanced Mathematics | [Measure Theory](#measure-theory-advancedmeasure) | `advanced.measure` |
| | [Topology Explorer](#topology-explorer-advancedtopology) | `advanced.topology` |
| | [Functional Analysis](#functional-analysis-advancedfunctional) | `advanced.functional` |

**Count: 10 + 2 + 23 + 6 + 3 + 3 + 2 + 14 + 3 = 66.** Every id here appears in `data/ToolRegistry.kt`, and every id there appears here.

---

## Related documentation

- [User guide](USER_GUIDE.md) — navigation, input rules, settings, errors
- [Calculus guide](CALCULUS_GUIDE.md) — the calculus tools in depth
- [Architecture](ARCHITECTURE.md) — how the engines are built
- [Roadmap](../ROADMAP.md) — verified limitations and outstanding work

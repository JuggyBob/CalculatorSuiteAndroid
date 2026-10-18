# Calculus Guide

The Calculus tool takes **one expression** and runs ten different operations on it, showing its working for each.

> **This guide is deliberately specific about limits.** Some of the symbolic engine's boundaries produce surprising results, and a document that glossed over them would be worse than useless. Everything in [Limitations](#8-limitations--read-this) is verified against the code.

---

## 1. The expression language

The Calculus tool has **its own parser**, separate from the scientific calculator's. The two do not share syntax, and the scientific calculator's functions and angle modes are *not* available here.

### Grammar

```
expression := term (('+' | '−') term)*
term       := factor (('*' | '×' | '·' | '/' | '÷') factor)*
factor     := unary ('^' factor)?          // right-associative
unary      := ('−' | '+')? postfix
postfix    := primary ('!')*
primary    := number | constant | name '(' args ')' | name | '(' expression ')'
```

### Numbers and operators

- Scientific notation: `1e-3`, `2.5E10`
- Both minus signs are accepted: `-` and `−` (U+2212), and `–` (en dash)
- `^` is right-associative: `2^3^2` = `2^9` = 512
- Implicit multiplication: `2x`, `3(x+1)`, `2sin(30)`, `2π`
- Postfix `!` is the factorial

### Constants

| Names | Meaning |
|---|---|
| `pi`, `π`, `p` | π |
| `e` | Euler's number |
| `tau`, `τ` | 2π |
| `phi`, `φ` | Golden ratio |

### Functions

| Group | Functions |
|---|---|
| Trigonometric | `sin` `cos` `tan` `csc` `sec` `cot` `asin` `acos` `atan` `atan2(y,x)` |
| Hyperbolic | `sinh` `cosh` `tanh` `asinh` `acosh` `atanh` |
| Logarithms | `ln` `log` `log2` |
| Exponential | `exp` |
| Roots | `sqrt` `cbrt` `root(x, n)` |
| Rounding | `abs` `sign` `floor` `ceil` `round` |
| Other | `min` `max` `fact` |

**The tool always works in radians.** There is no degree mode here — convert explicitly with `deg(x)` and `rad(x)` if you need to.

### One field, ten operations

Type an expression into `f(x) =`, then pick the operation:

`d/dx` · `∫` · `lim` · `Σ∞` · `T` · `Σ Π` · `= 0` · `f′=0` · `y′` · `plot`

---

## 2. Symbolic or numerical?

The tool is explicit about which is which. This distinction matters when reading a result.

| Operation | Method | Marked `≈`? |
|---|---|---|
| Derivative | **Symbolic** (chain, product, quotient, power, sum rules) | No |
| Partial derivative | **Symbolic** | No |
| Indefinite integral | **Symbolic** (pattern-matched) | No |
| Definite integral | **Numerical** — adaptive Simpson, tolerance ~1e-13 | **Yes** |
| Limit | Direct substitution, else **numerical** one-sided evaluation at h = 1e-4 | Usually |
| Infinite series | **Exact closed form** when geometric, else a finite partial sum | Only for a partial sum |
| Taylor / Maclaurin | **Symbolic** — repeated differentiation, each simplified | No |
| Σ and Π | **Exact** for integers via `BigInteger`/exact decimal accumulation | No |
| Roots | **Numerical** — 2000-sample sign scan, then bisection to 1e-12 | **Yes** |
| Extrema | **Symbolic** f′, exact f″ where available, otherwise a central difference | No |
| Differential equation | **Numerical** — classical RK4 | **Yes** |

An unprefixed result is exact. A `≈` result names its method in the panel beneath it.

---

## 3. Derivative (`d/dx`)

Enter `x^2 + 3x` → **Derivative** gives:

```
d/dx (x^2 + 3x)
2x + 3
Sum/difference rule: differentiate each term separately.
```

**Differentiate with respect to** sets the variable (default `x`).

**Checks at a point** — give a value and the tool reports `f(x)`, `f′(x)`, and the central-difference estimate. When they agree it says so. This is a genuine independent check: the numeric derivative is computed from your original function, not from the symbolic answer.

**Partial derivatives** — add a second variable and it holds the other fixed.

**Which functions differentiate symbolically:** `sin` `cos` `tan` `asin` `acos` `atan` `sinh` `cosh` `tanh` `ln` `log` `log2` `exp` `sqrt` `cbrt` `abs`. Anything else becomes `NaN` internally, which surfaces as the tool declining to produce a symbolic derivative.

### Worked examples

| Input | Operation | Result |
|---|---|---|
| `x^2 + 3x` | derivative | `2x + 3` |
| `sin(x)` | derivative | `cos(x)` |
| `x^3 - 5/2^1/10` | derivative | `3x^2` |

---

## 4. Integral (`∫`)

### Indefinite

Pattern-matched against a fixed set of forms, with sums and differences decomposed. When nothing matches, the tool says so rather than guessing.

Recognised: constants, `x`, `−f`, `1·f`, `c·xⁿ`, `xⁿ`, `1/x` → `ln`, `sin(v)`, `cos(v)`, `exp(v)`, and sums/differences of any of those.

| Input | Result |
|---|---|
| `x^2` | `x^3 ÷ 3 + C` |
| `3x^2` | `x^3 + C` |
| `0.5x^3` | `x^4 ÷ 8 + C` |
| `1/x` | `ln\|x\| + C` |
| `x^3 - 5/2^1/10` | `x^4 ÷ 4 − 0.25x + C` |
| `7/3` | `2.3333333333...x + C` |

The `+ C` is the constant of integration and is always shown.

### Definite

Enter **Lower limit a** and **Upper limit b**. The tool first tries the exact antiderivative; when it has one it uses it, otherwise it falls back to adaptive Simpson and marks the result `≈`.

| Input | Limits | Result |
|---|---|---|
| `sin(x)` | 0, π | ≈ 2 |
| `x^2` | 0, 3 | `9` (exact) |

**Errors:** *"The integrand has a singularity inside the interval, or is not defined there"* when the function is undefined anywhere between the limits.

---

## 5. Limit (`lim`)

**`x approaches`** takes a number, or `Infinity` / `-Infinity`. **Approach** selects *Both sides*, *From right* or *From left*.

The tool substitutes directly where that is defined. At a removable singularity it evaluates one-sided at h = 1e-4 and averages when both sides are requested — and **rejects the limit as not existing** when the two sides differ by more than 1e-2 relative.

| Input | Point | Result |
|---|---|---|
| `sin(x)/x` | 0 | ≈ 1 |
| `(x^2-4)/(x-2)` | 2 | ≈ 4 |

---

## 6. Series, Taylor and Σ/Π

### Infinite series (`Σ∞`)

Enter **First index k** and **Last index k**. For a geometric `r^k` the tool gives a **closed form** and a convergence verdict (converges iff `|r| < 1`). Otherwise it sums a finite partial sum, capped at 5000 terms.

| Input | Verdict |
|---|---|
| `0.5^k` | Converges |
| `2^k` | Diverges |

### Taylor / Maclaurin (`T`)

**Centre a** and **Order** (0–12). Each derivative is computed symbolically and simplified, then evaluated at the centre.

The tool prints the centre value and every coefficient, including the zeros:

| Input | Centre | Order | Output |
|---|---|---|---|
| `e^x` | 0 | 6 | `1`, `1`, `0.5`, `0.1666...`, `0.0416...`, `0.0083...`, `0.00138...` |
| `x^3 - 5/2^1/10` | 0 | 6 | `f(a) = -0.25`, then `0`, `0`, `1`, `0`, `0`, `0` |

That last row is worth reading twice. The expression is a polynomial of degree 3, so **every derivative of order 4 and above is exactly zero**, and the tool now says `0` rather than claiming the derivative is undefined. It distinguishes three cases properly:

- a derivative that is **exactly zero** → reported as `0`
- a derivative that is **genuinely undefined** at the centre → reported as undefined
- a derivative that **exists but cannot be differentiated symbolically that many times** → reported as such

A genuine singularity is still caught: `lim`/`T` of `1/x` at `x = 0` reports that the function is not defined there.

### Σ and Π (`Σ Π`)

**From k** and **To k**. The index is inferred: the variable you named if it appears in the expression, otherwise the first variable that does. So `Σ` of `k` while the screen's variable is `x` still uses `k` as the index.

| Input | Range | Result |
|---|---|---|
| `k` | 1 … 10 | `55` |
| `k` | 1 … 5 (product) | `120` |

---

## 7. Roots, extrema, ODEs and the graph

### Roots (`= 0`)

Enter **Search from** and **Search to**. The tool scans for sign changes and bisects each bracket to 1e-12. Roots that touch the axis without changing sign are found too.

| Input | Interval | Result |
|---|---|---|
| `x^2 - 4` | 0 … 3 | `-2`, `2` |
| `x^3 - x` | wide | `-1`, `0`, `1` |

Roots are numerical, so they carry `≈`.

### Extrema (`f′=0`)

Enter **Interval from** and **Interval to**. The tool finds where f′ = 0 and classifies each point using the exact second derivative where it can compute one.

| Input | Interval | Result |
|---|---|---|
| `x^2 - 4x` | 0 … 3 | `x = 2`, `f(x) = −4`, **Local minimum** |

### Differential equation (`y′`)

Solves `dy/dx = f(x, y)` with classical RK4.

**x₀**, **y₀**, **Step size h**, **Steps** (1–10000).

| Equation | x₀ | y₀ | h | Steps | Result |
|---|---|---|---|---|---|
| `y` | 0 | 1 | 0.05 | 100 | at x = 5, y ≈ 148.413 (the true value is e⁵ ≈ 148.413) |

The first few steps are listed so you can see the method working, and the result is marked `≈`.

### Graph (`plot`)

**What it does:**

- Plots `f` over the current window
- **Drag to pan**
- **Pinch to zoom about the point you touch**
- **Show f′** overlays the derivative
- **Show area** shades between **Shade area from a** and **Shade area to b**
- **Reset view** returns to the default window (x and y from −10 to 10)
- A fullscreen toggle expands the plot

Three pills summarise the current view state: whether `f` is shown, whether `f′` is shown, and whether the area is shaded.

Implementation notes that explain its behaviour:

- The plot is a Compose `Canvas` with clipping enabled, so the curve cannot paint over surrounding text.
- Sampling is `widthPx / 1.5` steps, clamped to between 120 and 900, and points with `|y| ≥ 1e12` are dropped so a near-singularity does not flatten the curve.
- The window span is clamped between 1e-4 and 1e7, so you can zoom in but not past a degenerate view.
- Shading uses 240 steps.

---

## 8. Limitations — read this

The symbolic engine is a **pattern matcher with exact rules**, not a computer algebra system. Its boundaries are real:

### Integration

**An indefinite integral can be returned as "exact" when it is not.** The power rule is applied to a power's base *as written*, without the chain factor. So:

| Input | Returned | Correct? |
|---|---|---|
| `sin(x)^2` | `sin(x)^3 ÷ 3 + C`, labelled **"found exactly"** | **No** — the right answer is `x/2 − sin(2x)/4 + C` |
| `exp(x)^2` | similarly | **No**, for the same reason |

For any power whose base is not the integration variable, **use the definite integral**, which is computed numerically and labelled `≈`.

Conversely, some simple things are rejected:

| Input | Result | Note |
|---|---|---|
| `2/x` | "No elementary antiderivative" | Should be `2·ln\|x\| + C`; the log rule only fires when the numerator is exactly `1` |
| `tan(x)` | "No elementary antiderivative" | Correct — the antiderivative involves a logarithm, not an elementary power form |

`sin`, `cos` and `exp` are only recognised when their argument is exactly the integration variable.

### Roots, extrema and the graph ignore your variable name

All three evaluate against a hard-coded `x`. If you change **Differentiate with respect to** to `t`, differentiation follows it, but root finding, extrema and the graph do not.

### Symbolic differentiation coverage

`sec`, `csc`, `cot`, `floor`, `ceil`, `round`, `sign`, `root`, `atan2`, `min`, `max` and `fact` have no symbolic rule. They evaluate numerically where the operation supports it, and otherwise decline.

### Not implemented

Higher-order differential equations; series arithmetic beyond the geometric closed form; complex analysis; symbolic root-finding for cubics or quartics; implicit-equation solving; indefinite integrals of anything not in the pattern list.

---

## 9. Conventions & Help

The tool's own **Conventions & Help** panel contains the symbol table, the full syntax, a per-operation explanation and worked examples. It sits at the bottom of the screen and is hidden while the tool is expanded to fullscreen, so the plot gets the whole screen.

---

## 10. Related tools

| Tool | When to use it instead |
|---|---|
| [Scientific calculator](USER_GUIDE.md#5-the-scientific-calculator) | Plain arithmetic and trig with degree support and memory keys |
| [Equations](FEATURES_AND_TOOLS.md#equations-algebraequations) | Linear, quadratic and 2×2 systems solved **exactly** with fractions, not scanned numerically |
| [Average](FEATURES_AND_TOOLS.md#average-algebraaverage) | Series arithmetic is not attempted; compute finite sums here |

---

## Related documentation

- [Features and tools](FEATURES_AND_TOOLS.md) — all 49 tools
- [User guide](USER_GUIDE.md) — navigation, settings, errors
- [Roadmap](../ROADMAP.md) — the limitations above are tracked as outstanding work
- [Architecture](ARCHITECTURE.md) — how the engine is built

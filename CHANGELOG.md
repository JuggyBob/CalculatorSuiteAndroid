# Changelog

All notable changes to Calculator Suite are recorded here.

This project uses a plain, human-written changelog. **Version 1.0.0 was the first public release**; 2.0.0 and 3.0.0 are the major ones.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for the version number.

## [3.0.2] — 2026-10-22

A release that lets the app keep itself up to date: **the app can now look for a newer version, download it and hand it to Android's installer**, and **a new About screen shows what changed in the newest release**. It also adds **resistor networks of any size**, **a method selector for systems of equations**, and it fixes the way the circuit canvas zooms and the way a wire finds its terminal. `versionName = 3.0.2`, `versionCode = 6`, `minSdk 26`, `targetSdk 36`.

This release upgrades over 3.0.1 in place: the signing key is unchanged, so the app installs straight over the old one with your settings and history intact.

### Added — Check for updates

- **The app can look for its own update.** The settings now have an **Updates** card with **Check for updates**. The check asks the project's GitHub release page for the newest published release, compares its version with the one that is installed, and says which of the two is newer. Nothing is downloaded until you ask for it.
- **A newer version can be downloaded inside the app.** The download shows how far it has got, is written to the app's own storage, and is only kept when it is complete; a download that is cut short is thrown away rather than offered.
- **The file is checked before it is offered to Android.** When the release publishes a digest, the downloaded file is checked against it. The package name, the version code and the signing certificate have to match the app that is installed: a file for another app, a file that is not newer, and a file signed with another certificate are all refused, with a sentence saying why.
- **Android does the installing.** The package is handed to Android's own installer, which asks you to confirm the installation. The app never installs anything by itself, and never says that it has. If the phone has not yet allowed this app to install packages, the system's "install unknown apps" screen is opened for it instead.
- **Versions are compared the way versions are meant to be.** A tag of `v3.0.2` is the version `3.0.2`, `0.0.10` is newer than `0.0.9`, a missing part counts as zero (`3` and `3.0` are the same), and a pre-release such as `3.1.0-beta1` is older than `3.1.0`. An update is never offered when the published release is older than the one that is installed, and a release whose version cannot be read is never offered as an update — the app points at the release page instead.
- **A release with several packages is not guessed at.** If a release publishes more than one installable file, the app says so and offers the release page rather than picking one at random.

### Added — About, and what is new

- **The settings have an About card.** *About Calc Suite* shows the version that is installed, the newest published release with the day it was published, and the notes of that release.
- **The notes come from the release itself**, so they always say what the release says. When the phone has no connection, the notes of the last release that was read on the device are shown instead, and the screen says that this is what they are.
- **The notes are drawn with a small, safe subset of Markdown**: headings, lists, bold, italic, code and links. A link shows its text and its address. Nothing in the notes can run.
- **The installed version is shown at the foot of the settings screen**, together with the number of tools.

### Added — Resistor networks of any size

- The electrical calculators now take a **network of resistors** instead of a fixed number of them. The resistors are added one at a time and joined in series or in parallel, and the app works out the equivalent resistance and the current in each branch. **There is no cap on how many resistors can be added**, and a mixed network — a set in series with a set in parallel — is worked out as well.

### Added — A method for systems of equations

- The **System 2×2** mode of the Equations tool now has a **Method** selector: *Automatic*, *Cramer's rule*, *Substitution*, *Elimination* or *Comparison*. Each method shows the working that it does, and a method that does not apply to the system is refused with a list of the ones that do.

### Fixed

- **Pinch-zoom on the circuit canvas now zooms the right way.** Spreading two fingers apart made the picture smaller; it now makes it bigger. The zoom value is how many world units a pixel covers, and it moves the right way now.
- **A wire finds the terminal it was dropped on.** The area around a terminal that grabs a wire was too wide, so a wire dropped near one terminal could attach itself to the next one along, and the circuit was then wrongly joined — which is what produced the false "not connected" reports. The area is now 22 dp, and it grows and shrinks with the zoom.
- **An AI answer can be opened in the calculator.** The button on an AI answer now opens the tool that produced it, in the right mode. Where the app has no matching tool, the button says so instead of opening the wrong screen.
- **A card inside a card lines up again.** The content of a solution card was indented a second time, so it did not line up with the cards around it. One spacing rule now decides where the content of a card begins, and a card inside a card says that it is one.
- **A number in front of a square is read.** `1x^2 + y = 9` was refused as "too high a degree", although it is the same as `x^2 + y = 9`. The check on the degree of a product was counting the x² term as a term of degree three; the same check also let `x^2 * y` through and dropped its term.

### Verification

- 1464 JVM unit tests, 0 failures.
- Android Lint: 0 errors, and no new warnings.
- `assembleDebug` and the signed release build both succeed.
- On a device: the check reaches GitHub and reports "You have the newest version (3.0.1)", and the About screen shows the installed version, the newest release with the day it was published, and the notes of that release.

## [3.0.1] — 2026-10-21

A small release that fixes the circuit builder: **a part can now be dragged where you want it**, and **the name of a part sits over the part it names**. `versionName = 3.0.1`, `versionCode = 5`, `minSdk 26`, `targetSdk 36`.

This release upgrades over 3.0.0 in place: the signing key is unchanged, so the app installs straight over the old one.

### Fixed

- **A part can be dragged on the canvas.** Dragging did nothing at all, and three things were at fault: the gesture was thrown away and started again every time the circuit changed, so a drag never got going; the place on the canvas was worked out from the picture's old centre, so a touch landed in the wrong place as soon as the picture had been panned; and each step of the drag was put back on the grid, so a part held to the finger could not move far enough to leave the square it was in. A part now follows the finger exactly and is put on the grid when it is let go, and the drag can be undone.
- **The name of a part is centred on the part.** The names of the parts that stand on end, and the name of the ground, were written beside them instead of over them, and the name of a part was written at a fixed distance from its centre, so a name lay on top of its symbol as soon as the picture was zoomed in. Every name is now centred on its part and written clear of it, at a distance that grows with the zoom.
- **The list of example circuits is a list.** The names are on the left with dots filling the space beside them, and the sentence under each name is left-aligned with it, so the list reads like a table of contents rather than a centred column.

### Verification

- 1410 JVM unit tests, 0 failures.
- Android Lint: 0 errors, and no new warnings.
- `assembleDebug` and the signed release build both succeed.
- On-device checks covering the three fixes: a part dragged across the canvas and let go on the grid, a tap on a part still opening its editor, a drag on the empty paper still moving the picture, and the list of examples.

---

## [3.0.0] — 2026-10-21

A major release. **Electrical Circuits** — a circuit you can draw, wire and work out — joins **eleven electrical calculators**, and the AI problem solver now works on exactly the problem it shows you. `versionName = 3.0.0`, `versionCode = 4`, `minSdk 26`, `targetSdk 36`.

This release upgrades over 2.0.0 in place: the signing key is unchanged, so the app installs straight over the old one with your settings and history intact.

### Added — Electrical Circuits: draw a circuit and work it out

**Ohm's law is now the Electrical Circuits tool**, in the *Other* category as before. The Ohm's law calculator itself has not changed — the same four quantities, the same SI prefixes, the same diagram — and it is still there, as the first of the tool's calculators.

The new part is the **circuit builder**: you put parts on a canvas, wire them together, and the app works out the circuit from its own equations.

- **A canvas you draw on.** Parts are put down by tapping, or dragged straight out of the palette onto the canvas. The picture can be dragged about and pinched to zoom, and everything on it is cut off at the edge of the canvas rather than spilling over the rest of the screen.
- **The parts are drawn as the standard symbols** — the box of a resistor, the circle with the plus and the minus of a voltage source, the circle with the arrow of a current source, the hinged arm of a switch, and the three lines of the earth symbol of a ground, widest on top. The palette shows the very same pictures.
- **Wiring by tapping.** Tap a terminal and the wire follows your finger; tap the terminal it goes to and the wire is made. A dot marks every point where three or more terminals meet.
- **Editing a part.** Tap a part to open it: its name, its value with an SI prefix (`100 kΩ`, `4.7 µF`), its power rating, and — for a switch — whether it is open or closed. The part you have just put down opens straight away, so you can give it its value.
- **The circuit is solved by modified nodal analysis**, not by a table of patterns: the app writes the equations for the circuit as you have drawn it and solves them with Gaussian elimination and partial pivoting. A circuit that cannot be solved is refused with the reason — no ground, a resistance of zero, a short-circuited source, a part that hangs.
- **The connections come from the wires, not from the picture.** Two wires that merely cross on the screen join nothing, and a closed switch joins its two ends while an open one does not, exactly as in the circuit itself.
- **Every part is reported**: the voltage across it, the current through it and the power it turns into heat, together with the resistance the source sees and the power the source delivers.
- **The working is shown for the circuit in front of you.** Where the network can be reduced, the reduction is written out step by step with the real names and the real values of the parts — `R1 and R2 are in series: 100 Ω + 200 Ω = 300 Ω`. Where it cannot, the nodal method that was used instead is named and explained. The same six sections as the rest of the app: the result and what it means, the formula and its symbols, the working, why the formula applies, and an independent check.
- **Four states are told apart**: the canvas is empty, the circuit is not finished, the circuit cannot be worked out as it stands, or it has been solved. The message names the part that is at fault.
- **Seven sample circuits** to start from — a battery and a resistor, two resistors in series, two in parallel, a resistor with a parallel pair, a switch, three parallel branches, and a voltage divider. Each one is a real circuit: every part can be edited and the circuit reworked like any other.
- **Your work is kept.** The circuit is still on the canvas when you come back to the tool, and a circuit you calculate is saved in the history with the circuit inside it, so tapping that entry opens the tool with that circuit on the canvas.

### Added — eleven electrical calculators

All of them work out their answers from the same engines the circuit builder uses, and give the same six sections with their working.

- **Ohm's law and power** — any two of voltage, current, resistance and power, and the other two are found.
- **Resistors in series** — the total resistance, the current, the drop and the power of each part, and the total power.
- **Resistors in parallel** — the combined resistance, the current in each branch and the power of each branch.
- **A series part and a parallel part** — the commonest mixed network, reduced part by part.
- **Voltage divider** — the output across the second resistor, with the note that a load on the output changes the answer.
- **Current divider** — how one current splits between two branches, with the smaller resistance taking the larger share.
- **Writing a value in another prefix** — volts, amperes, ohms, watts, farads, seconds and joules, converted exactly.
- **Resistor colour code** — the bands read into a value and a tolerance, and the other way round.
- **A capacitor** — the charge it holds, the energy stored in it, and the time it takes to charge through a resistance.
- **How long a circuit takes to settle** — the time constant of an RC or an RL circuit, and what the curve has reached after one and after five of them.
- **An alternating circuit** — the reactances, the impedance, the current, the phase, the power factor and the real and reactive power.

### Improved — the AI problem solver works on the problem it shows

- **The problem that is shown and the problem that is worked out are now the same thing.** The arguments are checked against the tool's own list and the tool's defaults are filled in once, and that single set is what the engine is given and what the screen shows. Before, the screen showed the engine's own rewriting of the problem, which is a different expression from the one you recognised, and it looked as though the wrong problem had been solved.
- **A corrected input reaches the engine.** The arguments in the correction dialog are the ones that are run, and the reading is updated to match. A run that fails leaves the last good answer alone and says what went wrong.
- **The answer is shown as mathematics**: the working first and then the answer, so the result can be checked against the work that produced it.
- **The answer can be opened in the tool that made it**, with the problem already in the fields.

### Improved — the history reopens the problem it saved

- A **saved calculation now opens its tool with the problem in it**. The history entry keeps the values the calculation was made from, and the tool screen is given them when it opens — a saved circuit comes back with its parts in place, and a saved differential equation comes back with its equation and its limits.
- An entry whose tool **is not in this build** now shows a screen that says so, instead of an empty page.

### Improved — the equations and the inequalities explain themselves

- The **equations tool** now has a curated explanation in the same expandable sections the newer tools use: what the mode in use means, the formula and its symbols, the conditions the method needs, the working that actually ran, what the answer means and what the tool does not reach.
- The **inequalities** show their discriminant work: the coefficients, the calculation of Δ, whether the roots are real, repeated or absent, the roots themselves, and what the sign of the quadratic does between them.
- A **system of inequalities is written one expression per field**, with an "Add expression" button, so the keyboard never has to produce a new line.

### Fixed

- The **differential-equation graph is fitted to its solution**, so the curve is seen over the whole interval it was worked out for, whether it was found symbolically or numerically. The window still has the same number of units in a pixel on each axis, so a slope of one is drawn at forty-five degrees and the slope field is true, and a solution is never drawn beyond the interval it was computed for.
- The **AI screen's buttons** share one style: one line of text, the same height, the icon beside the label, and the two picture buttons one above the other so that neither has to wrap.
- The **action buttons** across the app share one style, so that a row of them reads as one group.

### Technical and reliability changes

- The circuit engine is a new package, `engine.electrical`, with no dependency on Android: the model, the topology, the solver, the validator, the explainer, the units and the calculators are all plain Kotlin, and they are tested on the JVM.
- A circuit is written down in a small text format of its own, so that it can be kept in the history, handed over and read back without the phone's own libraries — and a circuit that cannot be read back is refused rather than half-restored.
- The electrical units have one table of prefixes, used by the solver, the calculators and the screen alike, so a value can never be scaled twice or by the wrong power of ten.
- The shared explanation sections are one component, so that every tool that shows its working shows it in the same way.
- The history, the tool registry and the navigation now share one handoff mechanism, so that a problem can be passed from one screen to another whether it came from the AI, from the history or from another tool.

### Known limitations in 3.0.0

- The **circuit builder is for steady direct current** with ideal parts. There is no transient analysis, no frequency sweep and no non-ohmic component; the alternating-current calculator is a separate calculation for a single frequency, not a simulation of a circuit over time.
- A circuit needs **one ground**, and the voltages are measured against it.
- The **switch is ideal**: closed it is a conductor and open it is a break, with no contact resistance.
- The power rating of a resistor is kept, but the app does not yet warn when a part is being worked harder than its rating.
- The AI feature still needs a **vision-capable model**, and the limitations of the 2.0.0 release still stand.

### Documentation

- `README.md`, `CHANGELOG.md`, `ROADMAP.md`, `PRIVACY.md`, `SECURITY.md`, `SUPPORT.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`.
- `docs/`: `FEATURES_AND_TOOLS.md`, `USER_GUIDE.md`, `CALCULUS_GUIDE.md`, `ARCHITECTURE.md`, `BUILDING.md`, `DEPENDENCIES.md`, `FAQ.md`.

### Verification

- 1410 JVM unit tests, 0 failures — 129 of them new, covering the solver against the textbook answers, the topology, the sample circuits, the explanations against the numbers the solver found, the four states of a circuit, the calculators, the units and prefixes, saving a circuit and reading it back, and the geometry of the drawing.
- Android Lint: 0 errors, and no new warnings.
- `assembleDebug` and the signed release build both succeed.
- On-device checks covering the new tool end to end: the samples drawing as schematics, the numbers agreeing with the textbook, a part dragged out of the palette, wires drawn by tapping the terminals, the circuit kept between visits, and a circuit restored from the history.

---

## [2.0.0] — 2026-10-20

A major release. **Seventeen more tools** (49 → 66), an **AI problem solver** that reads a photograph of a mathematical problem and works it out with the app's own engines, a **customisable accent colour** that reaches every part of the app, and a long list of correctness and layout fixes. `versionName = 2.0.0`, `versionCode = 3`, `minSdk 26`, `targetSdk 36`.

This release upgrades over 1.0.0 in place: the signing key is unchanged, so the app can be installed straight over the old one.

> **Republished with a higher version code.** The first 2.0.0 APK carried the version code 2, which installs over 1.0.0 (version code 1) cleanly, and it was signed with the same key. The APK published under this tag now carries the version code **3**, so that it installs over the first 2.0.0 build as well as over 1.0.0. The version name is unchanged, and the signing key is the same one, so it is still an in-place upgrade of either.

> **If Android says the app "conflicts with an existing package"**, the copy already on the phone was signed with a *different* key — most often a debug build. Android will not replace one app with another signed by another key, whatever the version code is. Uninstall the old copy once (`Settings → Apps → Calc Suite → Uninstall`, or `adb uninstall com.calcsuite`) and the release installs over anything afterwards.

### Added — the AI problem solver

A brain button now sits beside the history button on the main screen. It opens a screen where you photograph a mathematical problem, or pick a picture of one, and the app:

1. sends the picture to the **AI provider you chose**, together with the list of the mathematical tools that may be used;
2. has the model **read the problem** and **choose a tool**, with the arguments that tool expects;
3. **checks the choice** against the registry and the arguments against that tool's own schema;
4. **runs the engine that tool already uses**, so the answer is Calculator Suite's own answer and not the model's.

- **Ten providers**: OpenAI, Anthropic, Google Gemini, OpenRouter, DeepSeek, Groq, Mistral AI, xAI, Together AI and Fireworks AI. Each one's address, authentication, picture format and reply shape were checked against that provider's own documentation before being written. The model name is typed by hand, so a model released today can be used today.
- **The key is kept by the phone's keystore**, encrypted with AES/GCM, and never written in plain text, logged, or included in an error message. The provider's own error is rewritten so that a key it repeats cannot leak. The key can be replaced or removed at any time.
- **The picture is really in the request**, in the format the provider documents: a data URL for the OpenAI-compatible eight, an image block with a source for Anthropic, and inline data for Gemini. Pictures are turned the right way up from their EXIF data and are made smaller only when they are very large, with the quality kept high so that small symbols and handwriting survive.
- **Twenty-four tools are eligible**, across the calculus, the algebra and the advanced mathematics, and every one of them calls the engine its own screen calls. The tools that cannot answer a mathematical problem — the random generator, the unit converters, the finance, health, date and time tools and the geometry diagrams — are not offered to the model at all, and a test keeps them out.
- **The grammar of each tool is the grammar of the parser that already exists.** The model is taught to write `x^2` rather than `x²`, and the two-equation system takes the six coefficients the engine actually takes, with the answer substituted back into both equations before it is shown.
- **Four answers are told apart**: a problem that was read and can be worked out, a problem that is ambiguous, a problem no eligible tool can answer, and a reply that could not be read. An ambiguous problem is shown to the user with the question that has to be answered; it is never guessed at. The extracted inputs can be corrected and the tool run again.
- **The answer is the engine's**, with its own working, and a number that was worked out numerically is marked as an approximation and says how it was found. The AI-generated prose is never shown as if it were the engine's output.

### Added — seventeen more tools (49 → 66)

The old catch-all **Calculus** tool in Algebra has been replaced by fourteen focused tools, and the categories are now eight.

**Calculus (14):**

- **Derivatives** — symbolic differentiation of one or two variables, of any order, with the working shown.
- **Integrals** — indefinite antiderivatives and definite integrals by adaptive Simpson, the latter marked as an approximation.
- **Limits** — one-sided and two-sided, including infinite limits, with the method named.
- **Taylor & Maclaurin** — expansions to order 12, with every coefficient shown.
- **Summations & Products** — Σ and Π notation, with the index inferred from the term.
- **Infinite Series** — a convergence verdict for a series with general terms, and a closed form where one exists.
- **Equation Solving & Roots** — root finding over an interval, with the roots polished by Newton's method.
- **Extrema & Optimization** — stationary points and their classification over an interval.
- **Function Graphing** — an interactive plot with pan, pinch-zoom about the point you touch, an optional `f′` layer, a shaded area between two limits and a reset.
- **Differential Equations** — first-order equations solved symbolically where possible (separable and linear), and numerically otherwise, with an initial value, an initial velocity, and the stability condition checked before anything is computed.
- **Multivariable Calculus** — partial derivatives of any order, mixed partials, the gradient, the directional derivative, the Jacobian, the Hessian, the divergence, the curl, the Laplacian, the critical points with the second-derivative test, and the double, triple and line integrals, with a rotatable surface and a contour picture drawn from the same expression.
- **Fourier Analysis** — the real Fourier series of an expression with the coefficients worked out, and the discrete transform of a list of samples, kept apart; the function and its approximation are drawn together.
- **Complex Analysis** — polar and exponential forms, the evaluation of complex functions, a numerical Cauchy–Riemann check, contour integrals and residues, with an interactive complex plane.
- **Partial Differential Equations** — the heat and wave equations in one space dimension and Laplace's and Poisson's equations in two, solved by finite differences, with the stability condition checked first, the classification of a second-order equation, and a colour map of the solution.

**Algebra (1):**

- **Inequalities** — single, compound and two-variable inequalities, with the solution set, the critical points, the sign chart and a picture of the region in the plane.

**Advanced mathematics (3):**

- **Measure Theory** — the measure of a finite union of intervals, the union and the overlap, countable additivity through the geometric series, the lower and upper Riemann sums and what they say about whether the integral exists, and null sets and almost-everywhere equality.
- **Topology Explorer** — sets of real numbers with open and closed ends, their interior, closure, boundary, connectedness and compactness; a finite topological space with its open sets; a region of the plane; and the homeomorphisms.
- **Functional Analysis** — inner products, norms, distances and angles, orthogonal projections, the least-squares fit, the eigenvalues and eigenvectors of a matrix, the Gram–Schmidt process, and an accurate account of the infinite-dimensional case.

All three advanced tools compute real values for the example in front of them and state what they do not reach.

### Added — the accent colour

- A row of **preset swatches** under the colour setting, with a **custom colour** picker offering a colour square, a hue slider, a hexadecimal field and a preview.
- The accent **reaches every part of the app**: the buttons, the selection, the icons, the curves in the graphs, the surfaces and the text. Every colour keeps the lightness the app already had, so the contrast is the same whatever colour is chosen.
- The **green is unchanged**: choosing green gives back the app's original colours exactly, so a user who has never chosen an accent sees what they have always seen. The error red and the amber of the third colour keep their own identity.
- The choice is **kept** between restarts, and a damaged setting falls back to the green rather than leaving the app without a colour.

### Fixed — correctness

Each of these is covered by a regression test.

- **The sign bound tighter than the power** in the calculus parser, so `-x^2` meant `(-x)^2` — the opposite of what the parser's own grammar says, what the printer assumes and what the rest of the app does. The sign is now looser than the power, and the two readings are kept apart:

  | Written | Read as | Value |
  |---|---|---|
  | `-2^2` | the negative of `2^2` | **-4** |
  | `(-2)^2` | the square of minus two | **4** |

  A negative number raised to an even power is positive **when the sign is part of the base**, as in `(-2)^2 = 4`, and it is negative **when the sign stands in front of the power**, as in `-2^2 = -(2^2) = -4`. That is the usual convention, and it is the one the whole app now follows: the standard calculator, the calculus tools and the printer. Both forms are pinned by a test.
- **A negative factor in a product was printed without brackets**, so `x * -2` was shown as `x-2`, a different expression. The printer now brackets it.
- **The curl** was using the wrong six derivatives and returned the wrong vector.
- **The least-squares solver** was checking the data against the number of columns instead of the number of rows, and refused every fit. The same mistake made **Projections in Functional Analysis crash**; the three sections now check their shapes before they compute.
- **The Fourier coefficients** were computed with the adaptive rule, which does not notice a fast oscillation, so the higher harmonics were wrong. A fixed rule with enough panels is used now, and the number of panels follows the number of harmonics.
- **The constant term** of a Fourier series was zero when no harmonics were asked for, instead of the mean of the function.
- **The complex inverse functions** took the wrong branch: the arcsine had the wrong sign in front of the square root, and the arccosine was not the complement of the arcsine. The principal cube root was picked by its imaginary part rather than by its argument.
- **A pole** was reported as a removable singularity, because a function that is undefined at the point was taken to have a removable singularity there. The limit now has to settle before it is believed, and the residue of a pole of higher order is computed from the derivative.
- **The wave equation** had no initial velocity, so a wave could only start at rest, and the rows of the solution were at the wrong times. It now takes an initial velocity, uses the second-order expansion for the first row, and the times line up with the rows.
- **The topology model** could not represent an open interval, so the open and closed sets were the same thing. The intervals now carry their ends, the boundary of a closed interval was empty, the closure of a finite space took the smallest closed set by size rather than the intersection, and the complement of a set included the points of the set.
- **The critical point search** only looked at the diagonal neighbours, so a point where the gradient changed sign in one direction alone was missed. It looks at all eight now.
- **The second homeomorphism** sent the line to (0, 3⁄2) rather than into the open interval.
- **The polynomial root finder** was duplicated in the differential equation engine; it is now the shared one, its iteration is synchronous, and each root is polished with Newton's method.

### Fixed — the pictures and the gestures

- The **pan** now moves the picture with the finger in every direction. The vertical pan had the opposite sign to the horizontal one, so dragging down moved the graph up; the Function Graphing, the Inequalities plane, the slope field, the complex plane and the surface all used it.
- The **pinch** keeps the point under the fingers where it was, and the scales are the same on both axes, so a slope of one is drawn at forty-five degrees.
- The **level curves** of the gradient field are traced with the marching-squares rule, so they follow the true contours and cannot be drawn as long lines across the picture. The arrows have a fixed length on the screen.
- The **surface** is drawn the right way up at every tilt, and turning it carries the part nearest the viewer with the finger. The surface and the gradient field show the same part of the function.
- The **complex plane** maps a fixed circle, so moving the picture shows another part of the same image and never changes the function.
- The **heat equation** opens with numbers that make the scheme stable, so the first picture shows the heat spreading rather than a sheet of nonsense. The Poisson equation opens with a source, so its picture shows the bowl it should.
- The **Laplace and Poisson picture** was flat. The cause was the default: the boundary was `0`, and Laplace's equation with a constant boundary has a constant steady state, so the field was zero everywhere and the picture was one colour. The default boundary is now `sin(pi*x)`, the range reported is the true one, a flat field is explained in words rather than left looking like a failure, and the colours of every field follow the accent instead of a fixed blue.
- The **category cards** are all the same height, and the redundant Calculus button at the bottom of the main menu is gone.
- The **explanations** are spaced with one shared rule, so the expandable sections in the new tools have the same breathing room as the ones in Inequalities.
- The **search bar** counts the tools from the registry, in the compact `Search 66 tools` form.
- The **Integrals** icon is the integral sign again.

### Changed

- The **network** is no longer only the currency converter. The AI problem solver talks to the provider you choose, and nothing else in the app talks to anybody. The full disclosure is in [PRIVACY.md](PRIVACY.md).
- The **tool registry** gained a new category, **Advanced Mathematics**, and the calculus tools were split out of Algebra into the Calculus category, so the menu matches the mathematics.

### Known limitations in 2.0.0

Documented in full in [ROADMAP.md](ROADMAP.md) and the guides under `docs/`; summarised here because they matter when reading the docs:

- The **AI feature needs a vision-capable model**. If the model you choose cannot read pictures, the request is refused rather than pretended to have worked, and the app never moves you to another model on its own.
- The **boundary of the two-dimensional equations** is a single expression used on all four sides, so the textbook "hot along the top, cold elsewhere" cannot be written directly.
- The indefinite integral pattern-matcher can return a result labelled "exact" for a power whose base is not the integration variable (for example `∫sin(x)²dx`). Prefer the definite integral for such expressions.
- `∫2/x dx` is reported as having no elementary antiderivative; only a numerator of exactly `1` is recognised as a logarithm.
- Calculus root finding, extrema and the graph are bound to the variable `x`.
- The expression parser reads a run of letters as one name, so `3xy` needs the multiplication signs putting in. The multivariable tool does that itself, because it knows the variables.
- The Fourier coefficients are found by numerical integration, so they are estimates.
- The contour integral and the residue are numerical estimates, and the Cauchy–Riemann check is a local numerical check, not a proof of analyticity.
- The measure tool handles finite unions of intervals only, and the topology tool handles intervals, finite spaces and a few plane regions.
- Above 3.3 × 10²⁴ the prime checker reports a *strong probable prime*, not a proof.

### Documentation

- `README.md`, `CHANGELOG.md`, `ROADMAP.md`, `PRIVACY.md`, `SECURITY.md`, `SUPPORT.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE`.
- `docs/`: `FEATURES_AND_TOOLS.md`, `USER_GUIDE.md`, `CALCULUS_GUIDE.md`, `ARCHITECTURE.md`, `BUILDING.md`, `DEPENDENCIES.md`, `FAQ.md`.

### Verification

- 1267 JVM unit tests, 0 failures.
- Android Lint: 0 errors, and no new warnings.
- `assembleDebug` and the signed release build both succeed.
- On-device checks covering the AI flow end to end, the accent across the whole app, every new tool opening, computing and drawing, and the light and dark themes.

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

[3.0.2]: https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v3.0.2
[3.0.1]: https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v3.0.1
[3.0.0]: https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v3.0.0
[2.0.0]: https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/2.0.0-release
[1.0.0]: https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v1.0.0

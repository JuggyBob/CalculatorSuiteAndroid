package com.calcsuite.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MultilineChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Water
import androidx.compose.ui.graphics.vector.ImageVector

/** The seven top-level categories of the app. */
enum class ToolCategory(val id: String, val label: String, val icon: ImageVector) {
    ALGEBRA("algebra", "Algebra", Icons.Filled.Functions),
    GEOMETRY("geometry", "Geometry", Icons.Filled.Category),
    CONVERT("convert", "Converters", Icons.Filled.SwapHoriz),
    FINANCE("finance", "Finance", Icons.Filled.AccountBalance),
    HEALTH("health", "Health", Icons.Filled.MonitorHeart),
    DATETIME("datetime", "Date & Time", Icons.Filled.CalendarMonth),
    CALCULUS("calculus", "Calculus", Icons.Filled.Calculate),
    ADVANCED("advanced", "Advanced Mathematics", Icons.Filled.AccountTree),
    OTHER("other", "Other", Icons.Filled.MoreHoriz),
}

/** One entry of the tool catalogue and of the navigation graph. */
data class ToolDef(
    val id: String,
    val title: String,
    val category: ToolCategory,
    val description: String,
    val synonyms: List<String>,
    val icon: ImageVector,
)

/** The single source of truth for every tool in the app. */
object ToolRegistry {

    private val tools: List<ToolDef> = listOf(
        // ------------------------------------------------------------ Algebra
        ToolDef(
            "algebra.percentage", "Percentage", ToolCategory.ALGEBRA,
            "Percent of a number, percent change, percentage points",
            listOf("percent", "%", "increase", "decrease", "discount", "change", "points", "markup"),
            Icons.Filled.Percent,
        ),
        ToolDef(
            "algebra.average", "Average", ToolCategory.ALGEBRA,
            "Mean, weighted mean, median, mode and geometric mean",
            listOf("mean", "median", "mode", "geometric", "weighted", "statistics", "dataset"),
            Icons.AutoMirrored.Filled.Sort,
        ),
        ToolDef(
            "algebra.proportion", "Proportion", ToolCategory.ALGEBRA,
            "Solve a/b = c/d for any unknown, direct or inverse",
            listOf("ratio proportion", "cross multiply", "solve for x", "a/b=c/d", "inverse"),
            Icons.Filled.ChangeHistory,
        ),
        ToolDef(
            "algebra.ratio", "Ratio", ToolCategory.ALGEBRA,
            "Simplify, scale, split totals and compare ratios",
            listOf("simplify ratio", "scale", "split", "share", "gcd", "proportion"),
            Icons.Filled.GridView,
        ),
        ToolDef(
            "algebra.equations", "Equations", ToolCategory.ALGEBRA,
            "Linear, quadratic and 2×2 systems with steps",
            listOf("solve", "quadratic", "linear", "simultaneous", "roots", "discriminant", "system"),
            Icons.Filled.Functions,
        ),
        ToolDef(
            "algebra.gcf_lcm", "GCF & LCM", ToolCategory.ALGEBRA,
            "Greatest common factor and least common multiple",
            listOf("gcd", "hcf", "greatest common divisor", "lcm", "factorization", "factors"),
            Icons.Filled.Calculate,
        ),
        ToolDef(
            "algebra.combinations", "Combinations", ToolCategory.ALGEBRA,
            "nCr combinations, nPr permutations and probabilities",
            listOf("ncr", "npr", "choose", "permutation", "factorial", "probability", "lottery"),
            Icons.Filled.Casino,
        ),
        ToolDef(
            "algebra.primes", "Prime checker", ToolCategory.ALGEBRA,
            "Primality test, factorization, neighbours",
            listOf("prime", "composite", "factorization", "is prime", "next prime"),
            Icons.Filled.Numbers,
        ),
        ToolDef(
            "algebra.random", "Number generator", ToolCategory.ALGEBRA,
            "Random integers or decimals with rules",
            listOf("random", "rng", "dice", "lottery", "pick", "seed", "shuffle"),
            Icons.Filled.Shuffle,
        ),
        ToolDef(
            "algebra.inequalities", "Inequalities", ToolCategory.ALGEBRA,
            "Solve inequalities and systems, with a sign chart and a number line",
            listOf(
                "inequality", "inequalities", "solve for x", "greater than", "less than",
                "at least", "at most", "compound inequality", "system of inequalities",
                "sign chart", "solution set", "interval notation", "number line",
                "half plane", "feasible region", "polynomial inequality",
                "rational inequality", "absolute value inequality",
            ),
            InequalityIcon,
        ),

        // ----------------------------------------------------------- Geometry
        ToolDef(
            "geometry.shapes", "2D Shapes", ToolCategory.GEOMETRY,
            "Area, perimeter and diagonals of 11 plane shapes",
            listOf("area", "perimeter", "circle", "triangle", "square", "rectangle", "trapezoid", "polygon", "ellipse", "rhombus", "parallelogram"),
            Icons.Filled.Category,
        ),
        ToolDef(
            "geometry.bodies", "3D Bodies", ToolCategory.GEOMETRY,
            "Volume and surface areas of 9 solids",
            listOf("volume", "surface area", "sphere", "cube", "cylinder", "cone", "pyramid", "prism", "hemisphere"),
            Icons.Filled.ViewInAr,
        ),

        // -------------------------------------------------------- Converters
        ToolDef(
            "convert.acceleration", "Acceleration", ToolCategory.CONVERT,
            "m/s², ft/s², g and more",
            listOf("gravity", "g force", "ms2", "feet per second squared"),
            Icons.Filled.Speed,
        ),
        ToolDef(
            "convert.angle", "Angle", ToolCategory.CONVERT,
            "Degrees, radians, gradians, turns, arcminutes",
            listOf("degrees", "radians", "grad", "turn", "arcmin", "arcsec", "dms"),
            Icons.Filled.Architecture,
        ),
        ToolDef(
            "convert.area", "Area", ToolCategory.CONVERT,
            "m², hectares, acres, square feet",
            listOf("square meters", "acres", "hectare", "square feet", "square miles", "land"),
            Icons.Filled.SquareFoot,
        ),
        ToolDef(
            "convert.cooking", "Cooking", ToolCategory.CONVERT,
            "Cups, spoons, millilitres, grams with densities",
            listOf("recipe", "cups", "tablespoon", "teaspoon", "ml", "ounces", "grams", "baking", "kitchen"),
            Icons.Filled.Restaurant,
        ),
        ToolDef(
            "convert.data_storage", "Data storage", ToolCategory.CONVERT,
            "Bytes, MB, MiB, bits and binary prefixes",
            listOf("mb", "mib", "gib", "bytes", "bits", "kib", "storage", "kb", "tb"),
            Icons.Filled.Storage,
        ),
        ToolDef(
            "convert.data_transfer", "Data transfer", ToolCategory.CONVERT,
            "Bit/s and B/s rates plus transfer time",
            listOf("mbps", "bandwidth", "download time", "transfer time", "bitrate", "internet speed"),
            Icons.Filled.NetworkCheck,
        ),
        ToolDef(
            "convert.energy", "Energy", ToolCategory.CONVERT,
            "Joules, calories, kWh, BTU, electronvolts",
            listOf("joule", "calorie", "kcal", "wh", "kwh", "btu", "electronvolt", "food calories"),
            Icons.Filled.Bolt,
        ),
        ToolDef(
            "convert.force", "Force", ToolCategory.CONVERT,
            "Newtons, pound-force, dyne, kgf",
            listOf("newton", "lbf", "pound force", "dyn", "kgf", "kilogram force"),
            Icons.Filled.FitnessCenter,
        ),
        ToolDef(
            "convert.fuel", "Fuel", ToolCategory.CONVERT,
            "L/100 km, mpg (US/UK), km/L and trip cost",
            listOf("fuel economy", "fuel consumption", "mpg", "l/100km", "km/l", "gas", "petrol", "mileage", "trip cost"),
            Icons.Filled.LocalGasStation,
        ),
        ToolDef(
            "convert.length", "Length", ToolCategory.CONVERT,
            "mm to miles, inches, nautical miles",
            listOf("distance", "meter", "feet", "inch", "yard", "mile", "nm", "nautical", "kilometer"),
            Icons.Filled.Straighten,
        ),
        ToolDef(
            "convert.numeric_base", "Numeric base", ToolCategory.CONVERT,
            "Binary, octal, hex, base 2–36, two's complement",
            listOf("hex", "hexadecimal", "binary", "octal", "base 16", "base 2", "programmer", "bits", "unsigned", "twos complement"),
            Icons.Filled.Memory,
        ),
        ToolDef(
            "convert.power", "Power", ToolCategory.CONVERT,
            "Watts, kilowatts, horsepower (mechanical/metric), BTU/h",
            listOf("watt", "kw", "hp", "horsepower", "megawatt", "btu per hour"),
            Icons.Filled.FlashOn,
        ),
        ToolDef(
            "convert.pressure", "Pressure", ToolCategory.CONVERT,
            "Pa, bar, atm, psi, torr, mmHg",
            listOf("pascal", "bar", "atmosphere", "psi", "torr", "mmhg", "kpa", "mpa", "tire pressure"),
            Icons.Filled.Compress,
        ),
        ToolDef(
            "convert.ring_size", "Ring size", ToolCategory.CONVERT,
            "US, UK, EU, JP sizes from circumference or diameter",
            listOf("ring", "jewellery", "jewelry", "finger size", "diameter", "circumference", "band"),
            Icons.Filled.RadioButtonChecked,
        ),
        ToolDef(
            "convert.roman", "Roman numerals", ToolCategory.CONVERT,
            "Convert 1–3999 to and from Roman numerals",
            listOf("roman", "numerals", "mcmxciv", "latin", "ancient numbers"),
            Icons.Filled.Abc,
        ),
        ToolDef(
            "convert.shoe_size", "Shoe size", ToolCategory.CONVERT,
            "EU, US, UK, JP shoe sizes (adult & children)",
            listOf("shoe", "sneaker", "footwear", "eu size", "uk size", "us size", "foot length", "kids shoes"),
            Icons.Filled.DirectionsWalk,
        ),
        ToolDef(
            "convert.speed", "Speed", ToolCategory.CONVERT,
            "km/h, mph, m/s, knots, ft/s",
            listOf("velocity", "kmh", "mph", "knots", "m/s", "pace"),
            Icons.Filled.Speed,
        ),
        ToolDef(
            "convert.temperature", "Temperature", ToolCategory.CONVERT,
            "Celsius, Fahrenheit, Kelvin with absolute zero",
            listOf("celsius", "fahrenheit", "kelvin", "°c", "°f", "weather", "heat"),
            Icons.Filled.Thermostat,
        ),
        ToolDef(
            "convert.time", "Time", ToolCategory.CONVERT,
            "ns to weeks, plus duration formatting",
            listOf("seconds", "minutes", "hours", "days", "weeks", "duration", "milliseconds", "convert time"),
            Icons.Filled.Schedule,
        ),
        ToolDef(
            "convert.torque", "Torque", ToolCategory.CONVERT,
            "N·m, lbf·ft, lbf·in, kgf·m",
            listOf("newton meter", "foot pound", "moment", "tightening", "lbf ft"),
            Icons.Filled.Build,
        ),
        ToolDef(
            "convert.volume", "Volume", ToolCategory.CONVERT,
            "Litres, gallons (US/Imperial), m³, cups",
            listOf("litre", "liter", "gallon", "quart", "pint", "fluid ounce", "cubic meter", "capacity"),
            Icons.Filled.Water,
        ),
        ToolDef(
            "convert.flow", "Volumetric flow", ToolCategory.CONVERT,
            "m³/s, L/min, gpm, cfs plus volume from time",
            listOf("flow rate", "l/min", "gpm", "cubic meters per second", "discharge", "throughput"),
            Icons.Filled.Water,
        ),
        ToolDef(
            "convert.weight", "Weight & mass", ToolCategory.CONVERT,
            "kg, g, tonnes, ounces, pounds, stones",
            listOf("mass", "kg", "pounds", "lbs", "ounces", "stone", "tonne", "grams", "body weight"),
            Icons.Filled.Scale,
        ),

        // ----------------------------------------------------------- Finance
        ToolDef(
            "finance.currency", "Currency converter", ToolCategory.FINANCE,
            "Live reference rates with offline cache",
            listOf("exchange rate", "usd", "eur", "gbp", "money", "forex", "dollar", "euro", "convert currency"),
            Icons.Filled.CurrencyExchange,
        ),
        ToolDef(
            "finance.unit_price", "Unit price", ToolCategory.FINANCE,
            "Compare price per kg, litre, item or metre",
            listOf("price per unit", "value", "compare prices", "grocery", "cost per kg", "cheaper"),
            Icons.Filled.AttachMoney,
        ),
        ToolDef(
            "finance.tax", "Sales tax", ToolCategory.FINANCE,
            "Add or extract tax, tax-inclusive prices",
            listOf("vat", "gst", "tax inclusive", "sales tax", "net", "gross"),
            Icons.Filled.Receipt,
        ),
        ToolDef(
            "finance.tip", "Tip", ToolCategory.FINANCE,
            "Tip, total and split with cent allocation",
            listOf("gratuity", "restaurant", "bill", "split", "service charge", "tipping"),
            Icons.Filled.Restaurant,
        ),
        ToolDef(
            "finance.loan", "Loan", ToolCategory.FINANCE,
            "Payments, interest, amortization, extra payments",
            listOf("mortgage", "amortization", "emi", "installment", "repayment", "car loan", "apr"),
            Icons.Filled.AccountBalance,
        ),
        ToolDef(
            "finance.interest", "Interest", ToolCategory.FINANCE,
            "Simple and compound interest with EAR",
            listOf("compound", "simple interest", "apy", "savings", "investment", "growth", "effective rate"),
            Icons.Filled.Savings,
        ),

        // ------------------------------------------------------------ Health
        ToolDef(
            "health.bmi", "Body mass index", ToolCategory.HEALTH,
            "BMI in metric or imperial units",
            listOf("bmi", "body mass", "obesity", "weight status", "quetelet"),
            Icons.Filled.MonitorWeight,
        ),
        ToolDef(
            "health.calories", "Caloric burn", ToolCategory.HEALTH,
            "Energy burned by activity using MET values",
            listOf("calories burned", "exercise", "met", "workout", "energy expenditure", "running", "cycling"),
            Icons.Filled.LocalFireDepartment,
        ),
        ToolDef(
            "health.body_fat", "Body fat", ToolCategory.HEALTH,
            "US Navy circumference method estimate",
            listOf("body fat percentage", "navy method", "fat estimate", "waist", "neck"),
            Icons.Filled.MonitorHeart,
        ),

        // ------------------------------------------------------- Date & Time
        ToolDef(
            "datetime.age", "Age calculator", ToolCategory.DATETIME,
            "Exact age in years, months, days and totals",
            listOf("age", "birthday", "born", "how old", "date of birth"),
            Icons.Filled.Cake,
        ),
        ToolDef(
            "datetime.add_subtract", "Add & subtract time", ToolCategory.DATETIME,
            "Add or subtract years, months, days, time",
            listOf("date math", "add days", "subtract months", "due date", "deadline", "date calculator"),
            Icons.Filled.CalendarMonth,
        ),
        ToolDef(
            "datetime.interval", "Time interval", ToolCategory.DATETIME,
            "Duration between two dates or timestamps",
            listOf("difference", "between dates", "duration", "elapsed", "countdown", "working days"),
            Icons.Filled.Timer,
        ),

        // ------------------------------------------------------------- Other
        // ---------------------------------------------------------- Calculus
        ToolDef(
            "calculus.derivatives", "Derivatives", ToolCategory.CALCULUS,
            "Symbolic and numerical derivatives, higher orders and partials",
            listOf(
                "derivative", "differentiate", "d/dx", "partial derivative",
                "gradient", "tangent", "rate of change", "higher order derivative",
            ),
            Icons.Filled.TrendingUp,
        ),
        ToolDef(
            "calculus.integrals", "Integrals", ToolCategory.CALCULUS,
            "Indefinite and definite integration, exact where it exists",
            listOf(
                "integral", "integrate", "antiderivative", "area under a curve",
                "definite integral", "indefinite integral", "accumulated area",
            ),
            IntegralIcon,
        ),
        ToolDef(
            "calculus.limits", "Limits", ToolCategory.CALCULUS,
            "Finite, one-sided and infinite limits",
            listOf(
                "limit", "as x approaches", "indeterminate form", "lhopital",
                "left hand limit", "right hand limit", "infinity",
            ),
            Icons.Filled.HorizontalRule,
        ),
        ToolDef(
            "calculus.taylor", "Taylor & Maclaurin", ToolCategory.CALCULUS,
            "Expansions around a centre, with every coefficient",
            listOf(
                "taylor series", "maclaurin series", "series expansion",
                "power series", "coefficients", "approximation by polynomial",
            ),
            Icons.Filled.AutoGraph,
        ),
        ToolDef(
            "calculus.summations", "Summations & Products", ToolCategory.CALCULUS,
            "Finite sums and products in sigma and pi notation",
            listOf(
                "sum", "summation", "sigma", "product", "pi notation", "factorial sum",
                "add up a series", "multiply a series",
            ),
            Icons.Filled.FormatListNumbered,
        ),
        ToolDef(
            "calculus.series", "Infinite Series", ToolCategory.CALCULUS,
            "Convergence of an infinite series and its closed form",
            listOf(
                "infinite series", "convergence", "diverges", "geometric series",
                "ratio test", "sum to infinity",
            ),
            Icons.Filled.Timeline,
        ),
        ToolDef(
            "calculus.roots", "Equation Solving & Roots", ToolCategory.CALCULUS,
            "Find the real roots of an equation",
            listOf(
                "roots", "solve for x", "zeros", "root finder", "equation",
                "where is it zero", "intersections",
            ),
            Icons.Filled.Functions,
        ),
        ToolDef(
            "calculus.extrema", "Extrema & Optimization", ToolCategory.CALCULUS,
            "Stationary points, local extrema and critical points",
            listOf(
                "extrema", "maximum", "minimum", "stationary point", "critical point",
                "inflection", "optimise", "optimization", "turning point",
            ),
            Icons.Filled.BubbleChart,
        ),
        ToolDef(
            "calculus.graph", "Function Graphing", ToolCategory.CALCULUS,
            "Plot a function, its derivative and an area under the curve",
            listOf(
                "graph", "plot", "sketch", "curve", "zoom", "pan", "graph a function",
                "area under the graph",
            ),
            Icons.Filled.ShowChart,
        ),
        ToolDef(
            "calculus.differential_equations", "Differential Equations", ToolCategory.CALCULUS,
            "Solve ODEs symbolically or numerically, and graph the solution",
            listOf(
                "differential equation", "ode", "dy/dx", "initial value problem",
                "ivp", "separable", "first order linear", "euler method",
                "runge kutta", "slope field", "direction field", "ivp solver",
            ),
            Icons.Filled.Timeline,
        ),

        ToolDef(
            "calculus.multivariable", "Multivariable Calculus", ToolCategory.CALCULUS,
            "Partial derivatives, gradients, Hessians and multiple integrals",
            listOf(
                "multivariable", "partial derivative", "gradient", "directional derivative",
                "jacobian", "hessian", "divergence", "curl", "laplacian",
                "critical point", "double integral", "triple integral", "line integral",
                "optimization in two variables", "surface", "3d graph",
            ),
            Icons.Filled.ViewInAr,
        ),
        ToolDef(
            "calculus.fourier", "Fourier Analysis", ToolCategory.CALCULUS,
            "Fourier series, harmonics and the discrete transform",
            listOf(
                "fourier", "fourier series", "harmonics", "coefficient",
                "discrete fourier transform", "dft", "fft", "frequency", "spectrum",
                "signal", "sine series", "cosine series", "gibbs",
            ),
            Icons.Filled.GraphicEq,
        ),
        ToolDef(
            "calculus.complex", "Complex Analysis", ToolCategory.CALCULUS,
            "Complex numbers, functions, contours and residues",
            listOf(
                "complex number", "imaginary", "modulus", "argument", "polar form",
                "conjugate", "euler", "contour integral", "residue", "cauchy riemann",
                "holomorphic", "branch cut", "complex plane",
            ),
            Icons.Filled.AllInclusive,
        ),
        ToolDef(
            "calculus.pde", "Partial Differential Equations", ToolCategory.CALCULUS,
            "Heat, wave, Laplace and Poisson equations, solved numerically",
            listOf(
                "partial differential equation", "pde", "heat equation", "diffusion",
                "wave equation", "laplace equation", "poisson equation",
                "boundary condition", "finite difference", "elliptic", "parabolic", "hyperbolic",
            ),
            Icons.Filled.Waves,
        ),

        ToolDef(
            "advanced.measure", "Measure Theory", ToolCategory.ADVANCED,
            "Sets, measures, countable additivity and the two integrals",
            listOf(
                "measure theory", "measure", "countably additive", "null set",
                "almost everywhere", "measurable", "lebesgue", "riemann integral",
                "interval measure", "sigma algebra",
            ),
            Icons.Filled.Balance,
        ),
        ToolDef(
            "advanced.topology", "Topology Explorer", ToolCategory.ADVANCED,
            "Open sets, closures, connectedness and homeomorphisms",
            listOf(
                "topology", "open set", "closed set", "interior", "closure", "boundary",
                "limit point", "isolated point", "connected", "path connected",
                "compact", "homeomorphism", "metric space", "open ball", "neighbourhood",
            ),
            Icons.Filled.Hub,
        ),
        ToolDef(
            "advanced.functional", "Functional Analysis", ToolCategory.ADVANCED,
            "Norms, inner products, operators and projections",
            listOf(
                "functional analysis", "vector space", "norm", "inner product",
                "orthogonality", "banach", "hilbert", "operator", "eigenvalue",
                "eigenvector", "projection", "least squares", "gram schmidt",
                "orthonormal basis",
            ),
            Icons.Filled.Functions,
        ),

        ToolDef(
            "other.mileage", "Mileage & trips", ToolCategory.OTHER,
            "Trip distance, fuel economy and trip cost",
            listOf("odometer", "fuel economy", "trip cost", "l/100km", "mpg", "consumption"),
            Icons.Filled.DirectionsCar,
        ),
        ToolDef(
            "other.electrical_circuits", "Electrical Circuits", ToolCategory.OTHER,
            "Build a circuit, wire it up, and work out the voltages, currents and power",
            listOf(
                "electrical", "circuits", "circuit", "voltage", "current", "resistance",
                "watts", "amps", "volts", "ohms", "power", "ohm's law", "resistor",
                "series", "parallel", "divider", "switch", "kirchhoff",
            ),
            Icons.Filled.ElectricBolt,
        ),
    )

    val all: List<ToolDef> get() = tools

    /**
     * Tool ids that were stored before the Calculus category existed.
     *
     * Favourites, the recently-used list and the history all keep the id of the
     * tool they were created from, so an old id has to keep resolving. The old
     * single "Calculus" screen now lives in the category, and the Derivatives
     * tool is the closest match for it.
     */
    private val LEGACY_IDS = mapOf(
        "algebra.calculus" to "calculus.derivatives",
        "other.ohms_law" to "other.electrical_circuits",
    )

    fun byId(id: String): ToolDef? =
        tools.firstOrNull { it.id == id } ?: LEGACY_IDS[id]?.let { legacy -> tools.firstOrNull { it.id == legacy } }

    /**
     * The current id for a tool id that may be an old one.
     *
     * Favourites, the recently-used list and the history all keep the id of
     * the tool they were created from, so an id that has since changed is
     * mapped to the one that is in force now. Without this a renamed tool
     * would appear twice in the recently-used list, under two ids.
     */
    fun canonicalId(id: String): String = LEGACY_IDS[id] ?: id

    fun inCategory(category: ToolCategory): List<ToolDef> = tools.filter { it.category == category }

    /**
     * Fuzzy search across titles, synonyms, descriptions and category names.
     * Results are ranked: title matches first, then synonyms, then the rest.
     */
    fun search(query: String): List<ToolDef> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return tools.mapNotNull { tool ->
            val score = score(tool, q)
            if (score > 0) tool to score else null
        }.sortedWith(compareByDescending<Pair<ToolDef, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first }
    }

    private fun score(tool: ToolDef, q: String): Int {
        val title = tool.title.lowercase()
        var score = 0
        if (title == q) score = 100
        else if (title.startsWith(q)) score = 80
        else if (title.contains(q)) score = 60

        for (syn in tool.synonyms) {
            val s = syn.lowercase()
            if (s == q) score = maxOf(score, 55)
            else if (s.startsWith(q)) score = maxOf(score, 45)
            else if (s.contains(q)) score = maxOf(score, 30)
        }
        if (tool.description.lowercase().contains(q)) score = maxOf(score, 20)
        if (tool.category.label.lowercase().contains(q)) score = maxOf(score, 15)

        // All whitespace-separated words must match somewhere.
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size > 1) {
            val haystack = (title + " " + tool.synonyms.joinToString(" ") + " " + tool.description).lowercase()
            if (words.any { w -> !haystack.contains(w) }) return 0
            // Every word was found (even if only separately): keep a modest
            // score so multi-word queries like "ohms law" still find the tool.
            if (score == 0) score = 10
        }
        return score
    }
}

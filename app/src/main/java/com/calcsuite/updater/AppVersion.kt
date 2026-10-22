package com.calcsuite.updater

/**
 * A version number, such as `3.0.1`, `v3.1.0` or `3.1.0-beta2`.
 *
 * Versions are compared by their numeric parts first, one part at a time, and
 * only then by the pre-release part. A version with a pre-release part comes
 * before the same version without one, so `3.1.0-beta1` is older than `3.1.0`.
 *
 * The class is deliberately free of Android types, so that the rules can be
 * tested on their own.
 */
data class AppVersion(
    /** The numeric parts, in order: 3, 0 and 1 for "3.0.1". */
    val parts: List<Int>,
    /**
     * The pre-release part, split at the dots: "beta2" for "3.1.0-beta2".
     * Empty for a finished version.
     */
    val preRelease: List<String> = emptyList(),
    /** The text the version was read from, without the leading "v". */
    val text: String = parts.joinToString("."),
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val count = maxOf(parts.size, other.parts.size)
        for (index in 0 until count) {
            val mine = parts.getOrElse(index) { 0 }
            val theirs = other.parts.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        // A finished version is newer than any pre-release of the same number.
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val count2 = maxOf(preRelease.size, other.preRelease.size)
        for (index in 0 until count2) {
            val mine = preRelease.getOrNull(index) ?: return -1
            val theirs = other.preRelease.getOrNull(index) ?: return 1
            val mineNumber = mine.toIntOrNull()
            val theirsNumber = theirs.toIntOrNull()
            val order = when {
                mineNumber != null && theirsNumber != null -> mineNumber.compareTo(theirsNumber)
                mineNumber != null -> -1
                theirsNumber != null -> 1
                else -> mine.compareTo(theirs)
            }
            if (order != 0) return order
        }
        return 0
    }

    /** True when this version is newer than [other]. */
    fun isNewerThan(other: AppVersion): Boolean = this > other

    override fun toString(): String = text

    companion object {
        /**
         * Reads a version, or returns null when the text is not a version.
         *
         * A leading "v" is allowed, so a tag such as "v3.0.1" reads the same as
         * "3.0.1". Build metadata after a "+" is ignored. Every numeric part
         * must be a whole number; "3.0.x" and "" are not versions.
         */
        fun parse(text: String): AppVersion? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            var body = trimmed
            if (body.startsWith("v") || body.startsWith("V")) body = body.substring(1)
            val plus = body.indexOf('+')
            if (plus >= 0) body = body.substring(0, plus)
            if (body.isEmpty()) return null
            var preRelease = emptyList<String>()
            val dash = body.indexOf('-')
            if (dash >= 0) {
                val tail = body.substring(dash + 1)
                body = body.substring(0, dash)
                if (tail.isEmpty()) return null
                preRelease = tail.split('.').filter { it.isNotEmpty() }
                if (preRelease.isEmpty()) return null
            }
            val pieces = body.split('.')
            if (pieces.isEmpty()) return null
            val parts = mutableListOf<Int>()
            for (piece in pieces) {
                if (piece.isEmpty()) return null
                if (!piece.all { it.isDigit() }) return null
                val number = piece.toIntOrNull() ?: return null
                parts.add(number)
            }
            return AppVersion(parts = parts, preRelease = preRelease, text = body)
        }
    }
}

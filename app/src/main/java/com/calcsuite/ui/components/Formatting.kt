package com.calcsuite.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.calcsuite.core.Num
import com.calcsuite.data.Settings
import java.math.BigDecimal
import java.util.Locale

val LocalSettings = compositionLocalOf { Settings.DEFAULT }

/** Number formatting bound to the current settings and locale. */
class Fmt(private val settings: Settings, locale: Locale) {
    val seps = Num.sepsFor(locale, settings.grouping, settings.thousandsSeparator)
    val precision: Int get() = settings.precision

    fun num(value: BigDecimal, precision: Int = settings.precision): String =
        Num.format(value, precision, seps)

    fun num(value: Double, precision: Int = settings.precision): String =
        Num.format(value, precision, seps)

    /** Character used by the keypad to enter a decimal point. */
    val decimalSeparator: Char get() = seps.decimal
}

@Composable
fun rememberFmt(): Fmt {
    val settings = LocalSettings.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.US
    return remember(settings, locale) { Fmt(settings, locale) }
}

@Composable
fun ProvideSettings(settings: Settings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettings provides settings, content = content)
}

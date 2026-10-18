package com.calcsuite.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Cohesive type scale. Headlines are tight and confident; the numeric style
 * uses tabular monospace figures so digits never shift while typing.
 */
val CalcTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = Default.bodyLarge.copy(letterSpacing = 0.15.sp),
    bodyMedium = Default.bodyMedium.copy(letterSpacing = 0.2.sp),
    bodySmall = Default.bodySmall.copy(letterSpacing = 0.25.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = Default.labelMedium.copy(letterSpacing = 0.4.sp),
    labelSmall = Default.labelSmall.copy(letterSpacing = 0.5.sp),
)

/** Tabular style for numbers, results and keypad glyphs. */
val NumberTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)

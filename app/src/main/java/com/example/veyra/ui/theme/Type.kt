package com.example.veyra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System font family with restrained weights and slightly open tracking.
// A custom typeface can replace FontFamily.Default here without touching any screen.
private val VeyraFontFamily = FontFamily.Default

val VeyraTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = VeyraFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// Spaced uppercase wordmark used by the brand header.
val VeyraWordmarkStyle = TextStyle(
    fontFamily = VeyraFontFamily,
    fontWeight = FontWeight.Light,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = 8.sp,
)

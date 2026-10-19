package com.example.veyra.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette. These are the only raw colour values in the app;
// everything else references them through the Material colour schemes in Theme.kt.
object VeyraPalette {
    val Charcoal = Color(0xFF17181C)
    val Ivory = Color(0xFFF3F0E9)
    val Champagne = Color(0xFFD8C8A4)

    // Neutral dark surfaces, stepping up from Charcoal.
    val Graphite = Color(0xFF1F2025)
    val Slate = Color(0xFF27282E)
    val Smoke = Color(0xFF32333A)
    val Ash = Color(0xFF45464E)

    // Muted text and outlines on dark surfaces.
    val Stone = Color(0xFFA9A69E)
    val Pebble = Color(0xFF6E6D69)

    // Light theme: a darker champagne for accents and text-level contrast on Ivory,
    // and Ivory-based surfaces stepping down towards Champagne.
    val Bronze = Color(0xFF6B5B37)
    val Sand = Color(0xFFE6DCC6)
    val Linen = Color(0xFFEBE7DE)
    val Parchment = Color(0xFFE3DED2)
    val Mist = Color(0xFFCBC6BA)
    val ErrorDark = Color(0xFFA3352A)

    val Error = Color(0xFFE5A39A)
    val OnError = Color(0xFF3A1410)
}

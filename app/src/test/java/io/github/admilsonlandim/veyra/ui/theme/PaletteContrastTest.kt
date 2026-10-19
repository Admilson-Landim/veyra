package io.github.admilsonlandim.veyra.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards text legibility: WCAG AA requires 4.5:1 for body text, 3:1 for large text and icons. */
class PaletteContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    private fun assertContrast(name: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast is %.2f, expected >= $minimum".format(ratio), ratio >= minimum)
    }

    @Test
    fun darkScheme_textIsLegible() = with(VeyraDarkColorScheme) {
        assertContrast("onBackground/background", onBackground, background, 4.5)
        assertContrast("onSurface/surface", onSurface, surface, 4.5)
        assertContrast("onSurfaceVariant/background", onSurfaceVariant, background, 4.5)
        assertContrast("onSurfaceVariant/surfaceContainer", onSurfaceVariant, surfaceContainer, 4.5)
        assertContrast("primary/background", primary, background, 4.5)
        assertContrast("primary/surfaceContainer", primary, surfaceContainer, 4.5)
        assertContrast("primary/secondaryContainer", primary, secondaryContainer, 4.5)
        assertContrast("onPrimary/primary", onPrimary, primary, 4.5)
    }

    @Test
    fun lightScheme_textIsLegible() = with(VeyraLightColorScheme) {
        assertContrast("onBackground/background", onBackground, background, 4.5)
        assertContrast("onSurfaceVariant/background", onSurfaceVariant, background, 4.5)
        assertContrast("primary/background", primary, background, 4.5)
        assertContrast("onPrimary/primary", onPrimary, primary, 4.5)
        assertContrast("onSurface/surfaceContainerHigh", onSurface, surfaceContainerHigh, 4.5)
        assertContrast("onSurfaceVariant/surfaceContainer", onSurfaceVariant, surfaceContainer, 4.5)
        assertContrast("primary/surfaceContainerHigh", primary, surfaceContainerHigh, 4.5)
        assertContrast("primary/secondaryContainer", primary, secondaryContainer, 4.5)
        assertContrast("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer, 4.5)
        assertContrast("error/background", error, background, 4.5)
        // Non-text UI (slider tracks, outlines) needs 3:1 against its background.
        assertContrast("outline/background", outline, background, 3.0)
    }

    @Test
    fun darkScheme_errorAndOutlinesAreVisible() = with(VeyraDarkColorScheme) {
        assertContrast("error/background", error, background, 4.5)
        assertContrast("outline/background", outline, background, 3.0)
    }
}

package com.monofocus.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun fixedLightThemeColorsMeetContrastTargets() {
        assertThemeContrast(LightColors, "light")
    }

    @Test
    fun fixedDarkThemeColorsMeetContrastTargets() {
        assertThemeContrast(DarkColors, "dark")
    }

    private fun assertThemeContrast(colors: ColorScheme, name: String) {
        assertTextContrast(colors.onBackground, colors.background, "$name onBackground/background")
        assertTextContrast(colors.onSurface, colors.surface, "$name onSurface/surface")
        assertTextContrast(
            colors.onSurfaceVariant,
            colors.surface,
            "$name onSurfaceVariant/surface",
        )
        assertTextContrast(
            colors.onSurfaceVariant,
            colors.surfaceVariant,
            "$name onSurfaceVariant/surfaceVariant",
        )
        assertTextContrast(
            colors.onSurfaceVariant,
            colors.surfaceVariant.copy(alpha = 0.55f).compositeOver(colors.surface),
            "$name onSurfaceVariant/tinted-card",
        )
        assertTextContrast(colors.onPrimary, colors.primary, "$name onPrimary/primary")
        assertNonTextContrast(colors.primary, colors.background, "$name primary/background")
        assertNonTextContrast(colors.primary, colors.surfaceVariant, "$name primary/surfaceVariant")
    }

    private fun assertTextContrast(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast $ratio should be at least 4.5", ratio >= 4.5)
    }

    private fun assertNonTextContrast(foreground: Color, background: Color, label: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast $ratio should be at least 3.0", ratio >= 3.0)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val opaqueForeground = foreground.compositeOver(background)
        val foregroundLuminance = relativeLuminance(opaqueForeground)
        val backgroundLuminance = relativeLuminance(background)
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val red = linearized(color.red.toDouble())
        val green = linearized(color.green.toDouble())
        val blue = linearized(color.blue.toDouble())
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun linearized(channel: Double): Double {
        return if (channel <= 0.03928) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }
    }
}

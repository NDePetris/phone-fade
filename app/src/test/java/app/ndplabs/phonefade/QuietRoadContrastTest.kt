package app.ndplabs.phonefade

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Solid/composited token checks only: not rendered or physical-grayscale acceptance. */
class QuietRoadContrastTest {
    private fun luminance(c: Color): Double {
        fun linear(value: Float): Double = if (value <= .04045f) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
        return .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)
    }
    private fun checkPair(name: String, foreground: Color, background: Color, target: Double = 4.5) {
        val a = luminance(foreground); val b = luminance(background)
        val ratio = (maxOf(a, b) + .05) / (minOf(a, b) + .05)
        assertTrue("$name: $ratio < $target", ratio >= target)
    }
    private fun checkTheme(s: ColorScheme) {
        for (background in listOf(s.background, s.surface, s.surfaceContainerLowest, s.surfaceContainerLow,
            s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceBright, s.surfaceDim)) {
            checkPair("body", s.onSurface, background)
            checkPair("support", s.onSurfaceVariant, background)
            checkPair("action", s.primary, background)
            checkPair("control outline", s.outline, background, 3.0)
            checkPair("error", s.error, background)
        }
        for ((text, container) in listOf(s.onPrimary to s.primary, s.onSecondary to s.secondary,
            s.onTertiary to s.tertiary, s.onPrimaryContainer to s.primaryContainer,
            s.onSecondaryContainer to s.secondaryContainer, s.onTertiaryContainer to s.tertiaryContainer,
            s.onError to s.error, s.onErrorContainer to s.errorContainer, s.inverseOnSurface to s.inverseSurface)) {
            checkPair("container", text, container)
        }
        checkPair("hero outlined label", s.primary, s.primaryContainer)
        checkPair("hero outlined boundary", s.outline, s.primaryContainer, 3.0)
        checkPair("snackbar action", s.inversePrimary, s.inverseSurface)
        // Material's disabled semantics accompany intentionally subdued 38%-alpha labels.
        // They are excluded from WCAG active-control thresholds, but remain distinguishable.
        val disabled = s.onSurface.copy(alpha = .38f).compositeOver(s.surfaceContainerLow)
        checkPair("disabled distinctness", disabled, s.surfaceContainerLow, 1.5)
    }
    @Test fun lightRoles() = checkTheme(QuietRoadLight)
    @Test fun darkRoles() = checkTheme(QuietRoadDark)
    @Test fun heroInkOnDarkestUnveiledArtwork() {
        for (background in listOf(Color(0xFFB8CCBF), Color(0xFFB4D4E2), Color(0xFF8FAE9D)))
            checkPair("light landscape", QuietRoadLight.onPrimaryContainer, background)
        for (background in listOf(Color(0xFF3C5F53), Color(0xFF406679)))
            checkPair("dark landscape", QuietRoadDark.onPrimaryContainer, background)
        // The grove is always beneath >= 50% hero-colored reading veil at its x coordinates.
        checkPair("dark grove through veil", QuietRoadDark.onPrimaryContainer,
            QuietRoadDark.primaryContainer.copy(alpha = .5f).compositeOver(Color(0xFF638675)))
    }
}

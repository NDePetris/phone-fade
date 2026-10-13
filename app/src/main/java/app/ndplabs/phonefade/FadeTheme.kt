package app.ndplabs.phonefade

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun FadeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFAACEC8), onPrimary = Color(0xFF113631),
        primaryContainer = Color(0xFF284D47), onPrimaryContainer = Color(0xFFC6EBE4),
        background = Color(0xFF101413), surface = Color(0xFF101413),
    ) else lightColorScheme(
        primary = Color(0xFF355F58), onPrimary = Color.White,
        primaryContainer = Color(0xFFD6EAE5), onPrimaryContainer = Color(0xFF102E29),
        background = Color(0xFFF7FAF8), surface = Color(0xFFF7FAF8),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

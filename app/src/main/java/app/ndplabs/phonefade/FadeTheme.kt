package app.ndplabs.phonefade

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val QuietRoadLight = lightColorScheme(
    primary = Color(0xFF2E5A4F), onPrimary = Color(0xFFF6F3EC),
    primaryContainer = Color(0xFFDFE9E3), onPrimaryContainer = Color(0xFF1F2B28),
    secondary = Color(0xFF355D70), onSecondary = Color(0xFFF6F3EC),
    secondaryContainer = Color(0xFFDCEAF0), onSecondaryContainer = Color(0xFF1F2B28),
    tertiary = Color(0xFF79501F), onTertiary = Color(0xFFF6F3EC),
    tertiaryContainer = Color(0xFFF0E4D2), onTertiaryContainer = Color(0xFF1F2B28),
    background = Color(0xFFF6F3EC), onBackground = Color(0xFF1F2B28),
    surface = Color(0xFFF6F3EC), onSurface = Color(0xFF1F2B28),
    surfaceVariant = Color(0xFFEDE9E0), onSurfaceVariant = Color(0xFF2E5A4F),
    surfaceTint = Color(0xFF2E5A4F),
    surfaceDim = Color(0xFFE5E1D8), surfaceBright = Color(0xFFFFFCF6),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAF7F1),
    surfaceContainer = Color(0xFFF0EDE5), surfaceContainerHigh = Color(0xFFEDE9E0),
    surfaceContainerHighest = Color(0xFFE5E1D8),
    outline = Color(0xFF6B776F), outlineVariant = Color(0xFFC8BCA7),
    inverseSurface = Color(0xFF263832), inverseOnSurface = Color(0xFFF6F3EC), inversePrimary = Color(0xFF79AFC9),
    error = Color(0xFF943E38), onError = Color(0xFFF6F3EC),
    errorContainer = Color(0xFFFBE9E7), onErrorContainer = Color(0xFF943E38), scrim = Color(0xFF1F2B28),
)
internal val QuietRoadDark = darkColorScheme(
    primary = Color(0xFF79AFC9), onPrimary = Color(0xFF1F2B28),
    primaryContainer = Color(0xFF263F35), onPrimaryContainer = Color(0xFFF6F3EC),
    secondary = Color(0xFFA4C4AE), onSecondary = Color(0xFF1F2B28),
    secondaryContainer = Color(0xFF30463E), onSecondaryContainer = Color(0xFFF6F3EC),
    tertiary = Color(0xFFE7BE7A), onTertiary = Color(0xFF1F2B28),
    tertiaryContainer = Color(0xFF40341F), onTertiaryContainer = Color(0xFFF6F3EC),
    background = Color(0xFF1F2B28), onBackground = Color(0xFFF6F3EC),
    surface = Color(0xFF263832), onSurface = Color(0xFFF6F3EC),
    surfaceVariant = Color(0xFF30463E), onSurfaceVariant = Color(0xFFC8BCA7), surfaceTint = Color(0xFF79AFC9),
    surfaceDim = Color(0xFF1F2B28), surfaceBright = Color(0xFF2D4139),
    surfaceContainerLowest = Color(0xFF19231F), surfaceContainerLow = Color(0xFF263832),
    surfaceContainer = Color(0xFF293D35), surfaceContainerHigh = Color(0xFF2B4037),
    surfaceContainerHighest = Color(0xFF2D4139),
    outline = Color(0xFF7F9B89), outlineVariant = Color(0xFF50675D),
    inverseSurface = Color(0xFFF6F3EC), inverseOnSurface = Color(0xFF1F2B28), inversePrimary = Color(0xFF2E5A4F),
    error = Color(0xFFF1A59D), onError = Color(0xFF1F2B28),
    errorContainer = Color(0xFF4A2725), onErrorContainer = Color(0xFFF1A59D), scrim = Color(0xFF101A16),
)

@Composable
fun FadeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) QuietRoadDark else QuietRoadLight, content = content)
}

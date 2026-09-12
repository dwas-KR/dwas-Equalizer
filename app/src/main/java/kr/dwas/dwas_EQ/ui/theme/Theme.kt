package kr.dwas.dwas_EQ.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kr.dwas.dwas_EQ.ui.AppThemeMode

private val DwasLightColors = lightColorScheme(
    primary = Color(0xFF1D67F2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEFF),
    onPrimaryContainer = Color(0xFF0C2D6B),
    secondary = Color(0xFF56647A),
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EBEF),
    outline = Color(0xFFB8BDC7),
)

private val DwasDarkColors = darkColorScheme(
    primary = Color(0xFF7AA7FF),
    onPrimary = Color(0xFF08234F),
    primaryContainer = Color(0xFF173A76),
    onPrimaryContainer = Color(0xFFE5EDFF),
    secondary = Color(0xFFB5C0D4),
    background = Color(0xFF15171A),
    surface = Color(0xFF1F2226),
    surfaceVariant = Color(0xFF2A2E34),
    outline = Color(0xFF626872),
)

@Composable
fun DwasEqTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DwasDarkColors else DwasLightColors,
        content = content,
    )
}

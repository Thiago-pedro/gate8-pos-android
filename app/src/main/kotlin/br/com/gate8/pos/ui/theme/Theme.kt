package br.com.gate8.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CheckinOk = Color(0xFF2E7D32)
val CheckinUsed = Color(0xFFF9A825)
val CheckinWrongEvent = Color(0xFFEF6C00)
val CheckinInvalid = Color(0xFFC62828)

private val Gate8ColorScheme = lightColorScheme(
    primary = Gate8Colors.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Gate8Colors.AccentBlueDark,
    onPrimaryContainer = Color.White,
    secondary = Gate8Colors.AccentBlue,
    onSecondary = Color.White,
    tertiary = Gate8Colors.BackgroundTop,
    onTertiary = Color.White,
    error = Gate8Colors.Error,
)

@Composable
fun Gate8Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Gate8ColorScheme,
        content = content,
    )
}

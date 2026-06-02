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

@Composable
fun Gate8Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content,
    )
}

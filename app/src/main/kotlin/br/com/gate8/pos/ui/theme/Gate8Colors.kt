package br.com.gate8.pos.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Gate8Colors {
    val BackgroundTop = Color(0xFF1E4FD8)
    val BackgroundBottom = Color(0xFF0B1020)
    val AccentBlue = Color(0xFF3B82F6)
    val AccentBlueDark = Color(0xFF2563EB)
    val CardSurface = Color(0xFF151B2E)
    val CardSurfaceElevated = Color(0xFF1C2438)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val BadgeBlue = Color(0xFF3B82F6)
    val Error = Color(0xFFEF4444)
    val Success = Color(0xFF22C55E)

    val ScreenGradient = Brush.verticalGradient(
        colors = listOf(BackgroundTop, Color(0xFF122454), BackgroundBottom),
    )
}

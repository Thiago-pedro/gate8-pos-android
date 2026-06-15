package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.ui.theme.Gate8Colors

@Composable
fun Gate8MenuButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    centerText: Boolean = false,
) {
    val textAlign = if (centerText) TextAlign.Center else TextAlign.Start
    val alpha = if (enabled) 1f else 0.45f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Gate8Colors.AccentBlue.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            title,
            color = Gate8Colors.TextOnButton.copy(alpha = alpha),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            subtitle,
            color = Gate8Colors.TextOnButton.copy(alpha = 0.85f * alpha),
            fontSize = 12.sp,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

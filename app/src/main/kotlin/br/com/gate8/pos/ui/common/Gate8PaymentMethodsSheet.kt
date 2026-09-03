package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Escolha de forma de pagamento (débito / crédito / Pix / dinheiro),
 * reutilizada no carrinho e na recarga cashless.
 */
@Composable
fun Gate8PaymentMethodsSheet(
    title: String,
    amountLabel: String,
    onPayDebit: () -> Unit,
    onPayCredit: () -> Unit,
    onPayPix: () -> Unit,
    onPayCash: () -> Unit,
    cashEnabled: Boolean = true,
) {
    Gate8ScreenBackgroundFillWidth {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                title,
                color = Gate8Colors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                amountLabel,
                color = Gate8Colors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Text(
                "Forma de pagamento",
                color = Gate8Colors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            PaymentMethodChoice("Débito", onPayDebit)
            Spacer(Modifier.height(8.dp))
            PaymentMethodChoice("Crédito", onPayCredit)
            Spacer(Modifier.height(8.dp))
            PaymentMethodChoice("Pix", onPayPix)
            Spacer(Modifier.height(8.dp))
            PaymentMethodChoice(
                label = if (cashEnabled) "Dinheiro" else "Dinheiro (caixa fechado)",
                onClick = onPayCash,
                enabled = cashEnabled,
            )
        }
    }
}

@Composable
fun PaymentMethodChoice(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Gate8Colors.AccentBlue else Gate8Colors.AccentBlue.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Gate8Colors.TextOnButton, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

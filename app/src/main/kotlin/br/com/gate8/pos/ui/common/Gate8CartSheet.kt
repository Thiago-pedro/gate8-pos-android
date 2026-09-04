package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Mensagem exibida sob o spinner enquanto o pagamento processa, conforme a forma escolhida.
 * Retorna null quando não há mensagem específica (ex.: dinheiro, flavor Cielo — UI nativa).
 */
fun paymentLoadingMessage(method: PaymentMethodApi?): String? {
    if (method == PaymentMethodApi.CASHLESS) {
        return "Aproxime o cartão cashless na maquininha"
    }
    if (BuildConfig.FLAVOR.equals("cielo", ignoreCase = true)) return null
    return when (method) {
        PaymentMethodApi.DEBIT, PaymentMethodApi.CREDIT ->
            "Aproxime, insira ou passe o cartão na parte superior da maquininha"
        PaymentMethodApi.PIX -> "Gerando o QR Code Pix..."
        else -> null
    }
}

data class Gate8CartLineUi(
    val id: String,
    val description: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val canIncrement: Boolean,
)

@Composable
fun Gate8CartSheet(
    itemCount: Int,
    total: Double,
    lines: List<Gate8CartLineUi>,
    loading: Boolean,
    loadingMessage: String? = null,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onPayDebit: () -> Unit,
    onPayCredit: () -> Unit,
    onPayPix: () -> Unit,
    onPayCash: () -> Unit,
    onPayCashless: (() -> Unit)? = null,
    onClear: () -> Unit,
    cashEnabled: Boolean = true,
    showCashless: Boolean = false,
) {
    Gate8ScreenBackgroundFillWidth {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Carrinho",
            color = Gate8Colors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "$itemCount item(ns) · Total R$ ${"%.2f".format(total)}",
            color = Gate8Colors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        if (lines.isEmpty()) {
                Text(
                    "Nenhum item no carrinho.",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
        } else {
            lines.forEach { line ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Gate8Colors.CardSurface.copy(alpha = 0.85f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    ) {
                        Text(
                            line.description,
                            color = Gate8Colors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${line.quantity}x R$ ${"%.2f".format(line.unitPrice)}",
                            color = Gate8Colors.TextSecondary,
                            fontSize = 12.sp,
                        )
                        Text(
                            "Subtotal R$ ${"%.2f".format(line.lineTotal)}",
                            color = Gate8Colors.AccentBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Gate8QuantitySelector(
                        quantity = line.quantity,
                        canIncrement = line.canIncrement,
                        compact = false,
                        onIncrement = { onIncrement(line.id) },
                        onDecrement = { onDecrement(line.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                if (!loadingMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        loadingMessage,
                        color = Gate8Colors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }
            }
        } else {
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
            if (showCashless && onPayCashless != null) {
                Spacer(Modifier.height(8.dp))
                PaymentMethodChoice("Cashless", onPayCashless)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Limpar carrinho",
                color = Gate8Colors.TextOnLight,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClear)
                    .padding(8.dp),
            )
        }
    }
    }
}

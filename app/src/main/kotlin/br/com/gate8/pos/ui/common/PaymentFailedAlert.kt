package br.com.gate8.pos.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.runtime.Composable

@Composable
fun PaymentFailedAlert(
    reason: String?,
    onDismiss: () -> Unit,
) {
    Gate8AlertDialog(
        title = "Pagamento não concluído",
        reason = reason?.takeIf { it.isNotBlank() } ?: PaymentUserMessages.DEFAULT_FAILURE,
        detail = PaymentUserMessages.CART_RETRY_HINT,
        icon = Icons.Filled.CreditCard,
        onDismiss = onDismiss,
    )
}

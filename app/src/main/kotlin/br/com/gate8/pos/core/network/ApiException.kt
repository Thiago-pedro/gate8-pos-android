package br.com.gate8.pos.core.network

class ApiException(
    val httpCode: Int,
    message: String,
    val errorCode: String? = null,
    val available: Int? = null,
    val productId: String? = null,
) : Exception(message)

fun ApiException.saleErrorMessage(): String = when (errorCode) {
    "insufficient_stock" -> "Estoque insuficiente. Disponível: ${available ?: "?"}."
    "product_not_available" -> "Produto indisponível ou inativo."
    "cashier_closed" -> "Caixa fechado. Abra o caixa na Home para vender em dinheiro."
    else -> message ?: "Erro na venda"
}

fun ApiException.isStockOrProductError(): Boolean =
    errorCode == "insufficient_stock" || errorCode == "product_not_available"

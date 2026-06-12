package br.com.gate8.pos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LastSaleLineRecord(
    val description: String,
    val quantity: Int,
    val unitPrice: Double,
) {
    val lineTotal: Double get() = quantity * unitPrice
}

@Serializable
data class LastSaleRecord(
    val saleId: String? = null,
    val clientReference: String,
    val total: Double,
    val paymentMethod: String,
    val paymentLabel: String,
    val nsu: String? = null,
    val authorization: String? = null,
    val transactionId: String? = null,
    val brand: String? = null,
    val lines: List<LastSaleLineRecord> = emptyList(),
    val ticketCodes: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val voided: Boolean = false,
)

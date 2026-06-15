package br.com.gate8.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CashierStatusDto(
    val open: Boolean = false,
    val session: CashierSessionDto? = null,
    val totals: CashierTotalsDto? = null,
    val movements: List<CashierMovementDto> = emptyList(),
    val summary: CashierCloseSummaryDto? = null,
)

@Serializable
data class CashierSessionDto(
    val id: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("operator_name") val operatorName: String? = null,
    val status: String? = null,
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("counted_balance") val countedBalance: Double? = null,
    @SerialName("expected_balance") val expectedBalance: Double? = null,
    val difference: Double? = null,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null,
    val notes: String? = null,
)

@Serializable
data class CashierTotalsDto(
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("cash_sales") val cashSales: Double = 0.0,
    val withdrawals: Double = 0.0,
    val expenses: Double = 0.0,
    @SerialName("expected_drawer") val expectedDrawer: Double = 0.0,
    @SerialName("sale_count") val saleCount: Int = 0,
    @SerialName("grand_total") val grandTotal: Double = 0.0,
    @SerialName("by_payment_method") val byPaymentMethod: List<CashierPaymentRowDto> = emptyList(),
)

@Serializable
data class CashierPaymentRowDto(
    val method: String,
    val label: String,
    val count: Int = 0,
    val total: Double = 0.0,
)

@Serializable
data class CashierMovementDto(
    val id: String,
    val type: String,
    val amount: Double,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CashierCloseSummaryDto(
    val session: CashierSessionDto? = null,
    val period: CashierPeriodDto? = null,
    val totals: CashierTotalsDto? = null,
    val movements: List<CashierMovementDto> = emptyList(),
    @SerialName("by_payment_method") val byPaymentMethod: List<CashierPaymentRowDto> = emptyList(),
    @SerialName("sale_count") val saleCount: Int = 0,
    @SerialName("grand_total") val grandTotal: Double = 0.0,
    @SerialName("counted_balance") val countedBalance: Double? = null,
    @SerialName("expected_balance") val expectedBalance: Double? = null,
    val difference: Double? = null,
)

@Serializable
data class CashierPeriodDto(
    val from: String,
    val to: String,
)

@Serializable
data class CashierOperatorRequestDto(
    @SerialName("operator_name") val operatorName: String,
)

@Serializable
data class CashierOpenRequestDto(
    @SerialName("opening_balance") val openingBalance: Double,
    @SerialName("operator_name") val operatorName: String,
    val notes: String? = null,
)

@Serializable
data class CashierCloseRequestDto(
    @SerialName("counted_balance") val countedBalance: Double,
    val notes: String? = null,
)

@Serializable
data class CashierMovementRequestDto(
    val amount: Double,
    val description: String,
)

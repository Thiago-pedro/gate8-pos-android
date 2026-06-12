package br.com.gate8.pos.printer

data class CashierPrintMovement(
    val typeLabel: String,
    val amount: Double,
    val description: String?,
    val timeLabel: String?,
)

data class CashierPrintPayload(
    val deviceName: String?,
    val producerName: String?,
    val operatorName: String?,
    val openedAtLabel: String,
    val closedAtLabel: String?,
    val openingBalance: Double,
    val cashSales: Double,
    val withdrawals: Double,
    val expenses: Double,
    val expectedDrawer: Double,
    val countedBalance: Double?,
    val difference: Double?,
    val saleCount: Int,
    val grandTotal: Double,
    val byPaymentMethod: List<ReportPrintRow>,
    val movements: List<CashierPrintMovement>,
)

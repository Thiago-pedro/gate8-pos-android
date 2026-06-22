package br.com.gate8.pos.printer

data class ReportPrintRow(
    val label: String,
    val count: Int,
    val total: Double,
)

/** Item do ranking de mais vendidos: nome, quantidade total e valor total. */
data class ReportPrintItem(
    val name: String,
    val quantity: Int,
    val total: Double,
)

/** Situação do caixa para incluir no rodapé do relatório. */
data class ReportCashierInfo(
    val open: Boolean,
    val operatorName: String?,
    val openingBalance: Double,
    val cashSales: Double,
    val withdrawals: Double,
    val expenses: Double,
    val expectedDrawer: Double,
    val countedBalance: Double?,
    val difference: Double?,
)

data class ReportPrintPayload(
    val periodLabel: String,
    /** Escopo do relatório: "Bilheteria", "Conveniência" ou null (geral). */
    val segmentLabel: String? = null,
    val deviceName: String?,
    val producerName: String?,
    val saleCount: Int,
    val voidCount: Int,
    val grossTotal: Double,
    val voidTotal: Double,
    val netTotal: Double,
    val averageTicket: Double,
    val byPaymentMethod: List<ReportPrintRow>,
    val byBrand: List<ReportPrintRow>,
    val topItems: List<ReportPrintItem> = emptyList(),
    val cashier: ReportCashierInfo? = null,
)

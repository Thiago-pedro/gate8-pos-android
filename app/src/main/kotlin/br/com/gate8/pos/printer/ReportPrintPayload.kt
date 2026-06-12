package br.com.gate8.pos.printer

data class ReportPrintRow(
    val label: String,
    val count: Int,
    val total: Double,
)

data class ReportPrintPayload(
    val periodLabel: String,
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
)

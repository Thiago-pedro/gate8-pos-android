package br.com.gate8.pos.printer

data class CashlessStatementLine(
    val dateLabel: String,
    val label: String,
    val amountLabel: String,
    val balanceAfterLabel: String,
)

data class CashlessStatementPayload(
    val uidHex: String,
    val cpf: String? = null,
    val phone: String? = null,
    val balanceReais: Double,
    val lines: List<CashlessStatementLine>,
    val terminalName: String? = null,
    val establishmentName: String? = null,
)

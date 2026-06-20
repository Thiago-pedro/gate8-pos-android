package br.com.gate8.pos.printer

/**
 * Dados de um ingresso individual para impressão na maquininha (Bilheteria),
 * espelhando o layout do ingresso do site: evento, lote, data, local, portador,
 * preço, QR Code + código de validação manual e código da compra.
 */
data class TicketPrintPayload(
    val eventName: String,
    val batchName: String,
    val eventDateLabel: String? = null,
    val venue: String? = null,
    /** Nome do terminal/maquininha (ex.: "CX1"). */
    val terminalName: String? = null,
    val holderName: String? = null,
    val price: Double,
    /** Conteúdo do QR e do código de validação manual (ex.: "008A2FE2"). */
    val validationCode: String,
    /** Código da compra (ex.: "GT8-BFNR6D") — vem do backend; opcional por enquanto. */
    val purchaseCode: String? = null,
)

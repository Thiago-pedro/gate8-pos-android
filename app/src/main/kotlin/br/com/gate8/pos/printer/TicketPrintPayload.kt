package br.com.gate8.pos.printer

/**
 * Dados de um ingresso individual para impressão na maquininha (Bilheteria):
 * 1) comprovante de venda (estilo conveniência) + 2) ingresso igual ao site/PDF.
 */
data class TicketPrintPayload(
    val eventName: String,
    val batchName: String,
    val eventDateLabel: String? = null,
    val venue: String? = null,
    /** Nome do terminal (comprovante). */
    val terminalName: String? = null,
    val holderName: String? = null,
    val price: Double,
    /**
     * Conteúdo do QR (= `qr_payload` do backend, igual ao `code`).
     * Nunca usar [manualCode] aqui.
     */
    val qrPayload: String,
    /** Código curto de validação manual (ex.: `008A2FE2`), sem espaços. */
    val manualCode: String,
    /** Código da compra (ex.: `GT8-BFNR6D`). */
    val purchaseCode: String? = null,
    /** Ex.: `Válido`. */
    val statusLabel: String = "Válido",
    /** Data/hora da venda (`dd/MM/yyyy HH:mm`) no comprovante. */
    val saleDateLabel: String? = null,
    /** Já formatado: `Emitido: dd/MM/yyyy HH:mm`. */
    val issuedAtLabel: String? = null,
)

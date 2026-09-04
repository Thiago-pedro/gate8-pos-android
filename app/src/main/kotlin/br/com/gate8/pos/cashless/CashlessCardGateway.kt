package br.com.gate8.pos.cashless

/**
 * Leitura/gravação de cartão Mifare cashless (Cielo Smart).
 */
interface CashlessCardGateway {
    /** Lê UID + saldo Gate8 (ou dump se o cartão ainda não for Gate8). */
    suspend fun readCard(): CashlessCardSnapshot

    /**
     * Soma [amountReais] ao saldo Gate8 no cartão.
     * Se o cartão não tiver formato Gate8, inicializa o bloco de saldo.
     * Recusa cartão bloqueado.
     * [requireUid]: se informado, só grava se o UID bater.
     */
    suspend fun topUp(amountReais: Double, requireUid: String? = null): CashlessCardSnapshot

    /**
     * Grava o saldo absoluto (em reais) e o flag de bloqueio.
     * [rejectUid]: aborta sem gravar se o cartão aproximado for este UID.
     * [requireUid]: aborta se o cartão aproximado NÃO for este UID.
     */
    suspend fun writeBalance(
        amountReais: Double,
        blocked: Boolean,
        rejectUid: String? = null,
        requireUid: String? = null,
    ): CashlessCardSnapshot

    /**
     * Debita [amountReais] do saldo Gate8 (pagamento na conveniência).
     * Recusa cartão bloqueado, sem formato Gate8 ou saldo insuficiente.
     */
    suspend fun debit(amountReais: Double): CashlessCardSnapshot
}

data class CashlessCardSnapshot(
    /** UID em hex (ex. `A1B2C3D4`). */
    val uidHex: String,
    /** Saldo em reais se o cartão estiver no formato Gate8. */
    val balanceReais: Double?,
    /** true se o bloco tem magic `G8CL`. */
    val isGate8Format: Boolean,
    /** true se o cartão Gate8 está marcado como bloqueado. */
    val isBlocked: Boolean = false,
    /** Hex dos 16 bytes do bloco de saldo (diagnóstico). */
    val blockHex: String,
    /** ASCII legível do bloco (bytes imprimíveis). */
    val blockAscii: String,
    val message: String? = null,
)

class CashlessUnavailableException(
    message: String = "Cashless Mifare disponível apenas na Cielo Smart.",
) : Exception(message)

class CashlessOperationException(
    val resultCode: String,
    detail: String,
) : Exception(detail.ifBlank { "Falha cashless ($resultCode)" })

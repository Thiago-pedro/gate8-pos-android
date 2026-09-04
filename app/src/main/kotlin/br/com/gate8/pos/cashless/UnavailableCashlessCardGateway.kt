package br.com.gate8.pos.cashless

/** Flavor sem leitor Mifare (mock / Mercado Pago). */
class UnavailableCashlessCardGateway : CashlessCardGateway {
    override suspend fun readCard(): CashlessCardSnapshot =
        throw CashlessUnavailableException()

    override suspend fun topUp(amountReais: Double, requireUid: String?): CashlessCardSnapshot =
        throw CashlessUnavailableException()

    override suspend fun writeBalance(
        amountReais: Double,
        blocked: Boolean,
        rejectUid: String?,
        requireUid: String?,
    ): CashlessCardSnapshot =
        throw CashlessUnavailableException()

    override suspend fun debit(amountReais: Double): CashlessCardSnapshot =
        throw CashlessUnavailableException()
}

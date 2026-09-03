package br.com.gate8.pos.cashless

/** Flavor sem leitor Mifare (mock / Mercado Pago). */
class UnavailableCashlessCardGateway : CashlessCardGateway {
    override suspend fun readCard(): CashlessCardSnapshot =
        throw CashlessUnavailableException()

    override suspend fun topUp(amountReais: Double): CashlessCardSnapshot =
        throw CashlessUnavailableException()
}

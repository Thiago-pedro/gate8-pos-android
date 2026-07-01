package br.com.gate8.pos.payment

/** Bootstrap do flavor de pagamento — no-op no mock. */
interface PaymentRuntime {
    fun onApplicationStart() {}
}

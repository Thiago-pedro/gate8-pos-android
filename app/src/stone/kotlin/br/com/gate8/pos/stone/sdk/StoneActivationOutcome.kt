package br.com.gate8.pos.stone.sdk

enum class StoneActivationOutcome {
    /** StoneCode já presente após StoneStart.init ou sessão do POS. */
    ALREADY_ACTIVE,

    /** ActiveApplicationProvider.activate concluiu com sucesso. */
    NEWLY_ACTIVATED,
}

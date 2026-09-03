package br.com.gate8.pos.payment

/**
 * Bandeira de cartão para `acquirer.brand` / relatório.
 * Nunca envia forma de pagamento (DEBITO, CREDITO, PIX…).
 */
object CardBrandNormalizer {
    private val REJECTED = setOf(
        "DEBITO", "CREDITO", "DEBIT", "CREDIT", "PIX", "DINHEIRO", "CASH",
        "OTHER", "OUTRO", "DEBITO_AVISTA", "CREDITO_AVISTA",
        "CREDITO_PARCELADO_LOJA", "CREDITO_PARCELADO_ADM",
        "CARTAO", "CARD", "PAGAMENTO", "MOCK",
    )

    private val KNOWN_BRANDS = listOf(
        "MASTERCARD", "HIPERCARD", "AMERICAN EXPRESS", "DINERS CLUB",
        "VISA", "ELO", "AMEX", "HIPER", "CABAL", "DINERS", "MASTER",
    )

    private val ALIASES = mapOf(
        "MASTER" to "MASTERCARD",
        "MASTERCARD" to "MASTERCARD",
        "VISA" to "VISA",
        "ELO" to "ELO",
        "AMEX" to "AMEX",
        "AMERICAN EXPRESS" to "AMEX",
        "HIPERCARD" to "HIPERCARD",
        "HIPER" to "HIPERCARD",
        "CABAL" to "CABAL",
        "DINERS" to "DINERS",
        "DINERS CLUB" to "DINERS",
    )

    /** Códigos numéricos comuns em retornos TEF (quando a API manda número). */
    private val CODE_MAP = mapOf(
        "1" to "VISA",
        "2" to "MASTERCARD",
        "3" to "AMEX",
        "4" to "ELO",
        "5" to "HIPERCARD",
        "6" to "DINERS",
        "7" to "CABAL",
    )

    /**
     * @return bandeira normalizada (ex. `VISA`) ou `null` se inválida/ausente.
     */
    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        CODE_MAP[trimmed]?.let { return it }

        val key = trimmed.uppercase().take(40)
        if (key in REJECTED) return null

        ALIASES[key]?.let { return it }

        // Ex.: "VISA CREDITO" / "CREDITO MASTERCARD" / "Debito Nubank" → extrai se houver marca
        val embedded = KNOWN_BRANDS.firstOrNull { key.contains(it) }
        if (embedded != null) {
            return ALIASES[embedded] ?: embedded
        }

        // "Debito Nubank" / rótulos de app sem bandeira conhecida → null
        if (key.startsWith("DEBITO ") || key.startsWith("CREDITO ") ||
            key.startsWith("DEBIT ") || key.startsWith("CREDIT ")
        ) {
            return null
        }

        val compact = key.replace(Regex("[^A-Z0-9]"), "")
        if (compact in REJECTED || compact in setOf("DEBITOAVISTA", "CREDITOAVISTA")) {
            return null
        }

        // Aceita literal só se não parecer forma de pagamento
        if (REJECTED.any { key.contains(it) }) return null
        return key.takeIf { it.any(Char::isLetter) }
    }
}

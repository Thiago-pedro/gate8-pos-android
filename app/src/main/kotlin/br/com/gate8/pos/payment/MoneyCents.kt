package br.com.gate8.pos.payment

import kotlin.math.roundToLong

/** Valores de pagamento em centavos, para a soma fechar sem erro de ponto flutuante. */
object MoneyCents {
    fun fromReais(value: Double): Long = (value * 100.0).roundToLong()

    fun toReais(cents: Long): Double = cents / 100.0

    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val reais = abs / 100
        val frac = abs % 100
        val text = "%d,%02d".format(reais, frac)
        return if (negative) "-$text" else text
    }

    /**
     * Aceita "10", "10,5", "10,50" e "10.50".
     * Mais de dois decimais, zero e negativo não entram.
     */
    fun parse(raw: String): Long? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val normalized = if (text.contains(',')) {
            text.replace(".", "").replace(',', '.')
        } else {
            text
        }
        if (!normalized.matches(Regex("""\d+(\.\d{1,2})?"""))) return null
        val pieces = normalized.split('.')
        val reais = pieces[0].toLongOrNull() ?: return null
        val frac = pieces.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        val cents = reais * 100 + frac
        return cents.takeIf { it > 0L }
    }
}

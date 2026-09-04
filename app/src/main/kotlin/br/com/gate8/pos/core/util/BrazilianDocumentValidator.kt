package br.com.gate8.pos.core.util

/**
 * Validação de CPF (11 dígitos + dígitos verificadores).
 */
object BrazilianDocumentValidator {

    fun isValidCpf(raw: String): Boolean {
        val cpf = raw.filter { it.isDigit() }
        if (cpf.length != 11) return false
        if (cpf.all { it == cpf[0] }) return false // 000… / 111… etc.
        val digits = cpf.map { it - '0' }
        val d1 = checkDigit(digits, 10)
        if (digits[9] != d1) return false
        val d2 = checkDigit(digits, 11)
        return digits[10] == d2
    }

    private fun checkDigit(digits: List<Int>, weightStart: Int): Int {
        var sum = 0
        var weight = weightStart
        for (i in 0 until weightStart - 1) {
            sum += digits[i] * weight
            weight--
        }
        val mod = sum % 11
        return if (mod < 2) 0 else 11 - mod
    }
}

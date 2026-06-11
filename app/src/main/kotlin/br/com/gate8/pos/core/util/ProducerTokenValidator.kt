package br.com.gate8.pos.core.util

object ProducerTokenValidator {
    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val TOKEN_REGEX = Regex("^[${CHARSET}]{6}$")

    fun normalize(input: String): String =
        input.uppercase().filter { it in CHARSET }.take(6)

    fun isValid(token: String): Boolean = TOKEN_REGEX.matches(token)
}

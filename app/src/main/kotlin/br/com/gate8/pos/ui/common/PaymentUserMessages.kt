package br.com.gate8.pos.ui.common

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.payment.PaymentTimedOutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/** Mensagens amigáveis para falhas de pagamento na maquininha / API. */
object PaymentUserMessages {

    const val CART_RETRY_HINT = "Os itens continuam no carrinho. Tente novamente."

    const val DEFAULT_FAILURE = "Não foi possível concluir o pagamento."

    /** Terminal Cielo ainda sem elegibilidade de pagamento (opt-in). */
    const val CIELO_OPTIN =
        "Terminal Cielo ainda não liberado para pagamento (opt-in). " +
            "Avise o suporte Cielo Smart (integracaosmart@cielo.com.br) com o código do erro."

    /**
     * Cielo respondeu que a cobrança já tinha sido feita (ex.: retry logo após
     * uma tentativa aprovada / mesma janela de anti-duplicidade).
     */
    const val CIELO_ALREADY_DONE =
        "Esta cobrança já foi processada na maquininha. " +
            "Confira se o pagamento anterior foi aprovado e se a venda/ingresso saiu. " +
            "Não cobre de novo sem confirmar."

    fun failureReason(error: Throwable?): String {
        if (error == null) return DEFAULT_FAILURE
        if (error is PaymentTimedOutException) {
            return "Não foi possível confirmar o pagamento a tempo. " +
                "Verifique na maquininha se a cobrança foi aprovada antes de tentar de novo."
        }
        if (error is ApiException) return formatApiException(error)
        return formatThrowable(error)
    }

    private fun formatApiException(error: ApiException): String {
        val message = stripLeadingErrorCodes(error.message?.trim().orEmpty())
        when (error.errorCode?.lowercase()) {
            "failed", "rejected" ->
                return message.takeIf { it.isNotBlank() } ?: "Pagamento recusado na maquininha."
            "order_already_queued" ->
                return message.takeIf { it.isNotBlank() }
                    ?: "Já existe cobrança pendente na maquininha. Conclua ou cancele na Point."
        }
        if (message.isNotBlank()) return ensureSentence(message)
        return DEFAULT_FAILURE
    }

    private fun formatThrowable(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        val message = error.message?.trim().orEmpty()
        val rootMessage = root.message?.trim().orEmpty()
        val combined = "$message $rootMessage"

        cieloOptinMessage(combined)?.let { return it }
        cieloAlreadyDoneMessage(combined)?.let { return it }

        val cleaned = stripLeadingErrorCodes(message.ifBlank { rootMessage })

        return when {
            root is SocketTimeoutException ||
                message.equals("timeout", ignoreCase = true) ||
                rootMessage.equals("timeout", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                rootMessage.contains("timeout", ignoreCase = true) ->
                "Tempo esgotado aguardando a maquininha."

            root is UnknownHostException ||
                message.contains("Unable to resolve host", ignoreCase = true) ->
                "Sem conexão com o servidor. Verifique a internet do aparelho."

            root is ConnectException ||
                message.contains("Failed to connect", ignoreCase = true) ->
                "Não foi possível conectar ao servidor. Tente novamente."

            root is HttpException ->
                "Servidor Gate8 retornou erro ${root.code()}. Tente de novo."

            root is IOException ->
                "Falha de conexão. Verifique a rede e tente novamente."

            message.contains("autentica", ignoreCase = true) ->
                "Credenciais Cielo rejeitadas. Confira CIELO_CLIENT_ID e CIELO_ACCESS_TOKEN."

            cleaned.isNotBlank() -> ensureSentence(cleaned)
            else -> DEFAULT_FAILURE
        }
    }

    /** Detecta -999/-990 e texto "optin" / "não elegível" vindos do UriApp Cielo. */
    fun cieloOptinMessage(raw: String): String? {
        val text = raw.lowercase()
        if (text.contains("optin") || text.contains("opt-in") || text.contains("elegivel")) {
            return CIELO_OPTIN
        }
        if (Regex("""-99[09]\b""").containsMatchIn(raw)) {
            return CIELO_OPTIN
        }
        return null
    }

    /** "Transação já efetuada" / código -4281 — não mostrar números crus na UI. */
    fun cieloAlreadyDoneMessage(raw: String): String? {
        val text = raw.lowercase()
            .replace('á', 'a')
            .replace('ã', 'a')
        if (
            text.contains("ja efetuada") ||
            text.contains("already") && text.contains("transaction") ||
            text.contains("-4281")
        ) {
            return CIELO_ALREADY_DONE
        }
        return null
    }

    /**
     * Remove prefixos tipo `2, -4281, ` que a Cielo devolve junto com o motivo.
     * Ex.: `2, -4281, Transação já efetuada.` → `Transação já efetuada.`
     */
    fun stripLeadingErrorCodes(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val stripped = trimmed
            .replace(Regex("""^(?:\s*-?\d+\s*,)+\s*"""), "")
            .trim()
        return stripped.ifBlank { trimmed }
    }

    private fun ensureSentence(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.last() in ".!?") trimmed else "$trimmed."
    }
}


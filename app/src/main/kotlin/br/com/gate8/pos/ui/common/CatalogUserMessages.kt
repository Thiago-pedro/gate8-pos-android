package br.com.gate8.pos.ui.common

import br.com.gate8.pos.core.network.ApiException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/** Mensagens amigáveis para falha ao carregar catálogo / produtos. */
object CatalogUserMessages {

    fun fromThrowable(error: Throwable?, fallback: String): String {
        if (error == null) return fallback
        val root = generateSequence(error) { it.cause }.last()
        return when {
            root is HttpException -> fromHttp(root.code())
            error is ApiException -> fromHttp(error.httpCode, error.message)
            root is SocketTimeoutException -> "Tempo esgotado ao falar com o servidor."
            root is UnknownHostException ->
                "Sem conexão com o servidor. Verifique a internet do aparelho."
            root is ConnectException ->
                "Não foi possível conectar ao servidor. Tente novamente."
            root is IOException ->
                "Falha de conexão. Verifique a rede e tente novamente."
            else -> {
                val msg = error.message?.trim().orEmpty()
                when {
                    msg.matches(Regex("""HTTP\s+\d{3}""", RegexOption.IGNORE_CASE)) ->
                        fromHttp(msg.substringAfter("HTTP").trim().toIntOrNull() ?: 0)
                    msg.isNotBlank() -> msg
                    else -> fallback
                }
            }
        }
    }

    fun fromHttp(code: Int, serverMessage: String? = null): String {
        val detail = serverMessage?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("HTTP") }
        return when (code) {
            401 -> "Sessão expirada. Faça login de novo no Gate8."
            403 -> "Dispositivo inativo. Libere a maquininha no painel."
            404 -> "Catálogo não encontrado. Confira o evento no painel Gate8."
            in 500..599 ->
                detail?.let { "Servidor Gate8 indisponível ($code): $it" }
                    ?: "Servidor Gate8 indisponível (erro $code). Tente atualizar em instantes."
            0 -> fallbackGeneric(detail)
            else -> detail ?: "Não foi possível atualizar o catálogo (erro $code)."
        }
    }

    private fun fallbackGeneric(detail: String?): String =
        detail ?: "Não foi possível atualizar o catálogo."
}

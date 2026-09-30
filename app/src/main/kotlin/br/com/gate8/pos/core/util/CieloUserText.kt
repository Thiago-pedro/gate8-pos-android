package br.com.gate8.pos.core.util

import java.nio.charset.Charset

/**
 * Texto que a Cielo devolve no callback (muitas vezes UTF-8 lido como Latin-1).
 * Ex.: `OperaÃ§Ã£o nÃ£o finalizada` → `Operação não finalizada`.
 */
object CieloUserText {

    fun repair(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if ('Ã' !in trimmed && 'Â' !in trimmed) return trimmed
        val repaired = decodeAs(trimmed, Charsets.ISO_8859_1)
            ?: decodeAs(trimmed, Charset.forName("windows-1252"))
            ?: trimmed
        return if (looksHealthier(repaired, trimmed)) repaired else trimmed
    }

    fun isOperationNotFinished(raw: String): Boolean {
        val folded = fold(repair(raw))
        if (folded.contains("operacao nao finalizada")) return true
        if (folded.contains("nao finalizada") && folded.contains("opera")) return true
        return false
    }

    fun isUserCancel(raw: String): Boolean {
        val folded = fold(repair(raw))
        return folded.contains("cancel") ||
            folded.contains("usuario") ||
            isOperationNotFinished(raw)
    }

    private fun fold(text: String): String =
        text.lowercase()
            .replace('á', 'a')
            .replace('à', 'a')
            .replace('ã', 'a')
            .replace('â', 'a')
            .replace('é', 'e')
            .replace('ê', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ô', 'o')
            .replace('ú', 'u')
            .replace('ç', 'c')

    private fun decodeAs(text: String, charset: Charset): String? =
        runCatching { String(text.toByteArray(charset), Charsets.UTF_8) }.getOrNull()

    private fun looksHealthier(candidate: String, original: String): Boolean {
        if (candidate.isBlank() || candidate == original) return false
        val candidateMojibake = candidate.count { it == 'Ã' }
        val originalMojibake = original.count { it == 'Ã' }
        return candidateMojibake < originalMojibake
    }
}

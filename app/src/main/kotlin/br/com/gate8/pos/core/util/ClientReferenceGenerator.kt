package br.com.gate8.pos.core.util

import java.util.UUID

object ClientReferenceGenerator {
    /** Nova referência por venda — nunca reciclar até sync confirmado. */
    fun newReference(deviceShortId: String, useTestPrefix: Boolean): String {
        val prefix = if (useTestPrefix) "TEST-" else ""
        val ref = "$deviceShortId-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        return (prefix + ref).take(100)
    }
}

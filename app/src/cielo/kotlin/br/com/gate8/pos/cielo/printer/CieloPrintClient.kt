package br.com.gate8.pos.cielo.printer

import android.content.Intent
import android.net.Uri
import android.util.Log
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import br.com.gate8.pos.cielo.deeplink.CieloDeeplinkResponse
import br.com.gate8.pos.cielo.deeplink.CieloDeeplinkSession
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Envia texto para a impressora térmica via `lio://print`. */
internal object CieloPrintClient {
    private val queue = Executors.newSingleThreadExecutor()

    fun printLines(lines: List<String>) {
        if (lines.isEmpty()) return
        val text = lines.joinToString("\n") + "\n\n"
        queue.submit {
            runBlocking { printTextAsync(text) }
        }.get(PRINT_TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    private suspend fun printTextAsync(text: String) {
        if (BuildConfig.CIELO_CLIENT_ID.isBlank() || BuildConfig.CIELO_ACCESS_TOKEN.isBlank()) {
            Log.w(TAG, "Credenciais Cielo ausentes — impressão ignorada")
            return
        }
        val body = JSONObject().apply {
            put("clientID", BuildConfig.CIELO_CLIENT_ID)
            put("accessToken", BuildConfig.CIELO_ACCESS_TOKEN)
            put("operation", "PRINT_TEXT")
            put("styles", JSONArray().put(JSONObject()))
            put("value", JSONArray().put(text))
        }
        when (val response = launchPrint(body)) {
            is CieloDeeplinkResponse.Error ->
                Log.e(TAG, "Falha impressão Cielo (${response.code}): ${response.reason}")
            is CieloDeeplinkResponse.Success ->
                Log.i(TAG, "Impressão Cielo OK")
        }
    }

    private suspend fun launchPrint(body: JSONObject): CieloDeeplinkResponse {
        val base64 = CieloDeeplinkSession.toBase64(body.toString())
        val uri = Uri.parse(
            "lio://print?request=${Uri.encode(base64)}&urlCallback=${Uri.encode(CieloDeeplinkSession.CALLBACK)}",
        )
        return CieloDeeplinkSession.awaitResponse {
            val activity = CieloActivityHolder.get()
                ?: throw IllegalStateException("Abra o app Gate8 na Cielo Smart para imprimir.")
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            Log.i(TAG, "Deep link aberto: lio://print")
        }
    }

    private const val PRINT_TIMEOUT_SEC = 90L
    private const val TAG = "CieloPrint"
}

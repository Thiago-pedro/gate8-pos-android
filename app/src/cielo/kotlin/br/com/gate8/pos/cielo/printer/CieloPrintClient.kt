package br.com.gate8.pos.cielo.printer

import android.net.Uri
import android.util.Log
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import br.com.gate8.pos.cielo.deeplink.CieloDeeplinkResponse
import br.com.gate8.pos.cielo.deeplink.CieloDeeplinkSession
import br.com.gate8.pos.cielo.deeplink.CieloLioLauncher
import br.com.gate8.pos.cielo.deeplink.CieloLioOp
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/** Envia texto/imagem para a impressora térmica via `lio://print` (fila assíncrona). */
internal object CieloPrintClient {
    private val worker = Executors.newSingleThreadExecutor()
    private val brLocale = Locale("pt", "BR")

    private const val ALIGN_CENTER = 0
    private const val SIZE_META = 20
    /** Item + preço no mesmo bloco — evita espaço extra entre chamadas PRINT_TEXT. */
    private const val SIZE_ITEM_PRICE = 36
    private const val SIZE_ORDER = 48

    /** Enfileira impressão de texto simples (comprovantes, relatórios). */
    fun printLines(lines: List<String>) {
        if (lines.isEmpty()) return
        val text = lines.joinToString("\n") + "\n\n"
        enqueuePrint {
            printTextAsync(text, ALIGN_CENTER, SIZE_META)
        }
    }

    /**
     * Ficha da conveniência: logo Gate8 + metadados + item/preço em destaque, centralizados
     * via estilos nativos da Cielo (não depende de padding com espaços).
     */
    fun printConvenienceFicha(
        logoPath: String?,
        producerName: String?,
        dateTime: String,
        terminalName: String,
        itemDescription: String,
        unitPrice: String,
        authorization: String?,
    ) {
        enqueuePrint {
            logoPath?.let { path -> printImageAsync(path) }
            val meta = buildString {
                producerName?.takeIf { it.isNotBlank() }?.let {
                    append(it.trim())
                    append('\n')
                }
                append(dateTime)
                append('\n')
                append(terminalName)
                append('\n')
            }
            printTextAsync(meta, ALIGN_CENTER, SIZE_META)
            printTextAsync(
                buildString {
                    append(itemDescription.trim().uppercase(brLocale))
                    append('\n')
                    append(unitPrice.trim())
                    append('\n')
                },
                ALIGN_CENTER,
                SIZE_ITEM_PRICE,
            )
            if (!authorization.isNullOrBlank()) {
                printTextAsync("\nAUT.: ${authorization.trim()}\n", ALIGN_CENTER, SIZE_META)
            }
            printTextAsync("\n" + ".".repeat(32) + "\n\n", ALIGN_CENTER, SIZE_META)
        }
    }

    /**
     * Pedido da cozinha: logo + PEDIDO N em destaque + itens (sem preço).
     */
    fun printKitchenFicha(
        logoPath: String?,
        producerName: String?,
        dateTime: String,
        terminalName: String,
        orderNumber: Int,
        items: List<Pair<Int, String>>,
    ) {
        enqueuePrint {
            logoPath?.let { path -> printImageAsync(path) }
            val meta = buildString {
                producerName?.takeIf { it.isNotBlank() }?.let {
                    append(it.trim())
                    append('\n')
                }
                append(dateTime)
                append('\n')
                append(terminalName)
                append('\n')
            }
            printTextAsync(meta, ALIGN_CENTER, SIZE_META)
            printTextAsync("PEDIDO $orderNumber\n", ALIGN_CENTER, SIZE_ORDER)
            val itemBlock = buildString {
                items.forEach { (qty, desc) ->
                    append(qty)
                    append("x ")
                    append(desc.trim().uppercase(brLocale))
                    append('\n')
                }
            }
            if (itemBlock.isNotBlank()) {
                printTextAsync(itemBlock, ALIGN_CENTER, SIZE_ITEM_PRICE)
            }
            printTextAsync("\n" + ".".repeat(32) + "\n\n", ALIGN_CENTER, SIZE_META, formFeed = true)
        }
    }

    /**
     * Ingresso bilheteria — layout compacto do site Gate8.
     * Poucas chamadas PRINT_* (cada uma abre margem na bobina) + avanço no fim.
     */
    fun printGate8Ticket(
        logoPath: String?,
        qrPath: String?,
        topText: String,
        manualText: String,
        bottomText: String,
    ) {
        enqueuePrint {
            logoPath?.let { path -> printImageAsync(path) }
            printTextAsync(topText, ALIGN_CENTER, 20)
            qrPath?.let { path -> printImageAsync(path) }
            printTextAsync(manualText, ALIGN_CENTER, 26)
            // Rodapé (validação / compra / válido / emitido / aviso) — fonte maior
            printTextAsync(bottomText, ALIGN_CENTER, 22, formFeed = true)
        }
    }

    private fun enqueuePrint(block: suspend () -> Unit) {
        worker.execute {
            runBlocking {
                runCatching { block() }
                    .onFailure { Log.e(TAG, "Fila de impressão falhou", it) }
            }
        }
    }

    private suspend fun printTextAsync(
        text: String,
        align: Int,
        textSize: Int,
        formFeed: Boolean = false,
    ) {
        if (text.isBlank()) return
        val body = baseBody().apply {
            put("operation", "PRINT_TEXT")
            put(
                "styles",
                JSONArray().put(
                    JSONObject().apply {
                        put("key_attributes_align", align)
                        put("key_attributes_textsize", textSize)
                        put("key_attributes_typeface", 1)
                        if (formFeed) put("form_feed", 1)
                    },
                ),
            )
            put("value", JSONArray().put(text))
        }
        dispatch(body)
    }

    private suspend fun printImageAsync(imagePath: String) {
        val body = baseBody().apply {
            put("operation", "PRINT_IMAGE")
            put(
                "styles",
                JSONArray().put(
                    JSONObject().apply {
                        put("key_attributes_align", ALIGN_CENTER)
                        put("form_feed", 0)
                    },
                ),
            )
            put("value", JSONArray().put(imagePath))
        }
        dispatch(body)
    }

    private fun baseBody(): JSONObject = JSONObject().apply {
        put("clientID", BuildConfig.CIELO_CLIENT_ID)
        put("accessToken", BuildConfig.CIELO_ACCESS_TOKEN)
    }

    private suspend fun dispatch(body: JSONObject) {
        if (BuildConfig.CIELO_CLIENT_ID.isBlank() || BuildConfig.CIELO_ACCESS_TOKEN.isBlank()) {
            Log.w(TAG, "Credenciais Cielo ausentes — impressão ignorada")
            return
        }
        when (val response = launchPrint(body)) {
            is CieloDeeplinkResponse.Error ->
                Log.e(TAG, "Falha impressão Cielo (${response.code}): ${response.reason}")
            is CieloDeeplinkResponse.Success ->
                Log.d(TAG, "Impressão Cielo OK (${body.optString("operation")})")
        }
    }

    private suspend fun launchPrint(body: JSONObject): CieloDeeplinkResponse {
        val base64 = CieloDeeplinkSession.toBase64(body.toString())
        val uri = Uri.parse(
            "lio://print?request=${Uri.encode(base64)}&urlCallback=${Uri.encode(CieloDeeplinkSession.CALLBACK_PRINT)}",
        )
        return CieloDeeplinkSession.awaitResponse(CieloLioOp.PRINT) {
            val activity = CieloActivityHolder.get()
                ?: throw IllegalStateException("Abra o app Gate8 na Cielo Smart para imprimir.")
            CieloLioLauncher.start(activity, uri)
            Log.i(TAG, "Deep link aberto: lio://print ${body.optString("operation")}")
        }
    }

    private const val TAG = "CieloPrint"
}

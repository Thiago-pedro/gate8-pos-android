package br.com.gate8.pos.data.repository

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.ManagerCardRequestDto
import br.com.gate8.pos.data.remote.dto.ManagerCardResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cartão do gerente compartilhado entre as maquininhas da mesma conveniência. */
class ManagerCardRepository(
    private val api: PosApiService,
    private val json: Json,
) {
    data class Card(
        val uid: String,
        val deviceId: String?,
        val deviceName: String?,
    )

    sealed interface Lookup {
        /** O endpoint ainda não existe no painel. A maquininha segue com o que já tem. */
        data object Unavailable : Lookup
        data object Empty : Lookup
        data class Found(val card: Card) : Lookup
    }

    /** Atualiza o cartão local com o da conveniência. Um cartão antigo desta maquininha sobe uma vez. */
    suspend fun sync(config: DeviceConfigStore) {
        when (val lookup = fetch()) {
            Lookup.Unavailable -> Unit
            is Lookup.Found -> config.setManagerCard(lookup.card.uid, lookup.card.deviceId, lookup.card.deviceName)
            Lookup.Empty -> {
                val local = config.getManagerCardUid()
                val alreadyShared = !config.getManagerCardDeviceId().isNullOrBlank()
                if (!local.isNullOrBlank() && !alreadyShared) {
                    when (val saved = save(local)) {
                        is Lookup.Found ->
                            config.setManagerCard(saved.card.uid, saved.card.deviceId, saved.card.deviceName)
                        else -> Unit
                    }
                } else {
                    config.clearManagerCardUid()
                }
            }
        }
    }

    suspend fun fetch(): Lookup {
        val response = api.getManagerCard()
        if (response.code() == 404) return Lookup.Unavailable
        if (!response.isSuccessful) {
            throw ApiException(response.code(), response.errorBody()?.string() ?: "Falha ao consultar o cartão do gerente")
        }
        return response.body().toLookup()
    }

    suspend fun save(uid: String): Lookup {
        val response = api.saveManagerCard(ManagerCardRequestDto(uid.trim().uppercase()))
        if (response.code() == 404) return Lookup.Unavailable
        if (response.code() == 409) return parseConflict(response.errorBody()?.string())
        if (!response.isSuccessful) {
            throw ApiException(response.code(), response.errorBody()?.string() ?: "Falha ao gravar o cartão do gerente")
        }
        return response.body().toLookup()
    }

    suspend fun clear(): Lookup {
        val response = api.clearManagerCard()
        if (response.code() == 404) return Lookup.Unavailable
        if (!response.isSuccessful) {
            throw ApiException(response.code(), response.errorBody()?.string() ?: "Falha ao remover o cartão do gerente")
        }
        return Lookup.Empty
    }

    private fun ManagerCardResponseDto?.toLookup(): Lookup {
        val uid = this?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (this == null || !enrolled || uid == null) return Lookup.Empty
        return Lookup.Found(
            Card(
                uid = uid.uppercase(),
                deviceId = deviceId?.trim()?.takeIf { it.isNotEmpty() },
                deviceName = deviceName?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    private fun parseConflict(body: String?): Lookup {
        if (!body.isNullOrBlank() && body.trim().startsWith("{")) {
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val uid = root["uid"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@runCatching
                return Lookup.Found(
                    Card(
                        uid = uid.uppercase(),
                        deviceId = root["device_id"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() },
                        deviceName = root["device_name"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
        }
        return Lookup.Empty
    }
}

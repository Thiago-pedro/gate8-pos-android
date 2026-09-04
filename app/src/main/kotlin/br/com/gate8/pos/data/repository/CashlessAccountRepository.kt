package br.com.gate8.pos.data.repository

import android.util.Log
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.local.dao.CashlessAccountDao
import br.com.gate8.pos.data.local.entity.CashlessAccountEntity
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.CashlessBlockByCpfRequestDto
import br.com.gate8.pos.data.remote.dto.CashlessCardDto
import br.com.gate8.pos.data.remote.dto.CashlessPatchRequestDto
import br.com.gate8.pos.data.remote.dto.CashlessReassignRequestDto
import br.com.gate8.pos.data.remote.dto.CashlessRegisterRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cadastro cashless: tenta API Lovable e espelha no Room.
 * Se a rota ainda não existir / rede falhar → usa só o cache local da maquininha.
 */
class CashlessAccountRepository(
    private val api: PosApiService,
    private val dao: CashlessAccountDao,
    private val json: Json,
) {
    suspend fun getByUid(uidHex: String): CashlessAccountEntity? {
        val uid = uidHex.uppercase()
        val remote = runCatching { api.getCashlessByUid(uid) }.getOrNull()
        if (remote != null) {
            if (remote.isSuccessful) {
                val body = remote.body()
                return if (body?.found == true && body.card != null) {
                    cache(body.card)
                } else {
                    dao.deleteByUid(uid)
                    null
                }
            }
            val errBody = remote.errorBody()?.string()
            if (!shouldFallback(remote.code(), errBody)) {
                throw parseApiError(remote.code(), errBody)
            }
            Log.w(TAG, "getByUid: API indisponível (${remote.code()}) — local")
        }
        return dao.getByUid(uid)
    }

    suspend fun getByCpf(cpfDigits: String): CashlessAccountEntity? {
        val cpf = cpfDigits.filter { it.isDigit() }
        val remote = runCatching { api.getCashlessByCpf(cpf) }.getOrNull()
        if (remote != null) {
            if (remote.isSuccessful) {
                val body = remote.body()
                return if (body?.found == true && body.card != null) {
                    cache(body.card)
                } else {
                    null
                }
            }
            val errBody = remote.errorBody()?.string()
            if (!shouldFallback(remote.code(), errBody)) {
                throw parseApiError(remote.code(), errBody)
            }
            Log.w(TAG, "getByCpf: API indisponível (${remote.code()}) — local")
        }
        return dao.getByCpf(cpf)
    }

    suspend fun register(uidHex: String, cpfDigits: String, phoneDigits: String, balanceCents: Int = 0) {
        val cpf = cpfDigits.filter { it.isDigit() }
        val phone = phoneDigits.filter { it.isDigit() }
        val uid = uidHex.uppercase()
        val cents = balanceCents.coerceAtLeast(0)

        val remote = runCatching {
            api.registerCashlessCard(
                CashlessRegisterRequestDto(
                    uidHex = uid,
                    cpf = cpf,
                    phone = phone,
                    balanceCents = cents,
                ),
            )
        }.getOrNull()

        if (remote != null) {
            if (remote.isSuccessful) {
                val card = remote.body()?.card
                    ?: throw ApiException(remote.code(), "Resposta vazia no cadastro cashless")
                cache(card)
                return
            }
            val errBody = remote.errorBody()?.string()
            if (remote.code() == 409) {
                throw parseApiError(409, errBody)
            }
            if (!shouldFallback(remote.code(), errBody)) {
                throw parseApiError(remote.code(), errBody)
            }
            Log.w(TAG, "register: API indisponível (${remote.code()}) — salvando local")
        } else {
            Log.w(TAG, "register: sem rede/API — salvando local")
        }

        registerLocal(uid, cpf, phone, cents)
    }

    suspend fun updateBalance(uidHex: String, balanceCents: Int) {
        val uid = uidHex.uppercase()
        val cents = balanceCents.coerceAtLeast(0)
        patchRemote(uid, CashlessPatchRequestDto(balanceCents = cents))
        val current = dao.getByUid(uid) ?: return
        dao.upsert(current.copy(balanceCents = cents, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setBlocked(uidHex: String, blocked: Boolean, balanceCents: Int? = null) {
        val uid = uidHex.uppercase()
        patchRemote(
            uid,
            CashlessPatchRequestDto(
                balanceCents = balanceCents,
                blocked = blocked,
            ),
        )
        val current = dao.getByUid(uid) ?: return
        dao.upsert(
            current.copy(
                blocked = blocked,
                balanceCents = balanceCents ?: current.balanceCents,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun blockByCpf(cpfDigits: String): CashlessAccountEntity {
        val cpf = cpfDigits.filter { it.isDigit() }
        val remote = runCatching {
            api.blockCashlessByCpf(CashlessBlockByCpfRequestDto(cpf = cpf))
        }.getOrNull()

        if (remote != null) {
            if (remote.isSuccessful) {
                val card = remote.body()?.card
                    ?: throw ApiException(remote.code(), "Resposta vazia")
                return cache(card)
            }
            val errBody = remote.errorBody()?.string()
            if (remote.code() == 404 && looksLikeJson(errBody)) {
                // API viva: CPF não encontrado — tenta local; se falhar, mensagem da API
                return runCatching { blockByCpfLocal(cpf) }
                    .getOrElse { throw parseApiError(404, errBody) }
            }
            if (!shouldFallback(remote.code(), errBody)) {
                throw parseApiError(remote.code(), errBody)
            }
            Log.w(TAG, "blockByCpf: API indisponível (${remote.code()}) — local")
        }

        return blockByCpfLocal(cpf)
    }

    suspend fun reassignUid(
        oldUid: String,
        newUid: String,
        balanceCents: Int,
        cpf: String,
        phone: String,
    ) {
        val old = oldUid.uppercase()
        val newId = newUid.uppercase()
        val cents = balanceCents.coerceAtLeast(0)
        val cpfDigits = cpf.filter { it.isDigit() }
        val phoneDigits = phone.filter { it.isDigit() }

        val remote = runCatching {
            api.reassignCashlessCard(
                CashlessReassignRequestDto(
                    oldUidHex = old,
                    newUidHex = newId,
                    balanceCents = cents,
                ),
            )
        }.getOrNull()

        if (remote != null) {
            if (remote.isSuccessful) {
                val card = remote.body()?.card
                    ?: throw ApiException(remote.code(), "Resposta vazia no reassign")
                dao.deleteByUid(old)
                cache(card)
                return
            }
            val errBody = remote.errorBody()?.string()
            if (remote.code() == 409) {
                throw parseApiError(409, errBody)
            }
            if (!shouldFallback(remote.code(), errBody)) {
                throw parseApiError(remote.code(), errBody)
            }
            Log.w(TAG, "reassign: API indisponível (${remote.code()}) — local")
        }

        dao.deleteByUid(old)
        dao.upsert(
            CashlessAccountEntity(
                uidHex = newId,
                cpf = cpfDigits,
                phone = phoneDigits,
                blocked = false,
                balanceCents = cents,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun patchRemote(uid: String, body: CashlessPatchRequestDto) {
        val remote = runCatching { api.patchCashlessCard(uid, body) }.getOrNull() ?: return
        if (remote.isSuccessful) {
            remote.body()?.card?.let { cache(it) }
            return
        }
        val errBody = remote.errorBody()?.string()
        // 404 JSON = UID não na nuvem; segue local. 404 HTML = rota inexistente.
        if (shouldFallback(remote.code(), errBody)) return
        throw parseApiError(remote.code(), errBody)
    }

    private suspend fun registerLocal(uid: String, cpf: String, phone: String, cents: Int) {
        val existingCpf = dao.getByCpf(cpf)
        if (existingCpf != null && !existingCpf.uidHex.equals(uid, ignoreCase = true)) {
            error("CPF já vinculado ao cartão ${existingCpf.uidHex}. Use Cartão perdido para transferir.")
        }
        dao.upsert(
            CashlessAccountEntity(
                uidHex = uid,
                cpf = cpf,
                phone = phone,
                blocked = false,
                balanceCents = cents,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun blockByCpfLocal(cpf: String): CashlessAccountEntity {
        val account = dao.getByCpf(cpf)
            ?: error("CPF não encontrado no cadastro desta maquininha.")
        val updated = account.copy(blocked = true, updatedAt = System.currentTimeMillis())
        dao.upsert(updated)
        return updated
    }

    private suspend fun cache(card: CashlessCardDto): CashlessAccountEntity {
        val entity = CashlessAccountEntity(
            uidHex = card.uidHex.uppercase(),
            cpf = card.cpf.filter { it.isDigit() },
            phone = card.phone.filter { it.isDigit() },
            blocked = card.blocked,
            balanceCents = card.balanceCents.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        return entity
    }

    private fun shouldFallback(code: Int, errorBody: String?): Boolean {
        if (code == 404 || code == 405 || code == 501 || code == 502 || code == 503) return true
        if (errorBody?.contains("<html", ignoreCase = true) == true) return true
        // IOException path already null; empty 404 etc.
        return false
    }

    private fun looksLikeJson(body: String?): Boolean {
        val t = body?.trim().orEmpty()
        return t.startsWith("{") || t.startsWith("[")
    }

    private fun parseApiError(code: Int, body: String?): ApiException {
        if (!body.isNullOrBlank() && looksLikeJson(body)) {
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val errorCode = root["error"]?.jsonPrimitive?.content
                    ?: root["code"]?.jsonPrimitive?.content
                val message = when (errorCode) {
                    "cpf_already_linked" -> {
                        val linkedUid = root["uid_hex"]?.jsonPrimitive?.content
                        if (linkedUid != null) {
                            "CPF já vinculado ao cartão $linkedUid. Use Cartão perdido para transferir."
                        } else {
                            "CPF já vinculado a outro cartão. Use Cartão perdido para transferir."
                        }
                    }
                    "uid_cpf_mismatch" -> "Este cartão já está cadastrado com outro CPF."
                    "cpf_not_found" -> "CPF não encontrado no cadastro."
                    else -> root["message"]?.jsonPrimitive?.content
                        ?: root["error"]?.jsonPrimitive?.content
                        ?: body
                }
                return ApiException(code, message, errorCode)
            }
        }
        return ApiException(code, body ?: "Erro cashless ($code)")
    }

    companion object {
        private const val TAG = "CashlessAccounts"
    }
}

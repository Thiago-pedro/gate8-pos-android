package br.com.gate8.pos.data.repository

import android.util.Log
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.local.dao.CashlessAccountDao
import br.com.gate8.pos.data.local.dao.CashlessMovementDao
import br.com.gate8.pos.data.local.entity.CashlessAccountEntity
import br.com.gate8.pos.data.local.entity.CashlessMovementEntity
import br.com.gate8.pos.data.local.entity.CashlessMovementType
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
    private val movementDao: CashlessMovementDao,
    private val json: Json,
) {
    suspend fun getByUid(uidHex: String): CashlessAccountEntity? {
        val uid = uidHex.uppercase()
        // Já limpo para reuso local: não ressuscita cadastro antigo.
        if (wasReleasedForReuse(uid)) {
            dao.deleteByUid(uid)
            return null
        }
        // Lookup nunca deve derrubar a UI: qualquer falha da API cai no espelho local.
        val remote = runCatching { api.getCashlessByUid(uid) }.getOrNull()
        if (remote != null) {
            if (remote.isSuccessful) {
                val body = runCatching { remote.body() }.getOrNull()
                return if (body?.found == true && body.card != null) {
                    runCatching { cache(body.card) }.getOrElse {
                        Log.w(TAG, "getByUid: falha ao cachear — local", it)
                        dao.getByUid(uid)
                    }
                } else {
                    // Backend: cartão encerrado / livre → found:false. Limpa espelho local.
                    dao.deleteByUid(uid)
                    return null
                }
            }
            Log.w(TAG, "getByUid: API ${remote.code()} — usando cadastro local")
        }
        return dao.getByUid(uid) ?: resolveMissingRemoteUid(uid)
    }

    /**
     * Não pode gastar se bloqueado ou se o saldo saiu deste UID e ainda não houve
     * novo cadastro ativo / liberação para reuso.
     */
    suspend fun isUidRevokedForUse(uidHex: String): Boolean {
        val uid = uidHex.uppercase()
        if (wasReleasedForReuse(uid)) return false
        val account = dao.getByUid(uid)
        // Cadastro ativo (mesmo após reuso do UID com outro CPF) → pode usar.
        if (account != null && !account.blocked) return false
        if (account?.blocked == true) return true
        return hasTransferOut(uid)
    }

    /** Após zerar/limpar o chip: encerra cadastro ativo no Lovable e apaga espelho local. */
    suspend fun closeCard(uidHex: String) {
        val uid = uidHex.uppercase()
        val remote = runCatching { api.closeCashlessCard(uid) }.getOrNull()
        if (remote != null) {
            if (remote.isSuccessful || remote.code() == 404) {
                // 404 = já não há cadastro ativo — equivalente a encerrado.
                dao.deleteByUid(uid)
                return
            }
            val errBody = remote.errorBody()?.string()
            if (remote.code() == 409 && errorCodeOf(errBody) == "card_replaced") {
                dao.deleteByUid(uid)
                return
            }
            if (!shouldFallback(remote.code(), errBody)) {
                throw parseApiError(remote.code(), errBody)
            }
            Log.w(TAG, "closeCard: API ${remote.code()} — encerrando só local")
        } else {
            Log.w(TAG, "closeCard: sem rede/API — encerrando só local")
        }
        dao.deleteByUid(uid)
    }

    /** Após zerar residual de cartão substituído, libera o UID para nova festa. */
    suspend fun releaseUidForReuse(uidHex: String) {
        closeCard(uidHex)
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

    suspend fun register(
        uidHex: String,
        name: String,
        cpfDigits: String,
        phoneDigits: String,
        balanceCents: Int = 0,
    ) {
        val holderName = name.trim()
        val cpf = cpfDigits.filter { it.isDigit() }
        val phone = phoneDigits.filter { it.isDigit() }
        val uid = uidHex.uppercase()
        val cents = balanceCents.coerceAtLeast(0)

        val remote = runCatching {
            api.registerCashlessCard(
                CashlessRegisterRequestDto(
                    uidHex = uid,
                    name = holderName,
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

        registerLocal(uid, holderName, cpf, phone, cents)
    }

    suspend fun updateBalance(uidHex: String, balanceCents: Int) {
        val uid = uidHex.uppercase()
        val cents = balanceCents.coerceAtLeast(0)
        val current = dao.getByUid(uid) ?: return
        // Não ressuscita saldo de cartão bloqueado a partir do chip.
        if (current.blocked) return
        when (val patch = patchRemoteResult(uid, CashlessPatchRequestDto(balanceCents = cents))) {
            PatchRemoteResult.CardReplaced -> {
                // Cadastro encerrado na nuvem — próximo passo é POST de cadastro novo.
                dao.deleteByUid(uid)
                return
            }
            PatchRemoteResult.Ok, PatchRemoteResult.Skipped -> Unit
        }
        dao.upsert(current.copy(balanceCents = cents, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setBlocked(uidHex: String, blocked: Boolean, balanceCents: Int? = null) {
        val uid = uidHex.uppercase()
        when (
            val patch = patchRemoteResult(
                uid,
                CashlessPatchRequestDto(
                    balanceCents = balanceCents,
                    blocked = blocked,
                ),
            )
        ) {
            PatchRemoteResult.CardReplaced -> {
                dao.deleteByUid(uid)
                return
            }
            PatchRemoteResult.Ok, PatchRemoteResult.Skipped -> Unit
        }
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
                dao.upsert(revokedStub(old))
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

        val holderName = dao.getByUid(old)?.name.orEmpty()
        dao.deleteByUid(old)
        // Stub do UID antigo: bloqueado e zerado — consulta não trata como "pronto para usar".
        dao.upsert(revokedStub(old))
        dao.upsert(
            CashlessAccountEntity(
                uidHex = newId,
                name = holderName,
                cpf = cpfDigits,
                phone = phoneDigits,
                blocked = false,
                balanceCents = cents,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordMovement(
        uidHex: String,
        type: String,
        amountCents: Int,
        balanceAfterCents: Int,
        cpf: String? = null,
        note: String? = null,
    ) {
        val uid = uidHex.uppercase()
        val accountCpf = cpf?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
            ?: dao.getByUid(uid)?.cpf
        movementDao.insert(
            CashlessMovementEntity(
                uidHex = uid,
                cpf = accountCpf,
                type = type,
                amountCents = amountCents,
                balanceAfterCents = balanceAfterCents.coerceAtLeast(0),
                note = note,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Extrato do cartão (somente este UID).
     * Não mistura movimentações de outro cartão do mesmo CPF.
     */
    suspend fun listStatement(uidHex: String, cpf: String? = null): List<CashlessMovementEntity> {
        val uid = uidHex.uppercase()
        return movementDao.listByUid(uid)
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    /**
     * Garante a linha final SUBSTITUIDO quando o saldo já saiu deste UID.
     * Útil para cartões transferidos antes dessa movimentação existir.
     */
    suspend fun ensureSubstituidoMovement(uidHex: String, cpf: String? = null): List<CashlessMovementEntity> {
        val uid = uidHex.uppercase()
        val current = listStatement(uid)
        val saida = current.lastOrNull { it.type == CashlessMovementType.TRANSF_SAIDA }
            ?: return current
        if (current.any { it.type == CashlessMovementType.SUBSTITUIDO }) return current
        val newUidHint = saida.note
            ?.substringAfter("Para ", missingDelimiterValue = "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        recordMovement(
            uidHex = uid,
            type = CashlessMovementType.SUBSTITUIDO,
            amountCents = 0,
            balanceAfterCents = 0,
            cpf = cpf ?: saida.cpf,
            note = if (newUidHint != null) {
                "Cartão substituído pelo UID $newUidHint"
            } else {
                "Cartão substituído · saldo transferido"
            },
        )
        return listStatement(uid)
    }

    private suspend fun patchRemote(uid: String, body: CashlessPatchRequestDto) {
        when (val result = patchRemoteResult(uid, body)) {
            PatchRemoteResult.CardReplaced ->
                throw ApiException(
                    409,
                    "Este cartão foi encerrado/substituído. Faça um novo cadastro (CPF/telefone).",
                    "card_replaced",
                )
            PatchRemoteResult.Ok, PatchRemoteResult.Skipped -> Unit
        }
    }

    private suspend fun patchRemoteResult(uid: String, body: CashlessPatchRequestDto): PatchRemoteResult {
        val remote = runCatching { api.patchCashlessCard(uid, body) }.getOrNull()
            ?: return PatchRemoteResult.Skipped
        if (remote.isSuccessful) {
            remote.body()?.card?.let { cache(it) }
            return PatchRemoteResult.Ok
        }
        val errBody = remote.errorBody()?.string()
        if (remote.code() == 409 && errorCodeOf(errBody) == "card_replaced") {
            Log.w(TAG, "patch: card_replaced em $uid — cadastro encerrado")
            return PatchRemoteResult.CardReplaced
        }
        // 404 JSON = UID não na nuvem; segue local. 404 HTML = rota inexistente.
        if (shouldFallback(remote.code(), errBody)) return PatchRemoteResult.Skipped
        throw parseApiError(remote.code(), errBody)
    }

    private enum class PatchRemoteResult { Ok, Skipped, CardReplaced }

    private suspend fun hasTransferOut(uid: String): Boolean =
        movementDao.listByUid(uid).any { it.type == CashlessMovementType.TRANSF_SAIDA }

    private suspend fun wasReleasedForReuse(uid: String): Boolean {
        val movements = movementDao.listByUid(uid)
        val lastSaidaAt = movements
            .lastOrNull { it.type == CashlessMovementType.TRANSF_SAIDA }
            ?.createdAt
            ?: return false
        // Qualquer zerem depois da transferência libera o UID (reuso na próxima festa).
        val lastZeroAt = movements
            .lastOrNull { it.type == CashlessMovementType.ZERAGEM }
            ?.createdAt
            ?: return false
        return lastZeroAt >= lastSaidaAt
    }

    private fun revokedStub(uid: String): CashlessAccountEntity =
        CashlessAccountEntity(
            uidHex = uid.uppercase(),
            name = "",
            cpf = "",
            phone = "",
            blocked = true,
            balanceCents = 0,
            updatedAt = System.currentTimeMillis(),
        )

    /**
     * Fallback offline: se a API não respondeu, mantém stub bloqueado após transferência
     * ainda não liberada. Com API online found:false o getByUid já limpa e retorna null.
     */
    private suspend fun resolveMissingRemoteUid(uid: String): CashlessAccountEntity? {
        val local = dao.getByUid(uid)
        if (local?.blocked == true) return local
        if (hasTransferOut(uid) && !wasReleasedForReuse(uid)) {
            val stub = revokedStub(uid)
            dao.upsert(stub)
            return stub
        }
        return local
    }

    private suspend fun registerLocal(
        uid: String,
        name: String,
        cpf: String,
        phone: String,
        cents: Int,
    ) {
        if (cpf.isNotBlank()) {
            val existingCpf = dao.getByCpf(cpf)
            if (existingCpf != null && !existingCpf.uidHex.equals(uid, ignoreCase = true)) {
                error("CPF já vinculado ao cartão ${existingCpf.uidHex}. Use Cartão perdido para transferir.")
            }
        }
        val previous = dao.getByUid(uid)
        // Mesmo UID com outro CPF: encerra espelho local e abre cadastro novo (igual backend).
        if (previous != null &&
            cpf.isNotBlank() &&
            previous.cpf.isNotBlank() &&
            previous.cpf != cpf
        ) {
            dao.deleteByUid(uid)
            Log.i(TAG, "registerLocal: UID $uid reutilizado — CPF ${previous.cpf} → $cpf")
        }
        dao.upsert(
            CashlessAccountEntity(
                uidHex = uid,
                name = name.trim(),
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
            name = card.name.trim(),
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

    private fun errorCodeOf(body: String?): String? {
        if (body.isNullOrBlank() || !looksLikeJson(body)) return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonPrimitive?.content
                ?: root["code"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    private fun parseApiError(code: Int, body: String?): ApiException {
        if (!body.isNullOrBlank() && looksLikeJson(body)) {
            runCatching {
                val root = json.parseToJsonElement(body).jsonObject
                val errorCode = root["error"]?.jsonPrimitive?.content
                    ?: root["code"]?.jsonPrimitive?.content
                val message = when (errorCode) {
                    "invalid_cpf" ->
                        "CPF inválido. Digite um CPF com 11 dígitos válidos."
                    "cpf_already_linked" -> {
                        val linkedUid = root["uid_hex"]?.jsonPrimitive?.content
                        if (linkedUid != null) {
                            "CPF já vinculado ao cartão $linkedUid. Use Cartão perdido para transferir."
                        } else {
                            "CPF já vinculado a outro cartão. Use Cartão perdido para transferir."
                        }
                    }
                    "card_replaced" ->
                        "Este cartão foi encerrado/substituído. Faça um novo cadastro (CPF/telefone)."
                    "uid_cpf_mismatch" ->
                        // Backend novo não deve mais retornar isso; mantém mensagem clara se aparecer.
                        "Este cartão já teve outro CPF. Cadastre de novo com o CPF atual (POST)."
                    "cpf_not_found" -> "CPF não encontrado no cadastro."
                    else -> root["message"]?.jsonPrimitive?.content
                        ?.takeIf { it.isNotBlank() && !it.equals(errorCode, ignoreCase = true) }
                        ?: when (errorCode) {
                            null, "" -> body
                            else -> "Não foi possível concluir o cadastro ($errorCode)."
                        }
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

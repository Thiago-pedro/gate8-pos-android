package br.com.gate8.pos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val serverTime: String,
    val fetchedAt: Long,
)

@Entity(tableName = "pending_sales")
data class PendingSaleEntity(
    @PrimaryKey val clientReference: String,
    val payloadJson: String,
    val status: String,
    val saleId: String? = null,
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

object PendingSaleStatus {
    const val PENDING_SYNC = "PENDING_SYNC"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}

/** Cadastro cashless local: UID do Mifare ↔ CPF/telefone (fase 1 sem Lovable). */
@Entity(
    tableName = "cashless_accounts",
    indices = [Index(value = ["cpf"])],
)
data class CashlessAccountEntity(
    @PrimaryKey val uidHex: String,
    /** Somente dígitos. */
    val cpf: String,
    /** Somente dígitos. */
    val phone: String,
    val blocked: Boolean = false,
    /** Último saldo conhecido em centavos (espelho do chip). */
    val balanceCents: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Extrato local: cada recarga / zerar / bloqueio / transferência nesta maquininha. */
@Entity(
    tableName = "cashless_movements",
    indices = [
        Index(value = ["uidHex"]),
        Index(value = ["cpf"]),
    ],
)
data class CashlessMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uidHex: String,
    val cpf: String? = null,
    /** RECARGA | ZERAGEM | BLOQUEIO | TRANSF_SAIDA | TRANSF_ENTRADA */
    val type: String,
    /** Positivo = crédito; negativo = débito/saída. */
    val amountCents: Int,
    val balanceAfterCents: Int,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

object CashlessMovementType {
    const val RECARGA = "RECARGA"
    const val ZERAGEM = "ZERAGEM"
    const val BLOQUEIO = "BLOQUEIO"
    const val DESBLOQUEIO = "DESBLOQUEIO"
    const val TRANSF_SAIDA = "TRANSF_SAIDA"
    const val TRANSF_ENTRADA = "TRANSF_ENTRADA"
    const val CONSUMO = "CONSUMO"
}

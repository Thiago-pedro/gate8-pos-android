package br.com.gate8.pos.data.local.entity

import androidx.room.Entity
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

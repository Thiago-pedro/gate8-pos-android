package br.com.gate8.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.gate8.pos.data.local.entity.CatalogCacheEntity
import br.com.gate8.pos.data.local.entity.PendingSaleEntity

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_cache WHERE id = 1")
    suspend fun get(): CatalogCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CatalogCacheEntity)
}

@Dao
interface PendingSaleDao {
    @Query("SELECT * FROM pending_sales WHERE status = :status ORDER BY createdAt ASC")
    suspend fun listByStatus(status: String): List<PendingSaleEntity>

    @Query("SELECT * FROM pending_sales ORDER BY createdAt DESC")
    suspend fun listAll(): List<PendingSaleEntity>

    @Query("SELECT * FROM pending_sales WHERE clientReference = :ref LIMIT 1")
    suspend fun getByReference(ref: String): PendingSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingSaleEntity)

    @Query("DELETE FROM pending_sales WHERE clientReference = :ref")
    suspend fun delete(ref: String)
}

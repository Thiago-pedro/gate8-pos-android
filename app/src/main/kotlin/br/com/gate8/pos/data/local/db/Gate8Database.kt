package br.com.gate8.pos.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import br.com.gate8.pos.data.local.dao.CashlessAccountDao
import br.com.gate8.pos.data.local.dao.CashlessMovementDao
import br.com.gate8.pos.data.local.dao.CatalogDao
import br.com.gate8.pos.data.local.dao.PendingSaleDao
import br.com.gate8.pos.data.local.entity.CashlessAccountEntity
import br.com.gate8.pos.data.local.entity.CashlessMovementEntity
import br.com.gate8.pos.data.local.entity.CatalogCacheEntity
import br.com.gate8.pos.data.local.entity.PendingSaleEntity

@Database(
    entities = [
        CatalogCacheEntity::class,
        PendingSaleEntity::class,
        CashlessAccountEntity::class,
        CashlessMovementEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class Gate8Database : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun pendingSaleDao(): PendingSaleDao
    abstract fun cashlessAccountDao(): CashlessAccountDao
    abstract fun cashlessMovementDao(): CashlessMovementDao
}

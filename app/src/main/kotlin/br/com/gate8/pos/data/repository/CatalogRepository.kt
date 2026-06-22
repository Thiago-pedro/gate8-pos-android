package br.com.gate8.pos.data.repository

import br.com.gate8.pos.core.time.ServerClock
import br.com.gate8.pos.data.local.dao.CatalogDao
import br.com.gate8.pos.data.local.entity.CatalogCacheEntity
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import kotlinx.serialization.json.Json

class CatalogRepository(
    private val api: PosApiService,
    private val catalogDao: CatalogDao,
    private val json: Json,
    private val serverClock: ServerClock,
    private val configStore: DeviceConfigStore,
) {
    suspend fun fetchAndCache(): CatalogResponseDto {
        val catalog = api.getCatalog()
        serverClock.updateFromServerTime(catalog.serverTime)
        // Mantém estabelecimento e nome do dispositivo sincronizados a cada atualização
        // do catálogo (antes só atualizavam no login).
        catalog.merchantName?.takeIf { it.isNotBlank() }?.let { configStore.setMerchantName(it) }
        catalog.device.name.takeIf { it.isNotBlank() }?.let { configStore.setDeviceName(it) }
        catalogDao.upsert(
            CatalogCacheEntity(
                json = json.encodeToString(CatalogResponseDto.serializer(), catalog),
                serverTime = catalog.serverTime,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        return catalog
    }

    suspend fun getCached(): CatalogResponseDto? {
        val row = catalogDao.get() ?: return null
        return runCatching {
            json.decodeFromString(CatalogResponseDto.serializer(), row.json)
        }.getOrNull()
    }
}

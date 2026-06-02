package br.com.gate8.pos.ui.config

import androidx.lifecycle.ViewModel
import br.com.gate8.pos.data.prefs.DeviceConfigStore

class SetupViewModel(
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    fun loadBaseUrl(): String = configStore.getBaseUrl() ?: "https://gate8.club"
    fun loadToken(): String = configStore.getDeviceToken() ?: ""
    fun loadOperator(): String = configStore.getOperatorName()
    fun loadShortId(): String = configStore.getDeviceShortId()

    fun save(baseUrl: String, token: String, operator: String, shortId: String) {
        configStore.setBaseUrl(baseUrl)
        configStore.setDeviceToken(token)
        configStore.setOperatorName(operator)
        configStore.setDeviceShortId(shortId)
    }

    fun isConfigured(): Boolean = configStore.isConfigured()
}

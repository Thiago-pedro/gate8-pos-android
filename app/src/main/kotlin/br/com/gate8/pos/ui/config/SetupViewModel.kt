package br.com.gate8.pos.ui.config

import androidx.lifecycle.ViewModel
import br.com.gate8.pos.data.prefs.DeviceConfigStore

class SetupViewModel(
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    fun loadOperator(): String = configStore.getOperatorName()
    fun loadProducerName(): String? = configStore.getProducerName()
    fun loadDeviceName(): String? = configStore.getDeviceName()

    fun saveOperator(name: String) {
        configStore.setOperatorName(name)
    }

    fun logout() {
        configStore.logout()
    }

    fun isLoggedIn(): Boolean = configStore.isLoggedIn()
}

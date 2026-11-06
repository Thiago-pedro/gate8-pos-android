package br.com.gate8.pos.ui.kitchen

import android.content.Context
import androidx.lifecycle.ViewModel
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.kitchen.KitchenRuntime
import br.com.gate8.pos.kitchen.KitchenStation

class KitchenViewModel(
    private val appContext: Context,
    private val station: KitchenStation,
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    val state = station.state

    init {
        KitchenRuntime.start(appContext)
    }

    fun onScreenVisible() {
        KitchenRuntime.start(appContext)
        station.onScreenVisible()
    }

    fun isKitchenEnabled(): Boolean = configStore.isKitchenMode()

    fun exitKitchen() {
        configStore.setKitchenMode(false)
        station.requestStop()
        KitchenRuntime.stop(appContext)
    }
}

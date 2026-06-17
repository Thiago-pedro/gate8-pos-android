package br.com.gate8.pos.stone

import android.app.Activity
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/** Activity atual para providers Stone (PosTransactionProvider exige Activity). */
class StoneActivityHolder {
    private var activityRef: WeakReference<Activity>? = null
    private val _pixQrCode = MutableStateFlow<Bitmap?>(null)
    val pixQrCode: StateFlow<Bitmap?> = _pixQrCode.asStateFlow()

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    fun requireActivity(): Activity =
        activityRef?.get() ?: error("Tela indisponível para operação Stone. Reabra o app.")

    fun hasActivity(): Boolean = activityRef?.get() != null

    fun onPixQrCodeWaiting(bitmap: Bitmap?) {
        _pixQrCode.value = bitmap
    }

    fun clearPixQrCode() {
        _pixQrCode.value = null
    }
}

package br.com.gate8.pos.stone

import android.app.Activity
import java.lang.ref.WeakReference

/** Activity atual para providers Stone (PosTransactionProvider exige Activity). */
class StoneActivityHolder {
    private var activityRef: WeakReference<Activity>? = null

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
}

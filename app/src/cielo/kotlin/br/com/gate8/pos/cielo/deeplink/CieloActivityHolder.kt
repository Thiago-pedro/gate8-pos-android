package br.com.gate8.pos.cielo.deeplink

import android.app.Activity
import java.lang.ref.WeakReference

/** Guarda a Activity em foreground para abrir deep links Cielo. */
object CieloActivityHolder {
    @Volatile
    private var current: WeakReference<Activity>? = null

    fun set(activity: Activity?) {
        current = activity?.let { WeakReference(it) }
    }

    fun get(): Activity? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}

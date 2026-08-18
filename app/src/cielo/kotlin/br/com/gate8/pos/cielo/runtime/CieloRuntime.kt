package br.com.gate8.pos.cielo.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import br.com.gate8.pos.cielo.deeplink.CieloResponseActivity
import br.com.gate8.pos.payment.PaymentRuntime

class CieloRuntime(
    private val app: Application,
) : PaymentRuntime {
    override fun onApplicationStart() {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    // Callback Cielo é translúcido e some rápido — não usar como host do deep link.
                    if (activity is CieloResponseActivity) return
                    CieloActivityHolder.set(activity)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (CieloActivityHolder.get() === activity) {
                        CieloActivityHolder.set(null)
                    }
                }

                override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            },
        )
    }
}

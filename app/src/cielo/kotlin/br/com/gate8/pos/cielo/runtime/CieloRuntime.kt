package br.com.gate8.pos.cielo.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import br.com.gate8.pos.cielo.deeplink.CieloActivityHolder
import br.com.gate8.pos.payment.PaymentRuntime

class CieloRuntime(
    private val app: Application,
) : PaymentRuntime {
    override fun onApplicationStart() {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    CieloActivityHolder.set(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (CieloActivityHolder.get() === activity) {
                        CieloActivityHolder.set(null)
                    }
                }

                override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            },
        )
    }
}

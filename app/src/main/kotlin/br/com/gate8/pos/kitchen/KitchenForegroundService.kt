package br.com.gate8.pos.kitchen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import br.com.gate8.pos.R
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.ui.MainActivity
import org.koin.core.context.GlobalContext

/**
 * Mantém a busca da cozinha com a tela apagada ou o app em segundo plano.
 */
class KitchenForegroundService : Service() {
    private val station: KitchenStation by lazy { GlobalContext.get().get() }
    private val configStore: DeviceConfigStore by lazy { GlobalContext.get().get() }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        acquireWakeLock()
        station.setStopListener { stopSelf() }
        if (!configStore.isLoggedIn() || !configStore.isKitchenMode()) {
            stopSelf()
            return START_NOT_STICKY
        }
        station.start()
        return START_STICKY
    }

    override fun onDestroy() {
        station.setStopListener(null)
        station.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Modo cozinha",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Busca e imprime pedidos da cozinha"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        try {
            promote(buildNotification(R.drawable.ic_stat_kitchen))
        } catch (e: Exception) {
            Log.e(TAG, "Notificação da cozinha falhou, usando ícone do sistema", e)
            promote(buildNotification(android.R.drawable.stat_notify_sync))
        }
    }

    private fun buildNotification(icon: Int): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Modo cozinha")
            .setContentText("Ouvindo pedidos para imprimir")
            .setSmallIcon(icon)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun promote(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        try {
            val power = getSystemService(POWER_SERVICE) as PowerManager
            val lock = wakeLock ?: power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "gate8:kitchen-poll",
            ).also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            if (!lock.isHeld) lock.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "Wake lock da cozinha não foi adquirido", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock da cozinha não foi solto", e)
        }
        wakeLock = null
    }

    private companion object {
        const val TAG = "Gate8Kitchen"
        const val CHANNEL_ID = "gate8_kitchen"
        const val NOTIFICATION_ID = 8108
    }
}

object KitchenRuntime {
    fun start(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, KitchenForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Serviço da cozinha não iniciou — a busca segue no processo", e)
            runCatching { GlobalContext.get().get<KitchenStation>().start() }
                .onFailure { Log.e(TAG, "Busca da cozinha não iniciou", it) }
        }
    }

    fun stop(context: Context) {
        runCatching {
            context.applicationContext.stopService(
                Intent(context.applicationContext, KitchenForegroundService::class.java),
            )
        }.onFailure { Log.w(TAG, "Serviço da cozinha não parou", it) }
    }

    private const val TAG = "Gate8Kitchen"
}

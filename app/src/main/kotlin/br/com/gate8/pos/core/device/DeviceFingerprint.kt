package br.com.gate8.pos.core.device

import android.content.Context
import android.provider.Settings
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import java.util.UUID

object DeviceFingerprint {
    private const val MIN_LENGTH = 8

    fun getOrCreate(context: Context, store: DeviceConfigStore): String {
        store.getFingerprint()?.let { existing ->
            if (existing.length >= MIN_LENGTH) return existing
        }
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val fingerprint = when {
            !androidId.isNullOrBlank() && androidId != "9774d56d682e549c" -> "aid-$androidId"
            else -> "uuid-${UUID.randomUUID()}"
        }
        store.setFingerprint(fingerprint)
        return fingerprint
    }
}

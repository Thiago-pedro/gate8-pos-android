package br.com.gate8.pos.core.device

import android.content.Context
import android.provider.Settings
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.device.PosHardwareInfo
import java.util.UUID

object DeviceFingerprint {
    private const val MIN_LENGTH = 8

    fun getOrCreate(
        context: Context,
        store: DeviceConfigStore,
        hardware: PosHardwareInfo? = null,
    ): String {
        store.getFingerprint()?.let { existing ->
            if (existing.length >= MIN_LENGTH) return existing
        }
        // Mantemos o serial/ID do hardware como prefixo (ajuda o painel a correlacionar
        // a mesma máquina física) e adicionamos um sufixo aleatório por registro.
        // Assim, ao sair/trocar produtor o fingerprint é zerado e o próximo login
        // entra como um dispositivo NOVO, que precisa ser liberado no painel.
        val suffix = UUID.randomUUID().toString().take(8)
        val stoneSerial = hardware?.readTerminal()?.serialNumber
        val base = when {
            !stoneSerial.isNullOrBlank() -> "stone-$stoneSerial"
            else -> {
                val androidId = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID,
                )
                if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                    "aid-$androidId"
                } else {
                    "uuid"
                }
            }
        }
        val fingerprint = "$base-$suffix"
        store.setFingerprint(fingerprint)
        return fingerprint
    }
}
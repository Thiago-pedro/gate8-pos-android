package br.com.gate8.pos.stone.device

import br.com.gate8.pos.device.PosHardwareInfo
import br.com.gate8.pos.device.PosTerminalInfo
import stone.utils.Stone

class PosHardwareInfoLive : PosHardwareInfo {
    override fun readTerminal(): PosTerminalInfo =
        runCatching {
            val device = Stone.getPosAndroidDevice() ?: return PosTerminalInfo(null, null)
            PosTerminalInfo(
                serialNumber = device.posAndroidSerialNumber?.trim()?.takeIf { it.isNotEmpty() },
                manufacturer = device.posAndroidManufacturer?.trim()?.takeIf { it.isNotEmpty() },
            )
        }.getOrDefault(PosTerminalInfo(null, null))
}

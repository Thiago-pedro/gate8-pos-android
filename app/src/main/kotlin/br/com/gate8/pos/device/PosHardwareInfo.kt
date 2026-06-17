package br.com.gate8.pos.device

data class PosTerminalInfo(
    val serialNumber: String?,
    val manufacturer: String?,
) {
    val isPresent: Boolean =
        !serialNumber.isNullOrBlank() || !manufacturer.isNullOrBlank()
}

interface PosHardwareInfo {
    fun readTerminal(): PosTerminalInfo
}

class PosHardwareInfoUnavailable : PosHardwareInfo {
    override fun readTerminal(): PosTerminalInfo = PosTerminalInfo(null, null)
}

package br.com.gate8.pos.stone.settings

import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.stone.sdk.StoneActivationOutcome
import br.com.gate8.pos.stone.sdk.StoneSdkBridge
import br.com.gate8.pos.stone.sdk.StoneSdkNotLinkedException

class StoneSettingsGatewayImpl(
    private val bridge: StoneSdkBridge,
    private val config: DeviceConfigStore,
) : StoneSettingsGateway {
    override val showStoneSection: Boolean = true

    override fun getSavedStoneCode(): String = config.getStoneCode().orEmpty()

    override fun pixCredentialsConfigured(): Boolean =
        BuildConfig.STONE_PIX_QR_AUTHORIZATION.isNotBlank() &&
            BuildConfig.STONE_PIX_QR_PROVIDERID.isNotBlank()

    override fun posActiveStoneCodesHint(): String? {
        if (!bridge.isLinked) return null
        val codes = bridge.knownActiveStoneCodes()
        if (codes.isEmpty()) return null
        val saved = getSavedStoneCode()
        if (saved.isNotBlank() && codes.any { it.equals(saved, ignoreCase = true) }) return null
        return "StoneCode(s) no POS: ${codes.joinToString()}"
    }

    override suspend fun saveAndActivate(stoneCode: String): Result<String> {
        val code = stoneCode.trim()
        if (code.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o StoneCode do terminal"))
        }
        if (!bridge.isLinked) {
            return Result.failure(StoneSdkNotLinkedException())
        }
        config.setStoneCode(code)
        return bridge.ensureActivated(code).map { outcome ->
            when (outcome) {
                StoneActivationOutcome.ALREADY_ACTIVE ->
                    "StoneCode já ativo no terminal — pode transacionar."
                StoneActivationOutcome.NEWLY_ACTIVATED ->
                    if (pixCredentialsConfigured()) {
                        "StoneCode ativado com sucesso."
                    } else {
                        "StoneCode ativado. PIX: configure stonePixQrAuthorization e " +
                            "stonePixQrProviderId em local.properties e recompile o app."
                    }
            }
        }
    }
}

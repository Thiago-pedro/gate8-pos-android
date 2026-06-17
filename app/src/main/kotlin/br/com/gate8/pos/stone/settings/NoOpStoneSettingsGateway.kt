package br.com.gate8.pos.stone.settings

class NoOpStoneSettingsGateway : StoneSettingsGateway {
    override val showStoneSection: Boolean = false

    override fun getSavedStoneCode(): String = ""

    override fun pixCredentialsConfigured(): Boolean = false

    override fun posActiveStoneCodesHint(): String? = null

    override suspend fun saveAndActivate(stoneCode: String): Result<String> =
        Result.failure(UnsupportedOperationException())
}

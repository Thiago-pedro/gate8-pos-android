package br.com.gate8.pos.stone.settings

interface StoneSettingsGateway {
    val showStoneSection: Boolean

    fun getSavedStoneCode(): String

    fun pixCredentialsConfigured(): Boolean

    /** Códigos já ativos no POS (compartilhado com apps Stone). */
    fun posActiveStoneCodesHint(): String?

    suspend fun saveAndActivate(stoneCode: String): Result<String>
}

package br.com.gate8.pos.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.device.DeviceFingerprint
import br.com.gate8.pos.device.PosHardwareInfo
import br.com.gate8.pos.core.util.ProducerTokenValidator
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.LoginRepository
import br.com.gate8.pos.domain.model.LoginResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class LoginUiState(
    val loading: Boolean = false,
    val producerToken: String = "",
    val label: String = "",
    val error: String? = null,
    val pendingDeviceName: String? = null,
    val disabledDeviceName: String? = null,
)

sealed class LoginNavigation {
    data object Home : LoginNavigation()
    data object Pending : LoginNavigation()
}

class LoginViewModel(
    application: Application,
    private val configStore: DeviceConfigStore,
    private val loginRepository: LoginRepository,
    private val hardwareInfo: PosHardwareInfo,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<LoginNavigation>(extraBufferCapacity = 1)
    val navigation: SharedFlow<LoginNavigation> = _navigation.asSharedFlow()

    init {
        configStore.ensureDefaultBaseUrl()
        DeviceFingerprint.getOrCreate(application, configStore, hardwareInfo)
        val savedToken = configStore.getProducerToken()
        val savedLabel = configStore.getDeviceName() ?: ""
        _state.update {
            it.copy(
                producerToken = savedToken ?: "",
                label = savedLabel,
            )
        }
    }

    fun onProducerTokenChange(value: String) {
        _state.update { it.copy(producerToken = ProducerTokenValidator.normalize(value), error = null) }
    }

    fun onLabelChange(value: String) {
        _state.update { it.copy(label = value.take(80), error = null) }
    }

    fun login() {
        val token = _state.value.producerToken
        if (!ProducerTokenValidator.isValid(token)) {
            _state.update { it.copy(error = "Token inválido — use 6 caracteres (A-Z, 2-9)") }
            return
        }
        performLogin(token, _state.value.label)
    }

    fun retryPending() {
        val token = configStore.getProducerToken()
        if (token.isNullOrBlank() || !ProducerTokenValidator.isValid(token)) {
            _state.update { it.copy(error = "Token do produtor não encontrado. Faça login novamente.") }
            return
        }
        performLogin(token, configStore.getDeviceName())
    }

    private fun performLogin(token: String, label: String?) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, pendingDeviceName = null, disabledDeviceName = null) }
            val fingerprint = DeviceFingerprint.getOrCreate(getApplication(), configStore, hardwareInfo)
            configStore.setProducerToken(token)

            runCatching {
                loginRepository.login(
                    producerToken = token,
                    fingerprint = fingerprint,
                    label = label,
                )
            }.onSuccess { result ->
                handleLoginResult(result, token)
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = friendlyConnectionError(e)) }
            }
        }
    }

    private fun handleLoginResult(result: LoginResult, token: String) {
        when (result) {
            is LoginResult.Active -> {
                configStore.setDeviceToken(result.deviceToken)
                configStore.setDeviceId(result.deviceId)
                configStore.setDeviceName(result.deviceName)
                configStore.setProducerName(result.producerName)
                configStore.setMerchantName(result.merchantName)
                configStore.setProducerToken(token)
                _state.update { it.copy(loading = false) }
                _navigation.tryEmit(LoginNavigation.Home)
            }
            is LoginResult.Pending -> {
                _state.update {
                    it.copy(loading = false, pendingDeviceName = result.deviceName)
                }
                _navigation.tryEmit(LoginNavigation.Pending)
            }
            is LoginResult.Disabled -> {
                _state.update {
                    it.copy(
                        loading = false,
                        disabledDeviceName = result.deviceName,
                        error = "Maquininha bloqueada pelo produtor.",
                    )
                }
            }
            is LoginResult.Error -> {
                _state.update {
                    it.copy(loading = false, error = errorMessage(result.code))
                }
            }
        }
    }

    private fun friendlyConnectionError(e: Throwable): String {
        val msg = e.message.orEmpty()
        val root = generateSequence(e) { it.cause }.last()
        return when {
            root is UnknownHostException ||
                msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Sem conexão com o servidor. Verifique a internet do aparelho."
            root is ConnectException ||
                msg.contains("Failed to connect", ignoreCase = true) ->
                "Não foi possível conectar ao servidor. Tente novamente."
            root is SocketTimeoutException ||
                msg.contains("timeout", ignoreCase = true) ->
                "Tempo esgotado. Verifique a conexão e tente novamente."
            root is IOException ->
                "Falha de conexão. Verifique a rede e tente novamente."
            msg.isNotBlank() -> msg
            else -> "Falha de conexão. Tente novamente."
        }
    }

    private fun errorMessage(code: String): String = when (code) {
        "invalid_token" -> "Token do produtor inválido."
        "invalid_token_format" -> "Formato do token inválido (6 caracteres)."
        "invalid_fingerprint" -> "Identificador do aparelho inválido."
        "function gen_random_bytes(integer) does not exist" ->
            "Erro no servidor (banco). Peça ao Lovable habilitar extensão pgcrypto no Supabase."
        else -> if (code.length > 80) "Erro no servidor: ${code.take(80)}…" else "Erro: $code"
    }
}

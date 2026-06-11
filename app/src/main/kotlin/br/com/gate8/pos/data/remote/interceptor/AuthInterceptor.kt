package br.com.gate8.pos.data.remote.interceptor

import br.com.gate8.pos.core.session.SessionEvents
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val configStore: DeviceConfigStore,
    private val sessionEvents: SessionEvents,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = configStore.getDeviceToken()
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()
        val response = chain.proceed(request)
        val path = request.url.encodedPath
        if (!path.contains("/login") && (response.code == 401 || response.code == 403)) {
            configStore.clearSession()
            sessionEvents.notifyUnauthorized()
        }
        return response
    }
}

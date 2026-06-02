package br.com.gate8.pos.data.remote.interceptor

import br.com.gate8.pos.data.prefs.DeviceConfigStore
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val configStore: DeviceConfigStore,
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
        return chain.proceed(request)
    }
}

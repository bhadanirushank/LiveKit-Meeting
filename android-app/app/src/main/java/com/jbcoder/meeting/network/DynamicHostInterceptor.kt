package com.jbcoder.meeting.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class DynamicHostInterceptor @Inject constructor(
    private val devConfigManager: DevConfigManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val customIp = devConfigManager.getCustomIp()
        
        if (!customIp.isNullOrBlank()) {
            try {
                val newUrl = request.url.newBuilder()
                    .host(customIp)
                    .build()
                request = request.newBuilder()
                    .url(newUrl)
                    .build()
            } catch (e: Exception) {
                // If the user typed an invalid host, just proceed with the original request
            }
        }
        
        return chain.proceed(request)
    }
}

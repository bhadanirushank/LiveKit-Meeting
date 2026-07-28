package com.jbcoder.meeting.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionCoordinator: Lazy<SessionCoordinator>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val urlPath = originalRequest.url.encodedPath

        // 1. Bootstrap: Never attach any token
        if (urlPath.endsWith("/api/v1/session/bootstrap")) {
            return chain.proceed(originalRequest)
        }

        // 2. Refresh: Attach rtk_ (Refresh Token) only
        if (urlPath.endsWith("/api/v1/session/refresh")) {
            val rtk = runBlocking { sessionCoordinator.get().getRefreshToken() }
            if (rtk != null) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $rtk")
                    .build()
                return chain.proceed(newRequest)
            }
            return chain.proceed(originalRequest)
        }

        // 3. Other protected endpoints: Attach atk_ (Access Token)
        val atk = runBlocking { sessionCoordinator.get().getAccessToken() }
        if (atk != null) {
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $atk")
                .build()
            return chain.proceed(newRequest)
        }

        return chain.proceed(originalRequest)
    }
}

package com.jbcoder.meeting.network

import com.jbcoder.meeting.data.meeting.HostSessionStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

@Singleton
class HostAuthInterceptor @Inject constructor(
    private val hostSessionStore: Lazy<HostSessionStore>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val credential = hostSessionStore.get().hostCredential.value
        if (credential != null) {
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $credential")
                .build()
            return chain.proceed(newRequest)
        }
        
        return chain.proceed(originalRequest)
    }
}

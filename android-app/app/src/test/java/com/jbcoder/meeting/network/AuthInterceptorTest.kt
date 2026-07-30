package com.jbcoder.meeting.network

import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AuthInterceptorTest {

    @Test
    fun `bootstrap should not attach token`() {
        val mockCoordinator = mock<SessionCoordinator>()
        val interceptor = AuthInterceptor { mockCoordinator }
        
        val request = Request.Builder().url("https://api.example.com/api/v1/session/bootstrap").build()
        val chain = mock<Interceptor.Chain> {
            on { request() } doReturn request
            on { proceed(any()) } doReturn Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        val response = interceptor.intercept(chain)
        assertNull(response.request.header("Authorization"))
    }

    @Test
    fun `refresh should attach rtk`() = runBlocking {
        val mockCoordinator = mock<SessionCoordinator> {
            onBlocking { getRefreshToken() } doReturn "refresh-token"
        }
        val interceptor = AuthInterceptor { mockCoordinator }

        val request = Request.Builder().url("https://api.example.com/api/v1/session/refresh").build()
        var capturedRequest: Request? = null
        val chain = mock<Interceptor.Chain> {
            on { request() } doReturn request
            on { proceed(any()) }.thenAnswer {
                capturedRequest = it.arguments[0] as Request
                Response.Builder()
                    .request(capturedRequest!!)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build()
            }
        }

        interceptor.intercept(chain)
        assertEquals("Bearer refresh-token", capturedRequest?.header("Authorization"))
    }

    @Test
    fun `other endpoints should attach atk`() = runBlocking {
        val mockCoordinator = mock<SessionCoordinator> {
            onBlocking { getAccessToken() } doReturn "access-token"
        }
        val interceptor = AuthInterceptor { mockCoordinator }

        val request = Request.Builder().url("https://api.example.com/api/v1/meetings/xyz123").build()
        var capturedRequest: Request? = null
        val chain = mock<Interceptor.Chain> {
            on { request() } doReturn request
            on { proceed(any()) }.thenAnswer {
                capturedRequest = it.arguments[0] as Request
                Response.Builder()
                    .request(capturedRequest!!)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build()
            }
        }

        interceptor.intercept(chain)
        assertEquals("Bearer access-token", capturedRequest?.header("Authorization"))
    }
}

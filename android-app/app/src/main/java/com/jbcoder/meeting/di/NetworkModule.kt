package com.jbcoder.meeting.di

import com.jbcoder.meeting.BuildConfig
import com.jbcoder.meeting.network.AuthInterceptor
import com.jbcoder.meeting.network.MeetingApiService
import com.jbcoder.meeting.network.SessionCoordinator
import com.jbcoder.meeting.network.DynamicHostInterceptor
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        dynamicHostInterceptor: DynamicHostInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                // Redact headers explicitly
                redactHeader("Authorization")
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(dynamicHostInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideMeetingApiService(
        retrofit: Retrofit
    ): MeetingApiService {
        return retrofit.create(MeetingApiService::class.java)
    }

    @Provides
    @Singleton
    @javax.inject.Named("Unauthenticated")
    fun provideUnauthenticatedOkHttpClient(
        dynamicHostInterceptor: DynamicHostInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(dynamicHostInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @javax.inject.Named("Unauthenticated")
    fun provideUnauthenticatedRetrofit(
        @javax.inject.Named("Unauthenticated") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideUnauthenticatedMeetingApiService(
        @javax.inject.Named("Unauthenticated") retrofit: Retrofit
    ): com.jbcoder.meeting.network.UnauthenticatedMeetingApiService {
        return retrofit.create(com.jbcoder.meeting.network.UnauthenticatedMeetingApiService::class.java)
    }

    @Provides
    @Singleton
    @javax.inject.Named("HostAuthenticated")
    fun provideHostOkHttpClient(
        hostAuthInterceptor: com.jbcoder.meeting.network.HostAuthInterceptor,
        dynamicHostInterceptor: DynamicHostInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                redactHeader("Authorization")
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(dynamicHostInterceptor)
            .addInterceptor(hostAuthInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @javax.inject.Named("HostAuthenticated")
    fun provideHostRetrofit(
        @javax.inject.Named("HostAuthenticated") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideHostMeetingApiService(
        @javax.inject.Named("HostAuthenticated") retrofit: Retrofit
    ): com.jbcoder.meeting.network.HostMeetingApiService {
        return retrofit.create(com.jbcoder.meeting.network.HostMeetingApiService::class.java)
    }
}

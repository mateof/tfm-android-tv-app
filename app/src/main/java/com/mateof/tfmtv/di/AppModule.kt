package com.mateof.tfmtv.di

import com.mateof.tfmtv.data.api.AuthApi
import com.mateof.tfmtv.data.api.ChannelsApi
import com.mateof.tfmtv.data.api.FilesApi
import com.mateof.tfmtv.data.api.SystemApi
import com.mateof.tfmtv.data.net.ApiKeyInterceptor
import com.mateof.tfmtv.data.net.HostSelectionInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        hostSelection: HostSelectionInterceptor,
        apiKey: ApiKeyInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(hostSelection)
        .addInterceptor(apiKey)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Placeholder; HostSelectionInterceptor rewrites every request
        // to the configured server.
        .baseUrl("http://placeholder.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun provideSystemApi(r: Retrofit): SystemApi = r.create(SystemApi::class.java)
    @Provides @Singleton fun provideAuthApi(r: Retrofit): AuthApi = r.create(AuthApi::class.java)
    @Provides @Singleton fun provideChannelsApi(r: Retrofit): ChannelsApi = r.create(ChannelsApi::class.java)
    @Provides @Singleton fun provideFilesApi(r: Retrofit): FilesApi = r.create(FilesApi::class.java)
}

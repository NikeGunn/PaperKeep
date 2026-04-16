package com.scanvault.core.network.di

import android.content.Context
import com.scanvault.core.network.BuildConfig
import com.scanvault.core.network.auth.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * The API base URL. Injected from BuildConfig (sourced from api_base_url.properties).
     * Format: https://<id>.execute-api.ap-south-1.amazonaws.com/v1
     */
    @Provides
    @Named("api_base_url")
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    /**
     * Ktor HttpClient configured with:
     * - OkHttp engine (HTTP/2 by default in OkHttp 4+)
     * - Certificate pinning against AWS API Gateway ACM cert
     * - Auth plugin: injects Bearer token, auto-refreshes on 401 (stubbed refresh for now)
     * - JSON content negotiation (kotlinx.serialization)
     * - 30s request timeout
     * - Debug logging in non-release builds
     */
    @Provides
    @Singleton
    fun provideHttpClient(
        tokenStore: TokenStore,
        @ApplicationContext context: Context,
    ): HttpClient {
        val apiHost = BuildConfig.TLS_PIN_HOST
        val pin = BuildConfig.TLS_PIN_SHA256

        val certificatePinner = CertificatePinner.Builder()
            .add(apiHost, "sha256/$pin")
            .build()

        return HttpClient(OkHttp) {
            engine {
                config {
                    certificatePinner(certificatePinner)
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(Auth) {
                bearer {
                    loadTokens {
                        val access = tokenStore.accessToken ?: return@loadTokens null
                        BearerTokens(access, tokenStore.refreshToken ?: "")
                    }
                    refreshTokens {
                        // Token refresh is a no-op in Phase 4B — the server issues
                        // long-lived tokens. Full PKCE refresh added in Phase 5.
                        val existing = tokenStore.accessToken ?: return@refreshTokens null
                        BearerTokens(existing, tokenStore.refreshToken ?: "")
                    }
                    sendWithoutRequest { request ->
                        // Only send auth header to our own API — never to pre-signed S3 URLs
                        request.url.host == BuildConfig.TLS_PIN_HOST
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            android.util.Log.d("ScanVaultHttp", message)
                        }
                    }
                    level = LogLevel.INFO
                }
            }
        }
    }
}

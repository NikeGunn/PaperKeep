package com.scanvault.feature.account

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class KtorAccountRepository @Inject constructor(
    private val httpClient: HttpClient,
    @Named("api_base_url") private val baseUrl: String,
) : AccountRepository {

    override suspend fun createAccount(request: CreateAccountRequest): Result<SessionResponse> =
        runCatching {
            val response = httpClient.post("$baseUrl/accounts") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                val error = response.body<ApiError>()
                error("HTTP ${response.status.value}: ${error.message}")
            }
            response.body<SessionResponse>()
        }

    override suspend fun login(request: LoginRequest): Result<SessionResponse> =
        runCatching {
            val response = httpClient.post("$baseUrl/sessions") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                val error = response.body<ApiError>()
                error("HTTP ${response.status.value}: ${error.message}")
            }
            response.body<SessionResponse>()
        }
}

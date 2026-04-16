package com.scanvault.feature.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Request bodies ----------

@Serializable
data class CreateAccountRequest(
    val email: String,
    @SerialName("auth_hash") val authHash: String,
    @SerialName("auth_params") val authParams: Map<String, String>,
    @SerialName("wrapped_key") val wrappedKey: String,
    @SerialName("kdf_salt") val kdfSalt: String,
    @SerialName("kdf_params") val kdfParams: Map<String, String>,
)

@Serializable
data class LoginRequest(
    val email: String,
    @SerialName("auth_hash") val authHash: String,
    @SerialName("auth_params") val authParams: Map<String, String>,
)

// ---------- Response bodies ----------

@Serializable
data class SessionResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class ApiError(val message: String)

// ---------- Repository interface (mockable) ----------

interface AccountRepository {
    /** POST /accounts — create a new account. Returns the first session token. */
    suspend fun createAccount(request: CreateAccountRequest): Result<SessionResponse>

    /** POST /sessions — authenticate and get a session token. */
    suspend fun login(request: LoginRequest): Result<SessionResponse>
}

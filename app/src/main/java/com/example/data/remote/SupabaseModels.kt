package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseMessageDto(
    @Json(name = "id") val id: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "recipient_id") val recipientId: String,
    @Json(name = "ciphertext") val ciphertext: String,
    @Json(name = "iv") val iv: String,
    @Json(name = "media_ciphertext") val mediaCiphertext: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseProfileDto(
    @Json(name = "id") val id: String,
    @Json(name = "username") val username: String,
    @Json(name = "email") val email: String,
    @Json(name = "public_key") val publicKey: String,
    @Json(name = "avatar_url") val avatarUrl: String? = "",
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "user") val user: SupabaseUserObject? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseUserObject(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String? = null
)

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
    @Json(name = "name") val name: String,
    @Json(name = "email") val email: String? = null,
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

@JsonClass(generateAdapter = true)
data class UsernameLoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class UsernameLoginResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "user") val user: UsernameLoginUser,
    @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class UsernameLoginUser(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String
)

@JsonClass(generateAdapter = true)
data class SignUpRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "data") val data: SignUpMetadata
)

@JsonClass(generateAdapter = true)
data class SignUpMetadata(
    @Json(name = "username") val username: String,
    @Json(name = "name") val name: String,
    @Json(name = "age") val age: Int,
    @Json(name = "public_key") val publicKey: String
)

@JsonClass(generateAdapter = true)
data class SearchProfilesRequest(
    @Json(name = "query") val query: String
)

@JsonClass(generateAdapter = true)
data class SearchProfilesResponse(
    @Json(name = "profiles") val profiles: List<SupabaseProfileDto>
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "grant_type") val grantType: String = "refresh_token"
)

@JsonClass(generateAdapter = true)
data class RefreshTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "user") val user: UsernameLoginUser
)

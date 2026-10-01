package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SupabaseRestService(
    private val config: SupabaseConfig,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val messageAdapter = moshi.adapter(SupabaseMessageDto::class.java)
    private val messageListType = Types.newParameterizedType(List::class.java, SupabaseMessageDto::class.java)
    private val messageListAdapter = moshi.adapter<List<SupabaseMessageDto>>(messageListType)

    private val profileAdapter = moshi.adapter(SupabaseProfileDto::class.java)
    private val profileListType = Types.newParameterizedType(List::class.java, SupabaseProfileDto::class.java)
    private val profileListAdapter = moshi.adapter<List<SupabaseProfileDto>>(profileListType)

    private val authReqAdapter = moshi.adapter(SupabaseAuthRequest::class.java)
    private val authRespAdapter = moshi.adapter(SupabaseAuthResponse::class.java)
    private val usernameLoginReqAdapter = moshi.adapter(UsernameLoginRequest::class.java)
    private val usernameLoginRespAdapter = moshi.adapter(UsernameLoginResponse::class.java)
    private val signUpReqAdapter = moshi.adapter(SignUpRequest::class.java)
    private val searchProfilesReqAdapter = moshi.adapter(SearchProfilesRequest::class.java)
    private val searchProfilesRespAdapter = moshi.adapter(SearchProfilesResponse::class.java)
    private val refreshTokenReqAdapter = moshi.adapter(RefreshTokenRequest::class.java)
    private val refreshTokenRespAdapter = moshi.adapter(RefreshTokenResponse::class.java)

    private fun buildRequest(path: String): Request.Builder {
        val baseUrl = config.currentUrl.removeSuffix("/")
        val anonKey = config.currentAnonKey
        return Request.Builder()
            .url("$baseUrl$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
    }

    private fun buildEdgeFunctionRequest(functionName: String): Request.Builder {
        val baseUrl = config.currentUrl.removeSuffix("/")
        val anonKey = config.currentAnonKey
        return Request.Builder()
            .url("$baseUrl/functions/v1/$functionName")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Content-Type", "application/json")
    }

    /**
     * Pings Supabase REST endpoint to verify configuration.
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!config.isCustomConfigured) return@withContext false
            val request = buildRequest("/rest/v1/").head().build()
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful || response.code == 401 || response.code == 404
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inserts an encrypted message into temporary Supabase storage.
     */
    suspend fun insertMessage(message: SupabaseMessageDto): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = messageAdapter.toJson(message)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildRequest("/rest/v1/messages")
                .header("Prefer", "return=minimal")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetches pending messages for this recipient.
     */
    suspend fun fetchPendingMessages(recipientId: String): List<SupabaseMessageDto> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/rest/v1/messages?recipient_id=eq.$recipientId&select=*&order=created_at.asc")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            messageListAdapter.fromJson(responseBody) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * ACK-based Deletion:
     * Immediately and permanently purges the message from Supabase after local receipt.
     */
    suspend fun acknowledgeAndDeleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/rest/v1/messages?id=eq.$messageId")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Purges messages that have exceeded their 7-day TTL fallback.
     */
    suspend fun deleteExpiredMessages(currentIsoTime: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/rest/v1/messages?expires_at=lt.$currentIsoTime")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Username-only login via Edge Function.
     * Sends username + password, Edge Function resolves email server-side and authenticates.
     */
    suspend fun loginWithUsername(username: String, password: String): UsernameLoginResponse? = withContext(Dispatchers.IO) {
        try {
            val req = UsernameLoginRequest(username = username.trim().lowercase(), password = password)
            val json = usernameLoginReqAdapter.toJson(req)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildEdgeFunctionRequest("login-with-username")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBody = response.body?.string() ?: return@withContext null
            usernameLoginRespAdapter.fromJson(responseBody)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Registers a new user with Supabase GoTrue Auth including metadata.
     * Metadata includes username, name, age, and public_key for profile creation.
     */
    suspend fun signUpWithMetadata(
        email: String,
        password: String,
        username: String,
        name: String,
        age: Int,
        publicKey: String
    ): Result<SupabaseAuthResponse> = withContext(Dispatchers.IO) {
        try {
            val metadata = SignUpMetadata(username = username, name = name, age = age, publicKey = publicKey)
            val req = SignUpRequest(email = email, password = password, data = metadata)
            val json = signUpReqAdapter.toJson(req)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildRequest("/auth/v1/signup")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = when {
                    responseBody.isNotEmpty() -> responseBody
                    else -> "HTTP ${response.code}: ${response.message}"
                }
                return@withContext Result.failure(Exception("Supabase signup failed: $errorMsg"))
            }

            val authResponse = authRespAdapter.fromJson(responseBody)
                ?: return@withContext Result.failure(Exception("Failed to parse signup response"))

            Result.success(authResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upserts user public profile (ID, username, email, public_key).
     */
    suspend fun upsertProfile(profile: SupabaseProfileDto): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = profileAdapter.toJson(profile)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildRequest("/rest/v1/profiles")
                .header("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Searches profiles by username via Edge Function.
     */
    suspend fun searchProfiles(query: String): List<SupabaseProfileDto> = withContext(Dispatchers.IO) {
        try {
            val req = SearchProfilesRequest(query = query.trim().lowercase())
            val json = searchProfilesReqAdapter.toJson(req)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildEdgeFunctionRequest("search-profiles")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseBody = response.body?.string() ?: return@withContext emptyList()
            val searchResponse = searchProfilesRespAdapter.fromJson(responseBody)
            return@withContext searchResponse?.profiles ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches a profile by user ID.
     */
    suspend fun getProfileById(userId: String): SupabaseProfileDto? = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/rest/v1/profiles?id=eq.$userId&select=id,username,name,public_key,created_at")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val profiles = profileListAdapter.fromJson(body) ?: return@withContext null
            return@withContext profiles.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Authenticates with Supabase GoTrue Auth (legacy email-based).
     * Kept for compatibility but not used in username-only flow.
     */
    @Deprecated("Use loginWithUsername instead", ReplaceWith("loginWithUsername(username, password)"))
    suspend fun signInWithPassword(req: SupabaseAuthRequest): SupabaseAuthResponse? = withContext(Dispatchers.IO) {
        try {
            val json = authReqAdapter.toJson(req)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildRequest("/auth/v1/token?grant_type=password")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val respBody = response.body?.string() ?: return@withContext null
            authRespAdapter.fromJson(respBody)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Registers a new user with Supabase GoTrue Auth (legacy email-based).
     * Kept for compatibility but not used in metadata-based flow.
     */
    @Deprecated("Use signUpWithMetadata instead", ReplaceWith("signUpWithMetadata(email, password, username, name, age, publicKey)"))
    suspend fun signUpWithPassword(req: SupabaseAuthRequest): SupabaseAuthResponse? = withContext(Dispatchers.IO) {
        try {
            val json = authReqAdapter.toJson(req)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildRequest("/auth/v1/signup")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val respBody = response.body?.string() ?: return@withContext null
            authRespAdapter.fromJson(respBody)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     */
    suspend fun refreshAccessToken(refreshToken: String): RefreshTokenResponse? = withContext(Dispatchers.IO) {
        try {
            val req = RefreshTokenRequest(refreshToken = refreshToken)
            val json = refreshTokenReqAdapter.toJson(req)
            val body = json.toRequestBody(jsonMediaType)
            val request = buildRequest("/auth/v1/token?grant_type=refresh_token")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val respBody = response.body?.string() ?: return@withContext null
            refreshTokenRespAdapter.fromJson(respBody)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Signs out the user (revokes the refresh token).
     */
    suspend fun signOut(accessToken: String, refreshToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/auth/v1/logout")
                .header("Authorization", "Bearer $accessToken")
                .post("{\"refresh_token\": \"$refreshToken\"}".toRequestBody(jsonMediaType))
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

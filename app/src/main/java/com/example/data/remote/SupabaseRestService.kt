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

    private fun buildRequest(path: String): Request.Builder {
        val baseUrl = config.currentUrl.removeSuffix("/")
        val anonKey = config.currentAnonKey
        return Request.Builder()
            .url("$baseUrl$path")
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
     * Searches profiles by username or email.
     */
    suspend fun searchProfiles(query: String): List<SupabaseProfileDto> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("/rest/v1/profiles?username=ilike.*$query*&select=*")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            profileListAdapter.fromJson(body) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Authenticates with Supabase GoTrue Auth.
     */
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
     * Registers a new user with Supabase GoTrue Auth.
     */
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
}

package com.example.data.remote

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class RealtimeStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    ERROR
}

class SupabaseRealtimeClient(
    private val config: SupabaseConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {

    private var currentAccessToken: String? = null

    fun setAccessToken(token: String?) {
        Log.d("QC_RT", "setAccessToken called: ${if (token != null) "present" else "null"}, currentStatus=${_status.value}")
        if (currentAccessToken != token) {
            currentAccessToken = token
            // Rejoin channel with new token if currently connected
            if (_status.value == RealtimeStatus.LISTENING || _status.value == RealtimeStatus.CONNECTED) {
                Log.d("QC_RT", "setAccessToken: rejoining channel due to token change")
                scope.launch {
                    joinMessagesChannel()
                }
            }
        }
    }

    // Callback for subscription confirmation
    private var subscriptionConfirmedCallback: (() -> Unit)? = null

    fun setSubscriptionConfirmedCallback(callback: (() -> Unit)?) {
        subscriptionConfirmedCallback = callback
    }

    private val TAG = "SupabaseRealtime"
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)

    private val _status = MutableStateFlow(RealtimeStatus.DISCONNECTED)
    val status: StateFlow<RealtimeStatus> = _status.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SupabaseMessageDto>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SupabaseMessageDto> = _incomingMessages.asSharedFlow()

    private var currentConversationId: String? = null

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun updateConversationFilter(conversationId: String?) {
        Log.d("QC_RT", "updateConversationFilter: conversationId=$conversationId, currentStatus=${_status.value}")
        currentConversationId = conversationId
        // Rejoin channel with new filter if connected
        if (_status.value == RealtimeStatus.LISTENING) {
            Log.d("QC_RT", "updateConversationFilter: rejoining channel")
            joinMessagesChannel()
        }
    }

    /**
     * Connects to Supabase Realtime WebSocket and subscribes to Postgres changes on messages table.
     * Uses the publishable key for the apikey query parameter.
     * Authentication is done via the Realtime access_token mechanism AFTER WebSocket connection.
     */
    fun connect() {
        Log.d("QC_RT", "connect() called, currentStatus=${_status.value}")
        if (_status.value == RealtimeStatus.CONNECTED || _status.value == RealtimeStatus.LISTENING) {
            Log.d("QC_RT", "connect: already connected/listening, returning")
            return
        }

        val rawUrl = config.currentUrl
        if (rawUrl.isEmpty() || !rawUrl.startsWith("http")) {
            Log.d("QC_RT", "connect: no valid URL, treating as sandbox")
            _status.value = RealtimeStatus.CONNECTED // Local sandbox active
            return
        }

        // Use the publishable key for the apikey query parameter
        // DO NOT send Authorization header during WebSocket upgrade - causes 401
        // Auth is done via phoenix access_token event after connection
        val wsUrl = rawUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .removeSuffix("/") + "/realtime/v1/websocket?apikey=${config.currentAnonKey}&vsn=1.0.0"

        Log.d("QC_RT", "Connecting to realtime: $wsUrl, hasAccessToken=${currentAccessToken != null}")
        _status.value = RealtimeStatus.CONNECTING

        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("QC_RT", "WebSocket opened: responseCode=${response.code}")
                _status.value = RealtimeStatus.CONNECTED
                startHeartbeat()
                joinMessagesChannel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("QC_RT", "WebSocket message received: len=${text.length}")
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("QC_RT", "WebSocket closing: code=$code, reason=$reason")
                _status.value = RealtimeStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("QC_RT", "WebSocket failure: ${t.message}", t)
                _status.value = RealtimeStatus.ERROR
                heartbeatJob?.cancel()
                // Auto-reconnect after 5 seconds if scope active
                scope.launch {
                    delay(5000)
                    if (config.isCustomConfigured) {
                        connect()
                    }
                }
            }
        })
    }

    private var pendingJoinRef: String? = null

    private fun joinMessagesChannel() {
        val ref = refCounter.getAndIncrement().toString()
        pendingJoinRef = ref
        val filter = currentConversationId?.let { "conversation_id=eq.$it" } ?: ""
        val accessToken = currentAccessToken ?: ""
        val joinPayload = """
        {
            "topic": "realtime:public:messages",
            "event": "phx_join",
            "payload": {
                "access_token": "$accessToken",
                "config": {
                    "broadcast": { "ack": false, "self": false },
                    "presence": { "key": "" },
                    "postgres_changes": [
                        {
                            "event": "INSERT",
                            "schema": "public",
                            "table": "messages"
                            ${if (filter.isNotEmpty()) ",\n                            \"filter\": \"$filter\"" else ""}
                        }
                    ]
                }
            },
            "ref": "$ref"
        }
        """.trimIndent()
        Log.d("QC_RT", "Joining messages channel: currentConversationId=${currentConversationId}, filter=$filter, hasAuthToken=${currentAccessToken != null}")
        Log.d("QC_RT", "Join payload: $joinPayload")
        webSocket?.send(joinPayload)
        _status.value = RealtimeStatus.LISTENING
        Log.d("QC_RT", "Subscribed to Postgres changes on 'messages' table${if (filter.isNotEmpty()) " with filter: $filter" else ""}")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25_000)
                val ref = refCounter.getAndIncrement().toString()
                val hb = """{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"$ref"}"""
                webSocket?.send(hb)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleIncomingMessage(rawText: String) {
        Log.d("QC_RT", "handleIncomingMessage: rawLen=${rawText.length}")
        try {
            val mapAdapter = moshi.adapter(Map::class.java)
            val jsonMap = mapAdapter.fromJson(rawText) as? Map<String, Any?> ?: return

            val event = jsonMap["event"] as? String
            val payload = jsonMap["payload"] as? Map<String, Any?> ?: return
            val ref = jsonMap["ref"] as? String
            val topic = jsonMap["topic"] as? String

            // Handle phx_reply for channel join
            if (event == "phx_reply") {
                val status = payload["status"] as? String
                Log.d("QC_RT", "phx_reply received: topic=$topic, ref=$ref, status=$status, pendingJoinRef=$pendingJoinRef")
                
                // Handle channel subscription confirmation
                if (ref == pendingJoinRef && status == "ok") {
                    Log.d("QC_RT", "Subscription confirmed for ref: $ref")
                    pendingJoinRef = null
                    subscriptionConfirmedCallback?.invoke()
                } else if (ref == pendingJoinRef && status == "error") {
                    Log.e("QC_RT", "Subscription failed for ref: $ref, error: ${payload["response"]}")
                    pendingJoinRef = null
                }
                return
            }

            if (event == "postgres_changes") {
                Log.d("QC_RT", "postgres_changes event received")
                val data = payload["data"] as? Map<String, Any?> ?: payload
                val record = (data["record"] as? Map<String, Any?>) ?: return
                parseAndEmitRecord(record)
            }
        } catch (e: Exception) {
            Log.e("QC_RT", "Error parsing realtime message: ${e.message}")
        }
    }

    private fun parseAndEmitRecord(record: Map<String, Any?>) {
        try {
            val id = record["id"] as? String ?: return
            val senderId = record["sender_id"] as? String ?: return
            val conversationId = record["conversation_id"] as? String ?: return
            val recipientId = record["recipient_id"] as? String ?: return
            val ciphertext = record["ciphertext"] as? String ?: return
            val iv = record["iv"] as? String ?: return
            val mediaCiphertext = record["media_ciphertext"] as? String
            val createdAt = record["created_at"] as? String
            val expiresAt = record["expires_at"] as? String

            // Check if message is for the current active conversation
            Log.d("QC_RT", "parseAndEmitRecord: id=$id, conversationId=$conversationId, currentConversationId=$currentConversationId")
            if (currentConversationId == null || conversationId == currentConversationId) {
                val dto = SupabaseMessageDto(
                    id = id,
                    senderId = senderId,
                    recipientId = recipientId,
                    conversationId = conversationId,
                    ciphertext = ciphertext,
                    iv = iv,
                    mediaCiphertext = mediaCiphertext,
                    createdAt = createdAt,
                    expiresAt = expiresAt
                )
                _incomingMessages.tryEmit(dto)
                Log.d("QC_RT", "Emitted incoming Postgres change message: $id for conversation: $conversationId")
            } else {
                Log.d("QC_RT", "parseAndEmitRecord: FILTERED OUT - conversationId=$conversationId != currentConversationId=$currentConversationId")
            }
        } catch (e: Exception) {
            Log.e("QC_RT", "Failed to parse record: ${e.message}")
        }
    }

    /**
     * In-memory emission for seamless local sandbox / multi-profile testing.
     */
    fun emitSandboxPostgresChange(dto: SupabaseMessageDto) {
        // For sandbox testing, emit all messages (realtime filters by conversation in production)
        _incomingMessages.tryEmit(dto)
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _status.value = RealtimeStatus.DISCONNECTED
    }
}

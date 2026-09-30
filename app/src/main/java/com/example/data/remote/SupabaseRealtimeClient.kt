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
    private val TAG = "SupabaseRealtime"
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private val refCounter = AtomicInteger(1)

    private val _status = MutableStateFlow(RealtimeStatus.DISCONNECTED)
    val status: StateFlow<RealtimeStatus> = _status.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SupabaseMessageDto>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<SupabaseMessageDto> = _incomingMessages.asSharedFlow()

    private var currentRecipientId: String? = null

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun updateRecipientFilter(recipientId: String?) {
        currentRecipientId = recipientId
    }

    /**
     * Connects to Supabase Realtime WebSocket and subscribes to Postgres changes on messages table.
     */
    fun connect() {
        if (_status.value == RealtimeStatus.CONNECTED || _status.value == RealtimeStatus.LISTENING) {
            return
        }

        val rawUrl = config.currentUrl
        if (rawUrl.isEmpty() || !rawUrl.startsWith("http")) {
            _status.value = RealtimeStatus.CONNECTED // Local sandbox active
            return
        }

        val wsUrl = rawUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .removeSuffix("/") + "/realtime/v1/websocket?apikey=${config.currentAnonKey}&vsn=1.0.0"

        _status.value = RealtimeStatus.CONNECTING

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Realtime WebSocket opened")
                _status.value = RealtimeStatus.CONNECTED
                startHeartbeat()
                joinMessagesChannel()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Realtime closing: $reason")
                _status.value = RealtimeStatus.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Realtime failure: ${t.message}")
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

    private fun joinMessagesChannel() {
        val ref = refCounter.getAndIncrement().toString()
        val joinPayload = """
        {
            "topic": "realtime:public:messages",
            "event": "phx_join",
            "payload": {
                "config": {
                    "broadcast": { "ack": false, "self": false },
                    "presence": { "key": "" },
                    "postgres_changes": [
                        {
                            "event": "INSERT",
                            "schema": "public",
                            "table": "messages"
                        }
                    ]
                }
            },
            "ref": "$ref"
        }
        """.trimIndent()
        webSocket?.send(joinPayload)
        _status.value = RealtimeStatus.LISTENING
        Log.d(TAG, "Subscribed to Postgres changes on 'messages' table")
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
        try {
            val mapAdapter = moshi.adapter(Map::class.java)
            val jsonMap = mapAdapter.fromJson(rawText) as? Map<String, Any?> ?: return

            val event = jsonMap["event"] as? String
            val payload = jsonMap["payload"] as? Map<String, Any?> ?: return

            if (event == "postgres_changes") {
                val data = payload["data"] as? Map<String, Any?> ?: payload
                val record = (data["record"] as? Map<String, Any?>) ?: return
                parseAndEmitRecord(record)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing realtime message: ${e.message}")
        }
    }

    private fun parseAndEmitRecord(record: Map<String, Any?>) {
        try {
            val id = record["id"] as? String ?: return
            val senderId = record["sender_id"] as? String ?: return
            val recipientId = record["recipient_id"] as? String ?: return
            val ciphertext = record["ciphertext"] as? String ?: return
            val iv = record["iv"] as? String ?: return
            val mediaCiphertext = record["media_ciphertext"] as? String
            val createdAt = record["created_at"] as? String
            val expiresAt = record["expires_at"] as? String

            // Check if message is for the current active user
            if (currentRecipientId == null || recipientId == currentRecipientId) {
                val dto = SupabaseMessageDto(
                    id = id,
                    senderId = senderId,
                    recipientId = recipientId,
                    ciphertext = ciphertext,
                    iv = iv,
                    mediaCiphertext = mediaCiphertext,
                    createdAt = createdAt,
                    expiresAt = expiresAt
                )
                _incomingMessages.tryEmit(dto)
                Log.d(TAG, "Emitted incoming Postgres change message: $id")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse record: ${e.message}")
        }
    }

    /**
     * In-memory emission for seamless local sandbox / multi-profile testing.
     */
    fun emitSandboxPostgresChange(dto: SupabaseMessageDto) {
        if (currentRecipientId == null || dto.recipientId == currentRecipientId) {
            _incomingMessages.tryEmit(dto)
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _status.value = RealtimeStatus.DISCONNECTED
    }
}

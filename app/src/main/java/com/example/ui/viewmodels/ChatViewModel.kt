package com.example.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.DefaultChatRepository
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseRealtimeClient
import com.example.notifications.NotificationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ChatRepository = DefaultChatRepository(application),
    private val realtimeClient: SupabaseRealtimeClient = SupabaseRealtimeClient(SupabaseConfig(application))
) : AndroidViewModel(application) {

    private var currentConversationId: String = ""
    private var currentPeerId: String = ""
    private var isConversationOpen = false

    val currentUser: StateFlow<User?> = repository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        // Connect to realtime when ViewModel is created
        viewModelScope.launch {
            realtimeClient.connect()
        }

        // Collect incoming realtime messages and store them locally
        viewModelScope.launch {
            realtimeClient.incomingMessages.collect { dto ->
                Log.d("QC_RT", "Realtime DTO received: id=${dto.id}, conversationId=${dto.conversationId}, senderId=${dto.senderId}")
                val message = Message(
                    id = dto.id ?: UUID.randomUUID().toString(),
                    conversationId = dto.conversationId,
                    senderId = dto.senderId,
                    recipientId = dto.recipientId,
                    text = dto.ciphertext, // plaintext for now
                    timestamp = dto.createdAt?.let { java.time.Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis(),
                    status = com.example.data.model.MessageDeliveryStatus.DELIVERED,
                    isOutgoing = false
                )
                Log.d("QC_RT", "Inserting realtime message: ${message.id} for conversation ${message.conversationId}")
                repository.insertMessageFromRealtime(message)

                // Show notification if conversation is not open
                if (!isConversationOpen || currentConversationId != dto.conversationId) {
                    val peerUser = repository.getUserById(dto.senderId)
                    val senderName = peerUser?.username ?: "Unknown"
                    NotificationManager.showMessageNotification(
                        context = getApplication(),
                        senderUsername = senderName,
                        messagePreview = dto.ciphertext.take(50),
                        conversationId = dto.conversationId,
                        senderId = dto.senderId
                    )
                }
            }
        }
    }

    fun initChat(peerId: String) {
        currentPeerId = peerId
        Log.d("QC_CONV", "initChat called: peerId=$peerId")
        viewModelScope.launch {
            // Create or get conversation in Supabase
            val conversationResult = repository.createOrGetConversation(peerId)
            if (conversationResult.isFailure) {
                Log.e("QC_CONV", "createOrGetConversation failed: ${conversationResult.exceptionOrNull()}")
                return@launch
            }
            val conversation = conversationResult.getOrThrow()
            currentConversationId = conversation.id
            Log.d("QC_CONV", "Conversation created/retrieved: id=${conversation.id}")

            // Set up realtime filter for this conversation BEFORE connecting
            val accessToken = repository.getCurrentAccessToken()
            Log.d("QC_RT", "Setting access token for realtime: ${if (accessToken != null) "present" else "null"}")
            realtimeClient.setAccessToken(accessToken)
            Log.d("QC_RT", "Updating conversation filter: ${conversation.id}")
            realtimeClient.updateConversationFilter(conversation.id)

            // Wait for subscription confirmation before marking conversation as live
            var subscriptionConfirmed = false
            realtimeClient.setSubscriptionConfirmedCallback {
                Log.d("QC_RT", "Subscription confirmed callback invoked")
                subscriptionConfirmed = true
            }

            // Connect to realtime after setting access token and filter
            Log.d("QC_RT", "Calling realtimeClient.connect()")
            realtimeClient.connect()

            // Wait for subscription confirmation (with timeout)
            var waitTime = 0
            while (!subscriptionConfirmed && waitTime < 10000) { // 10 second timeout
                delay(100)
                waitTime += 100
            }

            if (!subscriptionConfirmed) {
                Log.w("QC_RT", "Realtime subscription confirmation timed out for conversation: ${conversation.id}")
            } else {
                Log.d("QC_RT", "Realtime subscription confirmed for conversation: ${conversation.id}")
            }

            isConversationOpen = true
            Log.d("QC_CONV", "Conversation marked as open: ${conversation.id}")

            repository.markConversationRead(conversation.id)

            launch {
                repository.getConversationFlow(conversation.id).collect { conv ->
                    Log.d("QC_UI", "Conversation flow updated: ${conv?.id}")
                    _conversation.value = conv
                }
            }

            launch {
                repository.getMessagesFlow(conversation.id).collect { list ->
                    Log.d("QC_UI", "Messages flow updated: count=${list.size}, conversationId=$currentConversationId")
                    _messages.value = list
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || currentPeerId.isEmpty()) return

        Log.d("QC_SEND", "sendMessage called: peerId=$currentPeerId, textLen=${clean.length}")
        viewModelScope.launch {
            _isSending.value = true
            Log.d("QC_SEND", "Calling repository.sendMessage")
            repository.sendMessage(currentPeerId, clean)
            Log.d("QC_SEND", "repository.sendMessage returned")
            _isSending.value = false
        }
    }

    fun onConversationClosed() {
        isConversationOpen = false
    }

    override fun onCleared() {
        super.onCleared()
        realtimeClient.disconnect()
    }
}

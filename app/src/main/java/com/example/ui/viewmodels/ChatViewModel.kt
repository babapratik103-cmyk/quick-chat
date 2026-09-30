package com.example.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import com.example.data.repository.DefaultChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ChatRepository = DefaultChatRepository(application)
) : AndroidViewModel(application) {

    private var currentPeerId: String = ""

    val currentUser: StateFlow<User?> = repository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun initChat(peerId: String) {
        currentPeerId = peerId
        viewModelScope.launch {
            repository.markConversationRead(peerId)

            launch {
                repository.getConversationFlow(peerId).collect { conv ->
                    _conversation.value = conv
                }
            }

            launch {
                repository.getMessagesFlow(peerId).collect { list ->
                    _messages.value = list
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || currentPeerId.isEmpty()) return

        viewModelScope.launch {
            _isSending.value = true
            repository.sendMessage(currentPeerId, clean)
            _isSending.value = false
        }
    }
}

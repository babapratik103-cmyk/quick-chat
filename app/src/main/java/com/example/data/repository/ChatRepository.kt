package com.example.data.repository

import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    val currentUserFlow: Flow<User?>
    suspend fun getCurrentUser(): User?

    // Registration with exact 6 fields
    suspend fun register(
        name: String,
        username: String,
        age: Int,
        email: String,
        password: String,
        confirmPassword: String
    ): Result<User>

    // Login with Username and Password only
    suspend fun login(
        username: String,
        password: String
    ): Result<User>

    // Session management
    suspend fun restoreSession(): Result<User?>
    suspend fun refreshSession(): Result<User?>
    suspend fun logout(): Result<Unit>

    suspend fun updateProfile(name: String, avatarColorHex: String): Result<User>
    suspend fun searchUsers(query: String): List<User>
    suspend fun getUserById(userId: String): User?
    suspend fun getUserByUsername(username: String): User?

    val conversationsFlow: Flow<List<Conversation>>
    suspend fun getConversation(peerId: String): Conversation?
    fun getConversationFlow(peerId: String): Flow<Conversation?>
    suspend fun markConversationRead(conversationId: String)

    fun getMessagesFlow(conversationId: String): Flow<List<Message>>
    suspend fun sendMessage(recipientId: String, text: String): Result<Message>
    suspend fun startConversationWithUser(user: User): Conversation

    // Create or get 1-to-1 conversation with a user via Supabase
    suspend fun createOrGetConversation(peerId: String): Result<Conversation>

    // Get current access token for realtime auth
    suspend fun getCurrentAccessToken(): String?

    // Insert a message received via realtime
    suspend fun insertMessageFromRealtime(message: com.example.data.model.Message): Result<Unit>
}

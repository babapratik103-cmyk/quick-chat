package com.example.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.example.data.local.AppDatabase
import com.example.data.local.AuthTokenStore
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.PrivateKeyStore
import com.example.data.local.UserEntity
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageDeliveryStatus
import com.example.data.model.User
import com.example.data.remote.SupabaseRestService
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseProfileDto
import com.example.data.remote.RefreshTokenResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DefaultChatRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val supabaseRestService: SupabaseRestService = SupabaseRestService(SupabaseConfig(context))
) : ChatRepository {

    private val userDao = database.userDao()
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    override val currentUserFlow: Flow<User?> = userDao.getCurrentUserFlow().map { entity ->
        entity?.toDomainModel()
    }

    override suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        userDao.getCurrentUser()?.toDomainModel()
    }

    override suspend fun register(
        name: String,
        username: String,
        age: Int,
        email: String,
        password: String,
        confirmPassword: String
    ): Result<User> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanUser = username.trim().removePrefix("@").lowercase()
        val cleanEmail = email.trim()

        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your name"))
        }

        val usernameRegex = Regex("^[a-z0-9_]{3,20}$")
        if (!cleanUser.matches(usernameRegex)) {
            return@withContext Result.failure(
                IllegalArgumentException("Username must be 3-20 characters and contain only lowercase letters, numbers, and underscores")
            )
        }

        if (age < 13 || age > 120) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid age (13 or older)"))
        }

        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }

        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }

        if (password != confirmPassword) {
            return@withContext Result.failure(IllegalArgumentException("Passwords do not match"))
        }

        val keyPairResult = PrivateKeyStore.generateKeyPair(context, cleanUser)
        val (privateKey, publicKey) = when (keyPairResult) {
            is PrivateKeyStore.KeyPairResult.Success -> keyPairResult.privateKey to keyPairResult.publicKey
            is PrivateKeyStore.KeyPairResult.Error -> return@withContext Result.failure(keyPairResult.exception)
        }

        val publicKeyBase64 = PrivateKeyStore.publicKeyToBase64(publicKey)

        val signUpResult = supabaseRestService.signUpWithMetadata(
            email = cleanEmail,
            password = password,
            username = cleanUser,
            name = cleanName,
            age = age,
            publicKey = publicKeyBase64
        )

        if (signUpResult.isFailure) {
            PrivateKeyStore.deleteKeyPair(cleanUser)
            return@withContext signUpResult.map { it as User } // type cast handled by caller
        }

        val authUserId = signUpResult.getOrThrow().user!!.id

        val avatarColors = listOf("#F59E0B", "#10B981", "#6366F1", "#EC4899", "#06B6D4")
        val randomColor = avatarColors.random()

        val newUser = UserEntity(
            id = authUserId,
            username = cleanUser,
            name = cleanName,
            age = age,
            email = cleanEmail,
            avatarColorHex = randomColor,
            isCurrentAccount = true,
            createdAt = System.currentTimeMillis()
        )

        try {
            userDao.clearActiveAccounts()
            userDao.insertUser(newUser)
            Result.success(newUser.toDomainModel())
        } catch (e: SQLiteConstraintException) {
            PrivateKeyStore.deleteKeyPair(cleanUser)
            Result.failure(IllegalArgumentException("Username @$cleanUser is already registered in the database"))
        } catch (e: Exception) {
            PrivateKeyStore.deleteKeyPair(cleanUser)
            Result.failure(e)
        }
    }

    override suspend fun login(
        username: String,
        password: String
    ): Result<User> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().removePrefix("@").lowercase()

        if (cleanUser.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your username"))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password"))
        }

        val loginResponse = supabaseRestService.loginWithUsername(cleanUser, password)

        if (loginResponse == null) {
            return@withContext Result.failure(IllegalArgumentException("Invalid username or password"))
        }

        // Check email verification
        if (loginResponse.emailConfirmedAt == null || loginResponse.emailConfirmedAt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please verify your email before logging in. Check your inbox for the verification link."))
        }

        val authUserId = loginResponse.user.id
        val userEmail = loginResponse.user.email ?: ""

        // Try local lookup first
        var user = userDao.getUserByUsername(cleanUser)

        // If not found locally, fetch from Supabase and create local cache
        if (user == null) {
            val profile = supabaseRestService.getProfileById(authUserId)
                ?: return@withContext Result.failure(IllegalArgumentException("Failed to fetch user profile from server"))

            // Check/create Keystore key pair for this user
            val keyPairResult = PrivateKeyStore.generateKeyPair(context, cleanUser)
            if (keyPairResult is PrivateKeyStore.KeyPairResult.Error) {
                return@withContext Result.failure(keyPairResult.exception)
            }

            user = UserEntity(
                id = authUserId,
                username = profile.username,
                name = profile.name,
                age = 0, // age not in profile DTO; will be updated on next sync
                email = userEmail,
                avatarColorHex = "#F59E0B",
                isCurrentAccount = true,
                createdAt = System.currentTimeMillis()
            )

            try {
                userDao.clearActiveAccounts()
                userDao.insertUser(user)
            } catch (e: SQLiteConstraintException) {
                // Race condition: another thread inserted; fetch the existing one
                user = userDao.getUserByUsername(cleanUser)
                    ?: return@withContext Result.failure(IllegalArgumentException("Failed to create local user cache"))
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        } else {
            // User exists locally; update active account
            userDao.clearActiveAccounts()
            userDao.setActiveAccount(user.id)
        }

        AuthTokenStore.saveTokens(
            context,
            loginResponse.accessToken,
            loginResponse.refreshToken,
            loginResponse.expiresIn.toLong()
        )

        Result.success(user.toDomainModel())
    }

    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val accessToken = AuthTokenStore.getAccessToken(context)
        val refreshToken = AuthTokenStore.getRefreshToken(context)
        if (accessToken != null && refreshToken != null) {
            supabaseRestService.signOut(accessToken, refreshToken)
        }
        AuthTokenStore.clearTokens(context)
        userDao.clearActiveAccounts()
        Result.success(Unit)
    }

    override suspend fun restoreSession(): Result<User?> = withContext(Dispatchers.IO) {
        if (!AuthTokenStore.hasValidSession(context)) {
            return@withContext Result.success(null)
        }

        val accessToken = AuthTokenStore.getAccessToken(context)
        val refreshToken = AuthTokenStore.getRefreshToken(context)

        if (accessToken == null || refreshToken == null) {
            AuthTokenStore.clearTokens(context)
            return@withContext Result.success(null)
        }

        // If access token is expired, try to refresh
        if (AuthTokenStore.isAccessTokenExpired(context)) {
            val refreshResult = refreshSession()
            return@withContext refreshResult
        }

        // Access token is still valid, get current user from local cache
        val currentUser = userDao.getCurrentUser()
        if (currentUser != null) {
            return@withContext Result.success(currentUser.toDomainModel())
        }

        // No local user, try to restore from Supabase using access token
        // For now, return null to force re-login
        return@withContext Result.success(null)
    }

    override suspend fun refreshSession(): Result<User?> = withContext(Dispatchers.IO) {
        val refreshToken = AuthTokenStore.getRefreshToken(context)
            ?: return@withContext Result.failure(IllegalStateException("No refresh token available"))

        val refreshResponse = supabaseRestService.refreshAccessToken(refreshToken)
            ?: return@withContext Result.failure(IllegalStateException("Failed to refresh session"))

        // Save new tokens
        AuthTokenStore.saveTokens(
            context,
            refreshResponse.accessToken,
            refreshResponse.refreshToken,
            refreshResponse.expiresIn.toLong()
        )

        // Get user profile
        val authUserId = refreshResponse.user.id
        val profile = supabaseRestService.getProfileById(authUserId)
            ?: return@withContext Result.failure(IllegalArgumentException("Failed to fetch user profile"))

        // Check/create Keystore key pair
        val keyPairResult = PrivateKeyStore.generateKeyPair(context, profile.username)
        if (keyPairResult is PrivateKeyStore.KeyPairResult.Error) {
            return@withContext Result.failure(keyPairResult.exception)
        }

        // Update or create local user
        var user = userDao.getUserByUsername(profile.username)
        if (user == null) {
            user = UserEntity(
                id = authUserId,
                username = profile.username,
                name = profile.name,
                age = 0,
                email = "",
                avatarColorHex = "#F59E0B",
                isCurrentAccount = true,
                createdAt = System.currentTimeMillis()
            )
            try {
                userDao.clearActiveAccounts()
                userDao.insertUser(user)
            } catch (e: SQLiteConstraintException) {
                user = userDao.getUserByUsername(profile.username)
                    ?: return@withContext Result.failure(IllegalArgumentException("Failed to create local user cache"))
            }
        } else {
            userDao.clearActiveAccounts()
            userDao.setActiveAccount(user.id)
        }

        Result.success(user.toDomainModel())
    }

    override suspend fun updateProfile(
        name: String,
        avatarColorHex: String
    ): Result<User> = withContext(Dispatchers.IO) {
        val currentUser = userDao.getCurrentUser()
            ?: return@withContext Result.failure(IllegalStateException("No authenticated user"))

        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
        }

        userDao.updateProfile(currentUser.id, cleanName, avatarColorHex)
        val updated = userDao.getUserById(currentUser.id)
            ?: return@withContext Result.failure(IllegalStateException("Failed to refresh user"))

        Result.success(updated.toDomainModel())
    }

    override suspend fun searchUsers(query: String): List<User> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().removePrefix("@").lowercase()
        if (cleanQuery.isEmpty()) {
            emptyList()
        } else {
            // Query Supabase as primary source for user discovery
            val supabaseProfiles = supabaseRestService.searchProfiles(cleanQuery)

            // Merge with local cache: upsert Supabase profiles into Room
            if (supabaseProfiles.isNotEmpty()) {
                val currentUserId = userDao.getCurrentUser()?.id
                val usersToUpsert = supabaseProfiles.mapNotNull { profile ->
                    // Skip current user
                    if (currentUserId != null && profile.id == currentUserId) return@mapNotNull null
                    UserEntity(
                        id = profile.id,
                        username = profile.username,
                        name = profile.name,
                        age = 0,
                        email = profile.email ?: "",
                        avatarColorHex = "#F59E0B",
                        isCurrentAccount = false,
                        createdAt = System.currentTimeMillis()
                    )
                }
                if (usersToUpsert.isNotEmpty()) {
                    try {
                        userDao.insertUsers(usersToUpsert)
                    } catch (e: Exception) {
                        // Ignore conflicts; local cache is best-effort
                    }
                }
            }

            // Return merged results from local cache (now includes Supabase results)
            userDao.searchUsersList(cleanQuery).map { it.toDomainModel() }
        }
    }

    override suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        userDao.getUserById(userId)?.toDomainModel()
    }

    override suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        val clean = username.trim().removePrefix("@").lowercase()
        userDao.getUserByUsername(clean)?.toDomainModel()
    }

    override val conversationsFlow: Flow<List<Conversation>> =
        conversationDao.getAllConversationsFlow().map { entities ->
            val result = mutableListOf<Conversation>()
            for (entity in entities) {
                val peer = userDao.getUserById(entity.id)
                if (peer != null) {
                    result.add(
                        Conversation(
                            id = entity.id,
                            peerUser = peer.toDomainModel(),
                            lastMessageText = entity.lastMessageText,
                            lastMessageTimestamp = entity.lastMessageTimestamp,
                            unreadCount = entity.unreadCount
                        )
                    )
                }
            }
            result.sortedByDescending { it.lastMessageTimestamp }
        }

    override suspend fun getConversation(peerId: String): Conversation? = withContext(Dispatchers.IO) {
        val entity = conversationDao.getConversationById(peerId) ?: return@withContext null
        val peer = userDao.getUserById(entity.id) ?: return@withContext null
        Conversation(
            id = entity.id,
            peerUser = peer.toDomainModel(),
            lastMessageText = entity.lastMessageText,
            lastMessageTimestamp = entity.lastMessageTimestamp,
            unreadCount = entity.unreadCount
        )
    }

    override fun getConversationFlow(peerId: String): Flow<Conversation?> =
        conversationDao.getConversationFlow(peerId).map { entity ->
            if (entity == null) null
            else {
                val peer = userDao.getUserById(entity.id)
                peer?.let {
                    Conversation(
                        id = entity.id,
                        peerUser = it.toDomainModel(),
                        lastMessageText = entity.lastMessageText,
                        lastMessageTimestamp = entity.lastMessageTimestamp,
                        unreadCount = entity.unreadCount
                    )
                }
            }
        }

    override suspend fun markConversationRead(conversationId: String) = withContext(Dispatchers.IO) {
        conversationDao.markAsRead(conversationId)
    }

    override fun getMessagesFlow(conversationId: String): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomainModel() }
        }

    override suspend fun sendMessage(
        recipientId: String,
        text: String
    ): Result<Message> = withContext(Dispatchers.IO) {
        val currentUser = userDao.getCurrentUser()
            ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

        val recipientUser = userDao.getUserById(recipientId)
            ?: return@withContext Result.failure(IllegalArgumentException("Recipient not found"))

        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Message cannot be empty"))
        }

        val messageId = "msg_${UUID.randomUUID().toString().take(10)}"
        val now = System.currentTimeMillis()

        val messageEntity = MessageEntity(
            id = messageId,
            conversationId = recipientId,
            senderId = currentUser.id,
            recipientId = recipientId,
            text = trimmedText,
            timestamp = now,
            status = MessageDeliveryStatus.SENDING,
            isOutgoing = true
        )

        messageDao.insertMessage(messageEntity)

        // Upsert conversation record
        val existingConvo = conversationDao.getConversationById(recipientId)
        val updatedConvo = ConversationEntity(
            id = recipientId,
            peerUsername = recipientUser.username,
            peerDisplayName = recipientUser.name,
            peerAvatarColorHex = recipientUser.avatarColorHex,
            lastMessageText = trimmedText,
            lastMessageTimestamp = now,
            unreadCount = existingConvo?.unreadCount ?: 0
        )
        conversationDao.insertOrUpdate(updatedConvo)

        // Progress status: SENDING -> SENT -> DELIVERED -> READ
        scope.launch {
            delay(400)
            messageDao.updateMessageStatus(messageId, MessageDeliveryStatus.SENT)
            delay(500)
            messageDao.updateMessageStatus(messageId, MessageDeliveryStatus.DELIVERED)
            delay(600)
            messageDao.updateMessageStatus(messageId, MessageDeliveryStatus.READ)
        }

        Result.success(messageEntity.toDomainModel())
    }

    override suspend fun startConversationWithUser(user: User): Conversation = withContext(Dispatchers.IO) {
        val existing = conversationDao.getConversationById(user.id)
        if (existing != null) {
            Conversation(
                id = existing.id,
                peerUser = user,
                lastMessageText = existing.lastMessageText,
                lastMessageTimestamp = existing.lastMessageTimestamp,
                unreadCount = existing.unreadCount
            )
        } else {
            val newConvo = ConversationEntity(
                id = user.id,
                peerUsername = user.username,
                peerDisplayName = user.name,
                peerAvatarColorHex = user.avatarColorHex,
                lastMessageText = "Started a new conversation",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0
            )
            conversationDao.insertOrUpdate(newConvo)
            Conversation(
                id = user.id,
                peerUser = user,
                lastMessageText = newConvo.lastMessageText,
                lastMessageTimestamp = newConvo.lastMessageTimestamp,
                unreadCount = 0
            )
        }
    }

    private fun UserEntity.toDomainModel(): User {
        return User(
            id = this.id,
            username = this.username,
            name = this.name,
            age = this.age,
            email = this.email,
            avatarColorHex = this.avatarColorHex,
            createdAt = this.createdAt
        )
    }

    private fun MessageEntity.toDomainModel(): Message {
        return Message(
            id = this.id,
            conversationId = this.conversationId,
            senderId = this.senderId,
            recipientId = this.recipientId,
            text = this.text,
            timestamp = this.timestamp,
            status = this.status,
            isOutgoing = this.isOutgoing
        )
    }
}

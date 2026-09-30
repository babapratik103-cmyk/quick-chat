package com.example.data.model

data class User(
    val id: String,
    val username: String,       // Primary identity throughout the app
    val name: String,           // Registration profile name
    val age: Int,               // Registration age
    val email: String,          // Verification email only
    val avatarColorHex: String = "#F59E0B",
    val createdAt: Long = System.currentTimeMillis()
)

enum class MessageDeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageDeliveryStatus = MessageDeliveryStatus.SENDING,
    val isOutgoing: Boolean = true
)

data class Conversation(
    val id: String, // Typically the peer's user ID
    val peerUser: User,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
)

data class MessageDeliveryAck(
    val messageId: String,
    val conversationId: String,
    val recipientId: String,
    val deliveredAt: Long = System.currentTimeMillis(),
    val status: MessageDeliveryStatus = MessageDeliveryStatus.DELIVERED
)

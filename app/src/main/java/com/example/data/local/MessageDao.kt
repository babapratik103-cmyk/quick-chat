package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MessageDeliveryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: String, status: MessageDeliveryStatus)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    // Upsert message from realtime: if message exists and is outgoing, keep SENT status; otherwise update to DELIVERED
    suspend fun upsertMessageFromRealtime(message: MessageEntity) {
        val existing = getMessageById(message.id)
        if (existing != null) {
            if (existing.isOutgoing) {
                // Message sent by us - keep existing status (SENT) and isOutgoing=true
                if (existing.status != MessageDeliveryStatus.SENT) {
                    val updated = existing.copy(status = MessageDeliveryStatus.SENT)
                    updateMessage(updated)
                }
            } else {
                // Incoming message - update to DELIVERED if not already
                if (existing.status != MessageDeliveryStatus.DELIVERED) {
                    val updated = existing.copy(status = MessageDeliveryStatus.DELIVERED)
                    updateMessage(updated)
                }
            }
        } else {
            // New incoming message
            val newMessage = message.copy(status = MessageDeliveryStatus.DELIVERED, isOutgoing = false)
            insertMessage(newMessage)
        }
    }
}

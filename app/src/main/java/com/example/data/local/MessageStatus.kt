package com.example.data.local

enum class MessageStatus {
    QUEUED,               // In local offline queue, waiting for network or server ack
    SENT_TO_SERVER,       // Stored temporarily on Supabase relay
    DELIVERED_AND_PURGED, // Recipient confirmed reception with ACK -> permanently deleted from server!
    FAILED                // Delivery failed or expired after 7-day TTL
}

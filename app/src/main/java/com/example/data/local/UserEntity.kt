package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val name: String,
    val age: Int,
    val email: String,
    val password: String = "",
    val avatarColorHex: String = "#F59E0B",
    val isCurrentAccount: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

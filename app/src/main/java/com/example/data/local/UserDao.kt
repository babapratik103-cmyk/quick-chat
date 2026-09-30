package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE isCurrentAccount = 1 LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isCurrentAccount = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) LIKE '%' || LOWER(:query) || '%' AND isCurrentAccount = 0 ORDER BY username ASC")
    fun searchUsers(query: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE LOWER(username) LIKE '%' || LOWER(:query) || '%' AND isCurrentAccount = 0 ORDER BY username ASC")
    suspend fun searchUsersList(query: String): List<UserEntity>

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET name = :name, avatarColorHex = :avatarColorHex WHERE id = :userId")
    suspend fun updateProfile(userId: String, name: String, avatarColorHex: String)

    @Query("UPDATE users SET isCurrentAccount = 0")
    suspend fun clearActiveAccounts()

    @Query("UPDATE users SET isCurrentAccount = 1 WHERE id = :userId")
    suspend fun setActiveAccount(userId: String)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)
}

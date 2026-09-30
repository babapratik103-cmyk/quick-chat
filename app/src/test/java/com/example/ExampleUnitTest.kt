package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.crypto.CryptoManager
import com.example.data.model.MessageDeliveryStatus
import com.example.data.repository.DefaultChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun testExactSixFieldRegistrationAndValidation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DefaultChatRepository(context)

        // Invalid: missing name
        val noNameResult = repository.register(
            name = "",
            username = "valid_user",
            age = 22,
            email = "user@test.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertFalse(noNameResult.isSuccess)

        // Invalid: short username (<3 chars)
        val shortUserResult = repository.register(
            name = "Test User",
            username = "ab",
            age = 22,
            email = "user@test.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertFalse(shortUserResult.isSuccess)

        // Invalid: special characters in username
        val specialCharResult = repository.register(
            name = "Test User",
            username = "user!name",
            age = 22,
            email = "user@test.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertFalse(specialCharResult.isSuccess)

        // Invalid: age under 13
        val youngResult = repository.register(
            name = "Test User",
            username = "kid_user",
            age = 10,
            email = "user@test.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertFalse(youngResult.isSuccess)

        // Invalid: passwords do not match
        val mismatchResult = repository.register(
            name = "Test User",
            username = "mismatch_user",
            age = 25,
            email = "user@test.com",
            password = "password123",
            confirmPassword = "password456"
        )
        assertFalse(mismatchResult.isSuccess)

        // Valid: All exact 6 fields satisfy requirements
        val successResult = repository.register(
            name = "Jordan Hayes",
            username = "jordan_hayes",
            age = 24,
            email = "jordan@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertTrue(successResult.isSuccess)
        val createdUser = successResult.getOrThrow()
        assertEquals("jordan_hayes", createdUser.username)
        assertEquals("Jordan Hayes", createdUser.name)
        assertEquals(24, createdUser.age)
        assertEquals("jordan@example.com", createdUser.email)
    }

    @Test
    fun testDatabaseEnforcedUsernameUniqueness() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DefaultChatRepository(context)

        // Register first user
        val firstUserResult = repository.register(
            name = "Alice Smith",
            username = "alice_smith",
            age = 28,
            email = "alice@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertTrue(firstUserResult.isSuccess)

        // Register duplicate username with different email and name
        val duplicateUsernameResult = repository.register(
            name = "Different Alice",
            username = "alice_smith",
            age = 30,
            email = "other_alice@example.com",
            password = "password456",
            confirmPassword = "password456"
        )
        // Must fail due to username uniqueness
        assertFalse(duplicateUsernameResult.isSuccess)
    }

    @Test
    fun testUsernameOnlyLoginAndNormalLogout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DefaultChatRepository(context)

        // Register test account
        val user = repository.register(
            name = "Alex Rivera",
            username = "alex_rivera",
            age = 26,
            email = "alex@example.com",
            password = "secretpassword",
            confirmPassword = "secretpassword"
        ).getOrThrow()

        // 1. Normal clean logout
        val logoutResult = repository.logout()
        assertTrue(logoutResult.isSuccess)
        assertEquals(null, repository.getCurrentUser())

        // 2. Reject incorrect password
        val wrongPassResult = repository.login("alex_rivera", "wrongpassword")
        assertFalse(wrongPassResult.isSuccess)

        // 3. Reject non-existent username
        val unknownUserResult = repository.login("unknown_user_123", "secretpassword")
        assertFalse(unknownUserResult.isSuccess)

        // 4. Successful login via Username only (no email)
        val loginResult = repository.login("alex_rivera", "secretpassword")
        assertTrue(loginResult.isSuccess)
        assertEquals(user.id, loginResult.getOrThrow().id)
        assertEquals("alex_rivera", loginResult.getOrThrow().username)
    }

    @Test
    fun testUsernameFocusedSearchAndMessaging() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DefaultChatRepository(context)

        // Register two users
        val sender = repository.register(
            name = "Sender User",
            username = "sender_one",
            age = 21,
            email = "sender@example.com",
            password = "password123",
            confirmPassword = "password123"
        ).getOrThrow()

        val recipient = repository.register(
            name = "Recipient User",
            username = "recipient_two",
            age = 23,
            email = "recipient@example.com",
            password = "password123",
            confirmPassword = "password123"
        ).getOrThrow()

        // Set active session back to sender
        repository.login("sender_one", "password123")

        // Search strictly by username
        val searchResults = repository.searchUsers("recipient")
        assertTrue(searchResults.isNotEmpty())
        assertEquals("recipient_two", searchResults.first().username)

        // Send 1-to-1 message
        val sendResult = repository.sendMessage(recipient.id, "Hello from Quick Chat!")
        assertTrue(sendResult.isSuccess)
        val msg = sendResult.getOrThrow()
        assertEquals("Hello from Quick Chat!", msg.text)
        assertEquals(MessageDeliveryStatus.SENDING, msg.status)
    }

    @Test
    fun testEcdhAndAes256GcmCrypto() {
        val alicePair = CryptoManager.generateEcKeyPair()
        val bobPair = CryptoManager.generateEcKeyPair()

        val aliceKey = CryptoManager.deriveSharedAesKey(alicePair.private, bobPair.public)
        val bobKey = CryptoManager.deriveSharedAesKey(bobPair.private, alicePair.public)

        assertEquals(aliceKey.encoded.toList(), bobKey.encoded.toList())

        val secret = "Zero-knowledge payload for Quick Chat test"
        val encrypted = CryptoManager.encryptText(secret, aliceKey)
        assertNotEquals(secret, encrypted.ciphertext)

        val decrypted = CryptoManager.decryptText(encrypted, bobKey)
        assertEquals(secret, decrypted)
    }
}

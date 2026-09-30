package com.example.data.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertext: String,
    val iv: String
)

object CryptoManager {
    private const val EC_CURVE = "secp256r1"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val AES_KEY_BYTES = 32

    private val secureRandom = SecureRandom()

    fun generateEcKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec(EC_CURVE)
        keyPairGenerator.initialize(ecSpec, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (t: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (t: Throwable) {
            java.util.Base64.getDecoder().decode(str.trim())
        }
    }

    fun publicKeyToBase64(publicKey: PublicKey): String {
        return encodeBase64(publicKey.encoded)
    }

    fun privateKeyToBase64(privateKey: PrivateKey): String {
        return encodeBase64(privateKey.encoded)
    }

    fun base64ToPublicKey(base64: String): PublicKey {
        val bytes = decodeBase64(base64)
        val keySpec = X509EncodedKeySpec(bytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    fun base64ToPrivateKey(base64: String): PrivateKey {
        val bytes = decodeBase64(base64)
        val keySpec = PKCS8EncodedKeySpec(bytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(keySpec)
    }

    /**
     * Derives a symmetric AES-256 key between local private key and remote public key using ECDH + HKDF/SHA-256.
     */
    fun deriveSharedAesKey(myPrivateKey: PrivateKey, peerPublicKey: PublicKey): SecretKeySpec {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive 32 bytes using SHA-256 hash of shared secret
        val sha256 = MessageDigest.getInstance("SHA-256")
        val derivedKeyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(derivedKeyBytes, 0, AES_KEY_BYTES, "AES")
    }

    /**
     * Encrypts plaintext string using AES-256-GCM with a random 12-byte IV.
     */
    fun encryptText(plaintext: String, aesKey: SecretKeySpec): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)

        val plaintextBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val ciphertextBytes = cipher.doFinal(plaintextBytes)

        return EncryptedPayload(
            ciphertext = encodeBase64(ciphertextBytes),
            iv = encodeBase64(iv)
        )
    }

    /**
     * Decrypts AES-256-GCM ciphertext payload back to plaintext string.
     */
    fun decryptText(encryptedPayload: EncryptedPayload, aesKey: SecretKeySpec): String {
        val iv = decodeBase64(encryptedPayload.iv)
        val ciphertextBytes = decodeBase64(encryptedPayload.ciphertext)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)

        val plaintextBytes = cipher.doFinal(ciphertextBytes)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    /**
     * Computes safety number / fingerprint for verifiable peer key authenticity.
     * Formats 64-bit digest into grouped digits: XXXX-XXXX-XXXX-XXXX
     */
    fun computeSafetyNumber(userAKey: String, userBKey: String): String {
        val sortedKeys = listOf(userAKey, userBKey).sorted().joinToString(":")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(sortedKeys.toByteArray(StandardCharsets.UTF_8))
        val sb = StringBuilder()
        for (i in 0 until 8) {
            val num = (hash[i].toInt() and 0xFF) * 256 + (hash[i + 8].toInt() and 0xFF)
            sb.append(String.format("%04d", num % 10000))
            if (i < 7 && (i + 1) % 2 == 0) {
                sb.append(" ")
            } else if (i < 7) {
                sb.append("-")
            }
        }
        return sb.toString().trim()
    }
}

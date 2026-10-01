package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

object PrivateKeyStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "quickchat_ec_key_"
    private const val EC_CURVE = "secp256r1"

    private fun getKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore
    }

    private fun keyAlias(userId: String): String {
        return "$KEY_ALIAS_PREFIX$userId"
    }

    fun generateKeyPair(context: Context, userId: String): KeyPairResult {
        try {
            val alias = keyAlias(userId)
            val keyStore = getKeyStore()

            if (keyStore.containsAlias(alias)) {
                val existingPrivateKey = keyStore.getKey(alias, null) as PrivateKey?
                val existingPublicKey = keyStore.getCertificate(alias)?.publicKey
                if (existingPrivateKey != null && existingPublicKey != null) {
                    return KeyPairResult.Success(existingPrivateKey, existingPublicKey)
                }
                keyStore.deleteEntry(alias)
            }

            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
            keyPairGenerator.initialize(spec)
            val keyPair = keyPairGenerator.generateKeyPair()

            val privateKey = keyPair.private
            val publicKey = keyPair.public

            return KeyPairResult.Success(privateKey, publicKey)
        } catch (e: Exception) {
            return KeyPairResult.Error(e)
        }
    }

    fun getPrivateKey(userId: String): PrivateKey? {
        try {
            val keyStore = getKeyStore()
            return keyStore.getKey(keyAlias(userId), null) as PrivateKey?
        } catch (e: Exception) {
            return null
        }
    }

    fun getPublicKey(userId: String): PublicKey? {
        try {
            val keyStore = getKeyStore()
            return keyStore.getCertificate(keyAlias(userId))?.publicKey
        } catch (e: Exception) {
            return null
        }
    }

    fun deleteKeyPair(userId: String): Boolean {
        try {
            val keyStore = getKeyStore()
            if (keyStore.containsAlias(keyAlias(userId))) {
                keyStore.deleteEntry(keyAlias(userId))
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun hasKeyPair(userId: String): Boolean {
        try {
            return getKeyStore().containsAlias(keyAlias(userId))
        } catch (e: Exception) {
            return false
        }
    }

    sealed class KeyPairResult {
        data class Success(val privateKey: PrivateKey, val publicKey: PublicKey) : KeyPairResult()
        data class Error(val exception: Exception) : KeyPairResult()
    }

    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun privateKeyToBase64(privateKey: PrivateKey): String {
        return Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
    }
}
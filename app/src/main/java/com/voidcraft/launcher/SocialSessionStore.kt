package com.voidcraft.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val SOCIAL_SESSION_PREFS = "voidcraft_secure_social_session"
private const val SOCIAL_SESSION_CIPHERTEXT = "token_ciphertext_v1"
private const val SOCIAL_SESSION_KEY_ALIAS = "com.voidcraft.launcher.social_session.v1"
private const val SOCIAL_SESSION_IV_BYTES = 12

/**
 * Keeps the online Social Hub bearer token encrypted with a non-exportable Android Keystore key.
 * Backup is disabled in the app manifest; the encrypted value alone is not enough to recover a token.
 */
internal class SocialSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        SOCIAL_SESSION_PREFS,
        Context.MODE_PRIVATE
    )

    fun readToken(): String? {
        val encoded = preferences.getString(SOCIAL_SESSION_CIPHERTEXT, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= SOCIAL_SESSION_IV_BYTES) {
                discardCiphertext()
                return null
            }

            val key = existingKey() ?: run {
                discardCiphertext()
                return null
            }
            val iv = payload.copyOfRange(0, SOCIAL_SESSION_IV_BYTES)
            val encrypted = payload.copyOfRange(SOCIAL_SESSION_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
                ?: run {
                    discardCiphertext()
                    null
                }
        } catch (_: Exception) {
            // Fail closed if a token is corrupted or the Android Keystore key is unavailable.
            discardCiphertext()
            null
        }
    }

    fun writeToken(token: String) {
        val cleanToken = token.trim()
        require(cleanToken.isNotEmpty()) { "The Social Hub returned an empty session token." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(cleanToken.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.size == SOCIAL_SESSION_IV_BYTES) { "Android Keystore returned an unexpected AES-GCM IV." }

        val payload = iv + encrypted
        val saved = preferences.edit()
            .putString(SOCIAL_SESSION_CIPHERTEXT, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not save the encrypted Social Hub session." }
    }

    fun clear() {
        val saved = preferences.edit().remove(SOCIAL_SESSION_CIPHERTEXT).commit()
        runCatching {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(SOCIAL_SESSION_KEY_ALIAS)) {
                keyStore.deleteEntry(SOCIAL_SESSION_KEY_ALIAS)
            }
        }
        check(saved) { "Could not clear the encrypted Social Hub session." }
    }

    private fun discardCiphertext() {
        preferences.edit().remove(SOCIAL_SESSION_CIPHERTEXT).apply()
    }

    private fun existingKey(): SecretKey? = runCatching {
        loadKeyStore().getKey(SOCIAL_SESSION_KEY_ALIAS, null) as? SecretKey
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            SOCIAL_SESSION_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}

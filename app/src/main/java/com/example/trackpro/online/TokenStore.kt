package com.example.trackpro.online

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Everything needed to act as the signed-in user, and to show who that is. */
@Serializable
data class StoredSession(
    val accessToken: String,
    /** Epoch ms. */
    val accessExpiresAt: Long,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val displayName: String,
)

interface TokenStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

/**
 * Keeps the TrackBoard session encrypted at rest with an AES-GCM key held in the Android
 * Keystore, so the key itself never leaves secure hardware where the device has it.
 *
 * Hand-rolled rather than `EncryptedSharedPreferences` because that library is deprecated.
 * The refresh token is worth protecting: it lasts 90 days, and a copy lifted from a backup
 * would sign in as this driver until it rotated.
 *
 * Any failure to decrypt — a wiped Keystore after a factory reset restore, a corrupted value —
 * reads as signed out. Losing a sign-in is recoverable; crashing on launch is not.
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    // A backed-up copy is harmless: the Keystore key does not travel with a backup, so a
    // restored blob fails to decrypt and simply reads as signed out.
    private val prefs = context.getSharedPreferences("trackboard_session", Context.MODE_PRIVATE)

    override fun load(): StoredSession? {
        val stored = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipherText = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            val plain = String(cipher.doFinal(cipherText), Charsets.UTF_8)
            OkHttpTrackBoardApi.json.decodeFromString(StoredSession.serializer(), plain)
        }.onFailure {
            Log.w(TAG, "Stored TrackBoard session unreadable; treating as signed out", it)
            clear()
        }.getOrNull()
    }

    override fun save(session: StoredSession) {
        val plain = OkHttpTrackBoardApi.json.encodeToString(StoredSession.serializer(), session)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = cipher.iv + cipherText
        // commit, not apply: a rotated refresh token must be on disk before it is relied on,
        // or a crash in between leaves only the revoked one.
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_BLOB).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "KeystoreTokenStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "trackboard_session_key"
        const val KEY_BLOB = "session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

package br.com.thigas.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SessionVault {
    data class Snapshot(
        val url: String,
        val cookies: String,
        val localStorage: String,
        val sessionStorage: String,
        val savedAt: Long
    )

    private const val PREFS = "thigas_session_vault"
    private const val VALUE = "portal_session"
    private const val KEY_ALIAS = "thigas_portal_session_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun save(
        context: Context,
        url: String,
        cookies: String,
        localStorage: String,
        sessionStorage: String
    ) {
        val json = JSONObject().apply {
            put("url", url)
            put("cookies", cookies)
            put("localStorage", localStorage)
            put("sessionStorage", sessionStorage)
            put("savedAt", System.currentTimeMillis())
        }.toString()

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(VALUE, encrypt(json))
            .apply()
    }

    fun load(context: Context): Snapshot? {
        val encrypted = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(VALUE, null)
            ?: return null

        return runCatching {
            val json = JSONObject(decrypt(encrypted))
            Snapshot(
                url = json.optString("url"),
                cookies = json.optString("cookies"),
                localStorage = json.optString("localStorage", "{}"),
                sessionStorage = json.optString("sessionStorage", "{}"),
                savedAt = json.optLong("savedAt", 0L)
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(VALUE)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .build()
        )

        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv = Base64.encodeToString(
            cipher.iv,
            Base64.NO_WRAP
        )

        val data = Base64.encodeToString(
            cipher.doFinal(
                plain.toByteArray(Charsets.UTF_8)
            ),
            Base64.NO_WRAP
        )

        return "$iv:$data"
    }

    private fun decrypt(value: String): String {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2)

        val iv = Base64.decode(
            parts[0],
            Base64.NO_WRAP
        )

        val data = Base64.decode(
            parts[1],
            Base64.NO_WRAP
        )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, iv)
        )

        return String(
            cipher.doFinal(data),
            Charsets.UTF_8
        )
    }
}

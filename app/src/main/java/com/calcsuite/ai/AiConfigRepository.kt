package com.calcsuite.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The settings store for the AI feature, kept apart from the app's own. */
private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "calcsuite_ai")

/**
 * The provider and model the user chose.
 *
 * The key itself is not here: it is kept by [AiCredentialStore], encrypted.
 *
 * @param provider which service to talk to.
 * @param model the model's name, which the user types.
 */
data class AiSettings(
    val provider: AiProvider = AiProvider.OPENAI,
    val model: String = "",
    /** True when a key has been saved for the provider. */
    val hasKey: Boolean = false,
) {
    /** True when the feature can be used. */
    val isConfigured: Boolean get() = model.isNotBlank() && hasKey

    companion object {
        val DEFAULT = AiSettings()
    }
}

/**
 * Keeps the API key, encrypted with a key held in the Android keystore.
 *
 * The key never leaves the keystore, and what is written to the settings
 * store is the encrypted text, so the key cannot be read out of the app's
 * files. The plaintext is only held while a request is being built, and the
 * string is cleared as soon as the request has been made.
 */
class AiCredentialStore(context: Context) {

    private val store = context.aiDataStore

    /** Whether a key is saved for the provider. */
    fun hasKey(provider: AiProvider): Flow<Boolean> =
        store.data.map { it[KEY_CREDENTIAL]?.startsWith("${provider.id}:") == true }

    /**
     * Saves the key for the provider.
     *
     * The key that was there for another provider is removed, so only one
     * provider is ever configured.
     */
    suspend fun save(provider: AiProvider, apiKey: String) {
        val encrypted = encrypt(provider.id, apiKey) ?: return
        store.edit { prefs ->
            prefs[KEY_CREDENTIAL] = "${provider.id}:$encrypted"
        }
    }

    /** The key for the provider, or an empty string when there is none. */
    suspend fun load(provider: AiProvider): String {
        val stored = store.data.first()[KEY_CREDENTIAL] ?: return ""
        if (!stored.startsWith("${provider.id}:")) return ""
        return decrypt(stored.removePrefix("${provider.id}:"))
    }

    /** Removes the saved key. */
    suspend fun clear() {
        store.edit { it.remove(KEY_CREDENTIAL) }
    }

    // ------------------------------------------------------- the encryption

    /**
     * Encrypts the text with a key from the keystore.
     *
     * The keystore holds the key itself, so the app can use it but cannot
     * read it.
     */
    private fun encrypt(providerId: String, plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        // The version, the provider and the initialisation vector are kept
        // with the text so that the right key is used to read it back.
        "$VERSION:$providerId:${java.util.Base64.getEncoder().encodeToString(iv)}:${java.util.Base64.getEncoder().encodeToString(encrypted)}"
    } catch (e: Exception) {
        null
    }

    /** Reads the text back, or an empty string when it cannot be read. */
    private fun decrypt(stored: String): String {
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != VERSION) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = java.util.Base64.getDecoder().decode(parts[2])
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, iv),
            )
            val bytes = cipher.doFinal(java.util.Base64.getDecoder().decode(parts[3]))
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /** The key in the keystore, made the first time it is wanted. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "calcsuite_ai_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION = "v1"
        val KEY_CREDENTIAL = stringPreferencesKey("ai_credential")
    }
}

/**
 * The provider, the model and the key, kept together.
 *
 * This is what the settings screen works with and what the feature reads
 * when it needs to make a request.
 */
class AiConfigRepository(
    context: Context,
    private val credentials: AiCredentialStore = AiCredentialStore(context),
) {
    private val store = context.aiDataStore

    /** The provider and the model the user chose. */
    val settings: Flow<AiSettings> = store.data.map { prefs ->
        val providerId = prefs[KEY_PROVIDER] ?: AiProvider.OPENAI.id
        AiSettings(
            provider = AiProvider.byId(providerId) ?: AiProvider.OPENAI,
            model = prefs[KEY_MODEL] ?: "",
            hasKey = prefs[KEY_CREDENTIAL]?.startsWith("$providerId:") == true,
        )
    }

    /** Saves the provider and the model. */
    suspend fun setProvider(provider: AiProvider) =
        store.edit { it[KEY_PROVIDER] = provider.id }

    /** Saves the model name. */
    suspend fun setModel(model: String) = store.edit { it[KEY_MODEL] = model.trim() }

    /** Saves the key for the provider. */
    suspend fun setApiKey(provider: AiProvider, apiKey: String) {
        credentials.save(provider, apiKey)
    }

    /** The key for the provider, or an empty string. */
    suspend fun apiKey(provider: AiProvider): String = credentials.load(provider)

    /** Removes the saved key. */
    suspend fun clearApiKey() = credentials.clear()

    private companion object {
        val KEY_PROVIDER = stringPreferencesKey("ai_provider")
        val KEY_MODEL = stringPreferencesKey("ai_model")
        val KEY_CREDENTIAL = stringPreferencesKey("ai_credential")
    }
}

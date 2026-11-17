package app.kanakku.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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

data class Prefs(
    val onboarded: Boolean = false,
    val lastScanAt: Long = 0,
    val salaryMinMinor: Long = 40_000_00,
    val salaryMaxMinor: Long = 60_000_00,
    val shiftLateSalary: Boolean = true,
    val hasGeminiKey: Boolean = false,
    val geminiModel: String = DEFAULT_MODEL,
    val aiCategorise: Boolean = true,
    /** 0 = no spending limit set. */
    val monthlyLimitMinor: Long = 0,
    /** Highest step of the learning track the user has marked done. */
    val learnStep: Int = 0,
) {
    companion object {
        /** Google's moving alias for the current Flash model, so the app keeps working as models are retired. */
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}

/** Encrypts small secrets so they are not stored in the clear. */
interface SecretBox {
    fun seal(plain: String): String
    fun open(sealed: String): String?
}

/** AES-GCM under a key that never leaves the Android Keystore. */
class KeystoreSecretBox : SecretBox {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = try {
        val (iv, body) = sealed.split(':', limit = 2)
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ALIAS = "kanakku.secrets.v1"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

class Settings(context: Context, private val box: SecretBox) {
    private val store = context.applicationContext.store

    val prefs: Flow<Prefs> = store.data.map { p ->
        Prefs(
            onboarded = p[ONBOARDED] ?: false,
            lastScanAt = p[LAST_SCAN] ?: 0,
            salaryMinMinor = p[SALARY_MIN] ?: 40_000_00,
            salaryMaxMinor = p[SALARY_MAX] ?: 60_000_00,
            shiftLateSalary = p[SHIFT_SALARY] ?: true,
            hasGeminiKey = !p[GEMINI_KEY].isNullOrEmpty(),
            geminiModel = p[GEMINI_MODEL] ?: Prefs.DEFAULT_MODEL,
            aiCategorise = p[AI_CATEGORISE] ?: true,
            monthlyLimitMinor = p[MONTHLY_LIMIT] ?: 0,
            learnStep = p[LEARN_STEP] ?: 0,
        )
    }

    suspend fun current(): Prefs = prefs.first()

    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = true }
    suspend fun setLastScan(at: Long) = store.edit { it[LAST_SCAN] = at }
    suspend fun setSalaryRange(minMinor: Long, maxMinor: Long) = store.edit {
        it[SALARY_MIN] = minOf(minMinor, maxMinor)
        it[SALARY_MAX] = maxOf(minMinor, maxMinor)
    }
    suspend fun setShiftLateSalary(on: Boolean) = store.edit { it[SHIFT_SALARY] = on }
    suspend fun setGeminiModel(model: String) = store.edit { it[GEMINI_MODEL] = model.trim().ifEmpty { Prefs.DEFAULT_MODEL } }
    suspend fun setAiCategorise(on: Boolean) = store.edit { it[AI_CATEGORISE] = on }
    suspend fun setMonthlyLimit(minor: Long) = store.edit { it[MONTHLY_LIMIT] = minor.coerceAtLeast(0) }
    suspend fun setLearnStep(step: Int) = store.edit { it[LEARN_STEP] = step.coerceAtLeast(0) }

    /** Stores the key encrypted; an empty value removes it. Returns false if it could not be protected. */
    suspend fun setGeminiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            store.edit { it.remove(GEMINI_KEY) }
            return true
        }
        val sealed = try {
            box.seal(trimmed)
        } catch (_: Exception) {
            return false
        }
        store.edit { it[GEMINI_KEY] = sealed }
        return true
    }

    suspend fun geminiKey(): String? = store.data.first()[GEMINI_KEY]?.let(box::open)?.takeIf { it.isNotBlank() }

    suspend fun clear() = store.edit { it.clear() }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val LAST_SCAN = longPreferencesKey("last_scan")
        val SALARY_MIN = longPreferencesKey("salary_min")
        val SALARY_MAX = longPreferencesKey("salary_max")
        val SHIFT_SALARY = booleanPreferencesKey("shift_salary")
        val GEMINI_KEY = stringPreferencesKey("gemini_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val AI_CATEGORISE = booleanPreferencesKey("ai_categorise")
        val MONTHLY_LIMIT = longPreferencesKey("monthly_limit")
        val LEARN_STEP = intPreferencesKey("learn_step")
    }
}

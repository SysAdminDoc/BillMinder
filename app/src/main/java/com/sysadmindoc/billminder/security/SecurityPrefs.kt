package com.sysadmindoc.billminder.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class SecurityState(
    val biometricEnabled: Boolean,
    val hasPin: Boolean,
    val hasDuressPin: Boolean,
    val autoLockMinutes: Int,
    val maskExternalContent: Boolean,
    val hideAmountsInApp: Boolean,
    val pinBlockedUntilMillis: Long
) {
    val lockConfigured: Boolean get() = biometricEnabled || hasPin
}

sealed interface PinUnlockResult {
    data object Unlocked : PinUnlockResult
    data object Duress : PinUnlockResult
    data class Incorrect(val blockedUntilMillis: Long? = null) : PinUnlockResult
    data class Blocked(val untilMillis: Long) : PinUnlockResult
}

object SecurityPrefs {
    private const val PREFS_NAME = "billminder_prefs"
    private const val KEY_LEGACY_PIN = "pin_code"
    private const val KEY_PIN_RECORD = "pin_record"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_DURESS_RECORD = "duress_pin_record"
    private const val KEY_DURESS_HASH = "duress_pin_hash"
    private const val KEY_DURESS_SALT = "duress_pin_salt"
    private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_FAILED_PIN_ATTEMPTS = "failed_pin_attempts"
    private const val KEY_PIN_BLOCKED_UNTIL = "pin_blocked_until"
    private const val KEY_MASK_EXTERNAL_CONTENT = "mask_external_content"
    private const val KEY_HIDE_AMOUNTS_IN_APP = "hide_amounts_in_app"

    private const val PIN_RECORD_VERSION = "v2"
    private const val PIN_RECORD_ALGORITHM = "pbkdf2-sha256"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val PBKDF2_BITS = 256
    private const val SALT_BYTES = 16
    private const val MAX_STORED_ITERATIONS = 2_000_000

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false) && hasPin(context)

    fun setBiometricEnabled(context: Context, enabled: Boolean): Boolean {
        if (enabled && !hasPin(context)) return false
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        return true
    }

    fun hasPin(context: Context): Boolean {
        val p = prefs(context)
        return p.getString(KEY_PIN_RECORD, null) != null ||
            p.getString(KEY_PIN_HASH, null) != null ||
            p.getString(KEY_LEGACY_PIN, null) != null
    }

    fun setPin(context: Context, pin: String): Boolean {
        if (!isValidPin(pin) || verifyDuressPin(context, pin)) return false
        storeRegularPin(context, pin)
        resetFailedAttempts(context)
        return true
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        p.getString(KEY_PIN_RECORD, null)?.let { record ->
            val verification = verifyRecord(pin, record)
            if (verification.matches && verification.needsUpgrade) storeRegularPin(context, pin)
            return verification.matches
        }

        val salt = p.getString(KEY_PIN_SALT, null)
        val expectedHash = p.getString(KEY_PIN_HASH, null)
        if (salt != null && expectedHash != null) {
            val matches = hashesEqual(expectedHash, hashLegacyPin(pin, salt))
            if (matches) storeRegularPin(context, pin)
            return matches
        }

        val legacyPin = p.getString(KEY_LEGACY_PIN, null)
        if (legacyPin != null && textEqual(legacyPin, pin)) {
            storeRegularPin(context, pin)
            return true
        }
        return false
    }

    fun hasDuressPin(context: Context): Boolean {
        val p = prefs(context)
        return p.getString(KEY_DURESS_RECORD, null) != null ||
            p.getString(KEY_DURESS_HASH, null) != null
    }

    fun setDuressPin(context: Context, pin: String): Boolean {
        if (!isValidPin(pin) || verifyPin(context, pin)) return false
        storeDuressPin(context, pin)
        return true
    }

    fun verifyDuressPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        p.getString(KEY_DURESS_RECORD, null)?.let { record ->
            val verification = verifyRecord(pin, record)
            if (verification.matches && verification.needsUpgrade) storeDuressPin(context, pin)
            return verification.matches
        }

        val salt = p.getString(KEY_DURESS_SALT, null) ?: return false
        val expectedHash = p.getString(KEY_DURESS_HASH, null) ?: return false
        val matches = hashesEqual(expectedHash, hashLegacyPin(pin, salt))
        if (matches) storeDuressPin(context, pin)
        return matches
    }

    fun getAutoLockMinutes(context: Context): Int =
        prefs(context).getInt(KEY_AUTO_LOCK_MINUTES, 0)

    fun setAutoLockMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_AUTO_LOCK_MINUTES, minutes).apply()
    }

    fun maskExternalContent(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASK_EXTERNAL_CONTENT, false)

    fun setMaskExternalContent(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASK_EXTERNAL_CONTENT, enabled).apply()
    }

    fun hideAmountsInApp(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_AMOUNTS_IN_APP, false)

    fun setHideAmountsInApp(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_AMOUNTS_IN_APP, enabled).apply()
    }

    fun readState(context: Context): SecurityState = SecurityState(
        biometricEnabled = isBiometricEnabled(context),
        hasPin = hasPin(context),
        hasDuressPin = hasDuressPin(context),
        autoLockMinutes = getAutoLockMinutes(context),
        maskExternalContent = maskExternalContent(context),
        hideAmountsInApp = hideAmountsInApp(context),
        pinBlockedUntilMillis = prefs(context).getLong(KEY_PIN_BLOCKED_UNTIL, 0L)
    )

    fun observe(context: Context): Flow<SecurityState> {
        val appContext = context.applicationContext
        return callbackFlow {
            val preferences = prefs(appContext)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                trySend(readState(appContext))
            }
            trySend(readState(appContext))
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.distinctUntilChanged()
    }

    @SuppressLint("ApplySharedPref")
    fun attemptUnlock(
        context: Context,
        pin: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PinUnlockResult {
        val p = prefs(context)
        val blockedUntil = p.getLong(KEY_PIN_BLOCKED_UNTIL, 0L)
        if (blockedUntil > nowMillis) return PinUnlockResult.Blocked(blockedUntil)

        if (verifyPin(context, pin)) {
            resetFailedAttempts(context)
            return PinUnlockResult.Unlocked
        }
        if (verifyDuressPin(context, pin)) {
            resetFailedAttempts(context)
            return PinUnlockResult.Duress
        }

        val failures = (p.getInt(KEY_FAILED_PIN_ATTEMPTS, 0) + 1).coerceAtMost(100)
        val delay = backoffDelayMillis(failures)
        val nextAllowedAt = if (delay == 0L) 0L else nowMillis + delay
        p.edit()
            .putInt(KEY_FAILED_PIN_ATTEMPTS, failures)
            .putLong(KEY_PIN_BLOCKED_UNTIL, nextAllowedAt)
            .commit()
        return PinUnlockResult.Incorrect(nextAllowedAt.takeIf { it > 0L })
    }

    @SuppressLint("ApplySharedPref")
    fun resetFailedAttempts(context: Context) {
        prefs(context).edit()
            .remove(KEY_FAILED_PIN_ATTEMPTS)
            .remove(KEY_PIN_BLOCKED_UNTIL)
            .commit()
    }

    private fun isValidPin(pin: String): Boolean = pin.length in 4..6 && pin.all(Char::isDigit)

    @SuppressLint("ApplySharedPref")
    private fun storeRegularPin(context: Context, pin: String) {
        prefs(context).edit()
            .putString(KEY_PIN_RECORD, createRecord(pin))
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_LEGACY_PIN)
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    private fun storeDuressPin(context: Context, pin: String) {
        prefs(context).edit()
            .putString(KEY_DURESS_RECORD, createRecord(pin))
            .remove(KEY_DURESS_HASH)
            .remove(KEY_DURESS_SALT)
            .commit()
    }

    private fun createRecord(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derivePin(pin, salt, PBKDF2_ITERATIONS)
        return listOf(
            PIN_RECORD_VERSION,
            PIN_RECORD_ALGORITHM,
            PBKDF2_ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString("|")
    }

    private fun verifyRecord(pin: String, record: String): RecordVerification {
        val parts = record.split('|')
        if (parts.size != 5 || parts[0] != PIN_RECORD_VERSION || parts[1] != PIN_RECORD_ALGORITHM) {
            return RecordVerification(false, false)
        }
        val iterations = parts[2].toIntOrNull()
            ?.takeIf { it in 10_000..MAX_STORED_ITERATIONS }
            ?: return RecordVerification(false, false)
        return try {
            val salt = Base64.decode(parts[3], Base64.NO_WRAP)
            val expected = Base64.decode(parts[4], Base64.NO_WRAP)
            if (salt.size < SALT_BYTES || expected.size != PBKDF2_BITS / 8) {
                RecordVerification(false, false)
            } else {
                RecordVerification(
                    matches = MessageDigest.isEqual(expected, derivePin(pin, salt, iterations)),
                    needsUpgrade = iterations < PBKDF2_ITERATIONS
                )
            }
        } catch (_: IllegalArgumentException) {
            RecordVerification(false, false)
        }
    }

    private fun derivePin(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_BITS)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun hashLegacyPin(pin: String, saltText: String): String {
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun backoffDelayMillis(failures: Int): Long = when (failures) {
        in 0..4 -> 0L
        5 -> 30_000L
        6 -> 60_000L
        7 -> 5 * 60_000L
        8 -> 15 * 60_000L
        9 -> 30 * 60_000L
        else -> 60 * 60_000L
    }

    private fun hashesEqual(expected: String, actual: String): Boolean = try {
        MessageDigest.isEqual(
            Base64.decode(expected, Base64.NO_WRAP),
            Base64.decode(actual, Base64.NO_WRAP)
        )
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun textEqual(expected: String, actual: String): Boolean = MessageDigest.isEqual(
        expected.toByteArray(Charsets.UTF_8),
        actual.toByteArray(Charsets.UTF_8)
    )

    private data class RecordVerification(
        val matches: Boolean,
        val needsUpgrade: Boolean
    )
}

package com.sysadmindoc.billminder.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object SecurityPrefs {
    private const val PREFS_NAME = "billminder_prefs"
    private const val KEY_LEGACY_PIN = "pin_code"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_DURESS_HASH = "duress_pin_hash"
    private const val KEY_DURESS_SALT = "duress_pin_salt"
    private const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun hasPin(context: Context): Boolean {
        val p = prefs(context)
        return p.getString(KEY_PIN_HASH, null) != null || p.getString(KEY_LEGACY_PIN, null) != null
    }

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        prefs(context).edit()
            .putString(KEY_PIN_SALT, saltText)
            .putString(KEY_PIN_HASH, hashPin(pin, saltText))
            .remove(KEY_LEGACY_PIN)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_PIN_SALT, null)
        val expectedHash = p.getString(KEY_PIN_HASH, null)
        if (salt != null && expectedHash != null) {
            return hashesEqual(expectedHash, hashPin(pin, salt))
        }

        val legacyPin = p.getString(KEY_LEGACY_PIN, null)
        if (legacyPin != null && legacyPin == pin) {
            setPin(context, pin)
            return true
        }
        return false
    }

    fun hasDuressPin(context: Context): Boolean =
        prefs(context).getString(KEY_DURESS_HASH, null) != null

    fun setDuressPin(context: Context, pin: String): Boolean {
        if (pin.isBlank() || verifyPin(context, pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        prefs(context).edit()
            .putString(KEY_DURESS_SALT, saltText)
            .putString(KEY_DURESS_HASH, hashPin(pin, saltText))
            .apply()
        return true
    }

    fun verifyDuressPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_DURESS_SALT, null) ?: return false
        val expectedHash = p.getString(KEY_DURESS_HASH, null) ?: return false
        return hashesEqual(expectedHash, hashPin(pin, salt))
    }

    fun getAutoLockMinutes(context: Context): Int =
        prefs(context).getInt(KEY_AUTO_LOCK_MINUTES, 0)

    fun setAutoLockMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_AUTO_LOCK_MINUTES, minutes).apply()
    }

    private fun hashPin(pin: String, saltText: String): String {
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun hashesEqual(expected: String, actual: String): Boolean = try {
        MessageDigest.isEqual(
            Base64.decode(expected, Base64.NO_WRAP),
            Base64.decode(actual, Base64.NO_WRAP)
        )
    } catch (_: IllegalArgumentException) {
        false
    }
}

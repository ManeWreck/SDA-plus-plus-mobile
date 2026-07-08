package com.manewreck.sdapp.mobile.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class AppLockRepository(
    private val preferencesRepository: AppPreferencesRepository,
) {
    fun hasPin(settings: AppSettings = preferencesRepository.loadSettings()): Boolean {
        return !settings.security.pinHash.isNullOrBlank() && !settings.security.pinSalt.isNullOrBlank()
    }

    fun verifyPin(pin: String, settings: AppSettings = preferencesRepository.loadSettings()): Boolean {
        val salt = settings.security.pinSalt ?: return false
        val hash = settings.security.pinHash ?: return false
        return hash == hashPin(pin, salt)
    }

    fun updatePin(settings: AppSettings, pin: String): AppSettings {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        return settings.copy(
            security = settings.security.copy(
                pinSalt = saltBase64,
                pinHash = hashPin(pin, saltBase64),
            ),
        )
    }

    fun setBiometricsEnabled(settings: AppSettings, enabled: Boolean): AppSettings {
        return settings.copy(
            security = settings.security.copy(
                biometricsEnabled = enabled,
            ),
        )
    }

    private fun hashPin(pin: String, saltBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.getDecoder().decode(saltBase64))
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest.digest())
    }
}

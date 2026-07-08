package com.manewreck.sdapp.mobile.data

import android.content.Context
import com.manewreck.sdapp.mobile.core.sync.CloudProviderType
import org.json.JSONObject

data class CloudSyncSettings(
    val provider: CloudProviderType = CloudProviderType.WebDav,
    val url: String = "",
    val login: String = "",
    val password: String = "",
    val remotePath: String = "SDAppVault",
)

enum class AccountSortMode {
    Alphabetical,
    Level,
}

enum class AppLanguage {
    English,
    Russian,
}

data class AppSecuritySettings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricsEnabled: Boolean = false,
)

data class AppSettings(
    val language: AppLanguage = AppLanguage.English,
    val cloudSync: CloudSyncSettings = CloudSyncSettings(),
    val security: AppSecuritySettings = AppSecuritySettings(),
    val accountSortMode: AccountSortMode = AccountSortMode.Alphabetical,
    val accountSearchEnabled: Boolean = true,
)

class AppPreferencesRepository(
    context: Context,
) {
    private val crypto = SecureFileCrypto(KEY_ALIAS)
    private val settingsFile = context.filesDir.resolve("app-settings.enc")

    fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) {
            return AppSettings()
        }

        return runCatching {
            val root = JSONObject(String(crypto.decrypt(settingsFile.readBytes()), Charsets.UTF_8))
            val providerName = root.optString(KEY_PROVIDER, CloudProviderType.WebDav.name)
            val provider = CloudProviderType.entries.firstOrNull { it.name == providerName } ?: CloudProviderType.WebDav
            val languageName = root.optString(KEY_LANGUAGE, AppLanguage.English.name)
            val language = AppLanguage.entries.firstOrNull { it.name == languageName } ?: AppLanguage.English
            val sortModeName = root.optString(KEY_ACCOUNT_SORT_MODE, AccountSortMode.Alphabetical.name)
            val accountSortMode = AccountSortMode.entries.firstOrNull { it.name == sortModeName } ?: AccountSortMode.Alphabetical
            val security = root.optJSONObject(KEY_SECURITY)

            AppSettings(
                language = language,
                cloudSync = CloudSyncSettings(
                    provider = provider,
                    url = root.optString(KEY_URL, ""),
                    login = root.optString(KEY_LOGIN, ""),
                    password = root.optString(KEY_PASSWORD, ""),
                    remotePath = root.optString(KEY_REMOTE_PATH, "SDAppVault"),
                ),
                security = AppSecuritySettings(
                    pinHash = security?.optString(KEY_PIN_HASH).takeUnless { it.isNullOrBlank() },
                    pinSalt = security?.optString(KEY_PIN_SALT).takeUnless { it.isNullOrBlank() },
                    biometricsEnabled = security?.optBoolean(KEY_BIOMETRICS_ENABLED, false) == true,
                ),
                accountSortMode = accountSortMode,
                accountSearchEnabled = root.optBoolean(KEY_ACCOUNT_SEARCH_ENABLED, true),
            )
        }.getOrElse { AppSettings() }
    }

    fun saveSettings(settings: AppSettings) {
        val root = JSONObject()
            .put(KEY_LANGUAGE, settings.language.name)
            .put(KEY_PROVIDER, settings.cloudSync.provider.name)
            .put(KEY_URL, settings.cloudSync.url)
            .put(KEY_LOGIN, settings.cloudSync.login)
            .put(KEY_PASSWORD, settings.cloudSync.password)
            .put(KEY_REMOTE_PATH, settings.cloudSync.remotePath)
            .put(KEY_ACCOUNT_SORT_MODE, settings.accountSortMode.name)
            .put(KEY_ACCOUNT_SEARCH_ENABLED, settings.accountSearchEnabled)
            .put(
                KEY_SECURITY,
                JSONObject()
                    .put(KEY_PIN_HASH, settings.security.pinHash)
                    .put(KEY_PIN_SALT, settings.security.pinSalt)
                    .put(KEY_BIOMETRICS_ENABLED, settings.security.biometricsEnabled),
            )

        val encrypted = crypto.encrypt(root.toString().toByteArray(Charsets.UTF_8))
        settingsFile.writeBytes(encrypted)
    }

    private companion object {
        const val KEY_ALIAS = "sdapp_settings_key"
        const val KEY_LANGUAGE = "language"
        const val KEY_PROVIDER = "cloud_provider"
        const val KEY_URL = "cloud_url"
        const val KEY_LOGIN = "cloud_login"
        const val KEY_PASSWORD = "cloud_password"
        const val KEY_REMOTE_PATH = "cloud_remote_path"
        const val KEY_ACCOUNT_SORT_MODE = "account_sort_mode"
        const val KEY_ACCOUNT_SEARCH_ENABLED = "account_search_enabled"
        const val KEY_SECURITY = "security"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
    }
}

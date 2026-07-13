package com.manewreck.sdapp.mobile.core.model

data class VaultAccount(
    val steamId: String,
    val accountName: String,
    val sharedSecret: String,
    val identitySecret: String? = null,
    val deviceId: String? = null,
    val session: SteamSessionSnapshot? = null,
    val revocationCode: String? = null,
    val serialNumber: String? = null,
    val uri: String? = null,
    val isFavorite: Boolean = false,
    val hasCloudBackup: Boolean = false,
    val hasSessionSnapshot: Boolean = false,
    val importedAtUtc: String? = null,
)

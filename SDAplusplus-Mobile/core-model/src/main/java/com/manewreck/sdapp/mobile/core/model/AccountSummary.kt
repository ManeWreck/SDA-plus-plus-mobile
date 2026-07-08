package com.manewreck.sdapp.mobile.core.model

data class AccountSummary(
    val steamId: String,
    val accountName: String,
    val isFavorite: Boolean,
    val hasCloudBackup: Boolean,
    val hasSessionSnapshot: Boolean = false,
    val importedAtUtc: String? = null,
)

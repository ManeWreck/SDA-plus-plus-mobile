package com.manewreck.sdapp.mobile.core.model

data class SyncState(
    val providerName: String,
    val lastAction: String?,
    val lastSyncDisplay: String?,
    val isConnected: Boolean,
)

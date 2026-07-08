package com.manewreck.sdapp.mobile.ui

import com.manewreck.sdapp.mobile.core.model.AccountSummary
import com.manewreck.sdapp.mobile.core.model.SyncState
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import com.manewreck.sdapp.mobile.data.AppSettings
import com.manewreck.sdapp.mobile.data.SteamPublicProfile

data class MobileUiState(
    val accounts: List<AccountSummary> = emptyList(),
    val publicProfiles: Map<String, SteamPublicProfile> = emptyMap(),
    val selectedAccountId: String? = null,
    val selectedAccount: VaultAccount? = null,
    val currentCode: String = "-----",
    val secondsRemaining: Int = 0,
    val statusMessage: String? = null,
    val isImporting: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val syncState: SyncState = SyncState(
        providerName = "WebDAV",
        lastAction = null,
        lastSyncDisplay = null,
        isConnected = false,
    ),
    val isUnlocked: Boolean = true,
    val pinDraft: String = "",
    val lockPinDraft: String = "",
    val biometricAvailable: Boolean = false,
)

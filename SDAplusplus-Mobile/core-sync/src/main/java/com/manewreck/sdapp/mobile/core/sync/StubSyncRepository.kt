package com.manewreck.sdapp.mobile.core.sync

import com.manewreck.sdapp.mobile.core.model.SyncState

class StubSyncRepository : SyncRepository {
    override suspend fun getState(): SyncState {
        return SyncState(
            providerName = "WebDAV",
            lastAction = null,
            lastSyncDisplay = "Not synced yet",
            isConnected = false,
        )
    }

    override suspend fun pull(): Result<Unit> = Result.success(Unit)

    override suspend fun push(): Result<Unit> = Result.success(Unit)
}

package com.manewreck.sdapp.mobile.core.sync

import com.manewreck.sdapp.mobile.core.model.SyncState

interface SyncRepository {
    suspend fun getState(): SyncState
    suspend fun pull(): Result<Unit>
    suspend fun push(): Result<Unit>
}

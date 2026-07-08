package com.manewreck.sdapp.mobile.core.vault

import com.manewreck.sdapp.mobile.core.model.AccountSummary
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import com.manewreck.sdapp.mobile.core.model.VaultSummary
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    suspend fun unlock(passphrase: String): Result<VaultSummary>
    fun observeAccounts(): Flow<List<AccountSummary>>
    suspend fun listAccounts(): List<AccountSummary>
    suspend fun getAccount(steamId: String): VaultAccount?
    suspend fun importMaFile(rawJson: String): Result<AccountSummary>
}

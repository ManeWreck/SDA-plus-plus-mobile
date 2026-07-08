package com.manewreck.sdapp.mobile.core.vault

import com.manewreck.sdapp.mobile.core.model.AccountSummary
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import com.manewreck.sdapp.mobile.core.model.VaultSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class StubVaultRepository : VaultRepository {
    private val accounts = listOf(
        VaultAccount(
            steamId = "76561198000000001",
            accountName = "acheronineternity",
            sharedSecret = "AAAAAAAAAAAAAAAAAAAAAA==",
            isFavorite = true,
            hasCloudBackup = true,
        ),
        VaultAccount(
            steamId = "76561198000000002",
            accountName = "manewreck",
            sharedSecret = "AAAAAAAAAAAAAAAAAAAAAA==",
            hasCloudBackup = true,
        ),
    )
    private val accountState = MutableStateFlow(accounts.map(::toSummary))

    override suspend fun unlock(passphrase: String): Result<VaultSummary> {
        return Result.success(
            VaultSummary(
                accountCount = accounts.size,
                encrypted = true,
                lastUnlockedUtc = null,
            ),
        )
    }

    override fun observeAccounts(): Flow<List<AccountSummary>> = accountState

    override suspend fun listAccounts(): List<AccountSummary> {
        return accountState.value
    }

    override suspend fun getAccount(steamId: String): VaultAccount? {
        return accounts.firstOrNull { it.steamId == steamId }
    }

    override suspend fun importMaFile(rawJson: String): Result<AccountSummary> {
        return if (rawJson.isBlank()) {
            Result.failure(IllegalArgumentException("maFile payload is empty."))
        } else {
            Result.success(accountState.value.first())
        }
    }

    private fun toSummary(account: VaultAccount): AccountSummary {
        return AccountSummary(
            steamId = account.steamId,
            accountName = account.accountName,
            isFavorite = account.isFavorite,
            hasCloudBackup = account.hasCloudBackup,
            importedAtUtc = account.importedAtUtc,
        )
    }
}

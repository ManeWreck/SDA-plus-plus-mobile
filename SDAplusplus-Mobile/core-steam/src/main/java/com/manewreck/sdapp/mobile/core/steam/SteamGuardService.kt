package com.manewreck.sdapp.mobile.core.steam

import com.manewreck.sdapp.mobile.core.model.VaultAccount

interface SteamGuardService {
    suspend fun generateCode(sharedSecret: String): Result<SteamGuardCode>
    suspend fun approveQrPayload(account: VaultAccount, qrPayload: String): Result<Unit>
}

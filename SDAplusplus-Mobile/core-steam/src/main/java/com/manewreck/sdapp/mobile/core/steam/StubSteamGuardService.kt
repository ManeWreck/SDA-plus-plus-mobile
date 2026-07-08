package com.manewreck.sdapp.mobile.core.steam

import com.manewreck.sdapp.mobile.core.model.VaultAccount

class StubSteamGuardService : SteamGuardService {
    override suspend fun generateCode(sharedSecret: String): Result<SteamGuardCode> {
        return Result.success(SteamGuardCode(code = "AB12C", secondsRemaining = 22))
    }

    override suspend fun approveQrPayload(account: VaultAccount, qrPayload: String): Result<Unit> {
        return if (qrPayload.isBlank()) {
            Result.failure(IllegalArgumentException("QR payload is empty."))
        } else {
            Result.success(Unit)
        }
    }
}

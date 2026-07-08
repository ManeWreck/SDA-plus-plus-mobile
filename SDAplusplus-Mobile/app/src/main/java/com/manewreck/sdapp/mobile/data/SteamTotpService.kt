package com.manewreck.sdapp.mobile.data

import android.util.Base64
import com.manewreck.sdapp.mobile.core.model.SteamSessionSnapshot
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import com.manewreck.sdapp.mobile.core.steam.SteamGuardCode
import com.manewreck.sdapp.mobile.core.steam.SteamGuardService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SteamTotpService : SteamGuardService {
    private val httpClient = OkHttpClient()

    override suspend fun generateCode(sharedSecret: String): Result<SteamGuardCode> {
        return runCatching {
            val decodedSecret = Base64.decode(sharedSecret, Base64.DEFAULT)
            val currentTime = System.currentTimeMillis() / 1000L
            val timeSlice = currentTime / 30L
            val secondsRemaining = (30L - (currentTime % 30L)).toInt()

            val buffer = ByteBuffer.allocate(8).putLong(timeSlice).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(decodedSecret, "HmacSHA1"))
            val hash = mac.doFinal(buffer)
            val offset = hash.last().toInt() and 0x0F

            var fullCode = ByteBuffer.wrap(hash, offset, 4).int and 0x7FFFFFFF
            val alphabet = "23456789BCDFGHJKMNPQRTVWXY"
            val builder = StringBuilder(5)
            repeat(5) {
                builder.append(alphabet[fullCode % alphabet.length])
                fullCode /= alphabet.length
            }

            SteamGuardCode(
                code = builder.toString(),
                secondsRemaining = secondsRemaining,
            )
        }
    }

    override suspend fun approveQrPayload(account: VaultAccount, qrPayload: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = qrPayload.trim()
                require(payload.isNotEmpty()) { "QR payload is empty." }

                val match = MODERN_QR_REGEX.matchEntire(payload)
                    ?: when {
                        OPENID_QR_REGEX.containsMatchIn(payload) -> {
                            throw UnsupportedOperationException(
                                "Legacy OpenID QR is not supported in the mobile build yet.",
                            )
                        }

                        else -> {
                            throw IllegalArgumentException("The QR code does not contain a supported Steam login URL.")
                        }
                    }

                val session = account.session?.copy()
                    ?: throw IllegalStateException(
                        "The selected account does not contain a mobile web session snapshot. Re-import a full SDA .maFile with Session data.",
                    )

                ensureAccessToken(session)

                val version = match.groupValues[1].toInt()
                val clientId = match.groupValues[2].toULong()
                getAuthSessionInfo(clientId, session.accessToken.orEmpty())

                val steamId = session.steamId.ifBlank { account.steamId }.toLongOrNull()
                    ?: throw IllegalStateException("The selected account does not have a valid SteamID in its Session snapshot.")
                val signaturePayload = ByteBuffer.allocate(18)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(version.toShort())
                    .putLong(clientId.toLong())
                    .putLong(steamId)
                    .array()

                val signature = computeHmacSha256(account.sharedSecret, signaturePayload)
                updateAuthSessionWithMobileConfirmation(
                    version = version,
                    clientId = clientId,
                    steamId = steamId,
                    signature = signature,
                    accessToken = session.accessToken.orEmpty(),
                )
            }
        }
    }

    private fun ensureAccessToken(session: SteamSessionSnapshot) {
        val refreshToken = session.refreshToken
        val accessToken = session.accessToken

        if (refreshToken.isNullOrBlank()) {
            require(!accessToken.isNullOrBlank()) {
                "The selected account does not have a saved Steam web session. Re-import the full SDA .maFile first."
            }
            if (isTokenExpired(accessToken)) {
                throw IllegalStateException("The selected account access token has expired and cannot be refreshed from this import.")
            }
            return
        }

        if (isTokenExpired(refreshToken)) {
            throw IllegalStateException("The selected account session has expired. Import a fresh .maFile or refresh the session on desktop first.")
        }

        if (accessToken.isNullOrBlank() || isTokenExpired(accessToken)) {
            refreshAccessToken(session)
        }
    }

    private fun refreshAccessToken(session: SteamSessionSnapshot) {
        val body = FormBody.Builder()
            .add("refresh_token", session.refreshToken.orEmpty())
            .add("steamid", session.steamId)
            .add("renewal_type", "0")
            .build()

        val request = Request.Builder()
            .url("https://api.steampowered.com/IAuthenticationService/GenerateAccessTokenForApp/v1/")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam token refresh failed with HTTP ${response.code}")
            }

            val json = JSONObject(responseBody).getJSONObject("response")
            session.accessToken = json.optString("access_token").ifBlank {
                throw IllegalStateException("Steam did not return a new access token.")
            }
        }
    }

    private fun updateAuthSessionWithMobileConfirmation(
        version: Int,
        clientId: ULong,
        steamId: Long,
        signature: ByteArray,
        accessToken: String,
    ) {
        val body = FormBody.Builder()
            .add("version", version.toString())
            .add("client_id", clientId.toString())
            .add("steamid", steamId.toString())
            .add("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            .add("confirm", "true")
            .add("persistence", "1")
            .build()

        val request = Request.Builder()
            .url("https://api.steampowered.com/IAuthenticationService/UpdateAuthSessionWithMobileConfirmation/v1/?access_token=${urlEncode(accessToken)}")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam QR approval failed with HTTP ${response.code}")
            }

            JSONObject(responseBody)
        }
    }

    private fun getAuthSessionInfo(clientId: ULong, accessToken: String) {
        val body = FormBody.Builder()
            .add("client_id", clientId.toString())
            .build()

        val request = Request.Builder()
            .url("https://api.steampowered.com/IAuthenticationService/GetAuthSessionInfo/v1/?access_token=${urlEncode(accessToken)}")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam auth-session lookup failed with HTTP ${response.code}")
            }

            JSONObject(responseBody)
        }
    }

    private fun computeHmacSha256(sharedSecret: String, payload: ByteArray): ByteArray {
        val decodedSecret = Base64.decode(sharedSecret, Base64.DEFAULT)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(decodedSecret, "HmacSHA256"))
        return mac.doFinal(payload)
    }

    private fun isTokenExpired(token: String): Boolean {
        val expiry = extractTokenExpiry(token)
        return expiry == null || (System.currentTimeMillis() / 1000L) >= expiry
    }

    private fun extractTokenExpiry(token: String): Long? {
        val parts = token.split('.')
        if (parts.size < 2) {
            return null
        }

        val normalized = parts[1]
            .replace('-', '+')
            .replace('_', '/')
            .let { value ->
                val padding = (4 - value.length % 4) % 4
                value + "=".repeat(padding)
            }

        val payload = String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8)
        return JSONObject(payload).optLong("exp").takeIf { it > 0L }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        val MODERN_QR_REGEX = Regex(
            pattern = "^https?://s\\.team/q/(\\d+)/(\\d+)(?:\\?.*)?$",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        val OPENID_QR_REGEX = Regex(
            pattern = "^(steam://|https?://steamcommunity\\.com/openid/login)",
            options = setOf(RegexOption.IGNORE_CASE),
        )
    }
}

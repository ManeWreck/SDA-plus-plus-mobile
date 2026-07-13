package com.manewreck.sdapp.mobile.data

import android.util.Base64
import com.manewreck.sdapp.mobile.core.model.SteamSessionSnapshot
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import okhttp3.FormBody
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

internal data class AuthenticatedSteamSession(
    val client: OkHttpClient,
    val sessionId: String,
    val steamId: String,
)

internal class SteamWebSessionClient {
    fun create(account: VaultAccount): AuthenticatedSteamSession {
        val session = account.session?.copy()
            ?: throw IllegalStateException("This account does not contain a Steam web session.")
        ensureAccessToken(session)

        val steamId = session.steamId.ifBlank { account.steamId }
        require(steamId.toLongOrNull() != null) { "This account has an invalid SteamID." }
        val accessToken = session.accessToken.orEmpty()
        val sessionId = session.sessionId?.takeIf { it.isNotBlank() } ?: generateSessionId()
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

        listOf("steamcommunity.com", "store.steampowered.com").forEach { domain ->
            addCookie(cookieManager, domain, "steamLoginSecure", "$steamId%7C%7C$accessToken", secure = true)
            addCookie(cookieManager, domain, "sessionid", sessionId, secure = true)
            addCookie(cookieManager, domain, "mobileClient", "android", secure = true)
            addCookie(cookieManager, domain, "mobileClientVersion", "777777 3.10.3", secure = true)
        }

        return AuthenticatedSteamSession(
            client = OkHttpClient.Builder()
                .cookieJar(JavaNetCookieJar(cookieManager))
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build(),
            sessionId = sessionId,
            steamId = steamId,
        )
    }

    private fun ensureAccessToken(session: SteamSessionSnapshot) {
        val accessToken = session.accessToken
        if (!accessToken.isNullOrBlank() && !isTokenExpired(accessToken)) {
            return
        }
        val refreshToken = session.refreshToken
        require(!refreshToken.isNullOrBlank() && !isTokenExpired(refreshToken)) {
            "The saved Steam session has expired. Refresh it in SDA++ Desktop and sync again."
        }

        val body = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("steamid", session.steamId)
            .add("renewal_type", "0")
            .build()
        val request = Request.Builder()
            .url("https://api.steampowered.com/IAuthenticationService/GenerateAccessTokenForApp/v1/")
            .post(body)
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Steam token refresh failed with HTTP ${response.code}.")
            }
            session.accessToken = JSONObject(responseBody)
                .getJSONObject("response")
                .optString("access_token")
                .ifBlank { throw IllegalStateException("Steam did not return a new access token.") }
        }
    }

    private fun addCookie(
        manager: CookieManager,
        domain: String,
        name: String,
        value: String,
        secure: Boolean,
    ) {
        val cookie = HttpCookie(name, value).apply {
            path = "/"
            this.domain = domain
            isHttpOnly = true
            this.secure = secure
            version = 0
        }
        manager.cookieStore.add(URI("https://$domain/"), cookie)
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractTokenExpiry(token)?.let { expiry ->
            System.currentTimeMillis() / 1000L >= expiry
        } ?: true
    }

    private fun extractTokenExpiry(token: String): Long? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            JSONObject(payload).optLong("exp").takeIf { it > 0L }
        }.getOrNull()
    }

    private fun generateSessionId(): String {
        val bytes = ByteArray(16).also(SecureRandom()::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

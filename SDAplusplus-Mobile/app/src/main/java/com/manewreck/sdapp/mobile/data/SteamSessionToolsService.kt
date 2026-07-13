package com.manewreck.sdapp.mobile.data

import com.manewreck.sdapp.mobile.core.model.VaultAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

internal class SteamSessionToolsService(
    private val sessionClient: SteamWebSessionClient = SteamWebSessionClient(),
) {
    suspend fun terminateAllSessions(account: VaultAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = sessionClient.create(account)
            val pageRequest = Request.Builder()
                .url(MANAGE_URL)
                .header("User-Agent", BROWSER_USER_AGENT)
                .get()
                .build()
            val page = session.client.newCall(pageRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Steam Guard management failed with HTTP ${response.code}.")
                }
                response.body?.string().orEmpty()
            }
            if (!containsDeauthorizeForm(page)) {
                throw IllegalStateException("Steam requires a fresh web login before sessions can be terminated.")
            }
            val formSessionId = SESSION_ID_REGEX.find(page)?.groupValues?.get(1).orEmpty().ifBlank { session.sessionId }
            val body = FormBody.Builder()
                .add("action", "deauthorize")
                .add("sessionid", formSessionId)
                .build()
            val request = Request.Builder()
                .url(MANAGE_ACTION_URL)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Referer", MANAGE_URL)
                .post(body)
                .build()
            session.client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Steam session termination failed with HTTP ${response.code}.")
                }
                if (looksLikeLoginPage(responseBody)) {
                    throw IllegalStateException("Steam rejected the saved web session. Refresh it in SDA++ Desktop first.")
                }
            }
        }
    }

    private fun containsDeauthorizeForm(html: String): Boolean {
        return html.contains("deauthorize_devices_form", ignoreCase = true) ||
            html.contains("manage_action", ignoreCase = true)
    }

    private fun looksLikeLoginPage(html: String): Boolean {
        return html.contains("loginForm", ignoreCase = true) ||
            html.contains("/login/home", ignoreCase = true)
    }

    private companion object {
        const val MANAGE_URL = "https://store.steampowered.com/twofactor/manage"
        const val MANAGE_ACTION_URL = "https://store.steampowered.com/twofactor/manage_action"
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/136.0.0.0 Mobile Safari/537.36"
        val SESSION_ID_REGEX = Regex(
            """<input[^>]*name=[\"']sessionid[\"'][^>]*value=[\"']([^\"']+)[\"']""",
            RegexOption.IGNORE_CASE,
        )
    }
}

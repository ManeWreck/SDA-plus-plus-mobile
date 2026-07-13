package com.manewreck.sdapp.mobile.data

import android.util.Base64
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class MobileConfirmation(
    val accountSteamId: String,
    val accountName: String,
    val id: String,
    val nonce: String,
    val creatorId: String,
    val type: Int,
    val headline: String,
    val summary: List<String>,
    val iconUrl: String?,
    val acceptLabel: String,
    val cancelLabel: String,
)

internal class SteamConfirmationService(
    private val sessionClient: SteamWebSessionClient = SteamWebSessionClient(),
) {
    suspend fun fetch(account: VaultAccount): Result<List<MobileConfirmation>> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfirmationSecrets(account)
            val session = sessionClient.create(account)
            val request = Request.Builder()
                .url("$COMMUNITY_URL/mobileconf/getlist?${query(account, session.steamId, "conf")}")
                .header("User-Agent", MOBILE_USER_AGENT)
                .get()
                .build()
            session.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Steam confirmations failed with HTTP ${response.code}.")
                }
                val root = JSONObject(body)
                if (root.optBoolean("needauth")) {
                    throw IllegalStateException("Steam requires a fresh web session for this account.")
                }
                if (!root.optBoolean("success")) {
                    throw IllegalStateException(root.optString("message").ifBlank { "Steam rejected the confirmation request." })
                }
                val items = root.optJSONArray("conf") ?: return@use emptyList()
                buildList {
                    for (index in 0 until items.length()) {
                        val item = items.getJSONObject(index)
                        val summaryJson = item.optJSONArray("summary")
                        val summary = buildList {
                            if (summaryJson != null) {
                                for (summaryIndex in 0 until summaryJson.length()) {
                                    add(summaryJson.optString(summaryIndex))
                                }
                            }
                        }
                        add(
                            MobileConfirmation(
                                accountSteamId = account.steamId,
                                accountName = account.accountName,
                                id = item.optString("id"),
                                nonce = item.optString("nonce"),
                                creatorId = item.optString("creator_id"),
                                type = item.optInt("type", 0),
                                headline = item.optString("headline").ifBlank { "Steam confirmation" },
                                summary = summary.filter(String::isNotBlank),
                                iconUrl = item.optString("icon").ifBlank { null },
                                acceptLabel = item.optString("accept").ifBlank { "Accept" },
                                cancelLabel = item.optString("cancel").ifBlank { "Reject" },
                            ),
                        )
                    }
                }
            }
        }
    }

    suspend fun respond(
        account: VaultAccount,
        confirmation: MobileConfirmation,
        accept: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfirmationSecrets(account)
            require(confirmation.accountSteamId == account.steamId) { "Confirmation account mismatch." }
            val session = sessionClient.create(account)
            val operation = if (accept) "allow" else "cancel"
            val tag = if (accept) "accept" else "reject"
            val url = buildString {
                append(COMMUNITY_URL)
                append("/mobileconf/ajaxop?op=")
                append(operation)
                append('&')
                append(query(account, session.steamId, tag))
                append("&cid=")
                append(encode(confirmation.id))
                append("&ck=")
                append(encode(confirmation.nonce))
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MOBILE_USER_AGENT)
                .get()
                .build()
            session.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || !runCatching { JSONObject(body).optBoolean("success") }.getOrDefault(false)) {
                    throw IllegalStateException("Steam did not ${if (accept) "accept" else "reject"} this confirmation.")
                }
            }
        }
    }

    private fun requireConfirmationSecrets(account: VaultAccount) {
        require(!account.identitySecret.isNullOrBlank()) { "identity_secret is missing from this maFile." }
        require(!account.deviceId.isNullOrBlank()) { "device_id is missing from this maFile." }
    }

    private fun query(account: VaultAccount, steamId: String, tag: String): String {
        val timestamp = System.currentTimeMillis() / 1000L
        return listOf(
            "p" to account.deviceId.orEmpty(),
            "a" to steamId,
            "k" to confirmationHash(account.identitySecret.orEmpty(), timestamp, tag),
            "t" to timestamp.toString(),
            "m" to "react",
            "tag" to tag,
        ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    }

    private fun confirmationHash(identitySecret: String, timestamp: Long, tag: String): String {
        val payload = ByteBuffer.allocate(8 + tag.toByteArray().size)
            .putLong(timestamp)
            .put(tag.toByteArray())
            .array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(Base64.decode(identitySecret, Base64.DEFAULT), "HmacSHA1"))
        return Base64.encodeToString(mac.doFinal(payload), Base64.NO_WRAP)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val COMMUNITY_URL = "https://steamcommunity.com"
        const val MOBILE_USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 13; Valve Steam App Version/3)"
    }
}

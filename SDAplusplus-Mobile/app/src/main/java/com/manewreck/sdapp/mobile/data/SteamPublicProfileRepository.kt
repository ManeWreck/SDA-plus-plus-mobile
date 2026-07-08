package com.manewreck.sdapp.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentHashMap.newKeySet
import java.util.concurrent.TimeUnit

class SteamPublicProfileRepository {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, SteamPublicProfile>()
    private val missingProfiles = newKeySet<String>()

    suspend fun getProfile(steamId: String): SteamPublicProfile? = withContext(Dispatchers.IO) {
        cache[steamId]?.let { return@withContext it }
        if (missingProfiles.contains(steamId)) {
            return@withContext null
        }

        val accountId = steamId64ToAccountId(steamId) ?: return@withContext null
        val request = Request.Builder()
            .url("https://steamcommunity.com/miniprofile/$accountId/json")
            .header("User-Agent", "SDA++ Mobile")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@use null
                }

                parseProfile(JSONObject(body))
            }
        }.getOrNull()?.also { parsed ->
            cache[steamId] = parsed
            missingProfiles.remove(steamId)
        } ?: run {
            missingProfiles.add(steamId)
        }

        return@withContext cache[steamId]
    }

    suspend fun getProfiles(steamIds: List<String>): Map<String, SteamPublicProfile> {
        val result = linkedMapOf<String, SteamPublicProfile>()
        steamIds.distinct().forEach { steamId ->
            val profile = getProfile(steamId)
            if (profile != null) {
                result[steamId] = profile
            }
        }
        return result
    }

    private fun parseProfile(root: JSONObject): SteamPublicProfile {
        return SteamPublicProfile(
            personaName = root.optString("persona_name").ifBlank { null },
            avatarUrl = root.optString("avatar_url").ifBlank { null },
            level = root.optInt("level", -1).takeIf { it >= 0 },
            levelClass = root.optString("level_class").ifBlank { null },
        )
    }

    private fun steamId64ToAccountId(steamId: String): Long? {
        val value = steamId.toLongOrNull() ?: return null
        val base = 76561197960265728L
        if (value <= base) {
            return null
        }
        return value - base
    }
}

data class SteamPublicProfile(
    val personaName: String?,
    val avatarUrl: String?,
    val level: Int?,
    val levelClass: String?,
)

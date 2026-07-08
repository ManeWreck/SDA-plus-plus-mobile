package com.manewreck.sdapp.mobile.data

import com.manewreck.sdapp.mobile.core.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class WebDavSyncRepository(
    private val vaultRepository: LocalVaultRepository,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var lastAction: String? = null
    private var lastSyncDisplay: String? = null
    private var lastConnected = false

    fun getState(settings: CloudSyncSettings): SyncState {
        return SyncState(
            providerName = settings.provider.name,
            lastAction = lastAction,
            lastSyncDisplay = lastSyncDisplay,
            isConnected = lastConnected && settings.url.isNotBlank(),
        )
    }

    suspend fun testConnection(settings: CloudSyncSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireWebDav(settings)
            val response = execute(settings, "", "PROPFIND", null, extraHeaders = mapOf("Depth" to "0"))
            response.use {
                val code = it.code
                if (code !in 200..299 && code != 207) {
                    throw IllegalStateException("WebDAV test failed with HTTP $code")
                }
            }
            markSuccess("Test")
        }
    }

    suspend fun push(settings: CloudSyncSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireWebDav(settings)
            ensureCollection(settings, "")
            ensureCollection(settings, "maFiles")
            vaultRepository.listEncryptedArtifacts().forEach { artifact ->
                putBytes(settings, artifact.relativePath, artifact.payload)
            }
            markSuccess("Push")
        }
    }

    suspend fun pull(settings: CloudSyncSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireWebDav(settings)
            val manifestBytes = getBytesOrNull(settings, "manifest.json.enc")
            if (manifestBytes != null) {
                val manifestPlain = vaultRepository.decryptArtifact(manifestBytes)
                val fileNames = vaultRepository.extractRemoteMaFileNames(manifestPlain)
                val artifacts = linkedMapOf(
                    "manifest.json.enc" to manifestBytes,
                )

                fileNames.forEach { fileName ->
                    artifacts["maFiles/$fileName"] = getBytes(settings, "maFiles/$fileName")
                }

                vaultRepository.replaceEncryptedArtifacts(artifacts)
                markSuccess("Pull")
                return@runCatching
            }

            val encryptedMaFileEntries = listCollectionEntries(settings, "maFiles")
                .filter { it.endsWith(".maFile.enc", ignoreCase = true) }

            if (encryptedMaFileEntries.isNotEmpty()) {
                val maFileArtifacts = linkedMapOf<String, ByteArray>()
                encryptedMaFileEntries.forEach { fileName ->
                    maFileArtifacts["maFiles/$fileName"] = getBytes(settings, "maFiles/$fileName")
                }

                vaultRepository.importEncryptedMaFiles(maFileArtifacts)
                markSuccess("Pull")
                return@runCatching
            }

            val legacyManifestBytes = getBytesOrNull(settings, "manifest.json")
            if (legacyManifestBytes != null) {
                val legacyFileNames = extractLegacyMaFileNames(legacyManifestBytes)
                if (legacyFileNames.isNotEmpty()) {
                    val plainArtifacts = linkedMapOf<String, ByteArray>()
                    legacyFileNames.forEach { fileName ->
                        val payload = getBytesOrNull(settings, "maFiles/$fileName")
                            ?: getBytesOrNull(settings, fileName)
                            ?: throw IllegalStateException("WebDAV GET failed for maFiles/$fileName and $fileName with HTTP 404")
                        plainArtifacts[fileName] = payload
                    }
                    vaultRepository.importPlainMaFiles(plainArtifacts)
                    markSuccess("Pull")
                    return@runCatching
                }
            }

            val plainMaFileEntries = listCollectionEntries(settings, "maFiles")
                .filter { it.endsWith(".maFile", ignoreCase = true) }

            if (plainMaFileEntries.isNotEmpty()) {
                val plainArtifacts = linkedMapOf<String, ByteArray>()
                plainMaFileEntries.forEach { fileName ->
                    plainArtifacts["maFiles/$fileName"] = getBytes(settings, "maFiles/$fileName")
                }
                vaultRepository.importPlainMaFiles(plainArtifacts)
                markSuccess("Pull")
                return@runCatching
            }

            val rootPlainMaFileEntries = listCollectionEntries(settings, "")
                .filter { it.endsWith(".maFile", ignoreCase = true) }

            if (rootPlainMaFileEntries.isNotEmpty()) {
                val plainArtifacts = linkedMapOf<String, ByteArray>()
                rootPlainMaFileEntries.forEach { fileName ->
                    plainArtifacts[fileName] = getBytes(settings, fileName)
                }
                vaultRepository.importPlainMaFiles(plainArtifacts)
                markSuccess("Pull")
                return@runCatching
            }

            throw IllegalStateException("Cloud folder is reachable, but no compatible vault files were found. Expected Android `.maFile.enc` or classic SDA `manifest.json` / `.maFile` backup.")
        }
    }

    private fun ensureCollection(settings: CloudSyncSettings, child: String) {
        val response = execute(settings, child, "MKCOL", null)
        response.use {
            val code = it.code
            if (code !in listOf(201, 200, 204, 405)) {
                throw IllegalStateException("WebDAV MKCOL failed with HTTP $code")
            }
        }
    }

    private fun putBytes(settings: CloudSyncSettings, relativePath: String, payload: ByteArray) {
        val requestBody = payload.toRequestBody("application/octet-stream".toMediaType())
        val response = execute(settings, relativePath, "PUT", requestBody)
        response.use {
            val code = it.code
            if (code !in 200..299) {
                throw IllegalStateException("WebDAV PUT failed for $relativePath with HTTP $code")
            }
        }
    }

    private fun getBytes(settings: CloudSyncSettings, relativePath: String): ByteArray {
        val response = execute(settings, relativePath, "GET", null)
        response.use {
            val code = it.code
            if (code !in 200..299) {
                throw IllegalStateException("WebDAV GET failed for $relativePath with HTTP $code")
            }
            return it.body?.bytes() ?: throw IllegalStateException("WebDAV GET returned an empty body for $relativePath")
        }
    }

    private fun getBytesOrNull(settings: CloudSyncSettings, relativePath: String): ByteArray? {
        val response = execute(settings, relativePath, "GET", null)
        response.use {
            if (it.code == 404) {
                return null
            }
            val code = it.code
            if (code !in 200..299) {
                throw IllegalStateException("WebDAV GET failed for $relativePath with HTTP $code")
            }
            return it.body?.bytes() ?: throw IllegalStateException("WebDAV GET returned an empty body for $relativePath")
        }
    }

    private fun listCollectionEntries(settings: CloudSyncSettings, relativePath: String): List<String> {
        val response = execute(settings, relativePath, "PROPFIND", null, extraHeaders = mapOf("Depth" to "1"))
        response.use {
            val code = it.code
            if (code == 404) {
                return emptyList()
            }
            if (code !in 200..299 && code != 207) {
                throw IllegalStateException("WebDAV PROPFIND failed for $relativePath with HTTP $code")
            }

            val body = it.body?.bytes() ?: return emptyList()
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(body.inputStream())
            val responses = document.getElementsByTagNameNS("*", "response")
            val entries = mutableListOf<String>()

            for (index in 0 until responses.length) {
                val node = responses.item(index) as? Element ?: continue
                val hrefNodes = node.getElementsByTagNameNS("*", "href")
                if (hrefNodes.length == 0) continue
                val href = hrefNodes.item(0).textContent.orEmpty()
                val decodedHref = URLDecoder.decode(href, Charsets.UTF_8.name()).trimEnd('/')
                val name = decodedHref.substringAfterLast('/', "")
                if (name.isBlank()) continue
                if (!decodedHref.contains("/${relativePath.trim('/')}") || name == relativePath.trim('/')) continue
                entries += name
            }

            return entries.distinct()
        }
    }

    private fun extractLegacyMaFileNames(plainManifest: ByteArray): List<String> {
        val root = org.json.JSONObject(String(plainManifest, Charsets.UTF_8))
        val entries = root.optJSONArray("entries") ?: return emptyList()
        return buildList {
            for (index in 0 until entries.length()) {
                val fileName = entries.optJSONObject(index)?.optString("filename").orEmpty()
                if (fileName.isNotBlank()) {
                    add(fileName)
                }
            }
        }
    }

    private fun execute(
        settings: CloudSyncSettings,
        relativePath: String,
        method: String,
        requestBody: okhttp3.RequestBody?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): okhttp3.Response {
        val requestBuilder = Request.Builder()
            .url(buildUrl(settings, relativePath))

        if (settings.login.isNotBlank()) {
            requestBuilder.header(
                "Authorization",
                okhttp3.Credentials.basic(settings.login, settings.password),
            )
        }
        extraHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

        val body = requestBody ?: ByteArray(0).toRequestBody(null, 0, 0)
        requestBuilder.method(method, if (method == "GET" || method == "HEAD") null else body)
        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private fun buildUrl(settings: CloudSyncSettings, relativePath: String): String {
        val baseUrl = settings.url.trim().trimEnd('/')
        val remoteFolder = settings.remotePath.trim().trim('/').takeIf { it.isNotBlank() }
        val cleanRelativePath = relativePath.trim('/')
        val pathSuffix = listOfNotNull(remoteFolder, cleanRelativePath.takeIf { it.isNotBlank() }).joinToString("/")
        return if (pathSuffix.isBlank()) baseUrl else "$baseUrl/$pathSuffix"
    }

    private fun requireWebDav(settings: CloudSyncSettings) {
        require(settings.provider == com.manewreck.sdapp.mobile.core.sync.CloudProviderType.WebDav) {
            "Only WebDAV is implemented in the current Android build."
        }
        require(settings.url.isNotBlank()) { "Cloud URL is empty." }
    }

    private fun markSuccess(action: String) {
        lastAction = action
        lastConnected = true
        lastSyncDisplay = "$action: ${DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())}"
    }
}

data class EncryptedArtifact(
    val relativePath: String,
    val payload: ByteArray,
)

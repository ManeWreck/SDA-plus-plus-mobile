package com.manewreck.sdapp.mobile.data

import android.content.Context
import com.manewreck.sdapp.mobile.core.model.AccountSummary
import com.manewreck.sdapp.mobile.core.model.SteamSessionSnapshot
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import com.manewreck.sdapp.mobile.core.model.VaultSummary
import com.manewreck.sdapp.mobile.core.vault.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class LocalVaultRepository(
    context: Context,
) : VaultRepository {
    private val crypto = SecureFileCrypto(KEY_ALIAS)
    private val vaultDirectory = File(context.filesDir, "vault").apply { mkdirs() }
    private val maFilesDirectory = File(vaultDirectory, "maFiles").apply { mkdirs() }
    private val encryptedManifestFile = File(vaultDirectory, "manifest.json.enc")
    private val legacyStorageFile = File(vaultDirectory, "accounts.json")
    private val accountState = MutableStateFlow(emptyList<AccountSummary>())

    init {
        migrateLegacyIfNeeded()
        refreshState()
    }

    override suspend fun unlock(passphrase: String): Result<VaultSummary> {
        return Result.success(
            VaultSummary(
                accountCount = accountState.value.size,
                encrypted = true,
                lastUnlockedUtc = null,
            ),
        )
    }

    override fun observeAccounts(): Flow<List<AccountSummary>> = accountState.asStateFlow()

    override suspend fun listAccounts(): List<AccountSummary> = accountState.value

    override suspend fun getAccount(steamId: String): VaultAccount? {
        val manifest = loadManifest()
        val entry = manifest.firstOrNull { it.steamId == steamId } ?: return null
        val encryptedFile = File(maFilesDirectory, entry.fileName)
        if (!encryptedFile.exists()) return null
        val decrypted = decryptArtifact(encryptedFile.readBytes())
        return parseMaFile(String(decrypted, Charsets.UTF_8)).copy(
            importedAtUtc = entry.importedAtUtc,
            isFavorite = entry.isFavorite,
            hasCloudBackup = entry.hasCloudBackup,
            hasSessionSnapshot = entry.hasSessionSnapshot,
        )
    }

    override suspend fun importMaFile(rawJson: String): Result<AccountSummary> {
        return runCatching {
            val imported = parseMaFile(rawJson)
            val manifest = loadManifest()
                .filterNot { it.steamId == imported.steamId || it.accountName.equals(imported.accountName, ignoreCase = true) }
                .toMutableList()

            val fileName = sanitizeFileName(imported.accountName) + ".maFile.enc"
            val importedAtUtc = Instant.now().toString()
            File(maFilesDirectory, fileName).writeBytes(crypto.encrypt(rawJson.toByteArray(Charsets.UTF_8)))

            manifest += ManifestEntry(
                steamId = imported.steamId,
                accountName = imported.accountName,
                fileName = fileName,
                importedAtUtc = importedAtUtc,
                isFavorite = imported.isFavorite,
                hasCloudBackup = imported.hasCloudBackup,
                hasSessionSnapshot = imported.hasSessionSnapshot,
            )

            saveManifest(manifest.sortedBy { it.accountName.lowercase() })
            refreshState()
            AccountSummary(
                steamId = imported.steamId,
                accountName = imported.accountName,
                isFavorite = imported.isFavorite,
                hasCloudBackup = imported.hasCloudBackup,
                hasSessionSnapshot = imported.hasSessionSnapshot,
                importedAtUtc = importedAtUtc,
            )
        }
    }

    fun listEncryptedArtifacts(): List<EncryptedArtifact> {
        val artifacts = mutableListOf<EncryptedArtifact>()
        if (encryptedManifestFile.exists()) {
            artifacts += EncryptedArtifact("manifest.json.enc", encryptedManifestFile.readBytes())
        }
        maFilesDirectory.listFiles { file -> file.isFile && file.extension == "enc" }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                artifacts += EncryptedArtifact("maFiles/${file.name}", file.readBytes())
            }
        return artifacts
    }

    fun decryptArtifact(payload: ByteArray): ByteArray = crypto.decrypt(payload)

    fun extractRemoteMaFileNames(plainManifest: ByteArray): List<String> {
        val array = JSONArray(String(plainManifest, Charsets.UTF_8))
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index).optString("fileName"))
            }
        }
    }

    fun replaceEncryptedArtifacts(artifacts: Map<String, ByteArray>) {
        encryptedManifestFile.delete()
        maFilesDirectory.listFiles()?.forEach { it.delete() }

        artifacts.forEach { (relativePath, payload) ->
            val target = File(vaultDirectory, relativePath.replace('/', File.separatorChar))
            target.parentFile?.mkdirs()
            target.writeBytes(payload)
        }

        refreshState()
    }

    fun importEncryptedMaFiles(artifacts: Map<String, ByteArray>) {
        maFilesDirectory.listFiles()?.forEach { it.delete() }

        val manifest = artifacts.entries
            .sortedBy { it.key.lowercase() }
            .map { (relativePath, payload) ->
                val fileName = relativePath.substringAfterLast('/')
                val decrypted = String(crypto.decrypt(payload), Charsets.UTF_8)
                val account = parseMaFile(decrypted)
                val importedAtUtc = Instant.now().toString()
                File(maFilesDirectory, fileName).writeBytes(payload)
                ManifestEntry(
                    steamId = account.steamId,
                    accountName = account.accountName,
                    fileName = fileName,
                    importedAtUtc = importedAtUtc,
                    isFavorite = account.isFavorite,
                    hasCloudBackup = true,
                    hasSessionSnapshot = account.hasSessionSnapshot,
                )
            }

        saveManifest(manifest)
        refreshState()
    }

    fun importPlainMaFiles(artifacts: Map<String, ByteArray>) {
        maFilesDirectory.listFiles()?.forEach { it.delete() }

        val manifest = artifacts.entries
            .sortedBy { it.key.lowercase() }
            .map { (relativePath, payload) ->
                val originalJson = String(payload, Charsets.UTF_8)
                val account = parseMaFile(originalJson)
                val fileName = sanitizeFileName(account.accountName) + ".maFile.enc"
                val importedAtUtc = Instant.now().toString()
                File(maFilesDirectory, fileName).writeBytes(crypto.encrypt(payload))
                ManifestEntry(
                    steamId = account.steamId,
                    accountName = account.accountName,
                    fileName = fileName,
                    importedAtUtc = importedAtUtc,
                    isFavorite = false,
                    hasCloudBackup = true,
                    hasSessionSnapshot = account.hasSessionSnapshot,
                )
            }

        saveManifest(manifest)
        refreshState()
    }

    fun toggleFavorite(steamId: String) {
        val manifest = loadManifest().toMutableList()
        val index = manifest.indexOfFirst { it.steamId == steamId }
        if (index == -1) {
            return
        }

        val current = manifest[index]
        manifest[index] = current.copy(isFavorite = !current.isFavorite)
        saveManifest(
            manifest.sortedWith(
                compareByDescending<ManifestEntry> { it.isFavorite }
                    .thenBy { it.accountName.lowercase() },
            ),
        )
        refreshState()
    }

    private fun refreshState() {
        accountState.value = loadManifest().map { entry ->
            AccountSummary(
                steamId = entry.steamId,
                accountName = entry.accountName,
                isFavorite = entry.isFavorite,
                hasCloudBackup = entry.hasCloudBackup,
                hasSessionSnapshot = entry.hasSessionSnapshot,
                importedAtUtc = entry.importedAtUtc,
            )
        }
    }

    private fun loadManifest(): List<ManifestEntry> {
        if (!encryptedManifestFile.exists()) {
            return emptyList()
        }

        val decrypted = String(crypto.decrypt(encryptedManifestFile.readBytes()), Charsets.UTF_8)
        if (decrypted.isBlank()) {
            return emptyList()
        }

        val array = JSONArray(decrypted)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ManifestEntry(
                        steamId = item.optString("steamId"),
                        accountName = item.optString("accountName"),
                        fileName = item.optString("fileName"),
                        importedAtUtc = item.optString("importedAtUtc"),
                        isFavorite = item.optBoolean("isFavorite", false),
                        hasCloudBackup = item.optBoolean("hasCloudBackup", false),
                        hasSessionSnapshot = item.optBoolean("hasSessionSnapshot", false),
                    ),
                )
            }
        }
    }

    private fun saveManifest(entries: List<ManifestEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("steamId", entry.steamId)
                    .put("accountName", entry.accountName)
                    .put("fileName", entry.fileName)
                    .put("importedAtUtc", entry.importedAtUtc)
                    .put("isFavorite", entry.isFavorite)
                    .put("hasCloudBackup", entry.hasCloudBackup)
                    .put("hasSessionSnapshot", entry.hasSessionSnapshot),
            )
        }
        encryptedManifestFile.writeBytes(crypto.encrypt(array.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun parseMaFile(rawJson: String): VaultAccount {
        val root = JSONObject(rawJson)
        val accountName = root.optString("account_name").ifBlank {
            throw IllegalArgumentException("maFile does not contain account_name.")
        }
        val sharedSecret = root.optString("shared_secret").ifBlank {
            throw IllegalArgumentException("maFile does not contain shared_secret.")
        }
        val session = root.optJSONObject("Session")
        val steamId = root.optString("steamid")
            .ifBlank { session?.optString("SteamID").orEmpty() }
            .ifBlank { accountName }
        val sessionSnapshot = session?.let {
            SteamSessionSnapshot(
                steamId = it.optString("SteamID").ifBlank { steamId },
                accessToken = it.optString("AccessToken").ifBlank { null },
                refreshToken = it.optString("RefreshToken").ifBlank { null },
                sessionId = it.optString("SessionID").ifBlank { null },
            )
        }
        val hasSessionSnapshot = !sessionSnapshot?.steamId.isNullOrBlank()

        return VaultAccount(
            steamId = steamId,
            accountName = accountName,
            sharedSecret = sharedSecret,
            identitySecret = root.optString("identity_secret").ifBlank { null },
            deviceId = root.optString("device_id").ifBlank { null },
            session = sessionSnapshot,
            revocationCode = root.optString("revocation_code").ifBlank { null },
            serialNumber = root.optString("serial_number").ifBlank { null },
            uri = root.optString("uri").ifBlank { null },
            hasSessionSnapshot = hasSessionSnapshot,
            importedAtUtc = Instant.now().toString(),
        )
    }

    private fun sanitizeFileName(accountName: String): String {
        return accountName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun migrateLegacyIfNeeded() {
        if (!legacyStorageFile.exists() || encryptedManifestFile.exists()) {
            return
        }

        val raw = legacyStorageFile.readText(Charsets.UTF_8)
        if (raw.isBlank()) {
            legacyStorageFile.delete()
            return
        }

        val array = JSONArray(raw)
        val manifest = mutableListOf<ManifestEntry>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val accountName = item.optString("accountName")
            val steamId = item.optString("steamId")
            val fileName = sanitizeFileName(accountName) + ".maFile.enc"
            val maFileJson = JSONObject()
                .put("account_name", accountName)
                .put("steamid", steamId)
                .put("shared_secret", item.optString("sharedSecret"))
                .put("identity_secret", item.optString("identitySecret"))
                .put("device_id", item.optString("deviceId"))
                .put("revocation_code", item.optString("revocationCode"))
                .put("serial_number", item.optString("serialNumber"))
                .put("uri", item.optString("uri"))

            File(maFilesDirectory, fileName).writeBytes(crypto.encrypt(maFileJson.toString().toByteArray(Charsets.UTF_8)))
            manifest += ManifestEntry(
                steamId = steamId,
                accountName = accountName,
                fileName = fileName,
                importedAtUtc = item.optString("importedAtUtc"),
                isFavorite = item.optBoolean("isFavorite", false),
                hasCloudBackup = item.optBoolean("hasCloudBackup", false),
                hasSessionSnapshot = true,
            )
        }
        saveManifest(manifest)
        legacyStorageFile.delete()
    }

    private data class ManifestEntry(
        val steamId: String,
        val accountName: String,
        val fileName: String,
        val importedAtUtc: String,
        val isFavorite: Boolean,
        val hasCloudBackup: Boolean,
        val hasSessionSnapshot: Boolean,
    )

    private companion object {
        const val KEY_ALIAS = "sdapp_vault_key"
    }
}

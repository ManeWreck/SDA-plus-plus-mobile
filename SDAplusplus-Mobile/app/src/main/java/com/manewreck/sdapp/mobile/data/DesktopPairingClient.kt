package com.manewreck.sdapp.mobile.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit

class DesktopPairingClient {
    private val relayClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun sendCloudSettings(pairingUri: String, settings: CloudSyncSettings): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.provider.name == "WebDav") { "Only WebDAV can be shared with desktop right now." }
            val normalizedUrl = settings.url.trim()
            val webDavUri = Uri.parse(normalizedUrl)
            require(webDavUri.scheme.equals("https", ignoreCase = true) && !webDavUri.host.isNullOrBlank()) {
                if (normalizedUrl.isEmpty()) {
                    "WebDAV URL is empty. Open Sync and enter the HTTPS URL."
                } else {
                    "Invalid WebDAV URL: use a complete HTTPS address such as https://webdav.yandex.ru/."
                }
            }
            require(settings.login.isNotBlank() && settings.password.isNotEmpty()) {
                "WebDAV login or app password is empty. Complete the fields in Sync first."
            }

            val uri = Uri.parse(pairingUri.trim())
            require(uri.scheme.equals("sdapp-pair", ignoreCase = true) && uri.host == "v1") { "Unsupported SDA++ pairing QR." }
            val descriptor = JSONObject(String(decodeUrl(uri.getQueryParameter("p") ?: error("Pairing data is missing.")), Charsets.UTF_8))
            require(descriptor.getInt("v") == 1) { "Unsupported pairing version." }
            require(System.currentTimeMillis() / 1000L <= descriptor.getLong("exp")) { "This pairing QR has expired." }

            val sessionIdText = descriptor.getString("sid")
            val sessionId = decodeUrl(sessionIdText)
            val desktopPublicKey = KeyFactory.getInstance("EC").generatePublic(
                X509EncodedKeySpec(Base64.decode(descriptor.getString("pk"), Base64.DEFAULT)),
            )
            val keyPair = KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"))
            }.generateKeyPair()
            val agreement = KeyAgreement.getInstance("ECDH").apply {
                init(keyPair.private)
                doPhase(desktopPublicKey, true)
            }
            val sharedSecret = agreement.generateSecret()
            val aesKey = hkdfSha256(sharedSecret, sessionId, "SDA++ local pairing v1".toByteArray(), 32)
            sharedSecret.fill(0)

            val payload = JSONObject()
                .put("v", 1)
                .put("provider", "webdav")
                .put("url", normalizedUrl)
                .put("login", settings.login.trim())
                .put("password", settings.password)
                .put("remote_path", settings.remotePath.ifBlank { "SDAppVault" })
                .put("issued_at", System.currentTimeMillis() / 1000L)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val nonce = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(sessionId)
            }
            val encrypted = cipher.doFinal(payload)
            payload.fill(0)
            aesKey.fill(0)
            val ciphertext = encrypted.copyOfRange(0, encrypted.size - 16)
            val tag = encrypted.copyOfRange(encrypted.size - 16, encrypted.size)

            val envelope = JSONObject()
                .put("v", 1)
                .put("sid", sessionIdText)
                .put("public_key", Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
                .put("nonce", Base64.encodeToString(nonce, Base64.NO_WRAP))
                .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .put("tag", Base64.encodeToString(tag, Base64.NO_WRAP))
                .toString()
                .toByteArray(Charsets.UTF_8)

            val hosts = descriptor.getJSONArray("hosts")
            val port = descriptor.getInt("port")
            var delivered = false
            val attemptedHosts = mutableListOf<String>()
            for (index in 0 until hosts.length()) {
                val host = hosts.getString(index)
                attemptedHosts += host
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 4_000)
                        socket.soTimeout = 6_000
                        val output = DataOutputStream(socket.getOutputStream())
                        output.writeInt(envelope.size)
                        output.write(envelope)
                        output.flush()
                        val input = DataInputStream(socket.getInputStream())
                        val responseSize = input.readInt()
                        require(responseSize in 1..4096) { "Invalid response from desktop." }
                        val response = ByteArray(responseSize).also(input::readFully)
                        require(JSONObject(String(response, Charsets.UTF_8)).optBoolean("ok")) { "Desktop rejected the pairing response." }
                        delivered = true
                    }
                    if (delivered) break
                } catch (_: Throwable) { }
            }

            var relayError: String? = null
            if (!delivered && descriptor.has("relay") && descriptor.has("relay_token")) {
                val relayUrl = descriptor.optString("relay").trim()
                val relayToken = descriptor.optString("relay_token").trim()
                try {
                    require(Uri.parse(relayUrl).scheme.equals("https", ignoreCase = true)) { "Pairing relay must use HTTPS." }
                    require(relayToken.matches(Regex("^[A-Za-z0-9_-]{43}$"))) { "Invalid pairing relay token." }
                    val request = Request.Builder()
                        .url("${relayUrl.trimEnd('/')}/v1/pair/$sessionIdText")
                        .header("Authorization", "Bearer $relayToken")
                        .put(envelope.toRequestBody("application/octet-stream".toMediaType()))
                        .build()
                    relayClient.newCall(request).execute().use { response ->
                        delivered = response.code == 200 || response.code == 201
                        if (!delivered) relayError = "relay returned HTTP ${response.code}"
                    }
                } catch (error: Throwable) {
                    relayError = error.message ?: error.javaClass.simpleName
                }
            }
            envelope.fill(0)
            require(delivered) {
                val relayHint = relayError?.let { " Encrypted relay fallback failed: $it." } ?: ""
                "Could not reach SDA++ Desktop at ${attemptedHosts.joinToString()}.$relayHint " +
                    "Create a new desktop pairing QR and try again."
            }
        }
    }

    private fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(salt, "HmacSHA256")) }
        val prk = extract.doFinal(input)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
            previous = expand.doFinal(previous + info + byteArrayOf(counter++.toByte()))
            val count = minOf(previous.size, length - offset)
            previous.copyInto(output, offset, 0, count)
            offset += count
        }
        prk.fill(0)
        return output
    }

    private fun decodeUrl(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

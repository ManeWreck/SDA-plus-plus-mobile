package com.manewreck.sdapp.mobile.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.biometric.BiometricManager
import com.manewreck.sdapp.mobile.core.sync.CloudProviderType
import com.manewreck.sdapp.mobile.data.AccountSortMode
import com.manewreck.sdapp.mobile.data.AppLanguage
import com.manewreck.sdapp.mobile.data.AppLockRepository
import com.manewreck.sdapp.mobile.data.AppPreferencesRepository
import com.manewreck.sdapp.mobile.data.DesktopPairingClient
import com.manewreck.sdapp.mobile.data.LocalVaultRepository
import com.manewreck.sdapp.mobile.data.MobileConfirmation
import com.manewreck.sdapp.mobile.data.SteamConfirmationService
import com.manewreck.sdapp.mobile.data.SteamPublicProfileRepository
import com.manewreck.sdapp.mobile.data.SteamSessionToolsService
import com.manewreck.sdapp.mobile.data.SteamTotpService
import com.manewreck.sdapp.mobile.data.WebDavSyncRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MobileViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val preferencesRepository = AppPreferencesRepository(application.applicationContext)
    private val lockRepository = AppLockRepository(preferencesRepository)
    private val vaultRepository = LocalVaultRepository(application.applicationContext)
    private val syncRepository = WebDavSyncRepository(vaultRepository)
    private val publicProfileRepository = SteamPublicProfileRepository()
    private val steamGuardService = SteamTotpService()
    private val confirmationService = SteamConfirmationService()
    private val sessionToolsService = SteamSessionToolsService()
    private val desktopPairingClient = DesktopPairingClient()
    private val _uiState = MutableStateFlow(MobileUiState())
    val uiState: StateFlow<MobileUiState> = _uiState.asStateFlow()

    private var codeJob: Job? = null
    private var codeUpdatesEnabled: Boolean = false

    init {
        val settings = preferencesRepository.loadSettings()
        val biometricAvailable = isBiometricAvailable()
        _uiState.update {
            it.copy(
                settings = settings,
                biometricAvailable = biometricAvailable,
                isUnlocked = !lockRepository.hasPin(settings),
                syncState = syncRepository.getState(settings.cloudSync),
            )
        }

        viewModelScope.launch {
            vaultRepository.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    val selectedId = when {
                        accounts.isEmpty() -> null
                        state.selectedAccountId != null && accounts.any { it.steamId == state.selectedAccountId } -> state.selectedAccountId
                        else -> accounts.first().steamId
                    }

                    state.copy(
                        accounts = accounts,
                        publicProfiles = state.publicProfiles.filterKeys { steamId -> accounts.any { it.steamId == steamId } },
                        selectedAccountId = selectedId,
                    )
                }
                enrichPublicProfiles(accounts.map { it.steamId })
                refreshSelectedAccount()
            }
        }
    }

    fun selectAccount(steamId: String) {
        _uiState.update { it.copy(selectedAccountId = steamId) }
        refreshSelectedAccount()
    }

    fun toggleSelectedFavorite() {
        val selectedId = _uiState.value.selectedAccountId ?: return
        viewModelScope.launch {
            vaultRepository.toggleFavorite(selectedId)
            refreshSelectedAccount()
        }
    }

    fun importMaFile(rawJson: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, statusMessage = null) }
            val result = vaultRepository.importMaFile(rawJson)
            result.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        selectedAccountId = summary.steamId,
                        statusMessage = "${appStrings(it.settings.language).importedPrefix} ${summary.accountName}",
                    )
                }
                refreshSyncState()
                refreshSelectedAccount()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        statusMessage = error.message ?: "Import failed.",
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun updateLanguage(language: AppLanguage) {
        _uiState.update { current ->
            val updated = current.settings.copy(language = language)
            preferencesRepository.saveSettings(updated)
            current.copy(
                settings = updated,
                statusMessage = appStrings(language).languageSaved,
                syncState = syncRepository.getState(updated.cloudSync),
            )
        }
    }

    fun updateAccountSortMode(sortMode: AccountSortMode) {
        _uiState.update { current ->
            val updated = current.settings.copy(accountSortMode = sortMode)
            preferencesRepository.saveSettings(updated)
            current.copy(settings = updated)
        }
    }

    fun setAccountSearchEnabled(enabled: Boolean) {
        _uiState.update { current ->
            val updated = current.settings.copy(accountSearchEnabled = enabled)
            preferencesRepository.saveSettings(updated)
            current.copy(settings = updated)
        }
    }

    fun updateCloudProvider(provider: CloudProviderType) {
        _uiState.update { current ->
            val settings = current.settings.copy(cloudSync = current.settings.cloudSync.copy(provider = provider))
            current.copy(settings = settings, syncState = syncRepository.getState(settings.cloudSync))
        }
    }

    fun updateCloudUrl(url: String) {
        _uiState.update { current ->
            val settings = current.settings.copy(cloudSync = current.settings.cloudSync.copy(url = url))
            current.copy(settings = settings, syncState = syncRepository.getState(settings.cloudSync))
        }
    }

    fun updateCloudLogin(login: String) {
        _uiState.update { current ->
            val settings = current.settings.copy(cloudSync = current.settings.cloudSync.copy(login = login))
            current.copy(settings = settings)
        }
    }

    fun updateCloudPassword(password: String) {
        _uiState.update { current ->
            val settings = current.settings.copy(cloudSync = current.settings.cloudSync.copy(password = password))
            current.copy(settings = settings)
        }
    }

    fun updateCloudRemotePath(remotePath: String) {
        _uiState.update { current ->
            val settings = current.settings.copy(cloudSync = current.settings.cloudSync.copy(remotePath = remotePath))
            current.copy(settings = settings)
        }
    }

    fun saveCloudSettings() {
        _uiState.update { current ->
            preferencesRepository.saveSettings(current.settings)
            current.copy(
                statusMessage = appStrings(current.settings.language).cloudSettingsSaved,
                syncState = syncRepository.getState(current.settings.cloudSync),
            )
        }
    }

    fun testCloudConnection() {
        runSyncAction(successMessage = currentStrings().connectionSuccess) { syncRepository.testConnection(_uiState.value.settings.cloudSync) }
    }

    fun pullFromCloud() {
        runSyncAction(successMessage = currentStrings().syncSuccess) { syncRepository.pull(_uiState.value.settings.cloudSync) }
    }

    fun pushToCloud() {
        runSyncAction(successMessage = currentStrings().syncSuccess) { syncRepository.push(_uiState.value.settings.cloudSync) }
    }

    fun updatePinDraft(pin: String) {
        _uiState.update { it.copy(pinDraft = pin.filter(Char::isDigit).take(8)) }
    }

    fun savePin() {
        val pin = _uiState.value.pinDraft
        val strings = currentStrings()
        if (pin.length < 4) {
            _uiState.update { it.copy(statusMessage = strings.pinTooShort) }
            return
        }

        _uiState.update { current ->
            val updated = lockRepository.updatePin(current.settings, pin)
            preferencesRepository.saveSettings(updated)
            current.copy(
                settings = updated,
                pinDraft = "",
                isUnlocked = true,
                statusMessage = strings.pinSaved,
            )
        }
    }

    fun updateLockPinDraft(pin: String) {
        _uiState.update { it.copy(lockPinDraft = pin.filter(Char::isDigit).take(8)) }
    }

    fun unlockWithPin() {
        val state = _uiState.value
        if (lockRepository.verifyPin(state.lockPinDraft, state.settings)) {
            _uiState.update { it.copy(isUnlocked = true, lockPinDraft = "", statusMessage = null) }
        } else {
            _uiState.update { it.copy(statusMessage = currentStrings().wrongPin) }
        }
    }

    fun lockApp() {
        if (lockRepository.hasPin(_uiState.value.settings)) {
            _uiState.update { it.copy(isUnlocked = false, lockPinDraft = "") }
        }
    }

    fun unlockWithBiometricsSuccess() {
        _uiState.update { it.copy(isUnlocked = true, lockPinDraft = "", statusMessage = null) }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        if (enabled && !isBiometricAvailable()) {
            _uiState.update { it.copy(statusMessage = currentStrings().biometricUnavailable) }
            return
        }

        _uiState.update { current ->
            val updated = lockRepository.setBiometricsEnabled(current.settings, enabled)
            preferencesRepository.saveSettings(updated)
            current.copy(
                settings = updated,
                statusMessage = if (enabled) currentStrings().biometricEnabled else null,
            )
        }
    }

    fun setAccountCodeUpdatesEnabled(enabled: Boolean) {
        if (codeUpdatesEnabled == enabled) {
            return
        }

        codeUpdatesEnabled = enabled
        if (!enabled) {
            codeJob?.cancel()
            codeJob = null
            return
        }

        refreshSelectedAccount()
    }

    suspend fun approveSelectedQrPayload(qrPayload: String): Result<String> {
        val account = _uiState.value.selectedAccount
            ?: _uiState.value.selectedAccountId?.let { vaultRepository.getAccount(it) }
            ?: return Result.failure(IllegalStateException(currentStrings().noAccountSelected))

        return steamGuardService.approveQrPayload(account, qrPayload)
            .map { currentStrings().qrApproved }
    }

    fun desktopPairingDirection(qrPayload: String): String = desktopPairingClient.direction(qrPayload)

    suspend fun pairDesktop(qrPayload: String, verificationCode: String): Result<String> {
        val current = _uiState.value.settings
        val normalizedCloud = current.cloudSync.copy(
            url = current.cloudSync.url.trim(),
            login = current.cloudSync.login.trim(),
            remotePath = current.cloudSync.remotePath.trim().ifBlank { "SDAppVault" },
        )
        val normalizedSettings = current.copy(cloudSync = normalizedCloud)
        preferencesRepository.saveSettings(normalizedSettings)
        _uiState.update { it.copy(settings = normalizedSettings) }
        val settings = normalizedSettings.cloudSync
        return desktopPairingClient.sendCloudSettings(qrPayload, verificationCode, settings)
            .map { currentStrings().desktopPairingSent }
    }

    suspend fun receiveDesktopSettings(qrPayload: String, verificationCode: String): Result<String> {
        return desktopPairingClient.receiveCloudSettings(qrPayload, verificationCode).map { cloud ->
            val updated = _uiState.value.settings.copy(cloudSync = cloud)
            preferencesRepository.saveSettings(updated)
            _uiState.update { it.copy(settings = updated) }
            currentStrings().desktopPairingReceived
        }
    }

    fun loadConfirmations() {
        if (_uiState.value.confirmationsLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(confirmationsLoading = true, confirmationsNotice = null)
            }
            val confirmations = mutableListOf<MobileConfirmation>()
            val failures = mutableListOf<String>()
            _uiState.value.accounts.forEach { summary ->
                val account = vaultRepository.getAccount(summary.steamId) ?: return@forEach
                if (account.identitySecret.isNullOrBlank() || account.deviceId.isNullOrBlank() || account.session == null) {
                    return@forEach
                }
                confirmationService.fetch(account)
                    .onSuccess(confirmations::addAll)
                    .onFailure { error -> failures += "${account.accountName}: ${buildErrorMessage(error)}" }
            }
            _uiState.update {
                it.copy(
                    confirmations = confirmations.sortedWith(
                        compareBy<MobileConfirmation> { confirmation -> confirmation.accountName.lowercase() }
                            .thenBy { confirmation -> confirmation.headline.lowercase() },
                    ),
                    confirmationsLoading = false,
                    confirmationsNotice = when {
                        confirmations.isEmpty() && failures.isNotEmpty() -> failures.first()
                        failures.isNotEmpty() -> "${failures.size} account(s) could not be checked."
                        else -> null
                    },
                )
            }
        }
    }

    fun respondToConfirmation(confirmation: MobileConfirmation, accept: Boolean) {
        if (_uiState.value.confirmationActionId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(confirmationActionId = confirmation.id, confirmationsNotice = null) }
            val account = vaultRepository.getAccount(confirmation.accountSteamId)
            val result = if (account == null) {
                Result.failure(IllegalStateException("Account is no longer available."))
            } else {
                confirmationService.respond(account, confirmation, accept)
            }
            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        confirmations = current.confirmations.filterNot {
                            it.accountSteamId == confirmation.accountSteamId && it.id == confirmation.id
                        },
                        confirmationActionId = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        confirmationActionId = null,
                        confirmationsNotice = buildErrorMessage(error),
                    )
                }
            }
        }
    }

    fun terminateSelectedAccountSessions() {
        if (_uiState.value.accountToolRunning) return
        val selectedId = _uiState.value.selectedAccountId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(accountToolRunning = true, statusMessage = null) }
            val account = vaultRepository.getAccount(selectedId)
            val result = if (account == null) {
                Result.failure(IllegalStateException(currentStrings().noAccountSelected))
            } else {
                sessionToolsService.terminateAllSessions(account)
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        accountToolRunning = false,
                        statusMessage = currentStrings().terminateSessionsSuccess,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(accountToolRunning = false, statusMessage = buildErrorMessage(error))
                }
            }
        }
    }

    private fun runSyncAction(successMessage: String, action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            val result = action()
            result.onSuccess {
                refreshSyncState()
                _uiState.update { it.copy(statusMessage = successMessage) }
            }.onFailure { error ->
                val readableMessage = buildErrorMessage(error)
                _uiState.update {
                    it.copy(statusMessage = "${currentStrings().syncFailed}: $readableMessage")
                }
            }
        }
    }

    private fun refreshSyncState() {
        _uiState.update { current ->
            current.copy(syncState = syncRepository.getState(current.settings.cloudSync))
        }
    }

    private fun refreshSelectedAccount() {
        codeJob?.cancel()
        viewModelScope.launch {
            val selectedId = _uiState.value.selectedAccountId
            val account = selectedId?.let { vaultRepository.getAccount(it) }
            _uiState.update {
                it.copy(
                    selectedAccount = account,
                    currentCode = if (account == null) "-----" else it.currentCode,
                    secondsRemaining = if (account == null) 0 else it.secondsRemaining,
                )
            }

            if (account == null) {
                _uiState.update { it.copy(currentCode = "-----", secondsRemaining = 0) }
                return@launch
            }

            if (!codeUpdatesEnabled) {
                return@launch
            }

            codeJob = viewModelScope.launch {
                while (true) {
                    val result = steamGuardService.generateCode(account.sharedSecret)
                    result.onSuccess { code ->
                        _uiState.update {
                            it.copy(
                                currentCode = code.code,
                                secondsRemaining = code.secondsRemaining,
                            )
                        }
                    }.onFailure {
                        _uiState.update {
                            it.copy(
                                currentCode = "ERROR",
                                secondsRemaining = 0,
                            )
                        }
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun currentStrings() = appStrings(_uiState.value.settings.language)

    private fun enrichPublicProfiles(steamIds: List<String>) {
        viewModelScope.launch {
            val missingSteamIds = steamIds.filterNot { _uiState.value.publicProfiles.containsKey(it) }
            if (missingSteamIds.isEmpty()) {
                return@launch
            }

            val fetchedProfiles = publicProfileRepository.getProfiles(missingSteamIds)
            if (fetchedProfiles.isEmpty()) {
                return@launch
            }

            _uiState.update { current ->
                current.copy(
                    publicProfiles = current.publicProfiles + fetchedProfiles,
                )
            }
        }
    }

    private fun buildErrorMessage(error: Throwable): String {
        val direct = error.message?.takeIf { it.isNotBlank() }
        if (direct != null) {
            return direct
        }

        val causeMessage = error.cause?.message?.takeIf { it.isNotBlank() }
        if (causeMessage != null) {
            return "${error::class.java.simpleName}: $causeMessage"
        }

        return error::class.java.simpleName.ifBlank { "Unknown error" }
    }

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(getApplication())
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}

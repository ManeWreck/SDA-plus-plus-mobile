package com.manewreck.sdapp.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manewreck.sdapp.mobile.ui.features.account.AccountDetailScreen
import com.manewreck.sdapp.mobile.ui.features.home.HomeScreen
import com.manewreck.sdapp.mobile.ui.features.lock.AppLockScreen
import com.manewreck.sdapp.mobile.ui.features.qr.QrScannerScreen
import com.manewreck.sdapp.mobile.ui.features.settings.SettingsScreen
import com.manewreck.sdapp.mobile.ui.features.sync.CloudSyncScreen
import com.manewreck.sdapp.mobile.ui.navigation.MobileDestination
import kotlinx.coroutines.launch

@Composable
fun SdaMobileRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scope = rememberCoroutineScope()
    val viewModel: MobileViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = appStrings(state.settings.language)
    val destinations = listOf(
        MobileDestination.Home,
        MobileDestination.QrScanner,
        MobileDestination.CloudSync,
        MobileDestination.Settings,
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: throw IllegalStateException("Unable to read selected file.")
            }.onSuccess(viewModel::importMaFile)
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: strings.failedToOpenFile,
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    val biometricLauncher = remember(activity, strings) {
        {
            if (activity == null) {
                Toast.makeText(context, strings.biometricUnavailable, Toast.LENGTH_SHORT).show()
            } else {
                showBiometricPrompt(activity, strings, viewModel::unlockWithBiometricsSuccess)
            }
        }
    }

    state.statusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearStatusMessage,
            confirmButton = {
                Button(onClick = viewModel::clearStatusMessage) {
                    Text(strings.ok)
                }
            },
            title = { Text(strings.vaultStatus) },
            text = { Text(message) },
        )
    }

    if (!state.isUnlocked) {
        AppLockScreen(
            pin = state.lockPinDraft,
            strings = strings,
            biometricsEnabled = state.settings.security.biometricsEnabled,
            biometricAvailable = state.biometricAvailable,
            onPinChanged = viewModel::updateLockPinDraft,
            onUnlock = viewModel::unlockWithPin,
            onUnlockWithBiometrics = biometricLauncher,
        )
        return
    }

    LaunchedEffect(currentRoute) {
        viewModel.setAccountCodeUpdatesEnabled(
            currentRoute?.startsWith("account/") == true,
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(MobileDestination.Home.route) {
                                    saveState = true
                                }
                            }
                        },
                        icon = { Text(destination.shortLabel) },
                        label = {
                            Text(
                                when (destination) {
                                    MobileDestination.Home -> strings.accounts
                                    MobileDestination.QrScanner -> strings.qr
                                    MobileDestination.CloudSync -> strings.sync
                                    MobileDestination.Settings -> strings.settings
                                    MobileDestination.Account -> strings.account
                                },
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MobileDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(MobileDestination.Home.route) {
                HomeScreen(
                    accounts = state.accounts,
                    publicProfiles = state.publicProfiles,
                    selectedAccountId = state.selectedAccountId,
                    accountSortMode = state.settings.accountSortMode,
                    accountSearchEnabled = state.settings.accountSearchEnabled,
                    strings = strings,
                    onImportMaFile = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onOpenAccount = { account ->
                        viewModel.selectAccount(account.steamId)
                        navController.navigate(MobileDestination.Account.createRoute(account.steamId))
                    },
                )
            }
            composable(MobileDestination.Account.route) { entry ->
                val steamId = entry.arguments?.getString("steamId")
                if (steamId != null && steamId != state.selectedAccountId) {
                    viewModel.selectAccount(steamId)
                }

                AccountDetailScreen(
                    account = state.selectedAccount,
                    publicProfile = state.selectedAccount?.steamId?.let { state.publicProfiles[it] },
                    currentCode = state.currentCode,
                    secondsRemaining = state.secondsRemaining,
                    strings = strings,
                    onOpenQrScanner = { navController.navigate(MobileDestination.QrScanner.route) },
                    onCopyCode = {
                        copyCodeToClipboard(context, state.currentCode)
                        Toast.makeText(context, strings.codeCopied, Toast.LENGTH_SHORT).show()
                    },
                    onCopySteamId = {
                        state.selectedAccount?.steamId?.let {
                            copyCodeToClipboard(context, it)
                            Toast.makeText(context, "SteamID copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleFavorite = viewModel::toggleSelectedFavorite,
                )
            }
            composable(MobileDestination.QrScanner.route) {
                QrScannerScreen(
                    accounts = state.accounts,
                    publicProfiles = state.publicProfiles,
                    selectedAccountId = state.selectedAccountId,
                    isCameraActive = currentRoute == MobileDestination.QrScanner.route,
                    strings = strings,
                    onSelectAccount = viewModel::selectAccount,
                    onApproveQr = viewModel::approveSelectedQrPayload,
                )
            }
            composable(MobileDestination.CloudSync.route) {
                CloudSyncScreen(
                    accountCount = state.accounts.size,
                    settings = state.settings.cloudSync,
                    syncState = state.syncState,
                    strings = strings,
                    onProviderSelected = viewModel::updateCloudProvider,
                    onUrlChanged = viewModel::updateCloudUrl,
                    onLoginChanged = viewModel::updateCloudLogin,
                    onPasswordChanged = viewModel::updateCloudPassword,
                    onRemotePathChanged = viewModel::updateCloudRemotePath,
                    onTest = viewModel::testCloudConnection,
                    onPull = viewModel::pullFromCloud,
                    onPush = viewModel::pushToCloud,
                    onSave = viewModel::saveCloudSettings,
                )
            }
            composable(MobileDestination.Settings.route) {
                SettingsScreen(
                    accountCount = state.accounts.size,
                    currentLanguage = state.settings.language,
                    accountSortMode = state.settings.accountSortMode,
                    accountSearchEnabled = state.settings.accountSearchEnabled,
                    pinDraft = state.pinDraft,
                    biometricsEnabled = state.settings.security.biometricsEnabled,
                    biometricAvailable = state.biometricAvailable,
                    strings = strings,
                    onLanguageChanged = viewModel::updateLanguage,
                    onAccountSortModeChanged = viewModel::updateAccountSortMode,
                    onAccountSearchEnabledChanged = viewModel::setAccountSearchEnabled,
                    onPinChanged = viewModel::updatePinDraft,
                    onSavePin = viewModel::savePin,
                    onBiometricsChanged = viewModel::setBiometricsEnabled,
                )
            }
        }
    }
}

private fun copyCodeToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Steam Guard code", code))
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    strings: AppStrings,
    onSuccess: () -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(strings.unlockWithBiometrics)
        .setSubtitle(strings.appLocked)
        .setNegativeButtonText(strings.ok)
        .build()
    prompt.authenticate(promptInfo)
}

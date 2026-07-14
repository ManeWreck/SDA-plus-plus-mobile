package com.manewreck.sdapp.mobile.ui.features.qr

import android.Manifest
import android.annotation.SuppressLint
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE
import com.google.mlkit.vision.common.InputImage
import com.manewreck.sdapp.mobile.core.model.AccountSummary
import com.manewreck.sdapp.mobile.data.SteamPublicProfile
import com.manewreck.sdapp.mobile.ui.AppStrings
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    accounts: List<AccountSummary>,
    publicProfiles: Map<String, SteamPublicProfile>,
    selectedAccountId: String?,
    isCameraActive: Boolean,
    strings: AppStrings,
    onSelectAccount: (String) -> Unit,
    onApproveQr: suspend (String) -> Result<String>,
    onPairingDirection: (String) -> String,
    onPairDesktop: suspend (String, String) -> Result<String>,
    onReceiveDesktopSettings: suspend (String, String) -> Result<String>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted = granted
    }

    val selectedAccount = accounts.firstOrNull { it.steamId == selectedAccountId } ?: accounts.firstOrNull()
    val selectedProfile = selectedAccount?.steamId?.let(publicProfiles::get)

    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var resultTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var approvalRunning by rememberSaveable { mutableStateOf(false) }
    var scanLocked by rememberSaveable { mutableStateOf(false) }
    var accountPickerOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDesktopPairing by rememberSaveable { mutableStateOf<String?>(null) }
    var pairingCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!cameraPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    pendingDesktopPairing?.let { payload ->
        val receivingFromDesktop = onPairingDirection(payload) == "to-mobile"
        AlertDialog(
            onDismissRequest = {
                pendingDesktopPairing = null
                pairingCode = ""
                scanLocked = false
            },
            confirmButton = {
                Button(onClick = {
                    pendingDesktopPairing = null
                    approvalRunning = true
                    resultTitle = strings.desktopPairingTitle
                    scope.launch {
                        val result = if (receivingFromDesktop) {
                            onReceiveDesktopSettings(payload, pairingCode)
                        } else {
                            onPairDesktop(payload, pairingCode)
                        }
                        approvalRunning = false
                        resultMessage = result.getOrElse { it.message ?: strings.desktopPairingFailed }
                        pairingCode = ""
                    }
                }, enabled = pairingCode.trim().replace("-", "").replace(" ", "").length == 8) {
                    Text(if (receivingFromDesktop) strings.receiveCloudSettings else strings.shareCloudSettings)
                }
            },
            dismissButton = {
                Button(onClick = {
                    pendingDesktopPairing = null
                    pairingCode = ""
                    scanLocked = false
                }) { Text(strings.cancel) }
            },
            title = { Text(strings.desktopPairingTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (receivingFromDesktop) strings.desktopPairingReceivePrompt else strings.desktopPairingPrompt)
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.uppercase().take(10) },
                        label = { Text(strings.pairingCode) },
                        singleLine = true,
                    )
                }
            },
        )
    }

    if (approvalRunning || resultMessage != null) {
        AlertDialog(
            onDismissRequest = {
                if (!approvalRunning) {
                    resultMessage = null
                    resultTitle = null
                    scanLocked = false
                }
            },
            confirmButton = {
                if (!approvalRunning) {
                    Button(
                        onClick = {
                            resultMessage = null
                            resultTitle = null
                            scanLocked = false
                        },
                    ) {
                        Text(strings.scanAgain)
                    }
                }
            },
            dismissButton = if (approvalRunning) {
                null
            } else {
                {
                    Button(
                        onClick = {
                            resultMessage = null
                            resultTitle = null
                            scanLocked = false
                        },
                    ) {
                        Text(strings.ok)
                    }
                }
            },
            title = { Text(resultTitle ?: strings.vaultStatus) },
            text = {
                if (approvalRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text(strings.qrApproving)
                    }
                } else {
                    Text(resultMessage.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (accountPickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { accountPickerOpen = false },
            containerColor = Color(0xFF232832),
            contentColor = Color(0xFFEAF1FF),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(44.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color(0xFF6A7284)),
                )
            },
        ) {
            Text(
                text = strings.chooseAccount,
                color = Color(0xFFEAF1FF),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                items(accounts, key = { it.steamId }) { account ->
                    QrAccountPickerRow(
                        account = account,
                        profile = publicProfiles[account.steamId],
                        isSelected = account.steamId == selectedAccount?.steamId,
                        onClick = {
                            onSelectAccount(account.steamId)
                            accountPickerOpen = false
                        },
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050608)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "STEAM GUARD",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFE7EDF9),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Surface(
                color = Color(0xFF171A20),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = strings.scanSteamQrAs,
                    color = Color(0xFFCCD5E4),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            selectedAccount?.let { account ->
                AccountDropdownCard(
                    account = account,
                    profile = selectedProfile,
                    onOpenPicker = { accountPickerOpen = true },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF0F1219), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (cameraPermissionGranted && isCameraActive) {
                    QrCameraPreview(
                        isActive = isCameraActive,
                        scanningEnabled = !scanLocked,
                        onQrDetected = { rawValue ->
                            if (!scanLocked && looksLikeDesktopPairingQr(rawValue)) {
                                scanLocked = true
                                pendingDesktopPairing = rawValue
                            } else if (!scanLocked && looksLikeSteamQr(rawValue) && selectedAccount != null) {
                                scanLocked = true
                                approvalRunning = true
                                resultTitle = strings.qrDetected
                                scope.launch {
                                    val result = onApproveQr(rawValue)
                                    approvalRunning = false
                                    result.onSuccess { message ->
                                        resultMessage = message
                                    }.onFailure { error ->
                                        resultMessage = error.message ?: strings.qrApprovalNotImplemented
                                    }
                                }
                            }
                        },
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            strings.cameraPermissionRequired,
                            color = Color(0xFFE7EDF9),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(strings.grantCameraAccess)
                        }
                    }
                }

                ScannerFrameOverlay()
            }

            Text(
                text = strings.qrComingSoon,
                color = Color(0xFF8E98AC),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AccountDropdownCard(
    account: AccountSummary,
    profile: SteamPublicProfile?,
    onOpenPicker: () -> Unit,
) {
    Surface(
        color = Color(0xFF2392FF),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenPicker,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniSteamAvatar(
                accountName = account.accountName,
                avatarUrl = profile?.avatarUrl,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.personaName ?: account.accountName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = account.steamId,
                    color = Color(0xFFD5E8FF),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF176FD1)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "⌄",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun QrAccountPickerRow(
    account: AccountSummary,
    profile: SteamPublicProfile?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniSteamAvatar(
            accountName = account.accountName,
            avatarUrl = profile?.avatarUrl,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile?.personaName ?: account.accountName,
                color = Color(0xFFF1F5FF),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.steamId,
                color = Color(0xFFA6AFC2),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(if (isSelected) Color(0xFF2392FF) else Color(0xFF2C3340))
                .border(
                    width = 1.dp,
                    color = if (isSelected) Color(0xFF8CC6FF) else Color(0xFF485264),
                    shape = RoundedCornerShape(100.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isSelected) "✓" else "",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun MiniSteamAvatar(
    accountName: String,
    avatarUrl: String?,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A2434), Color(0xFF0F131A)),
                ),
            )
            .border(1.dp, Color(0xFF8CC6FF), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "$accountName avatar",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = accountName.take(1).uppercase(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun ScannerFrameOverlay() {
    Box(
        modifier = Modifier
            .size(260.dp)
            .border(0.dp, Color.Transparent),
    ) {
        CornerMarker(modifier = Modifier.align(Alignment.TopStart))
        CornerMarker(modifier = Modifier.align(Alignment.TopEnd).rotateCorner(90f))
        CornerMarker(modifier = Modifier.align(Alignment.BottomEnd).rotateCorner(180f))
        CornerMarker(modifier = Modifier.align(Alignment.BottomStart).rotateCorner(270f))
    }
}

@Composable
private fun CornerMarker(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(5.dp)
                .background(Color(0xFF2E9BFF)),
        )
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(28.dp)
                .background(Color(0xFF2E9BFF)),
        )
    }
}

private fun Modifier.rotateCorner(degrees: Float): Modifier = this.graphicsLayer(rotationZ = degrees)

private fun looksLikeSteamQr(rawValue: String): Boolean {
    val payload = rawValue.trim()
    return payload.startsWith("https://s.team/q/", ignoreCase = true) ||
        payload.startsWith("http://s.team/q/", ignoreCase = true) ||
        payload.startsWith("steam://", ignoreCase = true) ||
        payload.startsWith("https://steamcommunity.com/openid/login", ignoreCase = true) ||
        payload.startsWith("http://steamcommunity.com/openid/login", ignoreCase = true)
}

private fun looksLikeDesktopPairingQr(rawValue: String): Boolean =
    rawValue.trim().let {
        it.startsWith("sdapp-pair://v2", ignoreCase = true) ||
            it.startsWith("sdapp-pair://v1", ignoreCase = true)
    }

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun QrCameraPreview(
    isActive: Boolean,
    scanningEnabled: Boolean,
    onQrDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanningEnabledState = rememberUpdatedState(scanningEnabled)
    val onQrDetectedState = rememberUpdatedState(onQrDetected)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(FORMAT_QR_CODE)
                .build(),
        )
    }

    DisposableEffect(lifecycleOwner, isActive) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()

            if (!isActive) {
                cameraProvider.unbindAll()
                previewView.alpha = 0f
                return@Runnable
            }

            previewView.alpha = 1f
            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (!scanningEnabledState.value) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT || !it.rawValue.isNullOrBlank() }
                            ?.rawValue
                            ?.let(onQrDetectedState.value)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
            runCatching { scanner.close() }
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        update = { view ->
            view.alpha = if (isActive) 1f else 0f
        },
        modifier = Modifier.fillMaxSize(),
    )
}

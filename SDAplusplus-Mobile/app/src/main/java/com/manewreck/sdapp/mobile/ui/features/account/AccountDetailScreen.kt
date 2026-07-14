package com.manewreck.sdapp.mobile.ui.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.manewreck.sdapp.mobile.core.model.VaultAccount
import com.manewreck.sdapp.mobile.data.SteamPublicProfile
import com.manewreck.sdapp.mobile.ui.AppStrings
import com.manewreck.sdapp.mobile.ui.components.MobileCard
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AccountDetailScreen(
    account: VaultAccount?,
    publicProfile: SteamPublicProfile?,
    currentCode: String,
    secondsRemaining: Int,
    strings: AppStrings,
    onOpenQrScanner: () -> Unit,
    onCopyCode: () -> Unit,
    onCopySteamId: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenConfirmations: () -> Unit,
    onTerminateSessions: () -> Unit,
    accountToolRunning: Boolean,
) {
    var showTerminateWarning by rememberSaveable { mutableStateOf(false) }
    val progress = if (account == null) 0f else secondsRemaining.coerceIn(0, 30) / 30f
    val importedAt = rememberFormattedInstant(account?.importedAtUtc)
    val profileState = rememberProfileState(publicProfile)
    val sessionHealthLabel = if (account?.hasSessionSnapshot == true) {
        "Snapshot imported"
    } else {
        "No mobile web session"
    }
    val sessionHealthText = if (account?.hasSessionSnapshot == true) {
        "This vault includes a mobile web session snapshot from the imported maFile."
    } else {
        "This import only contains Steam Guard secrets. Web-session actions will need a fresh login later."
    }

    if (showTerminateWarning) {
        AlertDialog(
            onDismissRequest = { showTerminateWarning = false },
            title = { Text(strings.terminateSessions) },
            text = { Text(strings.terminateSessionsWarning) },
            dismissButton = {
                OutlinedButton(onClick = { showTerminateWarning = false }) {
                    Text(strings.cancel)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTerminateWarning = false
                        onTerminateSessions()
                    },
                ) {
                    Text(strings.terminateSessions)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MobileCard {
            if (account != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileAvatar(
                        accountName = account.accountName,
                        avatarUrl = publicProfile?.avatarUrl,
                        levelLabel = publicProfile?.level?.toString() ?: "--",
                        profileState = profileState,
                        size = 64.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = publicProfile?.personaName ?: account.accountName,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DetailPillForProfileState(profileState)
                            if (account.isFavorite) {
                                StatusPill("Favorite", Color(0xFF4B3A17), Color(0xFFFFE29A))
                            }
                        }
                    }
                    OutlinedButton(onClick = onToggleFavorite) {
                        Text(if (account.isFavorite) "Unfavorite" else "Favorite")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        account?.accountName ?: strings.noAccountSelected,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            Text(
                strings.steamGuardCode,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                currentCode,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (account == null) {
                    strings.importAccountToGenerate
                } else {
                    "$secondsRemaining ${strings.secondsRemaining}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            if (account != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "SteamID",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            account.steamId,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(onClick = onCopySteamId, modifier = Modifier.wrapContentWidth()) {
                        Text("Copy SteamID")
                    }
                }
            }
        }

        MobileCard {
            Text("Account overview", style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("Vault encrypted", Color(0xFF233B68), Color(0xFFB8D0FF))
                    StatusPill(
                        if (account?.hasCloudBackup == true) "Cloud linked" else "Local-only",
                        if (account?.hasCloudBackup == true) Color(0xFF1F4A3A) else Color(0xFF403235),
                        if (account?.hasCloudBackup == true) Color(0xFFB7F2D0) else Color(0xFFFFD7C9),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        sessionHealthLabel,
                        if (account?.hasSessionSnapshot == true) Color(0xFF233B68) else Color(0xFF4A2E31),
                        if (account?.hasSessionSnapshot == true) Color(0xFFB8D0FF) else Color(0xFFFFC7CD),
                    )
                }
            }
            Text(
                sessionHealthText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (importedAt != null) {
                Text(
                    "Imported: $importedAt",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text(
                "Code refresh: ${secondsRemaining}s / 30s",
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        MobileCard {
            Text(strings.accountTools, style = MaterialTheme.typography.titleMedium)
            Text(
                if (account?.hasSessionSnapshot == true) sessionHealthText else strings.sessionRequired,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenConfirmations,
                    enabled = account != null && !accountToolRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.confirmations)
                }
                OutlinedButton(
                    onClick = { showTerminateWarning = true },
                    enabled = account?.hasSessionSnapshot == true && !accountToolRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.terminateSessions)
                }
            }
        }

        MobileCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onOpenQrScanner, modifier = Modifier.weight(1f)) {
                    Text(strings.scanQr)
                }
                Button(
                    onClick = onCopyCode,
                    enabled = account != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.copyCode)
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    accountName: String,
    avatarUrl: String?,
    levelLabel: String,
    profileState: ProfileState,
    size: androidx.compose.ui.unit.Dp,
) {
    val initials = accountName
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { it.take(1).uppercase() }
        .take(2)
        .ifBlank { accountName.take(2).uppercase() }

    val frameColor = when (profileState) {
        ProfileState.Loaded -> Color(0xFF4E6BD8)
        ProfileState.NewAccount -> Color(0xFFB98A3A)
        ProfileState.Unavailable -> Color(0xFF8C4A55)
    }
    val badgeColor = when (profileState) {
        ProfileState.Loaded -> Color(0xFF2A4278)
        ProfileState.NewAccount -> Color(0xFF5A441D)
        ProfileState.Unavailable -> Color(0xFF4A2E31)
    }
    val badgeTextColor = when (profileState) {
        ProfileState.Loaded -> Color(0xFFEAF2FF)
        ProfileState.NewAccount -> Color(0xFFFFE5A7)
        ProfileState.Unavailable -> Color(0xFFFFCFD4)
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111827))
                .border(1.5.dp, frameColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "$accountName avatar",
                    modifier = Modifier.matchParentSize(),
                )
            } else if (profileState != ProfileState.Loaded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF222838),
                                    Color(0xFF171C28),
                                    Color(0xFF0F131B),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD8DEEC)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 5.dp, bottomEnd = 5.dp))
                                .background(Color(0xFFD8DEEC)),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6D4A22),
                                    Color(0xFF2A2019),
                                    Color(0xFF111827),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFF5F7FF),
                    )
                }
            }
        }

        Surface(
            color = badgeColor,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp),
        ) {
            Text(
                text = levelLabel,
                color = badgeTextColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun DetailPillForProfileState(profileState: ProfileState) {
    when (profileState) {
        ProfileState.Loaded -> StatusPill("Public profile", Color(0xFF213A57), Color(0xFFC8DFFF))
        ProfileState.NewAccount -> StatusPill("New account", Color(0xFF4A3817), Color(0xFFFFE19B))
        ProfileState.Unavailable -> StatusPill("Unavailable", Color(0xFF4A2E31), Color(0xFFFFC7CD))
    }
}

@Composable
private fun StatusPill(
    label: String,
    background: Color,
    textColor: Color,
) {
    Surface(
        color = background,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun rememberProfileState(profile: SteamPublicProfile?): ProfileState {
    return when {
        profile == null -> ProfileState.Unavailable
        profile.level == 0 -> ProfileState.NewAccount
        else -> ProfileState.Loaded
    }
}

private enum class ProfileState {
    Loaded,
    NewAccount,
    Unavailable,
}

private fun rememberFormattedInstant(raw: String?): String? {
    if (raw.isNullOrBlank()) {
        return null
    }
    return runCatching {
        val zoned = Instant.parse(raw).atZone(ZoneId.systemDefault())
        val now = LocalDateTime.now(zoned.zone)
        val sameYear = zoned.year == now.year
        val pattern = if (sameYear) "dd MMM, HH:mm" else "dd MMM yyyy, HH:mm"
        DateTimeFormatter.ofPattern(pattern).format(zoned)
    }.getOrDefault(raw)
}

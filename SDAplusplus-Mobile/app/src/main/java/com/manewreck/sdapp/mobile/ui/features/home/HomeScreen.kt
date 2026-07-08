package com.manewreck.sdapp.mobile.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
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
import com.manewreck.sdapp.mobile.core.model.AccountSummary
import com.manewreck.sdapp.mobile.data.AccountSortMode
import com.manewreck.sdapp.mobile.ui.AppStrings
import com.manewreck.sdapp.mobile.ui.accountSortModeLabel
import com.manewreck.sdapp.mobile.ui.components.MobileCard
import com.manewreck.sdapp.mobile.data.SteamPublicProfile
import java.util.Comparator

@Composable
fun HomeScreen(
    accounts: List<AccountSummary>,
    publicProfiles: Map<String, SteamPublicProfile>,
    selectedAccountId: String?,
    accountSortMode: AccountSortMode,
    accountSearchEnabled: Boolean,
    strings: AppStrings,
    onImportMaFile: () -> Unit,
    onOpenAccount: (AccountSummary) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleAccounts = remember(accounts, publicProfiles, accountSortMode, accountSearchEnabled, searchQuery) {
        accounts
            .asSequence()
            .filter { account ->
                if (!accountSearchEnabled || searchQuery.isBlank()) {
                    true
                } else {
                    val profile = publicProfiles[account.steamId]
                    val haystack = buildString {
                        append(account.accountName)
                        append('\n')
                        append(account.steamId)
                        append('\n')
                        append(profile?.personaName.orEmpty())
                    }.lowercase()
                    haystack.contains(searchQuery.trim().lowercase())
                }
            }
            .sortedWith(Comparator { left, right ->
                if (left.isFavorite != right.isFavorite) {
                    return@Comparator when {
                        left.isFavorite -> -1
                        else -> 1
                    }
                }

                val leftName = (publicProfiles[left.steamId]?.personaName ?: left.accountName).lowercase()
                val rightName = (publicProfiles[right.steamId]?.personaName ?: right.accountName).lowercase()

                when (accountSortMode) {
                    AccountSortMode.Alphabetical -> leftName.compareTo(rightName)
                    AccountSortMode.Level -> {
                        val leftLevel = publicProfiles[left.steamId]?.level ?: -1
                        val rightLevel = publicProfiles[right.steamId]?.level ?: -1
                        if (leftLevel != rightLevel) {
                            rightLevel.compareTo(leftLevel)
                        } else {
                            leftName.compareTo(rightName)
                        }
                    }
                }
            })
            .toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MobileCard {
                Text("SDA++ Mobile", style = MaterialTheme.typography.headlineSmall)
                Text(
                    strings.importIntro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onImportMaFile, modifier = Modifier.weight(1f)) {
                        Text(strings.importMaFile)
                    }
                }
                Text(
                    "${strings.accountSort}: ${accountSortModeLabel(accountSortMode, strings)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    strings.favoritesFirst,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (accountSearchEnabled) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(strings.searchAccountsPlaceholder) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            }
        }

        if (visibleAccounts.isEmpty()) {
            item {
                MobileCard {
                    Text(
                        if (accounts.isEmpty()) strings.noAccountsYet else strings.noSearchResults,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (accounts.isEmpty()) strings.noAccountsBody else strings.searchAccountsPlaceholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        items(
            items = visibleAccounts,
            key = { account -> account.steamId },
        ) { account ->
            val profile = publicProfiles[account.steamId]
            AccountListCard(
                account = account,
                profile = profile,
                isSelected = account.steamId == selectedAccountId,
                subtitle = when {
                    account.steamId == selectedAccountId -> strings.currentlySelected
                    account.isFavorite && account.hasCloudBackup -> strings.favoriteCloudReady
                    account.hasCloudBackup -> strings.cloudBackupReady
                    else -> strings.localVaultOnly
                },
                onClick = { onOpenAccount(account) },
            )
        }
    }
}

@Composable
private fun AccountListCard(
    account: AccountSummary,
    profile: SteamPublicProfile?,
    isSelected: Boolean,
    subtitle: String,
    onClick: () -> Unit,
) {
    val profileState = rememberProfileState(profile)
    val frameColor = when (profileState) {
        ProfileState.Loaded -> Color(0xFF445FCA)
        ProfileState.NewAccount -> Color(0xFFAF8139)
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

    MobileCard(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountAvatar(
                accountName = account.accountName,
                avatarUrl = profile?.avatarUrl,
                levelLabel = profile?.level?.toString() ?: "--",
                useGenericPlaceholder = profileState == ProfileState.NewAccount || profileState == ProfileState.Unavailable,
                frameColor = frameColor,
                badgeColor = badgeColor,
                badgeTextColor = badgeTextColor,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = profile?.personaName ?: account.accountName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        MiniPill(
                            label = "Live",
                            background = Color(0xFF24496A),
                            textColor = Color(0xFFBFE0FF),
                        )
                    } else if (account.isFavorite) {
                        MiniPill(
                            label = "Fav",
                            background = Color(0xFF4A3817),
                            textColor = Color(0xFFFFE19B),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (profileState) {
                        ProfileState.Loaded -> MiniPill(
                            label = "Public profile",
                            background = Color(0xFF213A57),
                            textColor = Color(0xFFC8DFFF),
                        )
                        ProfileState.NewAccount -> MiniPill(
                            label = "New account",
                            background = Color(0xFF4A3817),
                            textColor = Color(0xFFFFE19B),
                        )
                        ProfileState.Unavailable -> MiniPill(
                            label = "Unavailable",
                            background = Color(0xFF4A2E31),
                            textColor = Color(0xFFFFC7CD),
                        )
                    }
                }
                Text(
                    text = account.steamId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AccountAvatar(
    accountName: String,
    avatarUrl: String?,
    levelLabel: String,
    useGenericPlaceholder: Boolean,
    frameColor: Color,
    badgeColor: Color,
    badgeTextColor: Color,
) {
    val initials = accountName
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { it.take(1).uppercase() }
        .take(2)
        .ifBlank { accountName.take(2).uppercase() }

    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF111827))
                .border(1.5.dp, frameColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "$accountName avatar",
                    modifier = Modifier.matchParentSize(),
                )
            } else if (useGenericPlaceholder) {
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
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD8DEEC)),
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(13.dp)
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
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                .offset(x = 4.dp, y = (-4).dp),
        ) {
            Text(
                text = levelLabel,
                color = badgeTextColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
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

@Composable
private fun MiniPill(
    label: String,
    background: Color,
    textColor: Color,
) {
    Surface(
        color = background,
        shape = CircleShape,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

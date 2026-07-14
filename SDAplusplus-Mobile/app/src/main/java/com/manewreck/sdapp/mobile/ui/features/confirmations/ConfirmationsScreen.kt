package com.manewreck.sdapp.mobile.ui.features.confirmations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manewreck.sdapp.mobile.data.MobileConfirmation
import com.manewreck.sdapp.mobile.ui.AppStrings
import com.manewreck.sdapp.mobile.ui.components.MobileCard

@Composable
fun ConfirmationsScreen(
    confirmations: List<MobileConfirmation>,
    loading: Boolean,
    notice: String?,
    actionId: String?,
    strings: AppStrings,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRespond: (MobileConfirmation, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MobileCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.confirmations, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            strings.confirmationsIntro,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("‹")
                    }
                }
                Button(
                    onClick = onRefresh,
                    enabled = !loading && actionId == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(strings.refresh)
                }
            }
        }

        if (loading) {
            item {
                MobileCard {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(strings.loadingConfirmations)
                    }
                }
            }
        } else if (confirmations.isEmpty()) {
            item {
                MobileCard {
                    Text(strings.noConfirmations, style = MaterialTheme.typography.titleMedium)
                    notice?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        items(
            items = confirmations,
            key = { confirmation -> "${confirmation.accountSteamId}:${confirmation.id}" },
        ) { confirmation ->
            MobileCard {
                Text(
                    confirmation.accountName,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    confirmation.headline,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                confirmation.summary.forEach { line ->
                    Text(
                        line,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onRespond(confirmation, false) },
                        enabled = actionId == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(confirmation.cancelLabel.ifBlank { strings.reject })
                    }
                    Button(
                        onClick = { onRespond(confirmation, true) },
                        enabled = actionId == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(confirmation.acceptLabel.ifBlank { strings.accept })
                    }
                }
            }
        }

        if (!notice.isNullOrBlank() && confirmations.isNotEmpty()) {
            item {
                Text(notice, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

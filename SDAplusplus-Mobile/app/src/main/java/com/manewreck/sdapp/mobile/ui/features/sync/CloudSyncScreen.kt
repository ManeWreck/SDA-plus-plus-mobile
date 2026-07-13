package com.manewreck.sdapp.mobile.ui.features.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.manewreck.sdapp.mobile.core.model.SyncState
import com.manewreck.sdapp.mobile.core.sync.CloudProviderType
import com.manewreck.sdapp.mobile.data.CloudSyncSettings
import com.manewreck.sdapp.mobile.ui.AppStrings
import com.manewreck.sdapp.mobile.ui.cloudProviderLabel
import com.manewreck.sdapp.mobile.ui.components.MobileCard
import com.manewreck.sdapp.mobile.ui.components.SimpleDropdownField

@Composable
fun CloudSyncScreen(
    accountCount: Int,
    settings: CloudSyncSettings,
    syncState: SyncState,
    strings: AppStrings,
    onProviderSelected: (CloudProviderType) -> Unit,
    onUrlChanged: (String) -> Unit,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onRemotePathChanged: (String) -> Unit,
    onTest: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MobileCard {
            Text(strings.cloudSync, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${strings.provider}: ${cloudProviderLabel(settings.provider)}",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "${strings.vaultAccounts}: $accountCount",
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                syncState.lastSyncDisplay ?: strings.lastSyncNotYet,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        MobileCard {
            SimpleDropdownField(
                label = strings.provider,
                value = cloudProviderLabel(settings.provider),
                options = CloudProviderType.entries.map(::cloudProviderLabel),
                onSelected = { selected ->
                    val provider = CloudProviderType.entries.first { cloudProviderLabel(it) == selected }
                    onProviderSelected(provider)
                },
            )
            OutlinedTextField(
                value = settings.url,
                onValueChange = onUrlChanged,
                label = { Text(strings.cloudUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 48.dp),
            )
            OutlinedTextField(
                value = settings.login,
                onValueChange = onLoginChanged,
                label = { Text(strings.cloudLogin) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = settings.password,
                onValueChange = onPasswordChanged,
                label = { Text(strings.cloudPassword) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            OutlinedTextField(
                value = settings.remotePath,
                onValueChange = onRemotePathChanged,
                label = { Text(strings.cloudFolder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Text(strings.testConnection)
                }
                Button(onClick = onPull, modifier = Modifier.weight(1f)) {
                    Text(strings.pull)
                }
                Button(onClick = onPush, modifier = Modifier.weight(1f)) {
                    Text(strings.push)
                }
            }
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(strings.save)
            }
            Text(
                strings.cloudSettingsHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

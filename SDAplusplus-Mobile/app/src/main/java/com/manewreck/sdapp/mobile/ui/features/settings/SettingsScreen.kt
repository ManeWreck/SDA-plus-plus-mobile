package com.manewreck.sdapp.mobile.ui.features.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.manewreck.sdapp.mobile.data.AccountSortMode
import com.manewreck.sdapp.mobile.data.AppLanguage
import com.manewreck.sdapp.mobile.ui.AppStrings
import com.manewreck.sdapp.mobile.ui.accountSortModeLabel
import com.manewreck.sdapp.mobile.ui.components.MobileCard
import com.manewreck.sdapp.mobile.ui.components.SimpleDropdownField

@Composable
fun SettingsScreen(
    accountCount: Int,
    currentLanguage: AppLanguage,
    accountSortMode: AccountSortMode,
    accountSearchEnabled: Boolean,
    pinDraft: String,
    biometricsEnabled: Boolean,
    biometricAvailable: Boolean,
    strings: AppStrings,
    onLanguageChanged: (AppLanguage) -> Unit,
    onAccountSortModeChanged: (AccountSortMode) -> Unit,
    onAccountSearchEnabledChanged: (Boolean) -> Unit,
    onPinChanged: (String) -> Unit,
    onSavePin: () -> Unit,
    onBiometricsChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MobileCard {
            Text(strings.settings, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${strings.importedAccounts}: $accountCount",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                strings.settingsIntro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                strings.encryptedVaultReady,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        MobileCard {
            SimpleDropdownField(
                label = strings.language,
                value = if (currentLanguage == AppLanguage.Russian) strings.russian else strings.english,
                options = listOf(strings.english, strings.russian),
                onSelected = { selected ->
                    onLanguageChanged(if (selected == strings.russian) AppLanguage.Russian else AppLanguage.English)
                },
            )
            SimpleDropdownField(
                label = strings.accountSort,
                value = accountSortModeLabel(accountSortMode, strings),
                options = listOf(strings.sortAlphabetical, strings.sortLevel),
                onSelected = { selected ->
                    onAccountSortModeChanged(
                        if (selected == strings.sortLevel) AccountSortMode.Level else AccountSortMode.Alphabetical,
                    )
                },
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.accountSearch, style = MaterialTheme.typography.titleSmall)
                    Text(
                        strings.enableAccountSearch,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        strings.favoritesFirst,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Switch(
                    checked = accountSearchEnabled,
                    onCheckedChange = onAccountSearchEnabledChanged,
                    modifier = Modifier.wrapContentWidth(),
                )
            }
        }

        MobileCard {
            Text(strings.security, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pinDraft,
                onValueChange = onPinChanged,
                label = { Text(strings.appPin) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Button(
                onClick = onSavePin,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(strings.setPin)
            }
            Text(
                strings.encryptionEnabled,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Switch(
                checked = biometricsEnabled,
                onCheckedChange = onBiometricsChanged,
                enabled = biometricAvailable,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (biometricAvailable) strings.biometrics else strings.biometricUnavailable,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

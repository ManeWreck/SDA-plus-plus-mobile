package com.manewreck.sdapp.mobile.ui.features.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.manewreck.sdapp.mobile.ui.AppStrings
import com.manewreck.sdapp.mobile.ui.components.MobileCard

@Composable
fun AppLockScreen(
    pin: String,
    strings: AppStrings,
    biometricsEnabled: Boolean,
    biometricAvailable: Boolean,
    onPinChanged: (String) -> Unit,
    onUnlock: () -> Unit,
    onUnlockWithBiometrics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        MobileCard {
            Text(strings.appLocked, style = MaterialTheme.typography.headlineSmall)
            Text(
                strings.appLockedBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = pin,
                onValueChange = onPinChanged,
                label = { Text(strings.enterPin) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            Button(
                onClick = onUnlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(strings.unlock)
            }
            if (biometricsEnabled && biometricAvailable) {
                Button(
                    onClick = onUnlockWithBiometrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(strings.unlockWithBiometrics)
                }
            }
        }
    }
}

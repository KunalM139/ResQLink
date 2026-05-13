package com.resqlink.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // User Profile
        SectionHeader("Profile")
        OutlinedTextField(
            value = uiState.userName,
            onValueChange = { viewModel.updateUserName(it) },
            label = { Text("Your Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.userPhone,
            onValueChange = { viewModel.updateUserPhone(it) },
            label = { Text("Your Phone Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Automatic SOS Triggers
        SectionHeader("Automatic SOS Triggers")
        SettingsCard {
            SettingsToggleItem(
                title = "Shake Detection",
                description = "Shake phone vigorously to trigger SOS",
                checked = uiState.shakeDetectionEnabled,
                onCheckedChange = { viewModel.toggleShakeDetection(it) }
            )

            HorizontalDivider()

            SettingsToggleItem(
                title = "Fall Detection",
                description = "Automatically send SOS when a fall is detected",
                checked = uiState.fallDetectionEnabled,
                onCheckedChange = { viewModel.toggleFallDetection(it) }
            )

            HorizontalDivider()

            SettingsToggleItem(
                title = "Power Button SOS",
                description = "Press power button 3 times quickly to trigger SOS",
                checked = uiState.powerButtonSosEnabled,
                onCheckedChange = { viewModel.togglePowerButtonSos(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Communication Settings
        SectionHeader("Communication")
        SettingsCard {
            SettingsToggleItem(
                title = "Mesh Relay",
                description = "Help relay emergency messages from other users",
                checked = uiState.meshRelayEnabled,
                onCheckedChange = { viewModel.toggleMeshRelay(it) }
            )

            HorizontalDivider()

            SettingsToggleItem(
                title = "SMS Backup",
                description = "Send SMS backup when sending emergency alerts",
                checked = uiState.smsBackupEnabled,
                onCheckedChange = { viewModel.toggleSmsBackup(it) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

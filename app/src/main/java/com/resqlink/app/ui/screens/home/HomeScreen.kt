package com.resqlink.app.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.resqlink.app.data.model.ConnectionStatus
import com.resqlink.app.service.MeshForegroundService
import com.resqlink.app.ui.components.SosButton
import com.resqlink.app.ui.components.StatusIndicator
import com.resqlink.app.ui.theme.EmergencyRed
import com.resqlink.app.ui.theme.MeshBlue
import com.resqlink.app.ui.theme.SafeGreen
import com.resqlink.app.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.lastSosResult) {
        when (uiState.lastSosResult) {
            is SosResult.Success -> {
                Toast.makeText(context, "Emergency alert sent!", Toast.LENGTH_LONG).show()
                viewModel.clearSosResult()
            }
            is SosResult.Error -> {
                val msg = (uiState.lastSosResult as SosResult.Error).message
                Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                viewModel.clearSosResult()
            }
            null -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "ResQLink",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = EmergencyRed
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Connection Status
        StatusIndicator(status = uiState.connectionStatus)

        Spacer(modifier = Modifier.height(24.dp))

        // Connection Info Card
        ConnectionInfoCard(status = uiState.connectionStatus)

        Spacer(modifier = Modifier.height(32.dp))

        // SOS Button
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            SosButton(
                isSending = uiState.isSending,
                onSosTriggered = { viewModel.sendSos() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Press and hold for 2 seconds to send emergency alert",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mesh Service Toggle
        MeshServiceToggle(
            isRunning = uiState.meshServiceRunning,
            onToggle = { enabled ->
                viewModel.setMeshServiceRunning(enabled)
                toggleMeshService(context, enabled)
            }
        )
    }
}

@Composable
private fun ConnectionInfoCard(status: ConnectionStatus) {
    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.ONLINE -> SafeGreen.copy(alpha = 0.1f)
            ConnectionStatus.CELLULAR_ONLY -> WarningAmber.copy(alpha = 0.1f)
            ConnectionStatus.OFFLINE_MESH -> MeshBlue.copy(alpha = 0.1f)
            ConnectionStatus.NO_CONNECTION -> EmergencyRed.copy(alpha = 0.1f)
        },
        label = "cardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = when (status) {
                    ConnectionStatus.ONLINE -> Icons.Default.Cloud
                    ConnectionStatus.CELLULAR_ONLY -> Icons.Default.Phone
                    ConnectionStatus.OFFLINE_MESH -> Icons.Default.Bluetooth
                    ConnectionStatus.NO_CONNECTION -> Icons.Default.CloudOff
                },
                contentDescription = null,
                tint = when (status) {
                    ConnectionStatus.ONLINE -> SafeGreen
                    ConnectionStatus.CELLULAR_ONLY -> WarningAmber
                    ConnectionStatus.OFFLINE_MESH -> MeshBlue
                    ConnectionStatus.NO_CONNECTION -> EmergencyRed
                },
                modifier = Modifier.size(32.dp)
            )

            Column {
                Text(
                    text = when (status) {
                        ConnectionStatus.ONLINE -> "Direct Internet Mode"
                        ConnectionStatus.CELLULAR_ONLY -> "Cellular SMS Mode"
                        ConnectionStatus.OFFLINE_MESH -> "Bluetooth Mesh Mode"
                        ConnectionStatus.NO_CONNECTION -> "No Connection"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when (status) {
                        ConnectionStatus.ONLINE -> "Messages will be sent directly to the server"
                        ConnectionStatus.CELLULAR_ONLY -> "Messages will be sent via SMS"
                        ConnectionStatus.OFFLINE_MESH -> "Messages will be relayed via nearby devices"
                        ConnectionStatus.NO_CONNECTION -> "Enable mesh service to relay via BLE"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MeshServiceToggle(isRunning: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mesh Relay Service",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isRunning) "Active — relaying emergency messages"
                    else "Disabled — enable to help relay messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isRunning,
                onCheckedChange = onToggle
            )
        }
    }
}

private fun toggleMeshService(context: Context, enable: Boolean) {
    val intent = if (enable) {
        MeshForegroundService.startIntent(context)
    } else {
        MeshForegroundService.stopIntent(context)
    }
    ContextCompat.startForegroundService(context, intent)
}

package com.resqlink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqlink.app.data.model.ConnectionStatus
import com.resqlink.app.ui.theme.EmergencyRed
import com.resqlink.app.ui.theme.MeshBlue
import com.resqlink.app.ui.theme.SafeGreen
import com.resqlink.app.ui.theme.WarningAmber

@Composable
fun StatusIndicator(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.ONLINE -> SafeGreen
            ConnectionStatus.CELLULAR_ONLY -> WarningAmber
            ConnectionStatus.OFFLINE_MESH -> MeshBlue
            ConnectionStatus.NO_CONNECTION -> EmergencyRed
        },
        label = "statusColor"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = statusColor
        )
    }
}

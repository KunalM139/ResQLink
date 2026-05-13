package com.resqlink.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqlink.app.ui.theme.EmergencyRed
import com.resqlink.app.ui.theme.EmergencyRedDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SosButton(
    isSending: Boolean,
    onSosTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var holdStartTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isPressed) EmergencyRedDark else EmergencyRed,
        animationSpec = tween(200),
        label = "buttonColor"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        if (!isSending) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(EmergencyRed.copy(alpha = 0.15f))
            )
        }

        // Main button
        Box(
            modifier = modifier
                .size(160.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(buttonColor)
                .border(4.dp, EmergencyRedDark, CircleShape)
                .pointerInput(isSending) {
                    if (isSending) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            holdStartTime = System.currentTimeMillis()

                            val holdJob = scope.launch {
                                delay(2000) // 2 second hold
                                onSosTriggered()
                            }

                            val released = tryAwaitRelease()
                            isPressed = false
                            if (released) {
                                val holdDuration = System.currentTimeMillis() - holdStartTime
                                if (holdDuration < 2000) {
                                    holdJob.cancel()
                                }
                            } else {
                                holdJob.cancel()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp
                )
            } else {
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}

package com.resqlink.app.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects rapid power button presses to trigger emergency SOS.
 *
 * Listens for ACTION_SCREEN_OFF / ACTION_SCREEN_ON broadcasts.
 * Three rapid presses within a 2-second window triggers the SOS.
 *
 * Note: Starting from Android 12+, some OEMs have built-in
 * emergency SOS on power button presses. This provides a
 * fallback mechanism within the app.
 */
@Singleton
class PowerButtonDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    var onPowerButtonSos: (() -> Unit)? = null

    private var pressCount = 0
    private var firstPressTime = 0L
    private var isRegistered = false

    companion object {
        private const val PRESS_COUNT_THRESHOLD = 3
        private const val PRESS_WINDOW_MS = 2000L
        private const val COOLDOWN_MS = 10_000L
        private var lastTriggerTime = 0L
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handlePress()
                Intent.ACTION_SCREEN_ON -> handlePress()
            }
        }
    }

    fun start() {
        if (isRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenReceiver, filter)
        isRegistered = true
        Timber.d("PowerButtonDetector started")
    }

    fun stop() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        isRegistered = false
        Timber.d("PowerButtonDetector stopped")
    }

    private fun handlePress() {
        val now = System.currentTimeMillis()

        if (now - firstPressTime > PRESS_WINDOW_MS) {
            pressCount = 0
        }

        if (pressCount == 0) {
            firstPressTime = now
        }

        pressCount++

        if (pressCount >= PRESS_COUNT_THRESHOLD) {
            if (now - lastTriggerTime > COOLDOWN_MS) {
                Timber.d("Power button SOS triggered!")
                lastTriggerTime = now
                onPowerButtonSos?.invoke()
            }
            pressCount = 0
        }
    }
}

package com.resqlink.app.trigger

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Detects fall patterns using accelerometer and gyroscope data.
 *
 * Detection algorithm phases:
 * 1. FREE-FALL: acceleration drops near zero (< 3 m/s²)
 * 2. IMPACT: sudden high acceleration spike (> 20 m/s²)
 * 3. STILLNESS: low movement after impact for several seconds
 *
 * All three phases must occur in sequence within the time window
 * to minimize false positives.
 */
@Singleton
class FallDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var onFallDetected: (() -> Unit)? = null

    private var state = FallState.MONITORING
    private var freeFallStartTime = 0L
    private var impactTime = 0L

    private enum class FallState {
        MONITORING,
        FREE_FALL_DETECTED,
        IMPACT_DETECTED,
        CONFIRMING_STILLNESS
    }

    companion object {
        private const val FREE_FALL_THRESHOLD = 3.0f        // m/s² (near weightlessness)
        private const val IMPACT_THRESHOLD = 20.0f          // m/s²
        private const val STILLNESS_THRESHOLD = 2.0f        // m/s² deviation from gravity
        private const val FREE_FALL_MIN_DURATION_MS = 100L  // minimum free-fall time
        private const val MAX_FALL_WINDOW_MS = 2000L        // free-fall → impact window
        private const val STILLNESS_DURATION_MS = 3000L     // post-impact stillness window
        private const val COOLDOWN_MS = 30_000L             // prevent repeated triggers
        private var lastTriggerTime = 0L
    }

    fun start() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        Timber.d("FallDetector started")
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        state = FallState.MONITORING
        Timber.d("FallDetector stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()

        when (state) {
            FallState.MONITORING -> {
                // Phase 1: Detect free-fall
                if (magnitude < FREE_FALL_THRESHOLD) {
                    freeFallStartTime = now
                    state = FallState.FREE_FALL_DETECTED
                    Timber.d("Free-fall detected")
                }
            }

            FallState.FREE_FALL_DETECTED -> {
                if (now - freeFallStartTime > MAX_FALL_WINDOW_MS) {
                    // Timeout — reset
                    state = FallState.MONITORING
                    return
                }

                // Phase 2: Detect impact spike
                if (magnitude > IMPACT_THRESHOLD &&
                    now - freeFallStartTime > FREE_FALL_MIN_DURATION_MS
                ) {
                    impactTime = now
                    state = FallState.IMPACT_DETECTED
                    Timber.d("Impact detected after free-fall")
                }
            }

            FallState.IMPACT_DETECTED -> {
                // Transition to stillness confirmation
                state = FallState.CONFIRMING_STILLNESS
            }

            FallState.CONFIRMING_STILLNESS -> {
                val deviationFromGravity = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)

                if (deviationFromGravity > STILLNESS_THRESHOLD + 5f) {
                    // Active movement — probably not a fall
                    state = FallState.MONITORING
                    return
                }

                // Phase 3: Confirm stillness after impact
                if (now - impactTime > STILLNESS_DURATION_MS &&
                    deviationFromGravity < STILLNESS_THRESHOLD
                ) {
                    if (now - lastTriggerTime > COOLDOWN_MS) {
                        Timber.d("Fall confirmed — triggering SOS")
                        lastTriggerTime = now
                        onFallDetected?.invoke()
                    }
                    state = FallState.MONITORING
                }

                if (now - impactTime > STILLNESS_DURATION_MS * 2) {
                    // Took too long — reset
                    state = FallState.MONITORING
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

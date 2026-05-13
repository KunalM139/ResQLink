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
 * Detects vigorous shaking gestures to trigger emergency SOS.
 *
 * Uses the accelerometer to measure acceleration magnitude.
 * A shake is registered when multiple high-acceleration events
 * occur within a short time window.
 */
@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var onShakeDetected: (() -> Unit)? = null

    private var shakeCount = 0
    private var lastShakeTime = 0L

    companion object {
        private const val SHAKE_THRESHOLD = 25.0f      // m/s²
        private const val SHAKE_COUNT_THRESHOLD = 3     // number of shakes
        private const val SHAKE_WINDOW_MS = 2000L       // time window
        private const val MIN_SHAKE_INTERVAL_MS = 300L  // debounce
    }

    fun start() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Timber.d("ShakeDetector started")
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        Timber.d("ShakeDetector stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Remove gravity component approximation
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (acceleration > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()

            if (now - lastShakeTime > SHAKE_WINDOW_MS) {
                shakeCount = 0
            }

            if (now - lastShakeTime > MIN_SHAKE_INTERVAL_MS) {
                shakeCount++
                lastShakeTime = now

                if (shakeCount >= SHAKE_COUNT_THRESHOLD) {
                    Timber.d("Shake SOS triggered!")
                    shakeCount = 0
                    onShakeDetected?.invoke()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

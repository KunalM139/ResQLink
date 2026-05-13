package com.resqlink.app.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.resqlink.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages BLE advertising to broadcast emergency packet availability.
 * Uses manufacturer-specific data containing a truncated message hash
 * to signal packet availability. Full data is transferred via GATT.
 */
@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var currentCallback: AdvertiseCallback? = null

    private val serviceUuid = ParcelUuid.fromString(Constants.BLE_SERVICE_UUID)

    val isSupported: Boolean
        get() = bluetoothAdapter?.bluetoothLeAdvertiser != null

    fun startAdvertising(packetHash: ByteArray) {
        if (!hasPermission()) {
            Timber.w("BLUETOOTH_ADVERTISE permission not granted")
            return
        }

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Timber.w("BLE advertising not supported")
            return
        }

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // Main advert: only service UUID (stays within 31-byte BLE 4.x limit)
        val data = AdvertiseData.Builder()
            .addServiceUuid(serviceUuid)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        // Scan response: manufacturer data with packet hash for extra info
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(Constants.BLE_MANUFACTURER_ID, packetHash.take(8).toByteArray())
            .build()

        currentCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Timber.d("BLE advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Timber.e("BLE advertising failed: error $errorCode")
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, scanResponse, currentCallback)
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException starting BLE advertising")
        }
    }

    fun stopAdvertising() {
        if (!hasPermission()) return
        try {
            currentCallback?.let { advertiser?.stopAdvertising(it) }
            currentCallback = null
            Timber.d("BLE advertising stopped")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException stopping BLE advertising")
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

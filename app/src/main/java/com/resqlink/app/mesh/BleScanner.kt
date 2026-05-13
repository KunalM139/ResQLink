package com.resqlink.app.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
 * Scans for nearby BLE devices advertising the ResQLink service UUID.
 * When a peer is discovered, the callback provides the scan result
 * for GATT-based packet transfer.
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    var onPacketDiscovered: ((ByteArray) -> Unit)? = null
    var onDeviceDiscovered: ((android.bluetooth.BluetoothDevice) -> Unit)? = null

    val isSupported: Boolean
        get() = bluetoothAdapter?.bluetoothLeScanner != null

    fun startScanning() {
        if (!hasPermission()) {
            Timber.w("BLUETOOTH_SCAN permission not granted")
            return
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Timber.w("BLE scanner not available")
            return
        }

        stopScanning()

        val serviceUuid = ParcelUuid.fromString(Constants.BLE_SERVICE_UUID)

        val filter = ScanFilter.Builder()
            .setServiceUuid(serviceUuid)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    Timber.d("Discovered ResQLink peer: ${device.address}")
                    onDeviceDiscovered?.invoke(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("BLE scan failed: error $errorCode")
            }
        }

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            Timber.d("BLE scanning started")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException starting BLE scan")
        }
    }

    fun stopScanning() {
        if (!hasPermission()) return
        try {
            scanCallback?.let { scanner?.stopScan(it) }
            scanCallback = null
            Timber.d("BLE scanning stopped")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException stopping BLE scan")
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }
}

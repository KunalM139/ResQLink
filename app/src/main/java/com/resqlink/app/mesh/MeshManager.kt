package com.resqlink.app.mesh

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core mesh networking manager.
 *
 * Orchestrates BLE advertising, scanning, GATT server/client, and relay logic
 * to form an ad-hoc emergency message relay network.
 *
 * Architecture:
 *  - GATT Server: exposes a characteristic that holds serialized emergency packets.
 *    Peers connect and read the characteristic to receive the full packet.
 *  - GATT Client: when a peer advertising the ResQLink service UUID is discovered,
 *    connects & reads its characteristic.
 *  - Relay Controller: prevents duplicates, enforces TTL, adds jitter.
 */
@Singleton
class MeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleAdvertiser: BleAdvertiser,
    private val bleScanner: BleScanner,
    private val packetSerializer: PacketSerializer,
    private val relayController: RelayController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    private var gattServer: BluetoothGattServer? = null
    private var currentPacketData: ByteArray? = null

    var onPacketReceived: ((EmergencyPacket) -> Unit)? = null

    private val serviceUuid = UUID.fromString(Constants.BLE_SERVICE_UUID)
    private val characteristicUuid = UUID.fromString(Constants.BLE_CHARACTERISTIC_UUID)

    // ── Lifecycle ──────────────────────────────────────────────────────────

    fun start() {
        startGattServer()
        startScanning()
        Timber.d("MeshManager started")
    }

    fun stop() {
        bleAdvertiser.stopAdvertising()
        bleScanner.stopScanning()
        stopGattServer()
        Timber.d("MeshManager stopped")
    }

    // ── Broadcast (sender side) ────────────────────────────────────────────

    fun broadcastPacket(packet: EmergencyPacket) {
        val serialized = packetSerializer.serialize(packet)
        currentPacketData = serialized
        relayController.markSeen(packet.messageId)

        // Start advertising with a hash so scanners know there's a new packet
        val hash = serialized.copyOfRange(0, minOf(serialized.size, 20))
        bleAdvertiser.startAdvertising(hash)

        // Update the GATT characteristic value
        updateGattCharacteristic(serialized)

        Timber.d("Broadcasting packet: ${packet.messageId}")
    }

    // ── GATT Server (serves packets to connecting peers) ───────────────────

    private fun startGattServer() {
        if (!hasBleConnectPermission()) return

        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.d("Peer connected: ${device?.address}")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.d("Peer disconnected: ${device?.address}")
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (characteristic?.uuid == characteristicUuid) {
                    val data = currentPacketData ?: ByteArray(0)
                    val chunk = if (offset < data.size) {
                        data.copyOfRange(offset, data.size)
                    } else {
                        ByteArray(0)
                    }
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, chunk)
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException sending GATT response")
                    }
                } else {
                    // Must always respond to read requests to avoid connection hangs
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, ByteArray(0))
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException sending GATT failure response")
                    }
                }
            }
        }

        try {
            gattServer = bluetoothManager?.openGattServer(context, serverCallback)

            val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                characteristicUuid,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            service.addCharacteristic(characteristic)
            gattServer?.addService(service)

            Timber.d("GATT server started")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException starting GATT server")
        }
    }

    private fun updateGattCharacteristic(data: ByteArray) {
        gattServer?.services?.find { it.uuid == serviceUuid }
            ?.getCharacteristic(characteristicUuid)
            ?.setValue(data)
    }

    private fun stopGattServer() {
        try {
            gattServer?.close()
            gattServer = null
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException closing GATT server")
        }
    }

    // ── Scanning & GATT Client (receives packets from peers) ───────────────

    // Track recently connected devices to avoid repeated GATT connections
    private val recentlyConnected = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun startScanning() {
        bleScanner.onDeviceDiscovered = { device ->
            scope.launch {
                handleDiscoveredDevice(device)
            }
        }
        bleScanner.startScanning()
    }

    private suspend fun handleDiscoveredDevice(device: BluetoothDevice) {
        val address = try { device.address } catch (e: SecurityException) { return }
        // Avoid reconnecting to the same device within 30 seconds
        val now = System.currentTimeMillis()
        val lastSeen = recentlyConnected[address]
        if (lastSeen != null && now - lastSeen < 30_000L) return
        recentlyConnected[address] = now

        // Clean up old entries
        recentlyConnected.entries.removeAll { now - it.value > 60_000L }

        Timber.d("Connecting to peer GATT to read full packet: $address")
        connectAndReadPacket(device)
    }

    /**
     * Connects to a peer GATT server to read the full emergency packet.
     * Called when we discover a peer via BLE scan.
     */
    fun connectAndReadPacket(device: BluetoothDevice) {
        if (!hasBleConnectPermission()) return

        try {
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            // Request large MTU so the full encrypted packet can be read
                            gatt?.requestMtu(512)
                        } catch (e: SecurityException) {
                            Timber.e(e, "SecurityException requesting MTU")
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        try { gatt?.close() } catch (_: SecurityException) {}
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                    Timber.d("MTU changed to $mtu (status=$status)")
                    try {
                        gatt?.discoverServices()
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException discovering services")
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val characteristic = gatt?.getService(serviceUuid)
                            ?.getCharacteristic(characteristicUuid)
                        if (characteristic != null) {
                            try {
                                gatt.readCharacteristic(characteristic)
                            } catch (e: SecurityException) {
                                Timber.e(e, "SecurityException reading characteristic")
                            }
                        } else {
                            Timber.w("ResQLink characteristic not found on peer")
                            try { gatt?.close() } catch (_: SecurityException) {}
                        }
                    } else {
                        try { gatt?.close() } catch (_: SecurityException) {}
                    }
                }

                @Deprecated("Deprecated in API 33")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic?.uuid == characteristicUuid) {
                        val data = characteristic?.value
                        if (data != null && data.isNotEmpty()) {
                            scope.launch {
                                val packet = packetSerializer.deserialize(data)
                                if (packet != null && relayController.shouldRelay(packet)) {
                                    relayController.jitterDelay()
                                    onPacketReceived?.invoke(packet)
                                    Timber.d("Packet received via mesh: ${packet.messageId}")
                                }
                            }
                        }
                    }
                    try {
                        gatt?.close()
                    } catch (e: SecurityException) {
                        Timber.e(e, "SecurityException closing GATT")
                    }
                }
            })
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException connecting GATT")
        }
    }

    private fun hasBleConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}

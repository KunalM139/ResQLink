package com.resqlink.app.util

object Constants {
    // BLE
    const val BLE_SERVICE_UUID = "0000FFFF-0000-1000-8000-00805F9B34FB"
    const val BLE_CHARACTERISTIC_UUID = "0000FF01-0000-1000-8000-00805F9B34FB"
    const val BLE_MANUFACTURER_ID = 0x0451  // custom manufacturer ID

    // Notification channels
    const val CHANNEL_MESH_ID = "resqlink_mesh"
    const val CHANNEL_ALERT_ID = "resqlink_alert"
    const val MESH_NOTIFICATION_ID = 1001

    // Packet expiry: 24 hours
    const val PACKET_EXPIRY_MS = 24 * 60 * 60 * 1000L

    // Relay jitter delays
    const val RELAY_MIN_DELAY_MS = 1000L
    const val RELAY_MAX_DELAY_MS = 3000L

    // Cleanup interval: 1 hour
    const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L

    // DataStore keys
    const val DATASTORE_NAME = "resqlink_prefs"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_PHONE = "user_phone"
}

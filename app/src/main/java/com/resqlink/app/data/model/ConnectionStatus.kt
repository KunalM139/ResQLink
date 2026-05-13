package com.resqlink.app.data.model

enum class ConnectionStatus {
    ONLINE,
    CELLULAR_ONLY,
    OFFLINE_MESH,
    NO_CONNECTION;

    val displayName: String
        get() = when (this) {
            ONLINE -> "Online"
            CELLULAR_ONLY -> "Cellular Only \u2014 SMS Mode"
            OFFLINE_MESH -> "Offline — Mesh Active"
            NO_CONNECTION -> "No Connection"
        }
}

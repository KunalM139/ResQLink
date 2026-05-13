package com.resqlink.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Double.toCoordinateString(): String {
    return "%.6f".format(this)
}

fun createGoogleMapsUrl(latitude: Double, longitude: Double): String {
    return "https://maps.google.com/?q=$latitude,$longitude"
}

package com.resqlink.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isCellularNetworkAvailable(): Boolean {
        return try {
            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            val simState = telephonyManager.simState
            if (simState != TelephonyManager.SIM_STATE_READY) return false

            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            val allNetworks = connectivityManager.allNetworks
            for (network in allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}

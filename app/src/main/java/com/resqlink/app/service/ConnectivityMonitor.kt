package com.resqlink.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.TelephonyManager
import com.resqlink.app.data.model.ConnectionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)
    private val telephonyManager =
        context.getSystemService(TelephonyManager::class.java)

    val isOnline: Boolean
        get() {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    val isCellularAvailable: Boolean
        get() = try {
            if (telephonyManager.simState != TelephonyManager.SIM_STATE_READY) false
            else {
                val allNetworks = connectivityManager.allNetworks
                allNetworks.any { network ->
                    connectivityManager.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                }
            }
        } catch (e: Exception) {
            false
        }

    fun currentStatus(): ConnectionStatus = try {
        when {
            isOnline -> ConnectionStatus.ONLINE
            isCellularAvailable -> ConnectionStatus.CELLULAR_ONLY
            else -> ConnectionStatus.OFFLINE_MESH
        }
    } catch (e: Exception) {
        Timber.w(e, "Error checking connectivity status")
        ConnectionStatus.OFFLINE_MESH
    }

    fun observeConnectionStatus(): Flow<ConnectionStatus> = callbackFlow {
        val emitStatus = {
            try {
                trySend(currentStatus())
            } catch (e: Exception) {
                Timber.w(e, "Error emitting connectivity status")
                trySend(ConnectionStatus.OFFLINE_MESH)
            }
        }

        // 1. Network callbacks for internet changes
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("Network available")
                emitStatus()
            }

            override fun onLost(network: Network) {
                Timber.d("Network lost")
                emitStatus()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                emitStatus()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // 2. Network callback for cellular data connection changes
        val cellularCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emitStatus()
            }

            override fun onLost(network: Network) {
                emitStatus()
            }
        }

        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        // 3. BroadcastReceiver for SIM state changes (enable/disable SIM)
        val simStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Timber.d("SIM state changed")
                emitStatus()
            }
        }

        val simFilter = IntentFilter().apply {
            @Suppress("DEPRECATION")
            addAction("android.intent.action.SIM_STATE_CHANGED")
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        }

        connectivityManager.registerNetworkCallback(request, callback)
        connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
        context.registerReceiver(simStateReceiver, simFilter)
        emitStatus()

        // 4. Periodic poll as fallback for edge cases (every 5 seconds)
        val pollJob = launch {
            while (true) {
                delay(5_000)
                emitStatus()
            }
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
            connectivityManager.unregisterNetworkCallback(cellularCallback)
            context.unregisterReceiver(simStateReceiver)
            pollJob.cancel()
        }
    }.distinctUntilChanged()
}

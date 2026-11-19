package com.example.util

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live network & GPS provider connectivity status for GET TAXI connected-first operation.
 * Uses Android's standard ConnectivityManager and LocationManager APIs with graceful fallback.
 */
data class ConnectivityStatus(
    val isNetworkConnected: Boolean = false,
    val isGpsProviderEnabled: Boolean = false,
    val transportLabel: String = "Checking"
)

class ConnectivityMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _status = MutableStateFlow(readCurrentStatus())
    val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        refresh()
        val cm = connectivityManager ?: return
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refresh()
            }

            override fun onLost(network: Network) {
                refresh()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                refresh()
            }
        }
        networkCallback = callback
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    fun stop() {
        val cm = connectivityManager ?: return
        val cb = networkCallback ?: return
        try {
            cm.unregisterNetworkCallback(cb)
        } catch (_: Exception) {
        } finally {
            networkCallback = null
        }
    }

    fun refresh() {
        _status.value = readCurrentStatus()
    }

    private fun readCurrentStatus(): ConnectivityStatus {
        var connected = false
        var transport = "Local Storage"
        try {
            val cm = connectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
                if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    connected = true
                    transport = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        else -> "Online"
                    }
                }
            }
        } catch (_: Exception) {
            connected = false
            transport = "Local Storage"
        }

        val gpsEnabled = try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        } catch (_: Exception) {
            false
        }

        return ConnectivityStatus(
            isNetworkConnected = connected,
            isGpsProviderEnabled = gpsEnabled,
            transportLabel = transport
        )
    }
}

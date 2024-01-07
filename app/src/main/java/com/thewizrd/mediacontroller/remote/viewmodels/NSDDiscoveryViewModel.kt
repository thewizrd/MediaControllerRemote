package com.thewizrd.mediacontroller.remote.viewmodels

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.Executors

class NSDDiscoveryViewModel(app: Application) : AndroidViewModel(app), NsdManager.DiscoveryListener,
    NsdManager.ResolveListener {
    companion object {
        const val TAG = "MDnsDiscovery"
        const val SERVICE_TYPE = "_itunes-smtc._tcp"
        const val SERVICE_NAME = "am-remote"
    }

    private val nsdManager: NsdManager

    private val _remoteServiceState =
        MutableStateFlow(ServiceState(discoveryState = DiscoveryState.UNKNOWN))
    private var discoveryTimer: Job? = null

    val remoteServiceState = _remoteServiceState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        _remoteServiceState.value
    )

    init {
        nsdManager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    fun initializeDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    this@NSDDiscoveryViewModel
                )

                discoveryTimer?.cancel()

                _remoteServiceState.update {
                    it.copy(
                        discoveryState = DiscoveryState.SEARCHING,
                        serviceInfo = null
                    )
                }

                discoveryTimer = launch {
                    supervisorScope {
                        delay(5000) // 5s

                        if (isActive) {
                            stopDiscovery()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopDiscovery() {
        resetServiceState()
        discoveryTimer?.cancel()

        runCatching {
            nsdManager.stopServiceDiscovery(this@NSDDiscoveryViewModel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
            }
            runCatching {
                nsdManager.stopServiceResolution(this)
            }
        }
    }

    private fun resetServiceState() {
        _remoteServiceState.update {
            it.copy(
                discoveryState = DiscoveryState.NOT_FOUND,
                serviceInfo = null
            )
        }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }

    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        runCatching {
            nsdManager.stopServiceDiscovery(this)
        }
    }

    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code:$errorCode")
        runCatching {
            nsdManager.stopServiceDiscovery(this)
        }
    }

    override fun onDiscoveryStarted(serviceType: String?) {
        Log.d(TAG, "Service discovery started")
    }

    override fun onDiscoveryStopped(serviceType: String?) {
        Log.i(TAG, "Discovery stopped: $serviceType")
    }

    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
        // A service was found! Do something with it.
        Log.d(TAG, "Service discovery success - $serviceInfo")

        if (serviceInfo?.serviceType?.trimEnd('.') == SERVICE_TYPE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                nsdManager.registerServiceInfoCallback(
                    serviceInfo,
                    Executors.newSingleThreadExecutor(),
                    serviceInfoCallback
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                nsdManager.resolveService(serviceInfo, Executors.newSingleThreadExecutor(), this)
            } else {
                nsdManager.resolveService(serviceInfo, this)
            }
        }
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
        if (serviceInfo?.serviceType?.trimEnd('.') == SERVICE_TYPE) {
            // Service removed
            discoveryTimer?.cancel()
            resetServiceState()
        }
    }

    private val serviceInfoCallback =
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        object : NsdManager.ServiceInfoCallback {

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "Service info callback registration failed: Error code: $errorCode")
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                onServiceResolved(serviceInfo)
            }

            override fun onServiceLost() {
                // Service removed
                discoveryTimer?.cancel()
                resetServiceState()
            }

            override fun onServiceInfoCallbackUnregistered() {
                resetServiceState()
            }
        }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        if (serviceInfo?.serviceType?.trimEnd('.') == SERVICE_TYPE) {
            // Service found; stop timer
            discoveryTimer?.cancel()

            val hostName = serviceInfo.attributes["hostname"]?.decodeToString()
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceInfo.hostAddresses.first().hostName
                } else {
                    serviceInfo.host.hostName
                }
            val port =
                serviceInfo.attributes["port"]?.decodeToString() ?: serviceInfo.port.toString()

            _remoteServiceState.update {
                it.copy(
                    discoveryState = DiscoveryState.DISCOVERED,
                    serviceInfo = "http://${hostName}:${port}"
                )
            }
        }
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Log.e(TAG, "Resolve failed: $errorCode")
        resetServiceState()
    }
}
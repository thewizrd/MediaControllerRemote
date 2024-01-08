package com.thewizrd.mediacontroller.remote.viewmodels

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class NSDDiscoveryViewModel(app: Application) : BaseDiscoveryViewModel(app),
    NsdManager.DiscoveryListener,
    NsdManager.ResolveListener {
    companion object {
        const val TAG = "NSDDiscovery"
    }

    private val nsdManager: NsdManager

    init {
        nsdManager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    override fun initializeDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    nsdManager.stopServiceDiscovery(this@NSDDiscoveryViewModel)
                }

                nsdManager.discoverServices(
                    SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    this@NSDDiscoveryViewModel
                )

                stopDiscoveryTimer()

                _remoteServiceState.update {
                    it.copy(
                        discoveryState = DiscoveryState.SEARCHING,
                        serviceInfo = null
                    )
                }

                startDiscoveryTimer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun stopDiscovery() {
        resetServiceState()
        stopDiscoveryTimer()

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
                runCatching {
                    nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
                }

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
            stopDiscoveryTimer()
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
                stopDiscoveryTimer()
                resetServiceState()
            }

            override fun onServiceInfoCallbackUnregistered() {
                resetServiceState()
            }
        }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        if (serviceInfo?.serviceType?.trimEnd('.') == SERVICE_TYPE) {
            // Service found; stop timer
            stopDiscoveryTimer()

            val hostName = serviceInfo.attributes["hostname"]?.decodeToString()
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceInfo.hostAddresses.first().hostName
                } else {
                    serviceInfo.host.hostName
                }
            val port =
                serviceInfo.attributes["port"]?.decodeToString() ?: serviceInfo.port.toString()

            val serviceAddress = "http://${hostName}:${port}"

            // Store server address
            saveLastServiceAddress(serviceAddress)

            _remoteServiceState.update {
                it.copy(
                    discoveryState = DiscoveryState.DISCOVERED,
                    serviceInfo = serviceAddress
                )
            }
        }
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Log.e(TAG, "Resolve failed: $errorCode")
        resetServiceState()
    }
}
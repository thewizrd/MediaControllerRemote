package com.thewizrd.mediacontroller.remote.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.druk.rx2dnssd.BonjourService
import com.github.druk.rx2dnssd.Rx2Dnssd
import com.github.druk.rx2dnssd.Rx2DnssdEmbedded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow

class MDnsDiscoveryViewModel(app: Application) : BaseDiscoveryViewModel(app) {
    companion object {
        const val TAG = "MDnsDiscovery"
    }

    private val dnssd: Rx2Dnssd
    private var browserJob: Job? = null

    init {
        dnssd = Rx2DnssdEmbedded(app.applicationContext)
    }

    override fun initializeDiscovery() {
        stopDiscoveryTimer()
        browserJob?.cancel()

        browserJob = dnssd.browse(SERVICE_TYPE, "local.")
            .compose(dnssd.resolve())
            .compose(dnssd.queryIPRecords())
            .asFlow()
            .cancellable()
            .onStart {
                // Update to searching state
                _remoteServiceState.update {
                    it.copy(
                        discoveryState = DiscoveryState.SEARCHING,
                        serviceInfo = null
                    )
                }
            }
            .onCompletion {
                browserJob = null
            }
            .catch {
                Log.e(TAG, "Error", it)
            }
            .onEach { service ->
                if (service.isLost) {
                    serviceLost()
                } else {
                    serviceResolved(service)
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)

        startDiscoveryTimer()
    }

    override fun stopDiscovery() {
        stopDiscoveryTimer()
        browserJob?.cancel()
        browserJob = null

        resetServiceState()
    }

    private fun serviceResolved(service: BonjourService) {
        // Service found; stop timer
        stopDiscoveryTimer()

        val serviceAddress = "http://${service.inetAddresses.first().hostAddress}:${service.port}"

        viewModelScope.launch {
            // Verify if service available
            if (checkServerAvailability(serviceAddress)) {
                // Store server address
                saveLastServiceAddress(serviceAddress)

                _remoteServiceState.update {
                    it.copy(
                        discoveryState = DiscoveryState.DISCOVERED,
                        serviceInfo = serviceAddress
                    )
                }
            } else {
                serviceLost()
            }
        }
    }

    private fun serviceLost() {
        // Service removed
        stopDiscoveryTimer()
        resetServiceState()
    }
}
package com.thewizrd.mediacontroller.remote.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.druk.rx2dnssd.BonjourService
import com.github.druk.rx2dnssd.Rx2Dnssd
import com.github.druk.rx2dnssd.Rx2DnssdEmbedded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.supervisorScope

class MDnsDiscoveryViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val TAG = "MDnsDiscovery"
        const val SERVICE_TYPE = "_itunes-smtc._tcp"
        const val SERVICE_NAME = "am-remote"
    }

    private val dnssd: Rx2Dnssd
    private var browserJob: Job? = null
    private var discoveryTimer: Job? = null

    private val _remoteServiceState =
        MutableStateFlow(ServiceState(discoveryState = DiscoveryState.UNKNOWN))

    val remoteServiceState = _remoteServiceState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        _remoteServiceState.value
    )

    init {
        dnssd = Rx2DnssdEmbedded(app.applicationContext)
    }

    fun initializeDiscovery() {
        discoveryTimer?.cancel()
        browserJob?.cancel()

        browserJob = dnssd.browse(SERVICE_TYPE, "local.")
            .compose(dnssd.resolve())
            .asFlow()
            .flowOn(Dispatchers.IO)
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
            .launchIn(viewModelScope)

        dnssd.queryIPRecords(null)

        discoveryTimer = viewModelScope.launch {
            supervisorScope {
                delay(10000) // 10s

                if (isActive) {
                    stopDiscovery()
                }
            }
        }
    }

    fun stopDiscovery() {
        discoveryTimer?.cancel()
        browserJob?.cancel()

        discoveryTimer = null
        browserJob = null

        resetServiceState()
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

    private fun serviceResolved(service: BonjourService) {
        // Service found; stop timer
        discoveryTimer?.cancel()

        _remoteServiceState.update {
            it.copy(
                discoveryState = DiscoveryState.DISCOVERED,
                serviceInfo = "http://${service.txtRecords["hostname"]}:${service.txtRecords["port"]}"
            )
        }
    }

    private fun serviceLost() {
        // Service removed
        discoveryTimer?.cancel()
        resetServiceState()
    }
}
package com.thewizrd.mediacontroller.remote.viewmodels

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thewizrd.mediacontroller.remote.model.http.createRetrofitBuilder
import com.thewizrd.mediacontroller.remote.preferences.PREFKEY_LASTSERVICEADDRESS
import com.thewizrd.mediacontroller.remote.preferences.dataStore
import com.thewizrd.mediacontroller.remote.services.AMRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.await
import retrofit2.create
import java.util.concurrent.TimeUnit

abstract class BaseDiscoveryViewModel(private val app: Application) : AndroidViewModel(app) {
    companion object {
        const val SERVICE_TYPE = "_itunes-smtc._tcp"
        const val SERVICE_NAME = "am-remote"
    }

    private var discoveryTimer: Job? = null
    protected open var DISCOVERY_TIMEOUT: Long = 30000

    protected val _remoteServiceState =
        MutableStateFlow(ServiceState(discoveryState = DiscoveryState.SEARCHING))

    val remoteServiceState = _remoteServiceState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        _remoteServiceState.value
    )

    private val lastServiceAddressFlow = app.applicationContext.dataStore.data.map {
        it[PREFKEY_LASTSERVICEADDRESS]
    }

    abstract fun initializeDiscovery()
    abstract fun stopDiscovery()

    fun init() {
        viewModelScope.launch {
            // Update to searching state
            _remoteServiceState.update {
                it.copy(
                    discoveryState = DiscoveryState.SEARCHING,
                    serviceInfo = null
                )
            }

            // Check if last server is available
            val lastServerAddress = lastServiceAddressFlow.firstOrNull()
            val isAvailable =
                !lastServerAddress.isNullOrBlank() && checkServerAvailability(lastServerAddress)

            if (isAvailable) {
                _remoteServiceState.update {
                    it.copy(
                        discoveryState = DiscoveryState.DISCOVERED,
                        serviceInfo = lastServerAddress
                    )
                }
            } else {
                // Failed to connect; update state
                resetServiceState()
                // Fallback to discovery
                initializeDiscovery()
            }
        }
    }

    open suspend fun checkServerAvailability(serviceAddress: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val amRemoteService = createRetrofitBuilder()
                    .baseUrl(serviceAddress)
                    .client(
                        // fail fast
                        OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .writeTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .callTimeout(5, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(false)
                            .build()
                    )
                    .build()
                    .create<AMRemoteService>()

                amRemoteService.ping().await()

                // If we get here, we're good
                true
            }.getOrElse {
                Log.e("BaseDiscovery", "Error", it)
                false
            }
        }
    }

    protected fun startDiscoveryTimer() {
        discoveryTimer?.cancel()
        discoveryTimer = viewModelScope.launch {
            supervisorScope {
                delay(DISCOVERY_TIMEOUT)

                if (isActive) {
                    stopDiscovery()
                }
            }
        }
    }

    protected fun stopDiscoveryTimer() {
        discoveryTimer?.cancel()
        discoveryTimer = null
    }

    protected fun resetServiceState() {
        _remoteServiceState.update {
            it.copy(
                discoveryState = DiscoveryState.NOT_FOUND,
                serviceInfo = null
            )
        }
    }

    protected fun saveLastServiceAddress(serviceAddress: String) {
        viewModelScope.launch {
            app.dataStore.edit { prefs ->
                prefs[PREFKEY_LASTSERVICEADDRESS] = serviceAddress
            }
        }
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
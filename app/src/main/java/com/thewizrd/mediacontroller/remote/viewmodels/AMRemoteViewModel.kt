package com.thewizrd.mediacontroller.remote.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thewizrd.mediacontroller.remote.model.AMRemoteCommand
import com.thewizrd.mediacontroller.remote.model.getKey
import com.thewizrd.mediacontroller.remote.services.AMRemoteService
import com.thewizrd.mediacontroller.remote.services.createAMRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import retrofit2.await
import kotlin.coroutines.coroutineContext

class AMRemoteViewModel(serviceBaseUrl: String) : ViewModel() {
    private val amRemoteService: AMRemoteService = createAMRemoteService(serviceBaseUrl)

    private val _playerState = MutableStateFlow(AMPlayerState())
    private val _connectionErrors =
        MutableSharedFlow<Throwable>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var pollerJob: Job? = null

    val playerState = _playerState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        _playerState.value
    )

    private val playerStateFlow = flow {
        while (coroutineContext.isActive) {
            runCatching {
                val state = amRemoteService.getPlayerState(false).await()
                emit(state.toAMPlayerState())
            }.onFailure {
                Log.e("AMRemote", "error getting player state", it)
                _connectionErrors.tryEmit(it)
            }

            delay(1000)
        }
    }.cancellable().flowOn(Dispatchers.IO)

    val connectionErrors = _connectionErrors.shareIn(
        viewModelScope,
        SharingStarted.Lazily
    )

    fun startPolling() {
        pollerJob?.cancel()
        pollerJob = viewModelScope.launch(Dispatchers.Default) {
            supervisorScope {
                playerStateFlow.collectLatest { newState ->
                    val oldState = _playerState.value

                    _playerState.update {
                        // Copy new state with existing artwork
                        newState.copy(artwork = it.artwork)
                    }

                    // Track change
                    if (oldState.trackData?.getKey() != newState.trackData?.getKey() && newState.trackData != null) {
                        updateArtwork()
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollerJob?.cancel()
    }

    private fun updatePlayerState(includeArtwork: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val state = amRemoteService.getPlayerState(includeArtwork).await()

                _playerState.update {
                    if (includeArtwork) {
                        withContext(Dispatchers.IO) {
                            state.toAMPlayerState()
                        }
                    } else {
                        state.toAMPlayerState().copy(
                            artwork = it.artwork
                        )
                    }
                }
            }.onFailure {
                Log.e("AMRemote", "error getting player state", it)
                _connectionErrors.tryEmit(it)
            }
        }
    }

    private fun updateArtwork() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val artworkData = amRemoteService.getArtwork().await()

                _playerState.update {
                    val artworkBmp = artworkData.toBitmap()
                    it.copy(
                        artwork = artworkBmp
                    )
                }
            }.onFailure {
                Log.e("AMRemote", "error getting player state", it)
                _connectionErrors.tryEmit(it)
            }
        }
    }

    fun sendCommand(@AMRemoteCommand command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                amRemoteService.sendPlayerCommand(command).await()
            }.onFailure {
                Log.e("AMRemote", "error sending command", it)
            }
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
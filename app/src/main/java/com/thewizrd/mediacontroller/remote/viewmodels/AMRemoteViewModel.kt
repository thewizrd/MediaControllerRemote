package com.thewizrd.mediacontroller.remote.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.thewizrd.mediacontroller.remote.model.AMEventType
import com.thewizrd.mediacontroller.remote.model.AMRemoteCommand
import com.thewizrd.mediacontroller.remote.model.EventMessage
import com.thewizrd.mediacontroller.remote.model.getKey
import com.thewizrd.mediacontroller.remote.model.http.createRetrofitBuilder
import com.thewizrd.mediacontroller.remote.services.AMRemoteService
import com.thewizrd.mediacontroller.remote.services.createAMRemoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
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
import okhttp3.OkHttpClient
import retrofit2.await
import retrofit2.create
import java.util.concurrent.TimeUnit

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

    private val amEventFlow = flow {
        supervisorScope {
            runCatching {
                val amSubService = createRetrofitBuilder()
                    .baseUrl(serviceBaseUrl)
                    .client(
                        OkHttpClient.Builder()
                            .readTimeout(0, TimeUnit.SECONDS) // keep-alive
                            .cache(null)
                            .build()
                    )
                    .build()
                    .create<AMRemoteService>()

                val response = amSubService.subscribeToEvents().await()

                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(EventMessage::class.java)

                response.byteStream().bufferedReader().use { reader ->
                    while (coroutineContext.isActive) {
                        val line = reader.readLine()

                        when {
                            line.startsWith("data: ") -> {
                                runCatching {
                                    val json = line.substringAfter("data: ")
                                    val event = jsonAdapter.fromJson(json)!!
                                    emit(event)
                                }.onFailure {
                                    Log.e("AMRemote", "error reading player state", it)
                                }
                            }

                            line.isEmpty() -> {
                                // empty line; data terminator
                            }
                        }
                    }
                }
            }.onFailure {
                Log.e("AMRemote", "error getting player state", it)
                _connectionErrors.tryEmit(it)
            }
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
                amEventFlow.collectLatest { event ->
                    Log.d("AMRemote", "event received - ${event.eventType}")
                    Log.d(
                        "AMRemote",
                        "artwork - ${event.payload.artwork != null}; size = ${event.payload.artwork?.length ?: 0}"
                    )

                    when (event.eventType) {
                        AMEventType.TRACK_CHANGE,
                        AMEventType.PLAYER_STATE_CHANGED -> {
                            val oldState = _playerState.value
                            val newState = event.payload.toAMPlayerState()

                            _playerState.update {
                                if (event.eventType == AMEventType.PLAYER_STATE_CHANGED) {
                                    newState.copy(
                                        artwork = newState.artwork?.takeIf { it.artworkBytes != null }
                                            ?: oldState.artwork,
                                        trackData = oldState.trackData?.let { t ->
                                            t.copy(
                                                progress = newState.trackData?.progress
                                                    ?: t.progress
                                            )
                                        } ?: newState.trackData
                                    )
                                } else {
                                    // Copy new state with existing artwork
                                    if (newState.artwork?.artworkBytes != null) {
                                        newState
                                    } else {
                                        newState.copy(artwork = it.artwork)
                                    }
                                }
                            }

                            if (event.eventType == AMEventType.TRACK_CHANGE) {
                                // Track change
                                Log.d(
                                    "AMRemote",
                                    "track change - old = ${oldState.trackData?.getKey()}; new = ${newState.trackData?.getKey()}"
                                )
                                if (oldState.trackData?.getKey() != newState.trackData?.getKey() && newState.trackData?.name != null && newState.artwork?.artworkBytes == null) {
                                    Log.d("AMRemote", "track change; fetching artwork")
                                    _playerState.update { it.copy(artwork = null) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollerJob?.cancel()
    }

    suspend fun updatePlayerState(includeArtwork: Boolean = false) = withContext(Dispatchers.IO) {
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

    private suspend fun updateArtwork() = withContext(Dispatchers.IO) {
        runCatching {
            val artworkData = amRemoteService.getArtwork().await()

            _playerState.update {
                it.copy(artwork = artworkData.toArtwork())
            }
        }.onFailure {
            Log.e("AMRemote", "error getting player state", it)
            _connectionErrors.tryEmit(it)
        }
    }

    suspend fun sendCommand(@AMRemoteCommand command: String) = withContext(Dispatchers.IO) {
        runCatching {
            amRemoteService.sendPlayerCommand(command).await()
        }.onFailure {
            Log.e("AMRemote", "error sending command", it)
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
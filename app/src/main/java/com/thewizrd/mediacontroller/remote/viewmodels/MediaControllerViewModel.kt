package com.thewizrd.mediacontroller.remote.viewmodels

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.thewizrd.mediacontroller.remote.model.AMRemoteCommand
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons
import com.thewizrd.mediacontroller.remote.model.MediaPlaybackAutoRepeatMode
import com.thewizrd.mediacontroller.remote.model.PlayPauseStopButtonState
import com.thewizrd.mediacontroller.remote.model.TrackData
import com.thewizrd.mediacontroller.remote.services.background.AMControllerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MediaControllerViewModel(private val ctx: Application) : AndroidViewModel(ctx) {
    private var controller: MediaController? = null

    private val _playerState = MutableStateFlow(AMPlayerState())
    private val _controllerState = MutableStateFlow(ControllerState.LOADING)

    val playerState = _playerState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        _playerState.value
    )

    val controllerState = _controllerState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        _controllerState.value
    )

    init {
        initController()
    }

    fun initController() {
        viewModelScope.launch {
            if (controller == null) {
                runCatching {
                    MediaController.Builder(
                        ctx,
                        SessionToken(ctx, ComponentName(ctx, AMControllerService::class.java))
                    )
                        .setListener(object : MediaController.Listener {
                            override fun onDisconnected(controller: MediaController) {
                                super.onDisconnected(controller)
                                _controllerState.update { ControllerState.DISCONNECTED }
                            }

                        })
                        .buildAsync()
                        .await()
                        .also {
                            it.addListener(mediaControllerListener)
                            _controllerState.update { ControllerState.CONNECTED }
                            controller = it

                            updatePlayerState()
                        }
                }
            }
        }
    }

    fun releaseController() {
        controller?.release()
    }

    private fun updatePlayerState() {
        if (controller == null || controller?.mediaItemCount == 0) {
            _playerState.update {
                it.copy(trackData = null, artwork = null)
            }
        } else {
            val mediaMetadata = controller?.mediaMetadata

            if (mediaMetadata == null || mediaMetadata == MediaMetadata.EMPTY) {
                _playerState.update {
                    it.copy(trackData = null, artwork = null)
                }
            } else {
                _playerState.update {
                    it.copy(
                        trackData = TrackData(
                            duration = controller?.duration?.div(1000)?.toInt() ?: 0,
                            name = mediaMetadata.title?.toString(),
                            artist = mediaMetadata.artist?.toString(),
                            album = mediaMetadata.albumTitle?.toString(),
                            progress = controller?.contentPosition?.div(1000)?.toInt() ?: 0,
                        ),
                        artwork = Artwork(mediaMetadata.artworkData),
                        isPlaying = controller?.isPlaying == true,
                        playPauseStopButtonState = when (controller?.isPlaying) {
                            true -> PlayPauseStopButtonState.PAUSE
                            else -> PlayPauseStopButtonState.PLAY
                        },
                        skipBackEnabled = controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) == true,
                        skipForwardEnabled = controller?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) == true,
                        shuffleEnabled = controller?.shuffleModeEnabled == true,
                        repeatMode = when (controller?.repeatMode) {
                            Player.REPEAT_MODE_ONE -> MediaPlaybackAutoRepeatMode.TRACK
                            Player.REPEAT_MODE_ALL -> MediaPlaybackAutoRepeatMode.LIST
                            else -> MediaPlaybackAutoRepeatMode.NONE
                        },
                        isRadio = controller?.isCurrentMediaItemLive == true
                    )
                }
            }
        }
    }

    fun sendPlayerCommand(@AMRemoteCommand command: String) {
        when (command) {
            AppleMusicControlButtons.SKIPBACK -> {
                controller?.seekToPreviousMediaItem()
            }

            AppleMusicControlButtons.SKIPFORWARD -> {
                controller?.seekToNextMediaItem()
            }

            AppleMusicControlButtons.PLAYPAUSESTOP -> {
                if (controller?.isPlaying == true) controller?.pause() else controller?.play()
            }

            AppleMusicControlButtons.SHUFFLE -> {
                controller?.shuffleModeEnabled = controller?.shuffleModeEnabled?.not() ?: false
            }

            AppleMusicControlButtons.REPEAT -> {
                controller?.repeatMode =
                    controller?.repeatMode?.minus(1)?.mod(3) ?: Player.REPEAT_MODE_OFF
            }
        }
    }

    fun updatePlayerPosition() {
        controller?.takeIf { it.mediaItemCount != 0 && it.isPlaying }?.let { ctrlr ->
            _playerState.update {
                it.copy(
                    trackData = it.trackData?.copy(
                        progress = TimeUnit.MILLISECONDS.toSeconds(ctrlr.contentPosition).toInt()
                    )
                )
            }
        }
    }

    private val mediaControllerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_METADATA,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_PLAYLIST_METADATA_CHANGED
                )
            ) {
                updatePlayerState()
            } else {
                if (events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED
                    )
                ) {
                    _playerState.update {
                        it.copy(
                            isPlaying = player.isPlaying,
                            playPauseStopButtonState = when (player.isPlaying) {
                                true -> PlayPauseStopButtonState.PAUSE
                                else -> PlayPauseStopButtonState.PLAY
                            }
                        )
                    }
                }

                if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                    _playerState.update {
                        it.copy(shuffleEnabled = player.shuffleModeEnabled)
                    }
                }

                if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                    _playerState.update {
                        it.copy(
                            repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_ONE -> MediaPlaybackAutoRepeatMode.TRACK
                                Player.REPEAT_MODE_ALL -> MediaPlaybackAutoRepeatMode.LIST
                                else -> MediaPlaybackAutoRepeatMode.NONE
                            }
                        )
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
        }
    }

    enum class ControllerState {
        UNKNOWN, LOADING, CONNECTED, DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        controller?.release()
        controller = null
    }
}
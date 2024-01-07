package com.thewizrd.mediacontroller.remote.viewmodels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.thewizrd.mediacontroller.remote.model.ArtworkResponse
import com.thewizrd.mediacontroller.remote.model.MediaPlaybackAutoRepeatMode
import com.thewizrd.mediacontroller.remote.model.PlayPauseStopButtonState
import com.thewizrd.mediacontroller.remote.model.PlayerStateResponse
import com.thewizrd.mediacontroller.remote.model.TrackData

data class AMPlayerState(
    val isPlaying: Boolean = false,
    val playPauseStopButtonState: String = PlayPauseStopButtonState.UNKNOWN,
    val repeatMode: String = MediaPlaybackAutoRepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val skipBackEnabled: Boolean = false,
    val artwork: Bitmap? = null,
    val trackData: TrackData? = null,
    val skipForwardEnabled: Boolean = false
)

fun PlayerStateResponse.toAMPlayerState(): AMPlayerState {
    return AMPlayerState(
        isPlaying = isPlaying,
        playPauseStopButtonState = playPauseStopButtonState,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        skipBackEnabled = skipBackEnabled,
        trackData = trackData,
        skipForwardEnabled = skipForwardEnabled,
        artwork = artwork?.let {
            val byteArr = Base64.decode(it, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(byteArr, 0, byteArr.size)
        },
    )
}

fun ArtworkResponse.toBitmap(): Bitmap? {
    return this.artwork?.let {
        val byteArr = Base64.decode(it, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(byteArr, 0, byteArr.size)
    }
}
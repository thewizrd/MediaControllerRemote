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
    val artwork: Artwork? = null,
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
        artwork = Artwork(artwork?.let { Base64.decode(it, Base64.DEFAULT) }),
    )
}

data class Artwork(
    val artworkBytes: ByteArray? = null
) {
    private var _bmp: Bitmap? = null

    val bitmap: Bitmap?
        get() {
            return _bmp ?: toBitmap().also { _bmp = it }
        }

    private fun toBitmap(): Bitmap? {
        return artworkBytes?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Artwork

        if (artworkBytes != null) {
            if (other.artworkBytes == null) return false
            if (!artworkBytes.contentEquals(other.artworkBytes)) return false
        } else if (other.artworkBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        return artworkBytes?.contentHashCode() ?: 0
    }
}

fun ArtworkResponse.toArtwork(): Artwork? {
    return this.artwork?.let {
        Artwork(Base64.decode(it, Base64.DEFAULT))
    }
}

fun ArtworkResponse.toBitmap(): Bitmap? {
    return this.artwork?.let {
        val byteArr = Base64.decode(it, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(byteArr, 0, byteArr.size)
    }
}
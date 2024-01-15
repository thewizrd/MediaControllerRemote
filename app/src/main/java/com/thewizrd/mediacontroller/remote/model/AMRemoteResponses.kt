package com.thewizrd.mediacontroller.remote.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlayerStateResponse(
    val isPlaying: Boolean,
    val playPauseStopButtonState: String,
    val repeatMode: String,
    val shuffleEnabled: Boolean,
    val skipBackEnabled: Boolean,
    val artwork: String? = null,
    val trackData: TrackData? = null,
    val skipForwardEnabled: Boolean,
    val isRadio: Boolean
)

@JsonClass(generateAdapter = true)
data class TrackData(
    val duration: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val name: String? = null,
    val progress: Int = 0
)

fun TrackData?.getKey(): String? {
    return this?.takeIf {
        it.name != null || it.artist != null || it.album != null
    }?.let {
        "${it.name}|${it.artist}|${it.album}"
    }
}

@JsonClass(generateAdapter = true)
data class ArtworkResponse(
    val artwork: String? = null
)

@JsonClass(generateAdapter = true)
data class EventMessage(
    val eventType: String,
    val payload: PlayerStateResponse
)
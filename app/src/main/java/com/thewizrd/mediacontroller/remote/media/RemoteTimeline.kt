package com.thewizrd.mediacontroller.remote.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class RemoteTimeline : Timeline {
    companion object {
        val DEFAULT by lazy { RemoteTimeline() }
    }

    constructor() : super()
    constructor(isLive: Boolean) : this() {
        this.isLive = isLive
    }

    private var isLive: Boolean = false
        set(value) {
            field = value

            liveConfiguration = if (value) {
                MediaItem.LiveConfiguration.Builder().build()
            } else {
                null
            }
        }

    private var liveConfiguration: MediaItem.LiveConfiguration? = null

    var mediaMetadata: MediaMetadata = MediaMetadata.EMPTY

    override fun getWindowCount(): Int = 1

    override fun getWindow(
        windowIndex: Int,
        window: Window,
        defaultPositionProjectionUs: Long
    ): Window {
        window.set(
            0,
            MediaItem.Builder()
                .setMediaMetadata(mediaMetadata)
                .build(),
            null,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            false,
            isLive,
            liveConfiguration,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.INDEX_UNSET,
            C.INDEX_UNSET,
            C.TIME_UNSET
        )
        return window
    }

    override fun getPreviousWindowIndex(
        windowIndex: Int,
        repeatMode: Int,
        shuffleModeEnabled: Boolean
    ): Int {
        return getFirstWindowIndex(shuffleModeEnabled)
    }

    override fun getNextWindowIndex(
        windowIndex: Int,
        repeatMode: Int,
        shuffleModeEnabled: Boolean
    ): Int {
        return getLastWindowIndex(shuffleModeEnabled)
    }

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = 0

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = if (isLive) 0 else 2

    override fun getPeriodCount(): Int = 1

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        period.set(0, 0, 0, 0, 0)
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int = 0

    override fun getUidOfPeriod(periodIndex: Int): Any = 0
}
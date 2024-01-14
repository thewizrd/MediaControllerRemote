package com.thewizrd.mediacontroller.remote.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Timeline

class RemoteTimeline : Timeline() {
    companion object {
        val DEFAULT by lazy { RemoteTimeline() }
    }

    override fun getWindowCount(): Int = 3

    override fun getWindow(
        windowIndex: Int,
        window: Window,
        defaultPositionProjectionUs: Long
    ): Window {
        window.set(
            0,
            MediaItem.Builder()
                .setMediaMetadata(MediaMetadata.EMPTY)
                .build(),
            null,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            false,
            false,
            null,
            0,
            0,
            0,
            0,
            0
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

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = 2

    override fun getPeriodCount(): Int = 1

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
        period.set(0, 0, 0, 0, 0)
        return period
    }

    override fun getIndexOfPeriod(uid: Any): Int = 0

    override fun getUidOfPeriod(periodIndex: Int): Any = 0
}
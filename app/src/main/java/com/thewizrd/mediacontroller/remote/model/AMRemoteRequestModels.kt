package com.thewizrd.mediacontroller.remote.model

import androidx.annotation.StringDef
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons.PLAYPAUSESTOP
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons.REPEAT
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons.SHUFFLE
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons.SKIPBACK
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons.SKIPFORWARD

object AppleMusicControlButtons {
    const val SHUFFLE = "Shuffle"
    const val SKIPBACK = "SkipBack"
    const val PLAYPAUSESTOP = "PlayPauseStop"
    const val SKIPFORWARD = "SkipForward"
    const val REPEAT = "Repeat"
}

@StringDef(SHUFFLE, SKIPBACK, PLAYPAUSESTOP, SKIPFORWARD, REPEAT)
@Retention(AnnotationRetention.SOURCE)
annotation class AMRemoteCommand

object PlayPauseStopButtonState {
    const val UNKNOWN = "Unknown"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val STOP = "Stop"
}

object MediaPlaybackAutoRepeatMode {
    const val NONE = "None"
    const val TRACK = "Track"
    const val LIST = "List"
}
package com.thewizrd.mediacontroller.remote.services.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.thewizrd.mediacontroller.remote.MainActivity
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons
import com.thewizrd.mediacontroller.remote.model.MediaPlaybackAutoRepeatMode
import com.thewizrd.mediacontroller.remote.viewmodels.AMPlayerState
import com.thewizrd.mediacontroller.remote.viewmodels.AMRemoteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.media3.session.R as mediaSessionRes

class AMControllerService : CustomMediaControllerService() {
    companion object {
        private const val JOB_ID = 1000
        private const val NOT_CHANNEL_ID = "MediaController.amcontrollerservice"

        private const val ACTION_START_SERVICE = "MediaController.Remote.action.START_SERVICE"
        private const val ACTION_PLAY_PAUSE_STOP = "MediaController.Remote.action.PLAY_PAUSE_STOP"
        private const val ACTION_SKIP_FORWARD = "MediaController.Remote.action.SKIP_FORWARD"
        private const val ACTION_SKIP_BACK = "MediaController.Remote.action.SKIP_BACK"
        private const val ACTION_STOP_SERVICE = "MediaController.Remote.action.STOP_SERVICE"

        private const val EXTRA_SERVICE_URL = "MediaController.Remote.extra.SERVICE_URL"

        fun startService(context: Context, serviceUrl: String) {
            val intent = Intent(context.applicationContext, AMControllerService::class.java)
                .setAction(ACTION_START_SERVICE)
                .putExtra(EXTRA_SERVICE_URL, serviceUrl)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(
                Intent(context.applicationContext, AMControllerService::class.java)
                    .setAction(ACTION_STOP_SERVICE)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var mediaSession: MediaSession? = null
    private lateinit var baseServiceUrl: String
    private var playerState: AMPlayerState? = null

    private lateinit var amRemoteViewModel: Lazy<AMRemoteViewModel>

    private var isPlaying = false
    private var progressJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        amRemoteViewModel = viewModels<AMRemoteViewModel>(
            factoryProducer = {
                viewModelFactory {
                    initializer {
                        AMRemoteViewModel(baseServiceUrl)
                    }
                }
            }
        )

        initializeSessionAndPlayer()
        setListener(MediaSessionServiceListener())
    }

    @OptIn(UnstableApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                intent.getStringExtra(EXTRA_SERVICE_URL)?.let { url ->
                    baseServiceUrl = url

                    scope.launch {
                        val vm = amRemoteViewModel.value

                        vm.updatePlayerState(true)
                        vm.startPolling()

                        scope.launch {
                            vm.connectionErrors.collectLatest {
                                if (it is IOException) {
                                    stopSelf()
                                }
                            }
                        }

                        scope.launch {
                            vm.playerState.collect { state ->
                                playerState = state
                                isPlaying = state.isPlaying
                                (mediaSession?.player as? MediaPlayer)?.invalidatePlayerState()

                                resetPlayerPositionJob()
                            }
                        }
                    }
                } ?: stopSelf()
            }

            ACTION_PLAY_PAUSE_STOP -> {
                scope.launch {
                    if (amRemoteViewModel.isInitialized()) {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.PLAYPAUSESTOP)
                    }
                }
            }

            ACTION_SKIP_BACK -> {
                scope.launch {
                    if (amRemoteViewModel.isInitialized()) {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SKIPBACK)
                    }
                }
            }

            ACTION_SKIP_FORWARD -> {
                scope.launch {
                    if (amRemoteViewModel.isInitialized()) {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SKIPFORWARD)
                    }
                }
            }

            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        runCatching {
            if (amRemoteViewModel.isInitialized()) {
                amRemoteViewModel.value.stopPolling()
            }
        }
        progressJob?.cancel()
        scope.cancel()
        mediaSession?.release()
        mediaSession?.player?.release()
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun initializeSessionAndPlayer() {
        if (mediaSession == null) {
            val player = MediaPlayer()

            mediaSession =
                MediaSession.Builder(this, player)
                    .setSessionActivity(getMainActivityIntent())
                    .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let { player ->
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    @UnstableApi
    private inner class MediaPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State {
            return State.Builder()
                .setAvailableCommands(
                    Commands.Builder()
                        .apply {
                            if (playerState?.skipBackEnabled == true) {
                                add(Player.COMMAND_SEEK_TO_PREVIOUS)
                                add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            }
                        }
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_STOP)
                        .apply {
                            if (playerState?.skipForwardEnabled == true) {
                                add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                add(Player.COMMAND_SEEK_TO_NEXT)
                            }
                        }
                        .add(Player.COMMAND_SET_SHUFFLE_MODE)
                        .add(Player.COMMAND_SET_REPEAT_MODE)
                        .add(Player.COMMAND_GET_METADATA)
                        .add(Player.COMMAND_GET_TIMELINE)
                        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                        .build()
                )
                .setPlaybackState(STATE_READY)
                .setShuffleModeEnabled(playerState?.shuffleEnabled ?: false)
                .setRepeatMode(
                    when (playerState?.repeatMode) {
                        MediaPlaybackAutoRepeatMode.LIST -> REPEAT_MODE_ALL
                        MediaPlaybackAutoRepeatMode.TRACK -> REPEAT_MODE_ONE
                        else -> REPEAT_MODE_OFF
                    }
                )
                .setContentPositionMs {
                    playerState?.trackData?.progress?.times(1000)?.toLong() ?: 0
                }
                .setPlaylist(
                    listOf(
                        MediaItemData.Builder(0)
                            .setDurationUs(
                                playerState?.trackData?.duration?.toLong()
                                    ?.takeIf { it >= 0 }
                                    ?.let { TimeUnit.SECONDS.toMicros(it) }
                                    ?: C.TIME_UNSET
                            )
                            .setIsSeekable(false)
                            .setMediaMetadata(
                                playerState?.trackData?.let {
                                    MediaMetadata.Builder()
                                        .setTitle(it.name)
                                        .setArtist(it.artist)
                                        .setAlbumTitle(it.album)
                                        .setArtworkData(
                                            playerState?.artwork?.artworkBytes,
                                            MediaMetadata.PICTURE_TYPE_FRONT_COVER
                                        )
                                        .build()
                                } ?: MediaMetadata.EMPTY
                            )
                            .build(),
                        MediaItemData.Builder(1).build()
                    )
                )
                .setCurrentMediaItemIndex(C.INDEX_UNSET)
                .setPlayWhenReady(
                    this@AMControllerService.isPlaying,
                    PLAY_WHEN_READY_CHANGE_REASON_REMOTE
                )
                .setDeviceInfo(
                    DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                        .build()
                )
                .build()
        }

        fun invalidatePlayerState() {
            scope.launch(Dispatchers.Main) {
                invalidateState()
            }
        }

        override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
            return scope.async {
                amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SHUFFLE)
            }.asListenableFuture()
        }

        override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
            return scope.async {
                amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.REPEAT)
            }.asListenableFuture()
        }

        override fun handleSetPlaylistMetadata(playlistMetadata: MediaMetadata): ListenableFuture<*> {
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            return scope.async {
                val result =
                    amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.PLAYPAUSESTOP)

                if (result.isSuccess) {
                    this@AMControllerService.isPlaying = playWhenReady
                }

                result
            }.asListenableFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            return scope.async {
                amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.PLAYPAUSESTOP)
            }.asListenableFuture()
        }

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int
        ): ListenableFuture<*> {
            return scope.async {
                when (seekCommand) {
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT -> {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SKIPFORWARD)
                    }

                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS -> {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SKIPBACK)
                    }

                    else -> {}
                }
            }.asListenableFuture()
        }
    }

    private fun resetPlayerPositionJob() {
        progressJob?.cancel()
        if (playerState?.isPlaying == true) {
            progressJob = scope.launch {
                while (isActive) {
                    delay(1000)
                    playerState = playerState?.copy(
                        trackData = playerState?.trackData?.copy(
                            progress = playerState?.trackData?.progress?.plus(1) ?: 0
                        )
                    )
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private inner class MediaSessionServiceListener : Listener {
        /**
         * This method is only required to be implemented on Android 12 or above when an attempt is made
         * by a media controller to resume playback when the {@link MediaSessionService} is in the
         * background.
         */
        override fun onForegroundServiceStartNotAllowedException() {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Notification permission is required but not granted
                return
            }
            val notificationManagerCompat = NotificationManagerCompat.from(this@AMControllerService)
            ensureNotificationChannel(notificationManagerCompat)
            val builder =
                NotificationCompat.Builder(this@AMControllerService, NOT_CHANNEL_ID)
                    .setSmallIcon(mediaSessionRes.drawable.media3_notification_small_icon)
                    .setContentText("Playback cannot be resumed")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(getMainActivityIntent())
            notificationManagerCompat.notify(JOB_ID, builder.build())
        }
    }

    private fun getMainActivityIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this@AMControllerService, 0,
            Intent(this@AMControllerService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureNotificationChannel(notificationManagerCompat: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT < 26) return

        // Gets an instance of the NotificationManager service
        var mChannel = notificationManagerCompat.getNotificationChannel(NOT_CHANNEL_ID)
        val notChannelName =
            applicationContext.getString(mediaSessionRes.string.default_notification_channel_name)
        if (mChannel == null) {
            mChannel = NotificationChannel(
                NOT_CHANNEL_ID,
                notChannelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        }

        // Configure the notification channel.
        mChannel.name = notChannelName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!mChannel.hasUserSetImportance()) {
                mChannel.importance = NotificationManager.IMPORTANCE_DEFAULT
            }
        }
        mChannel.setShowBadge(false)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        notificationManagerCompat.createNotificationChannel(mChannel)
    }

    private fun getActionPendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            applicationContext,
            0,
            Intent(applicationContext, AMControllerService::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}
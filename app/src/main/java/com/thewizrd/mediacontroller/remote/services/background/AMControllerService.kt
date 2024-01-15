package com.thewizrd.mediacontroller.remote.services.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Player.Commands
import androidx.media3.common.Player.RepeatMode
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.thewizrd.mediacontroller.remote.MainActivity
import com.thewizrd.mediacontroller.remote.R
import com.thewizrd.mediacontroller.remote.media.BaseRemotePlayer
import com.thewizrd.mediacontroller.remote.media.RemoteTimeline
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons
import com.thewizrd.mediacontroller.remote.model.MediaPlaybackAutoRepeatMode
import com.thewizrd.mediacontroller.remote.model.getKey
import com.thewizrd.mediacontroller.remote.preferences.PREFKEY_LASTSERVICEADDRESS
import com.thewizrd.mediacontroller.remote.preferences.dataStore
import com.thewizrd.mediacontroller.remote.viewmodels.AMPlayerState
import com.thewizrd.mediacontroller.remote.viewmodels.AMRemoteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.media3.session.R as mediaSessionRes
import androidx.media3.ui.R as mediaUiRes

class AMControllerService : CustomMediaControllerService() {
    companion object {
        private const val JOB_ID = 1000
        private const val NOT_CHANNEL_ID = "MediaController.amcontrollerservice"

        private const val ACTION_START_SERVICE = "MediaController.Remote.action.START_SERVICE"
        private const val ACTION_PLAY_PAUSE_STOP = "MediaController.Remote.action.PLAY_PAUSE_STOP"
        private const val ACTION_SKIP_FORWARD = "MediaController.Remote.action.SKIP_FORWARD"
        private const val ACTION_SKIP_BACK = "MediaController.Remote.action.SKIP_BACK"
        private const val ACTION_STOP_SERVICE = "MediaController.Remote.action.STOP_SERVICE"
        private const val ACTION_STOP_SERVICE_IF_NEEDED =
            "MediaController.Remote.action.STOP_SERVICE_IF_NEEDED"
        private const val ACTION_TOGGLE_SHUFFLE = "MediaController.Remote.action.TOGGLE_SHUFFLE"
        private const val ACTION_TOGGLE_REPEAT = "MediaController.Remote.action.TOGGLE_REPEAT"

        private const val EXTRA_SERVICE_URL = "MediaController.Remote.extra.SERVICE_URL"

        private const val SESSION_CMD_TOGGLE_SHUFFLE_ON = "${ACTION_TOGGLE_SHUFFLE}-${false}"
        private const val SESSION_CMD_TOGGLE_SHUFFLE_OFF = "${ACTION_TOGGLE_SHUFFLE}-${true}"
        private const val SESSION_CMD_TOGGLE_REPEAT_ALL =
            "${ACTION_TOGGLE_REPEAT}-${Player.REPEAT_MODE_ALL}"
        private const val SESSION_CMD_TOGGLE_REPEAT_ONE =
            "${ACTION_TOGGLE_REPEAT}-${Player.REPEAT_MODE_ONE}"
        private const val SESSION_CMD_TOGGLE_REPEAT_OFF =
            "${ACTION_TOGGLE_REPEAT}-${Player.REPEAT_MODE_OFF}"

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

        fun stopServiceIfNeeded(context: Context) {
            context.startService(
                Intent(context.applicationContext, AMControllerService::class.java)
                    .setAction(ACTION_STOP_SERVICE_IF_NEEDED)
            )
        }

        fun requestAction(context: Context, action: String) {
            val intent = Intent(context.applicationContext, AMControllerService::class.java)
                .setAction(action)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationProvider: MediaNotification.Provider
    private lateinit var actionFactory: MediaNotification.ActionFactory
    private lateinit var mainHandler: Handler

    private var mediaSession: MediaSession? = null
    private lateinit var baseServiceUrl: String
    private var playerState: AMPlayerState? = null

    private lateinit var amRemoteViewModel: Lazy<AMRemoteViewModel>

    private var startedByUser = false
    private var isConnected = false
    private var progressJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        mainHandler = Handler(Looper.getMainLooper())
        notificationManager = NotificationManagerCompat.from(this@AMControllerService)
        notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOT_CHANNEL_ID)
            .setNotificationId(JOB_ID)
            .build()
            .apply {
                setSmallIcon(R.drawable.note_icon)
            }
            .also {
                setMediaNotificationProvider(it)
            }
        actionFactory = AMActionFactory()

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
        val result = super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startedByUser = true
                intent.getStringExtra(EXTRA_SERVICE_URL)?.let { url ->
                    baseServiceUrl = url
                    initializeAMRemote()
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

            ACTION_TOGGLE_SHUFFLE -> {
                scope.launch {
                    if (amRemoteViewModel.isInitialized()) {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SHUFFLE)
                    }
                }
            }

            ACTION_TOGGLE_REPEAT -> {
                scope.launch {
                    if (amRemoteViewModel.isInitialized()) {
                        amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.REPEAT)
                    }
                }
            }

            ACTION_STOP_SERVICE -> {
                stopService()
            }

            ACTION_STOP_SERVICE_IF_NEEDED -> {
                mediaSession?.let {
                    if (shouldStopForeground(it)) {
                        stopService()
                    }
                } ?: {
                    stopService()
                }
            }
        }

        return result
    }

    fun stopService() {
        startedByUser = false
        stopSelf()
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
        isConnected = false
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private fun initializeSessionAndPlayer() {
        if (mediaSession == null) {
            val player = MediaPlayer()

            mediaSession =
                MediaSession.Builder(this, player)
                    .setSessionActivity(getMainActivityIntent())
                    .setCallback(AMMediaSessionCallback())
                    .build()
        }
    }

    private fun initializeAMRemote() {
        if (!isConnected) {
            scope.launch {
                val vm = amRemoteViewModel.value

                vm.updatePlayerState(true)
                vm.startPolling()

                scope.launch {
                    vm.connectionErrors.collectLatest {
                        if (it is IOException) {
                            isConnected = false
                            stopSelf()
                        }
                    }
                }

                scope.launch {
                    vm.playerState.collect { state ->
                        val oldState = playerState

                        playerState = state

                        resetPlayerPositionJob()
                        (mediaSession?.player as? MediaPlayer)?.invalidatePlayerState(
                            oldState,
                            state
                        )
                    }
                }
            }

            isConnected = true
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        scope.launch {
            val url = applicationContext.dataStore.data.map {
                it[PREFKEY_LASTSERVICEADDRESS]
            }.first()

            if (url != null) {
                baseServiceUrl = url
                initializeAMRemote()
            }
        }

        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let { player ->
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        } ?: {
            stopSelf()
        }
    }

    @UnstableApi
    private inner class MediaPlayer : BaseRemotePlayer() {
        override fun getAvailableCommands(): Commands {
            return Commands.Builder()
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
        }

        override fun getPlaybackState(): Int {
            return if (playerState?.trackData == null && playerState?.isPlaying == false) {
                Player.STATE_IDLE
            } else {
                Player.STATE_READY
            }
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            scope.launch {
                val result =
                    amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.PLAYPAUSESTOP)

                if (result.isSuccess && !playWhenReady) {
                    progressJob?.cancel()
                }
            }
        }

        override fun getPlayWhenReady(): Boolean = playerState?.isPlaying == true

        override fun setRepeatMode(repeatMode: Int) {
            scope.launch {
                amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.REPEAT)
            }
        }

        override fun getRepeatMode(): Int {
            return when (playerState?.repeatMode) {
                MediaPlaybackAutoRepeatMode.LIST -> REPEAT_MODE_ALL
                MediaPlaybackAutoRepeatMode.TRACK -> REPEAT_MODE_ONE
                else -> REPEAT_MODE_OFF
            }
        }

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            scope.launch {
                amRemoteViewModel.value.sendCommand(AppleMusicControlButtons.SHUFFLE)
            }
        }

        override fun getShuffleModeEnabled(): Boolean = playerState?.shuffleEnabled == true

        override fun seekTo(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: Int,
            isRepeatingCurrentItem: Boolean
        ) {
            scope.launch {
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
            }
        }

        override fun stop() {
            stopSelf()
        }

        override fun release() {
            if (amRemoteViewModel.isInitialized()) {
                amRemoteViewModel.value.stopPolling()
            }
        }

        override fun getMediaMetadata(): MediaMetadata {
            return playerState?.trackData?.let {
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
        }

        override fun getCurrentTimeline(): Timeline {
            return RemoteTimeline(isLive = playerState?.isRadio == true).apply {
                mediaMetadata = getMediaMetadata()
            }
        }

        override fun getDuration(): Long {
            return playerState?.trackData?.duration?.toLong()
                ?.takeIf { it >= 0 }
                ?.let { TimeUnit.SECONDS.toMillis(it) }
                ?: C.TIME_UNSET
        }

        override fun getCurrentPosition(): Long {
            return playerState?.trackData?.progress?.times(1000)?.toLong() ?: 0
        }

        fun invalidatePlayerState(oldState: AMPlayerState?, newState: AMPlayerState) {
            mainHandler.post {
                if (newState.trackData?.getKey() != oldState?.trackData?.getKey() || newState.artwork != oldState?.artwork) {
                    listeners.queueEvent(
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                    ) {
                        it.onMediaMetadataChanged(mediaMetadata)
                    }
                    listeners.queueEvent(
                        Player.EVENT_TIMELINE_CHANGED,
                    ) {
                        it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
                    }
                }
                if (newState.isPlaying != oldState?.isPlaying) {
                    /*
                    listeners.queueEvent(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                    ) {
                        it.onPlaybackStateChanged(playbackState)
                    }
                     */
                    listeners.queueEvent(
                        Player.EVENT_IS_PLAYING_CHANGED,
                    ) {
                        it.onIsPlayingChanged(isPlaying)
                    }
                    /*
                    listeners.queueEvent(
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    ) {
                        it.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                    }
                     */
                }
                if (newState.repeatMode != oldState?.repeatMode) {
                    listeners.queueEvent(
                        Player.EVENT_REPEAT_MODE_CHANGED,
                    ) {
                        it.onRepeatModeChanged(repeatMode)
                    }
                }
                if (newState.shuffleEnabled != oldState?.shuffleEnabled) {
                    listeners.queueEvent(
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    ) {
                        it.onShuffleModeEnabledChanged(shuffleModeEnabled)
                    }
                }
                if (newState.skipBackEnabled != oldState?.skipBackEnabled || newState.skipForwardEnabled != oldState?.skipForwardEnabled) {
                    listeners.queueEvent(
                        Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                    ) {
                        it.onAvailableCommandsChanged(availableCommands)
                    }
                }
                if (newState.isRadio != oldState?.isRadio) {
                    listeners.queueEvent(
                        Player.EVENT_TIMELINE_CHANGED,
                    ) {
                        it.onTimelineChanged(currentTimeline, TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
                    }
                }
                if (newState.repeatMode != oldState?.repeatMode || newState.shuffleEnabled != oldState?.shuffleEnabled) {
                    mediaSession?.setCustomLayout(
                        getCustomCommands(
                            shuffleOn = shuffleModeEnabled,
                            repeatMode = repeatMode
                        )
                    )
                }
                listeners.flushEvents()
            }
        }
    }

    private fun resetPlayerPositionJob() {
        mainHandler.post {
            progressJob?.cancel()
            if (playerState?.isPlaying == true) {
                progressJob = scope.launch {
                    while (isActive) {
                        delay(1000)
                        if (isActive) {
                            playerState = playerState?.copy(
                                trackData = playerState?.trackData?.copy(
                                    progress = playerState?.trackData?.progress?.plus(1) ?: 0
                                )
                            )
                        }
                    }
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

            ensureNotificationChannel()

            val builder =
                NotificationCompat.Builder(this@AMControllerService, NOT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.note_icon)
                    .setContentText("Playback cannot be resumed")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(getMainActivityIntent())
            notificationManager.notify(JOB_ID, builder.build())
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

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        // Gets an instance of the NotificationManager service
        var mChannel = notificationManager.getNotificationChannel(NOT_CHANNEL_ID)
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
        notificationManager.createNotificationChannel(mChannel)
    }

    @SuppressLint("MissingPermission", "PrivateResource")
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (!startedByUser && shouldStopForeground(session)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(JOB_ID)
            return
        }

        Util.postOrRun(Handler(session.player.applicationLooper)) {
            val notification = notificationProvider.createNotification(
                session,
                session.customLayout,
                actionFactory
            ) { mediaNotification ->
                updateNotificationInternal(session, mediaNotification, startInForegroundRequired)
            }

            updateNotificationInternal(session, notification, startInForegroundRequired)
        }
    }

    private fun shouldStopForeground(session: MediaSession): Boolean {
        return !isSessionAdded(session) || session.player.playbackState == Player.STATE_IDLE || session.player.playbackState == Player.STATE_ENDED
    }

    @SuppressLint("MissingPermission")
    private fun updateNotificationInternal(
        session: MediaSession,
        mediaNotification: MediaNotification,
        startInForegroundRequired: Boolean
    ) {
        Util.postOrRun(mainHandler) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // MediaNotificationManager: Call Notification.MediaStyle#setMediaSession() indirectly.
                val fwkToken =
                    session.sessionCompatToken.token as? android.media.session.MediaSession.Token
                if (fwkToken != null) {
                    mediaNotification.notification.extras.putParcelable(
                        Notification.EXTRA_MEDIA_SESSION,
                        fwkToken
                    )
                }
            }

            if (startedByUser || startInForegroundRequired) {
                ServiceCompat.startForeground(
                    this@AMControllerService,
                    mediaNotification.notificationId,
                    mediaNotification.notification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else {
                        0
                    }
                )
            } else {
                notificationManager.notify(
                    mediaNotification.notificationId,
                    mediaNotification.notification
                )

                if (!startedByUser && (session.player.playbackState == Player.STATE_IDLE || session.player.playbackState == Player.STATE_ENDED)) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                }
            }
        }
    }

    private inner class AMActionFactory : MediaNotification.ActionFactory {
        override fun createMediaAction(
            mediaSession: MediaSession,
            icon: IconCompat,
            title: CharSequence,
            command: Int
        ): NotificationCompat.Action {
            return NotificationCompat.Action(
                icon, title, createMediaActionPendingIntent(mediaSession, command.toLong())
            )
        }

        override fun createCustomAction(
            mediaSession: MediaSession,
            icon: IconCompat,
            title: CharSequence,
            customAction: String,
            extras: Bundle
        ): NotificationCompat.Action {
            return NotificationCompat.Action(icon, title, getActionPendingIntent(customAction))
        }

        override fun createCustomActionFromCustomCommandButton(
            mediaSession: MediaSession,
            customCommandButton: CommandButton
        ): NotificationCompat.Action {
            return NotificationCompat.Action(
                customCommandButton.iconResId,
                customCommandButton.displayName,
                customCommandButton.sessionCommand?.let {
                    getActionPendingIntent(it.customAction)
                } ?: createMediaActionPendingIntent(
                    mediaSession,
                    customCommandButton.playerCommand.toLong()
                )
            )
        }

        override fun createMediaActionPendingIntent(
            mediaSession: MediaSession,
            command: Long
        ): PendingIntent {
            return when (command.toInt()) {
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    getActionPendingIntent(ACTION_SKIP_BACK)
                }

                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    getActionPendingIntent(ACTION_SKIP_FORWARD)
                }

                Player.COMMAND_PLAY_PAUSE -> {
                    getActionPendingIntent(ACTION_PLAY_PAUSE_STOP)
                }

                Player.COMMAND_STOP -> {
                    getActionPendingIntent(ACTION_STOP_SERVICE)
                }

                Player.COMMAND_SET_SHUFFLE_MODE -> {
                    getActionPendingIntent(ACTION_TOGGLE_SHUFFLE)
                }

                Player.COMMAND_SET_REPEAT_MODE -> {
                    getActionPendingIntent(ACTION_TOGGLE_REPEAT)
                }

                else -> getActionPendingIntent("")
            }
        }

    }

    private fun getCustomCommands(
        shuffleOn: Boolean = mediaSession?.player?.shuffleModeEnabled == true,
        repeatMode: Int = mediaSession?.player?.repeatMode ?: Player.REPEAT_MODE_OFF
    ): List<CommandButton> {
        return listOf(getShuffleCommandButton(shuffleOn), getRepeatCommandButton(repeatMode))
    }

    private fun getShuffleCommandButton(
        shuffleOn: Boolean = mediaSession?.player?.shuffleModeEnabled == true,
        enabled: Boolean = true
    ): CommandButton {
        return CommandButton.Builder()
            .apply {
                setDisplayName(
                    getString(
                        if (shuffleOn) {
                            mediaUiRes.string.exo_controls_shuffle_on_description
                        } else {
                            mediaUiRes.string.exo_controls_shuffle_off_description
                        }
                    )
                )
                setEnabled(enabled)
                setIconResId(
                    if (shuffleOn) {
                        R.drawable.ic_rounded_shuffle_on
                    } else {
                        R.drawable.ic_rounded_shuffle
                    }
                )
                setSessionCommand(
                    SessionCommand(
                        "${ACTION_TOGGLE_SHUFFLE}-${shuffleOn}",
                        Bundle.EMPTY
                    )
                )
            }
            .build()
    }

    private fun getRepeatCommandButton(
        @RepeatMode repeatMode: Int = mediaSession?.player?.repeatMode ?: Player.REPEAT_MODE_OFF,
        enabled: Boolean = true
    ): CommandButton {
        return CommandButton.Builder()
            .apply {
                setDisplayName(
                    getString(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> mediaUiRes.string.exo_controls_repeat_all_description
                            Player.REPEAT_MODE_ONE -> mediaUiRes.string.exo_controls_repeat_one_description
                            else -> mediaUiRes.string.exo_controls_repeat_off_description
                        }
                    )
                )
                setEnabled(enabled)
                setIconResId(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ALL -> R.drawable.ic_rounded_repeat_on
                        Player.REPEAT_MODE_ONE -> R.drawable.ic_rounded_repeat_one_on
                        else -> R.drawable.ic_rounded_repeat
                    }
                )
                setSessionCommand(
                    SessionCommand(
                        "${ACTION_TOGGLE_REPEAT}-${repeatMode}",
                        Bundle.EMPTY
                    )
                )
            }
            .build()
    }

    @Suppress("PrivatePropertyName")
    private inner class AMMediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(SESSION_CMD_TOGGLE_SHUFFLE_OFF, Bundle.EMPTY))
                .add(SessionCommand(SESSION_CMD_TOGGLE_SHUFFLE_ON, Bundle.EMPTY))
                .add(SessionCommand(SESSION_CMD_TOGGLE_REPEAT_OFF, Bundle.EMPTY))
                .add(SessionCommand(SESSION_CMD_TOGGLE_REPEAT_ONE, Bundle.EMPTY))
                .add(SessionCommand(SESSION_CMD_TOGGLE_REPEAT_ALL, Bundle.EMPTY))

            return MediaSession.ConnectionResult.accept(
                availableSessionCommands.build(),
                connectionResult.availablePlayerCommands
            )
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            super.onPostConnect(session, controller)
            // Let the controller know about the custom layout right after it connected.
            session.setCustomLayout(controller, getCustomCommands())
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val player = session.player

            when {
                customCommand.customAction.startsWith(ACTION_TOGGLE_SHUFFLE) -> {
                    val newState = player.shuffleModeEnabled.not()
                    player.shuffleModeEnabled = newState

                    player.addListener(object : Player.Listener {
                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            player.removeListener(this)
                            session.setCustomLayout(getCustomCommands())
                        }
                    })
                }

                customCommand.customAction.startsWith(ACTION_TOGGLE_REPEAT) -> {
                    val newState = player.repeatMode.minus(1).mod(3)
                    player.repeatMode = newState

                    player.addListener(object : Player.Listener {
                        override fun onRepeatModeChanged(repeatMode: Int) {
                            player.removeListener(this)
                            session.setCustomLayout(getCustomCommands())
                        }
                    })
                }
            }

            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS));
        }
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
package com.thewizrd.mediacontroller.remote.ui.views

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thewizrd.mediacontroller.remote.R
import com.thewizrd.mediacontroller.remote.model.AppleMusicControlButtons
import com.thewizrd.mediacontroller.remote.model.MediaPlaybackAutoRepeatMode
import com.thewizrd.mediacontroller.remote.model.PlayPauseStopButtonState
import com.thewizrd.mediacontroller.remote.model.TrackData
import com.thewizrd.mediacontroller.remote.model.getKey
import com.thewizrd.mediacontroller.remote.ui.theme.MediaControllerRemoteTheme
import com.thewizrd.mediacontroller.remote.ui.theme.MinContrastOfPrimaryVsSurface
import com.thewizrd.mediacontroller.remote.ui.util.DynamicThemePrimaryColorsFromImage
import com.thewizrd.mediacontroller.remote.ui.util.contrastAgainst
import com.thewizrd.mediacontroller.remote.ui.util.isEnabled
import com.thewizrd.mediacontroller.remote.ui.util.rememberDominantColorState
import com.thewizrd.mediacontroller.remote.ui.util.verticalGradientScrim
import com.thewizrd.mediacontroller.remote.viewmodels.AMPlayerState
import com.thewizrd.mediacontroller.remote.viewmodels.AMRemoteViewModel
import com.thewizrd.mediacontroller.remote.viewmodels.BaseDiscoveryViewModel
import com.thewizrd.mediacontroller.remote.viewmodels.ServiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    discoveryViewModel: BaseDiscoveryViewModel,
    viewModelStoreOwner: ViewModelStoreOwner,
    serviceState: ServiceState
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val amRemoteViewModel = viewModel<AMRemoteViewModel>(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = viewModelFactory {
            initializer {
                val serviceInfo = serviceState.serviceInfo!!
                AMRemoteViewModel(serviceInfo)
            }
        }
    )
    val playerState by amRemoteViewModel.playerState.collectAsState()

    PlayerScreen(
        modifier = modifier,
        playerState = playerState,
        commandHandler = { command ->
            lifecycleOwner.lifecycleScope.launch {
                amRemoteViewModel.sendCommand(command)
            }
        }
    )

    LaunchedEffect(lifecycleOwner) {
        amRemoteViewModel.updatePlayerState(true)

        amRemoteViewModel.startPolling()

        amRemoteViewModel.connectionErrors.collectLatest {
            if (it is IOException) {
                discoveryViewModel.initializeDiscovery()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            amRemoteViewModel.stopPolling()
        }
    }

    DisposableEffect(serviceState) {
        onDispose {
            viewModelStoreOwner.viewModelStore.clear()
        }
    }
}

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    playerState: AMPlayerState,
    commandHandler: (String) -> Unit = {}
) {
    Surface(modifier) {
        PlayerContent(
            playerState = playerState,
            commandHandler = commandHandler
        )
    }
}

@Composable
fun PlayerContent(
    modifier: Modifier = Modifier,
    playerState: AMPlayerState,
    commandHandler: (String) -> Unit = {}
) {
    PlayerDynamicTheme(playerState) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalGradientScrim(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                    startYPercentage = 1f,
                    endYPercentage = 0f
                )
                .systemBarsPadding()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                PlayerAlbumArt(
                    modifier = Modifier.weight(10f),
                    artworkBitmap = playerState.artwork
                )
                Spacer(modifier = Modifier.height(32.dp))
                PlayerMetadata(playerState = playerState)
                Spacer(modifier = Modifier.height(32.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(10f)
                ) {
                    var sliderProgress by remember(playerState.isPlaying, playerState.trackData) {
                        mutableFloatStateOf(playerState.trackData?.progress?.toFloat() ?: 0f)
                    }

                    PlayerSlider(
                        progress = sliderProgress,
                        duration = (playerState.trackData?.duration ?: 1).toFloat(),
                    )
                    PlayerButtons(
                        modifier = Modifier.padding(vertical = 8.dp),
                        playerState = playerState,
                        commandHandler = commandHandler
                    )

                    if (playerState.isPlaying && playerState.trackData != null) {
                        LaunchedEffect(playerState.trackData) {
                            while (isActive) {
                                delay(100)
                                sliderProgress += 100 / 1000f
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TopAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Empty for now
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun PlayerAlbumArt(
    modifier: Modifier = Modifier,
    artworkBitmap: Bitmap? = null
) {
    if (artworkBitmap != null) {
        Image(
            bitmap = artworkBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .sizeIn(maxWidth = 500.dp, maxHeight = 500.dp)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.no_artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .sizeIn(maxWidth = 500.dp, maxHeight = 500.dp)
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerMetadata(
    playerState: AMPlayerState
) {
    Column(
        modifier = Modifier.heightIn(min = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val trackData = playerState.trackData

        if (trackData != null) {
            if (trackData.name != null) {
                Text(
                    text = trackData.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            if (trackData.artist != null) {
                Text(
                    text = trackData.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    fontWeight = FontWeight.Normal
                )
            }
            if (trackData.album != null) {
                Text(
                    text = trackData.album,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PlayerSlider(progress: Float = 0f, duration: Float = 1f) {
    val indicatorProgress: Float = if (duration > 0f && duration >= progress) {
        progress / duration
    } else {
        0f
    }

    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth(),
        progress = indicatorProgress,
        trackColor = Color(
            ColorUtils.blendARGB(
                MaterialTheme.colorScheme.primary.toArgb(),
                Color.Black.toArgb(),
                0.25f
            )
        ),
        color = Color(
            ColorUtils.blendARGB(
                MaterialTheme.colorScheme.primary.toArgb(),
                Color.White.toArgb(),
                0.25f
            )
        ),
        strokeCap = StrokeCap.Round
    )
}

@Composable
private fun PlayerButtons(
    modifier: Modifier = Modifier,
    playerState: AMPlayerState,
    commandHandler: (String) -> Unit = {},
    playerButtonSize: Dp = 72.dp,
    sideButtonSize: Dp = 48.dp,
    extraButtonSize: Dp = 32.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val sideButtonsModifier = Modifier
            .size(sideButtonSize)
            .clip(MaterialTheme.shapes.medium)
            .semantics { role = Role.Button }
        val extraButtonsModifier = Modifier
            .size(extraButtonSize)
            .clip(RoundedCornerShape(4.dp))
            .semantics { role = Role.Button }

        Image(
            imageVector = if (playerState.shuffleEnabled) {
                Icons.Rounded.ShuffleOn
            } else {
                Icons.Rounded.Shuffle
            },
            contentDescription = if (playerState.shuffleEnabled) {
                "Shuffle On"
            } else {
                "Shuffle Off"
            },
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            modifier = extraButtonsModifier.clickable {
                commandHandler.invoke(AppleMusicControlButtons.SHUFFLE)
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Image(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = "Skip Back",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(LocalContentColor.current.isEnabled(playerState.skipBackEnabled)),
            modifier = sideButtonsModifier.clickable {
                commandHandler.invoke(AppleMusicControlButtons.SKIPBACK)
            }
        )
        Image(
            imageVector = when (playerState.playPauseStopButtonState) {
                PlayPauseStopButtonState.STOP -> Icons.Rounded.StopCircle
                PlayPauseStopButtonState.PAUSE -> Icons.Rounded.PauseCircle
                PlayPauseStopButtonState.PLAY -> Icons.Rounded.PlayCircleFilled
                else -> Icons.Rounded.PlayCircleFilled
            },
            contentDescription = when (playerState.playPauseStopButtonState) {
                PlayPauseStopButtonState.STOP -> "Stop"
                PlayPauseStopButtonState.PAUSE -> "Pause"
                PlayPauseStopButtonState.PLAY -> "Play"
                else -> "Play"
            },
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            modifier = Modifier
                .size(playerButtonSize)
                .clip(RoundedCornerShape(playerButtonSize))
                .semantics { role = Role.Button }
                .clickable {
                    commandHandler.invoke(AppleMusicControlButtons.PLAYPAUSESTOP)
                }
        )
        Image(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = "Skip Forward",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(LocalContentColor.current.isEnabled(playerState.skipForwardEnabled)),
            modifier = sideButtonsModifier.clickable {
                commandHandler.invoke(AppleMusicControlButtons.SKIPFORWARD)
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Image(
            imageVector = when (playerState.repeatMode) {
                MediaPlaybackAutoRepeatMode.TRACK -> Icons.Rounded.RepeatOneOn
                MediaPlaybackAutoRepeatMode.LIST -> Icons.Rounded.RepeatOn
                else -> Icons.Rounded.Repeat
            },
            contentDescription = when (playerState.repeatMode) {
                MediaPlaybackAutoRepeatMode.TRACK -> "Repeat Track"
                MediaPlaybackAutoRepeatMode.LIST -> "Repeat On"
                else -> "Repeat Off"
            },
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            modifier = extraButtonsModifier.clickable {
                commandHandler.invoke(AppleMusicControlButtons.REPEAT)
            }
        )
    }
}

/**
 * Theme that updates the colors dynamically depending on the image
 * Source: Jetcaster (https://github.com/android/compose-samples/blob/main/Jetcaster/app/src/main/java/com/example/jetcaster/ui/player/PlayerScreen.kt)
 */
@Composable
private fun PlayerDynamicTheme(
    playerState: AMPlayerState,
    content: @Composable () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dominantColorState = rememberDominantColorState(
        defaultColor = MaterialTheme.colorScheme.surface
    ) { color ->
        // We want a color which has sufficient contrast against the surface color
        color.contrastAgainst(surfaceColor) >= MinContrastOfPrimaryVsSurface
    }
    DynamicThemePrimaryColorsFromImage(dominantColorState) {
        // Update the dominantColorState with colors coming from the image
        LaunchedEffect(playerState.artwork) {
            val key = playerState.trackData.getKey()

            if (key != null && playerState.artwork != null) {
                dominantColorState.updateColorsFromImage(key, playerState.artwork, false)
            } else {
                dominantColorState.reset()
            }
        }
        content()
    }
}

@Preview(device = Devices.PHONE)
@Preview(device = Devices.TABLET)
@Composable
private fun PreviewPlayerContent() {
    val playerState = AMPlayerState(
        isPlaying = true,
        playPauseStopButtonState = PlayPauseStopButtonState.PAUSE,
        shuffleEnabled = true,
        repeatMode = MediaPlaybackAutoRepeatMode.TRACK,
        skipBackEnabled = true,
        skipForwardEnabled = true,
        trackData = TrackData(
            name = "Song Title",
            artist = "Artist",
            album = "Album",
            duration = TimeUnit.MINUTES.toSeconds(3).toInt(),
            progress = TimeUnit.MINUTES.toSeconds(1).toInt()
        )
    )

    MediaControllerRemoteTheme(darkTheme = true) {
        BoxWithConstraints {
            PlayerScreen(playerState = playerState)
        }
    }
}
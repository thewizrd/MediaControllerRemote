package com.thewizrd.mediacontroller.remote

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.thewizrd.mediacontroller.remote.services.background.AMControllerService
import com.thewizrd.mediacontroller.remote.ui.theme.MediaControllerRemoteTheme
import com.thewizrd.mediacontroller.remote.ui.views.DiscoveryScreen
import com.thewizrd.mediacontroller.remote.ui.views.PlayerScreen
import com.thewizrd.mediacontroller.remote.viewmodels.BaseDiscoveryViewModel
import com.thewizrd.mediacontroller.remote.viewmodels.DiscoveryState
import com.thewizrd.mediacontroller.remote.viewmodels.MDnsDiscoveryViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mDnsDiscoveryViewModel: BaseDiscoveryViewModel by viewModels<MDnsDiscoveryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            // This app is only ever in dark mode, so hard code detectDarkMode to true.
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, detectDarkMode = { true })
        )

        setContent {
            MediaControllerRemoteTheme(darkTheme = true) {
                val serviceState by mDnsDiscoveryViewModel.remoteServiceState.collectAsState()

                when (serviceState.discoveryState) {
                    DiscoveryState.DISCOVERED -> {
                        PlayerScreen(
                            discoveryViewModel = mDnsDiscoveryViewModel
                        )
                    }

                    else -> {
                        DiscoveryScreen(
                            discoveryViewModel = mDnsDiscoveryViewModel,
                            serviceState = serviceState
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            mDnsDiscoveryViewModel.remoteServiceState.collectLatest {
                if (it.discoveryState == DiscoveryState.DISCOVERED && it.serviceInfo != null) {
                    AMControllerService.startService(applicationContext, it.serviceInfo)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mDnsDiscoveryViewModel.init()
    }

    override fun onPause() {
        super.onPause()
        mDnsDiscoveryViewModel.stopDiscovery()
        AMControllerService.stopServiceIfNeeded(applicationContext)
    }
}
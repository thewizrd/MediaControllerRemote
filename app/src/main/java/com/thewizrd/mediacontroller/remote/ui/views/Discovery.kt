package com.thewizrd.mediacontroller.remote.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thewizrd.mediacontroller.remote.R
import com.thewizrd.mediacontroller.remote.viewmodels.DiscoveryState
import com.thewizrd.mediacontroller.remote.viewmodels.MDnsDiscoveryViewModel
import com.thewizrd.mediacontroller.remote.viewmodels.ServiceState

@Composable
fun DiscoveryScreen(
    modifier: Modifier = Modifier,
    discoveryViewModel: MDnsDiscoveryViewModel,
    serviceState: ServiceState
) {
    // A surface container using the 'background' color from the theme
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        DiscoveryView(discoveryState = serviceState.discoveryState) {
            discoveryViewModel.initializeDiscovery()
        }
    }
}

@Composable
fun DiscoveryView(
    modifier: Modifier = Modifier,
    discoveryState: DiscoveryState,
    onInitializeDiscovery: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (discoveryState) {
                DiscoveryState.SEARCHING -> {
                    Text(text = "Looking for Apple Music Remote service")
                    CircularProgressIndicator()
                }

                DiscoveryState.DISCOVERED -> {
                    Text(text = "Service Discovered")
                }

                else -> {
                    // Unknown state; not found
                    Text(text = "Apple Music Remote service not found")
                    IconButton(
                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        onClick = {
                            onInitializeDiscovery.invoke()
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "Search"
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun DiscoverySearchingView() {
    DiscoveryView(discoveryState = DiscoveryState.SEARCHING) {}
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun DiscoveryFoundView() {
    DiscoveryView(discoveryState = DiscoveryState.DISCOVERED) {}
}

@Preview(showBackground = true, widthDp = 360, heightDp = 480)
@Composable
private fun DiscoveryNotFoundView() {
    DiscoveryView(discoveryState = DiscoveryState.NOT_FOUND) {}
}
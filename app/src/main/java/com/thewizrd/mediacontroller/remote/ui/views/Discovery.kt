package com.thewizrd.mediacontroller.remote.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thewizrd.mediacontroller.remote.R
import com.thewizrd.mediacontroller.remote.viewmodels.BaseDiscoveryViewModel
import com.thewizrd.mediacontroller.remote.viewmodels.DiscoveryState
import com.thewizrd.mediacontroller.remote.viewmodels.ServiceState

@Composable
fun DiscoveryScreen(
    modifier: Modifier = Modifier,
    discoveryViewModel: BaseDiscoveryViewModel,
    serviceState: ServiceState
) {
    // A surface container using the 'background' color from the theme
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        DiscoveryView(
            discoveryState = serviceState.discoveryState,
            onInitializeDiscovery = {
                discoveryViewModel.init()
            },
            onStopDiscovery = {
                discoveryViewModel.stopDiscovery()
            }
        )
    }
}

@Composable
fun DiscoveryView(
    modifier: Modifier = Modifier,
    discoveryState: DiscoveryState,
    onInitializeDiscovery: () -> Unit = {},
    onStopDiscovery: () -> Unit = {},
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
                    Text(text = stringResource(id = R.string.message_looking_for_remote_service))
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(
                        modifier = Modifier.padding(ButtonDefaults.TextButtonContentPadding),
                        onClick = onStopDiscovery
                    ) {
                        Text(text = stringResource(id = R.string.action_stop))
                    }
                }

                DiscoveryState.DISCOVERED -> {
                    Text(text = stringResource(id = R.string.message_remote_service_found))
                }

                else -> {
                    // Unknown state; not found
                    Text(text = stringResource(id = R.string.message_remote_service_not_found))
                    FilledTonalButton(
                        modifier = Modifier.padding(ButtonDefaults.ButtonWithIconContentPadding),
                        onClick = onInitializeDiscovery
                    ) {
                        Icon(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(id = R.string.action_search))
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
package com.thewizrd.mediacontroller.remote.viewmodels

data class ServiceState(
    val discoveryState: DiscoveryState = DiscoveryState.UNKNOWN,
    val serviceInfo: String? = null
)

enum class DiscoveryState {
    UNKNOWN,
    NOT_FOUND,
    SEARCHING,
    DISCOVERED
}
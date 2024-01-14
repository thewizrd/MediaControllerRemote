package com.thewizrd.mediacontroller.remote

import android.content.Context

interface ApplicationLib {
    val appContext: Context
    val applicationState: AppState
}
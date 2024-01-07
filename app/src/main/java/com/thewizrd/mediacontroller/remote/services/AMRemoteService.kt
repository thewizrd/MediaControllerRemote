package com.thewizrd.mediacontroller.remote.services

import com.thewizrd.mediacontroller.remote.model.AMRemoteCommand
import com.thewizrd.mediacontroller.remote.model.ArtworkResponse
import com.thewizrd.mediacontroller.remote.model.PlayerStateResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AMRemoteService {
    @GET("api/am-remote/playerState")
    fun getPlayerState(@Query("includeArtwork") includeArtwork: Boolean = false): Call<PlayerStateResponse>

    @GET("api/am-remote/artwork")
    fun getArtwork(): Call<ArtworkResponse>

    @POST("api/am-remote/command")
    fun sendPlayerCommand(@Body @AMRemoteCommand command: String): Call<Unit>
}
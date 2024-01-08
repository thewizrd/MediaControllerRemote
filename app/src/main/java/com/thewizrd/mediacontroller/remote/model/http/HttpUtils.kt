package com.thewizrd.mediacontroller.remote.model.http

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

fun createRetrofitBuilder(): Retrofit.Builder = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create())
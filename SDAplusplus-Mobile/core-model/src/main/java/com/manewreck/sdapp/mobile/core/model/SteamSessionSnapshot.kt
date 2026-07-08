package com.manewreck.sdapp.mobile.core.model

data class SteamSessionSnapshot(
    val steamId: String,
    var accessToken: String? = null,
    var refreshToken: String? = null,
    var sessionId: String? = null,
)

package com.manewreck.sdapp.mobile.core.model

data class VaultSummary(
    val accountCount: Int,
    val encrypted: Boolean,
    val lastUnlockedUtc: String?,
)

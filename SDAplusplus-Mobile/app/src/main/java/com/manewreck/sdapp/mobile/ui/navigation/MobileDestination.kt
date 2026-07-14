package com.manewreck.sdapp.mobile.ui.navigation

sealed class MobileDestination(
    val route: String,
    val label: String,
    val shortLabel: String,
) {
    data object Home : MobileDestination("home", "Accounts", "A")
    data object Account : MobileDestination("account/{steamId}", "Account", "D") {
        fun createRoute(steamId: String): String = "account/$steamId"
    }
    data object QrScanner : MobileDestination("qr", "QR", "Q")
    data object CloudSync : MobileDestination("sync", "Sync", "S")
    data object Settings : MobileDestination("settings", "Settings", "C")
    data object Confirmations : MobileDestination("confirmations", "Confirmations", "T")
}

package com.speechwithabhi.app.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Preview : Screen("preview/{documentId}") {
        fun createRoute(documentId: String) = "preview/$documentId"
    }
    data object Player : Screen("player/{documentId}") {
        fun createRoute(documentId: String) = "player/$documentId"
    }
    data object VoiceStudio : Screen("voice_studio")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data object About : Screen("about")
}

package com.maheshkatakam.stash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maheshkatakam.stash.StashApp
import com.maheshkatakam.stash.ui.editor.EditorScreen
import com.maheshkatakam.stash.ui.home.HomeScreen
import com.maheshkatakam.stash.ui.settings.SettingsScreen

@Composable
fun StashNavHost(app: StashApp, openItemId: Long?, onOpenConsumed: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(openItemId) {
        if (openItemId != null) {
            nav.navigate("item/$openItemId") { launchSingleTop = true }
            onOpenConsumed()
        }
    }

    fun back() {
        if (nav.previousBackStackEntry != null) nav.popBackStack()
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                app = app,
                onOpenItem = { id, record -> nav.navigate("item/$id?record=$record") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = "item/{id}?record={record}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("record") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val record = entry.arguments?.getBoolean("record") ?: false
            EditorScreen(app = app, itemId = id, startRecording = record, onBack = ::back)
        }
        composable("settings") {
            SettingsScreen(app = app, onBack = ::back)
        }
    }
}

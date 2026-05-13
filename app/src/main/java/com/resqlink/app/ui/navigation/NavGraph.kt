package com.resqlink.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.resqlink.app.ui.screens.alerts.AlertsScreen
import com.resqlink.app.ui.screens.contacts.ContactsScreen
import com.resqlink.app.ui.screens.home.HomeScreen
import com.resqlink.app.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Contacts : Screen("contacts", "Contacts", Icons.Default.Contacts)
    data object Alerts : Screen("alerts", "Alerts", Icons.Default.Notifications)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.Contacts, Screen.Alerts, Screen.Settings)

@Composable
fun ResQLinkNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.Contacts.route) {
            ContactsScreen()
        }

        composable(Screen.Alerts.route) {
            AlertsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

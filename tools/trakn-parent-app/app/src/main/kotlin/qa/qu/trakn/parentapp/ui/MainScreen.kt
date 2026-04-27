package qa.qu.trakn.parentapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import qa.qu.trakn.parentapp.data.SettingsRepository
import qa.qu.trakn.parentapp.data.models.AppSettings
import qa.qu.trakn.parentapp.ui.landing.LandingScreen
import qa.qu.trakn.parentapp.ui.locate.LocateScreen
import qa.qu.trakn.parentapp.ui.locate.LocateViewModel
import qa.qu.trakn.parentapp.ui.settings.SettingsScreen
import qa.qu.trakn.parentapp.ui.settings.SettingsViewModel

@Composable
fun MainScreen(
    locateViewModel: LocateViewModel,
    settingsViewModel: SettingsViewModel,
    settingsRepo: SettingsRepository,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "landing"
    val scope = rememberCoroutineScope()

    val showBottomNav = current == "locate" || current == "settings"

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    NavigationBarItem(
                        selected = current == "locate",
                        onClick  = { navController.navigate("locate") { launchSingleTop = true } },
                        icon     = { Icon(Icons.Default.LocationOn, contentDescription = "Locate") },
                        label    = { Text("Locate") },
                    )
                    NavigationBarItem(
                        selected = current == "settings",
                        onClick  = { navController.navigate("settings") { launchSingleTop = true } },
                        icon     = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label    = { Text("Settings") },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "landing",
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable("landing") {
                val settingsState by settingsViewModel.state.collectAsState()
                LandingScreen(
                    initialTagId    = settingsState.settings.tagId,
                    onStartTracking = { tagId ->
                        scope.launch {
                            val saved = settingsRepo.settings.first()
                            settingsRepo.update(saved.copy(tagId = tagId))
                            navController.navigate("locate") {
                                popUpTo("landing") { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable("locate") {
                LocateScreen(locateViewModel)
            }
            composable("settings") {
                DisposableEffect(Unit) {
                    onDispose { locateViewModel.refresh() }
                }
                SettingsScreen(
                    viewModel  = settingsViewModel,
                    onChangTag = {
                        navController.navigate("landing") {
                            popUpTo("locate") { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

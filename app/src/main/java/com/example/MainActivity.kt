package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent(this, AppServices.LocalVpnService::class.java)
            startService(intent)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onResume() {
        super.onResume()
        AppState.setOurAppForeground(true)
    }

    override fun onPause() {
        super.onPause()
        AppState.setOurAppForeground(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MyApplicationTheme {
                MainLayout(
                    onRequireVpnPermission = { intent -> vpnPermissionLauncher.launch(intent) }
                )
            }
        }
    }
}

@Composable
fun MainLayout(onRequireVpnPermission: (Intent) -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "select_app"

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "App") },
                    label = { Text("Target App") },
                    selected = currentRoute == "select_app",
                    onClick = { navController.navigate("select_app") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Proxy") },
                    label = { Text("Network Proxy") },
                    selected = currentRoute == "proxy",
                    onClick = { navController.navigate("proxy") { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "Overlay") },
                    label = { Text("Overlays") },
                    selected = currentRoute == "overlay",
                    onClick = { navController.navigate("overlay") { launchSingleTop = true } }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "select_app",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("select_app") {
                AppSelectionScreen(
                    context = navController.context,
                    onAppSelected = { navController.navigate("proxy") }
                )
            }
            composable("proxy") {
                ProxyDashboardScreen(
                    context = navController.context,
                    onRequireVpnPermission = onRequireVpnPermission
                )
            }
            composable("overlay") {
                OverlayManagerScreen(context = navController.context)
            }
        }
    }
}

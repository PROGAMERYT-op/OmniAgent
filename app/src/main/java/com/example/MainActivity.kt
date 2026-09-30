package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.FloatingOverlayService
import com.example.ui.MainViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.OnboardingDialog
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class MainNavTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    ROUTINES("Routines", Icons.Default.Bolt),
    PERMISSIONS("Permissions", Icons.Default.VerifiedUser),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(MainNavTab.DASHBOARD) }
    val onboardingCompleted by viewModel.preferences.onboardingCompleted.collectAsState()
    var showOnboardingDialog by remember { mutableStateOf(!onboardingCompleted) }
    val isServiceActive by FloatingOverlayService.isServiceActive.collectAsState()

    // Handle back button for sub-tabs to return to Dashboard
    BackHandler(enabled = selectedTab != MainNavTab.DASHBOARD) {
        selectedTab = MainNavTab.DASHBOARD
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "OmniAgent AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isServiceActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isServiceActive) Color(0xFF10B981) else Color(0xFF64748B)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isServiceActive) Color(0xFF10B981) else Color(0xFF94A3B8),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isServiceActive) "ACTIVE" else "IDLE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isServiceActive) Color(0xFF6EE7B7) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showOnboardingDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help Guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                MainNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainNavTab.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToPermissions = { selectedTab = MainNavTab.PERMISSIONS },
                    onNavigateToRoutines = { selectedTab = MainNavTab.ROUTINES }
                )
                MainNavTab.ROUTINES -> RoutinesScreen(
                    viewModel = viewModel
                )
                MainNavTab.PERMISSIONS -> PermissionsScreen(
                    viewModel = viewModel,
                    onContinueToDashboard = { selectedTab = MainNavTab.DASHBOARD }
                )
                MainNavTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenOnboarding = { showOnboardingDialog = true }
                )
            }
        }
    }

    if (showOnboardingDialog) {
        OnboardingDialog(
            onDismiss = {
                showOnboardingDialog = false
                viewModel.preferences.setOnboardingCompleted(true)
            },
            onComplete = {
                showOnboardingDialog = false
                viewModel.preferences.setOnboardingCompleted(true)
                selectedTab = MainNavTab.PERMISSIONS
            }
        )
    }
}

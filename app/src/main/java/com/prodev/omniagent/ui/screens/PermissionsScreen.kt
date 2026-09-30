package com.prodev.omniagent.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prodev.omniagent.engine.ScreenCaptureManager
import com.prodev.omniagent.service.FloatingOverlayService
import com.prodev.omniagent.ui.MainViewModel

@Composable
fun PermissionsScreen(
    viewModel: MainViewModel,
    onContinueToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsState()

    // Activity result launcher for Screen Capture (MediaProjection)
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ScreenCaptureManager.setProjectionResult(result.resultCode, result.data)
            FloatingOverlayService.updateForegroundType(context)
            viewModel.refreshPermissions()
        }
    }

    // Activity result launcher for Microphone permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refreshPermissions()
    }

    // Calculate granted score
    val grantedCount = listOf(
        permissions.hasAccessibility,
        permissions.hasOverlay,
        permissions.hasScreenCapture,
        permissions.hasBatteryOptimizationIgnored,
        permissions.hasRecordAudio
    ).count { it }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("System Permissions Center", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Surface(
                            color = if (permissions.allRequiredGranted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$grantedCount / 5 Granted",
                                color = if (permissions.allRequiredGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "To allow OmniAgent to view your screen, simulate touch gestures, and float smoothly, please authorize the required permissions below.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = { grantedCount / 5f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // 1. Accessibility Service
        item {
            PermissionCard(
                title = "Accessibility Service",
                description = "Required to inspect screen element hierarchy, execute click/scroll gestures, and automate smartphone workflows.",
                isGranted = permissions.hasAccessibility,
                icon = Icons.Default.AccessibilityNew,
                isRequired = true,
                actionLabel = if (permissions.hasAccessibility) "Active" else "Enable in Settings",
                testTag = "perm_accessibility",
                onAction = {
                    viewModel.openAccessibilitySettings()
                }
            )
        }

        // 2. Display Over Other Apps (Overlay)
        item {
            PermissionCard(
                title = "Display Over Other Apps",
                description = "Required to render the Draggable Floating Bubble, Chat Panel, and Real-Time HUD Action Pointer.",
                isGranted = permissions.hasOverlay,
                icon = Icons.Default.Layers,
                isRequired = true,
                actionLabel = if (permissions.hasOverlay) "Active" else "Enable Overlay",
                testTag = "perm_overlay",
                onAction = {
                    viewModel.openOverlaySettings()
                }
            )
        }

        // 3. Screen Capture (MediaProjection)
        item {
            PermissionCard(
                title = "Screen Capture (Vision Fallback)",
                description = "Used when UI DOM hierarchy is sparse or inside canvas/games, allowing Gemini Multimodal Vision to locate targets.",
                isGranted = permissions.hasScreenCapture,
                icon = Icons.Default.Screenshot,
                isRequired = false,
                actionLabel = if (permissions.hasScreenCapture) "Authorized" else "Grant Capture",
                testTag = "perm_screen_capture",
                onAction = {
                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    projectionManager?.let {
                        screenCaptureLauncher.launch(it.createScreenCaptureIntent())
                    }
                }
            )
        }

        // 4. Ignore Battery Optimizations
        item {
            PermissionCard(
                title = "Battery Optimization Exclusion",
                description = "Prevents Android OS from freezing the autonomous agent service when running complex multi-step background tasks.",
                isGranted = permissions.hasBatteryOptimizationIgnored,
                icon = Icons.Default.BatteryChargingFull,
                isRequired = false,
                actionLabel = if (permissions.hasBatteryOptimizationIgnored) "Excluded" else "Exclude Doze",
                testTag = "perm_battery",
                onAction = {
                    viewModel.requestBatteryOptimizationExemption()
                }
            )
        }

        // 5. Microphone Permission
        item {
            PermissionCard(
                title = "Microphone & Voice Input",
                description = "Allows speaking directly to OmniAgent using native Android Speech-to-Text for hands-free command execution.",
                isGranted = permissions.hasRecordAudio,
                icon = Icons.Default.Mic,
                isRequired = false,
                actionLabel = if (permissions.hasRecordAudio) "Granted" else "Grant Mic",
                testTag = "perm_microphone",
                onAction = {
                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            )
        }

        // Continue to dashboard button if required granted
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onContinueToDashboard,
                enabled = permissions.allRequiredGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("continue_dashboard_button")
            ) {
                Text(
                    text = if (permissions.allRequiredGranted) "Proceed to Dashboard" else "Accessibility & Overlay Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    isRequired: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    if (isRequired) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("REQUIRED", color = Color(0xFFFCA5A5), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(actionLabel, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ActionLogEntity
import com.example.engine.ScreenCaptureManager
import com.example.service.AgentAccessibilityService
import com.example.service.FloatingOverlayService
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToPermissions: () -> Unit,
    onNavigateToRoutines: () -> Unit
) {
    val isServiceActive by FloatingOverlayService.isServiceActive.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val quickDiagnostic by viewModel.quickTestOutput.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val context = LocalContext.current

    // Screen Capture Launcher: invoked directly when starting the agent service
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureManager.setProjectionResult(result.resultCode, result.data)
            viewModel.refreshPermissions()
            FloatingOverlayService.updateForegroundType(context)
        }
        // Always start the floating overlay service upon user interaction
        FloatingOverlayService.start(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Agent Service Status Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_status_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsing animated orb status
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isServiceActive) listOf(
                                        Color(0xFF00E5FF).copy(alpha = 0.3f),
                                        Color(0xFF0F172A)
                                    ) else listOf(
                                        Color(0xFF64748B).copy(alpha = 0.2f),
                                        Color(0xFF0F172A)
                                    )
                                ),
                                CircleShape
                            )
                            .border(
                                2.dp,
                                if (isServiceActive) Color(0xFF00E5FF) else Color(0xFF475569),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.SmartToy else Icons.Default.PowerSettingsNew,
                            contentDescription = "Agent Service State",
                            tint = if (isServiceActive) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isServiceActive) "OmniAgent AI Active" else "Agent Service Idle",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isServiceActive)
                            "Floating bubble is ready on top of your screen."
                        else
                            "Launch the floating bubble to execute voice & chat phone automation.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big Toggle Button
                    Button(
                        onClick = {
                            if (!permissions.allRequiredGranted && !isServiceActive) {
                                onNavigateToPermissions()
                            } else if (isServiceActive) {
                                viewModel.toggleAgentService()
                            } else {
                                // User tapped "Start Agent Service"
                                // If screen capture is not yet authorized in this session,
                                // prompt system permission directly right here!
                                if (!ScreenCaptureManager.hasProjectionPermission()) {
                                    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                    if (mediaProjectionManager != null) {
                                        try {
                                            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                        } catch (e: Exception) {
                                            viewModel.toggleAgentService()
                                        }
                                    } else {
                                        viewModel.toggleAgentService()
                                    }
                                } else {
                                    viewModel.toggleAgentService()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceActive) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                            contentColor = if (isServiceActive) Color.White else Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_agent_button")
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceActive) "Stop Agent Service" else "Start Agent Service",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Screen Capture / Vision AI status indicator
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (permissions.hasScreenCapture) Color(0x2210B981) else Color(0x2238BDF8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!permissions.hasScreenCapture) {
                                    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                                    mediaProjectionManager?.let {
                                        try {
                                            screenCaptureLauncher.launch(it.createScreenCaptureIntent())
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = if (permissions.hasScreenCapture) Icons.Default.CheckCircle else Icons.Default.Screenshot,
                                contentDescription = null,
                                tint = if (permissions.hasScreenCapture) Color(0xFF10B981) else Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (permissions.hasScreenCapture)
                                    "Screen Capture (Vision AI): Active"
                                else
                                    "Screen Capture: Auto-prompts on Start (Tap to grant)",
                                fontSize = 12.sp,
                                color = if (permissions.hasScreenCapture) Color(0xFFA7F3D0) else Color(0xFFBAE6FD)
                            )
                        }
                    }

                    // Warning banner if permissions missing
                    if (!permissions.allRequiredGranted && !isServiceActive) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x33F59E0B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPermissions() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Accessibility & Overlay permissions required. Tap to setup.",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFDE68A)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Quick Diagnostics & Gesture Test Suite
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diagnostics_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Engine Diagnostics & Test", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Verify DOM hierarchy inspection and simulated gestures safely.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.runHierarchyDiagnostic() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inspect_dom_button")
                        ) {
                            Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspect DOM", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (isServiceActive) {
                                    viewModel.toggleAgentService()
                                } else {
                                    viewModel.toggleAgentService()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_overlay_button")
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Overlay", fontSize = 12.sp)
                        }
                    }

                    // Diagnostic output console
                    AnimatedVisibility(visible = quickDiagnostic != null) {
                        quickDiagnostic?.let { diagText ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("DOM Node Output", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = { viewModel.clearDiagnosticOutput() },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = diagText,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Routine Shortcuts Bar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Favorite Routines", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = onNavigateToRoutines) {
                    Text("View All (${routines.size})", fontSize = 13.sp)
                }
            }
        }

        items(routines.take(2), key = { it.id }) { routine ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_item_${routine.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (routine.iconKey) {
                                "music" -> Icons.Default.MusicNote
                                "alarm" -> Icons.Default.Alarm
                                else -> Icons.Default.Bolt
                            },
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(routine.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            routine.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = { viewModel.runRoutine(routine) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Recent Agent Execution Logs
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recent Agent Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (recentLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No actions executed yet. Start the agent or run a routine to see live logs.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(recentLogs.take(5), key = { it.id }) { log ->
                ActionLogItemRow(log)
            }
        }
    }
}

@Composable
fun ActionLogItemRow(log: ActionLogEntity) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val statusColor = when (log.status) {
        "SUCCESS" -> Color(0xFF10B981)
        "CONFIRMED" -> Color(0xFF00E5FF)
        "CANCELLED" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(log.actionType, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(formattedTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    log.targetDescription,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = log.status,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

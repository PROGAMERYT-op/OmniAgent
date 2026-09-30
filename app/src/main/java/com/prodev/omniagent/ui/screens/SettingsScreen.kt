package com.prodev.omniagent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prodev.omniagent.ui.ConnectionTestState
import com.prodev.omniagent.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenOnboarding: () -> Unit
) {
    val prefs = viewModel.preferences
    val customApiKey by prefs.customApiKey.collectAsState()
    val selectedModel by prefs.selectedModel.collectAsState()
    val safetyGuardrails by prefs.safetyGuardrails.collectAsState()
    val privacyShield by prefs.privacyShield.collectAsState()
    val voiceMode by prefs.voiceMode.collectAsState()
    val testState by viewModel.connectionTestState.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val availableModels = listOf(
        "gemini-3.5-flash" to "Gemini 3.5 Flash (Fast, Multimodal, Recommended)",
        "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (Deep Reasoning)",
        "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite (Ultra Low Latency)"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Gemini AI Configuration
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini API Configuration", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter your Google Gemini API Key. By default, keys configured in AI Studio Secrets panel are used automatically.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { prefs.setCustomApiKey(it) },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Selector
                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = availableModels.firstOrNull { it.first == selectedModel }?.second ?: selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Reasoning Model") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("model_selector")
                        )

                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }
                        ) {
                            availableModels.forEach { (modelId, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(modelId, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        prefs.setSelectedModel(modelId)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test Connection Button
                    OutlinedButton(
                        onClick = { viewModel.testGeminiConnection() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("test_key_button")
                    ) {
                        if (testState is ConnectionTestState.Testing) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting...")
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Key Connection")
                        }
                    }

                    // Connection Test Result Pill
                    AnimatedVisibility(visible = testState !is ConnectionTestState.Idle) {
                        Spacer(modifier = Modifier.height(10.dp))
                        when (testState) {
                            is ConnectionTestState.Success -> {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text((testState as ConnectionTestState.Success).message, color = Color(0xFF6EE7B7), fontSize = 12.sp)
                                    }
                                }
                            }
                            is ConnectionTestState.Error -> {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text((testState as ConnectionTestState.Error).error, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Section: Safety & Privacy Controls
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Safety & Privacy Guardrails", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Safety Guardrails Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Safety Guardrails", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Prompts explicit floating confirmation when AI attempts payments, deletions, or messaging.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = safetyGuardrails,
                            onCheckedChange = { prefs.setSafetyGuardrails(it) },
                            modifier = Modifier.testTag("safety_guardrails_toggle")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Privacy Shield Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy Shield", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Sanitizes password & PIN fields from DOM hierarchy and blackouts sensitive regions in screenshots.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = privacyShield,
                            onCheckedChange = { prefs.setPrivacyShield(it) },
                            modifier = Modifier.testTag("privacy_shield_toggle")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Voice Mode Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hands-Free Voice Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Enables quick-mic listening in the floating overlay bubble.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = voiceMode,
                            onCheckedChange = { prefs.setVoiceMode(it) },
                            modifier = Modifier.testTag("voice_mode_toggle")
                        )
                    }
                }
            }
        }

        // Onboarding & Help Guide Revisit
        item {
            OutlinedCard(
                onClick = onOpenOnboarding,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_onboarding_guide")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("View Onboarding & Transparency Guide", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Learn how OmniAgent protects your privacy while operating your phone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }
        }
    }
}

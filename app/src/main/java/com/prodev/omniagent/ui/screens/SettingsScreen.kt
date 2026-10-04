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
import com.prodev.omniagent.ai.AvailableModel
import com.prodev.omniagent.ui.ConnectionTestState
import com.prodev.omniagent.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenOnboarding: () -> Unit
) {
    val prefs = viewModel.preferences
    val selectedProvider by prefs.selectedProvider.collectAsState()
    val testState by viewModel.connectionTestState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Section: Provider Selector ────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Provider", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Select which AI backend OmniAgent uses for reasoning and automation.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProviderChip(
                            label = "Gemini (Google)",
                            selected = selectedProvider == "gemini",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                prefs.setProvider("gemini")
                                viewModel.resetConnectionTestState()
                            }
                        )
                        ProviderChip(
                            label = "OpenRouter",
                            selected = selectedProvider == "openrouter",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                prefs.setProvider("openrouter")
                                viewModel.resetConnectionTestState()
                            }
                        )
                    }
                }
            }
        }

        // ── Section: Gemini Config (shown when provider == "gemini") ──────────
        item {
            AnimatedVisibility(visible = selectedProvider == "gemini") {
                GeminiConfigCard(viewModel = viewModel, testState = testState)
            }
        }

        // ── Section: OpenRouter Config (shown when provider == "openrouter") ──
        item {
            AnimatedVisibility(visible = selectedProvider == "openrouter") {
                OpenRouterConfigCard(viewModel = viewModel, testState = testState)
            }
        }

        // ── Section: Safety & Privacy Controls ───────────────────────────────
        item {
            SafetyPrivacyCard(prefs = prefs)
        }

        // ── Onboarding & Help Guide Revisit ───────────────────────────────────
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

// ── Provider chip ─────────────────────────────────────────────────────────────

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
        }
    }
}

// ── Gemini config card ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiConfigCard(viewModel: MainViewModel, testState: ConnectionTestState) {
    val prefs = viewModel.preferences
    val customApiKey by prefs.customApiKey.collectAsState()
    val selectedModel by prefs.selectedModel.collectAsState()
    val geminiModels by viewModel.geminiModels.collectAsState()
    val modelsLoading by viewModel.geminiModelsLoading.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

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

            // API Key field
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

            // Model selector row with refresh button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = geminiModels.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Gemini Model") },
                        trailingIcon = {
                            if (modelsLoading) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                            }
                        },
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
                        if (modelsLoading) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Loading models...", fontSize = 13.sp)
                                    }
                                },
                                onClick = {}
                            )
                        } else if (geminiModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Enter API key & tap ↻ Refresh", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { modelDropdownExpanded = false }
                            )
                        } else {
                            geminiModels.forEach { model ->
                                ModelDropdownItem(
                                    model = model,
                                    isSelected = model.id == selectedModel,
                                    onClick = {
                                        prefs.setSelectedModel(model.id)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.fetchGeminiModels() },
                    enabled = !modelsLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh models", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Connection
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
                    Text("Test Gemini Connection")
                }
            }

            ConnectionResultPill(testState = testState)
        }
    }
}

// ── OpenRouter config card ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenRouterConfigCard(viewModel: MainViewModel, testState: ConnectionTestState) {
    val prefs = viewModel.preferences
    val openRouterApiKey by prefs.openRouterApiKey.collectAsState()
    val selectedModel by prefs.openRouterModel.collectAsState()
    val openRouterModels by viewModel.openRouterModels.collectAsState()
    val modelsLoading by viewModel.openRouterModelsLoading.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OpenRouter Configuration", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "OpenRouter provides unified access to hundreds of AI models (Mixtral, Llama, Claude, GPT-4, and more). Get your key at openrouter.ai.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // API Key field
            OutlinedTextField(
                value = openRouterApiKey,
                onValueChange = { prefs.setOpenRouterApiKey(it) },
                label = { Text("OpenRouter API Key") },
                placeholder = { Text("sk-or-...") },
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
                    .testTag("openrouter_api_key_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Model selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = openRouterModels.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("OpenRouter Model") },
                        trailingIcon = {
                            if (modelsLoading) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("openrouter_model_selector")
                    )

                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        if (modelsLoading) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Loading models...", fontSize = 13.sp)
                                    }
                                },
                                onClick = {}
                            )
                        } else {
                            openRouterModels.forEach { model ->
                                ModelDropdownItem(
                                    model = model,
                                    isSelected = model.id == selectedModel,
                                    onClick = {
                                        prefs.setOpenRouterModel(model.id)
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.fetchOpenRouterModels() },
                    enabled = !modelsLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh OpenRouter models", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Test Connection
            OutlinedButton(
                onClick = { viewModel.testOpenRouterConnection() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("test_openrouter_button")
            ) {
                if (testState is ConnectionTestState.Testing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test OpenRouter Connection")
                }
            }

            ConnectionResultPill(testState = testState)
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun ModelDropdownItem(
    model: AvailableModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.id,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (model.displayName != model.id) {
                        Text(model.displayName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (model.description.isNotBlank()) {
                        Text(model.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        onClick = onClick
    )
}

@Composable
private fun ConnectionResultPill(testState: ConnectionTestState) {
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
                        Text(testState.message, color = Color(0xFF6EE7B7), fontSize = 12.sp)
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
                        Text(testState.error, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun SafetyPrivacyCard(prefs: com.prodev.omniagent.data.prefs.AgentPreferences) {
    val safetyGuardrails by prefs.safetyGuardrails.collectAsState()
    val privacyShield by prefs.privacyShield.collectAsState()
    val voiceMode by prefs.voiceMode.collectAsState()

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

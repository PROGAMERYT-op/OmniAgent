package com.prodev.omniagent.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prodev.omniagent.ai.AvailableModel
import com.prodev.omniagent.ai.GeminiAgentRepository
import com.prodev.omniagent.ai.OpenRouterRepository
import com.prodev.omniagent.data.db.ActionLogEntity
import com.prodev.omniagent.data.db.AppDatabase
import com.prodev.omniagent.data.db.RoutineEntity
import com.prodev.omniagent.data.prefs.AgentPreferences
import com.prodev.omniagent.engine.AgentExecutionController
import com.prodev.omniagent.engine.ScreenCaptureManager
import com.prodev.omniagent.service.AgentAccessibilityService
import com.prodev.omniagent.service.FloatingOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val hasOverlay: Boolean = false,
    val hasAccessibility: Boolean = false,
    val hasScreenCapture: Boolean = false,
    val hasBatteryOptimizationIgnored: Boolean = false,
    val hasRecordAudio: Boolean = false
) {
    val allRequiredGranted: Boolean
        get() = hasOverlay && hasAccessibility
}

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Error(val error: String) : ConnectionTestState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    val preferences = AgentPreferences.getInstance(context)
    val database = AppDatabase.getDatabase(context)
    val agentController = AgentExecutionController.getInstance(context)
    private val geminiRepository = GeminiAgentRepository()
    private val openRouterRepository = OpenRouterRepository()

    val routines: StateFlow<List<RoutineEntity>> = database.routineDao().getAllRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ActionLogEntity>> = database.routineDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissions = MutableStateFlow(checkPermissions())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _quickTestOutput = MutableStateFlow<String?>(null)
    val quickTestOutput: StateFlow<String?> = _quickTestOutput.asStateFlow()

    // ── Live model lists ──────────────────────────────────────────────────────

    private val _geminiModels = MutableStateFlow<List<AvailableModel>>(GeminiAgentRepository.FALLBACK_GEMINI_MODELS)
    val geminiModels: StateFlow<List<AvailableModel>> = _geminiModels.asStateFlow()

    private val _openRouterModels = MutableStateFlow<List<AvailableModel>>(OpenRouterRepository.FALLBACK_OPENROUTER_MODELS)
    val openRouterModels: StateFlow<List<AvailableModel>> = _openRouterModels.asStateFlow()

    private val _geminiModelsLoading = MutableStateFlow(false)
    val geminiModelsLoading: StateFlow<Boolean> = _geminiModelsLoading.asStateFlow()

    private val _openRouterModelsLoading = MutableStateFlow(false)
    val openRouterModelsLoading: StateFlow<Boolean> = _openRouterModelsLoading.asStateFlow()

    init {
        // Eagerly fetch available models so the picker is ready when user opens Settings
        fetchGeminiModels()
        fetchOpenRouterModels()
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    fun refreshPermissions() {
        _permissions.value = checkPermissions()
    }

    private fun checkPermissions(): PermissionState {
        val hasOverlay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        // Dual-gate accessibility check: OS registry AND live in-process instance.
        // This prevents the ghost "Active" state when the app crashes without onDestroy().
        val isSystemEnabled = AgentAccessibilityService.isEnabledInSystem(context)
        val isConnected = AgentAccessibilityService.isServiceRunning()
        // True dual-gate: the service must be registered in the OS AND have a live, bound
        // in-process instance. This prevents both false negatives (enabled but not yet bound)
        // and the ghost "Active" state reported when the in-process reference is stale.
        val hasAccessibility = isSystemEnabled && isConnected

        val hasScreenCapture = ScreenCaptureManager.hasProjectionPermission()

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val hasBattery = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else true

        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        return PermissionState(
            hasOverlay = hasOverlay,
            hasAccessibility = hasAccessibility,
            hasScreenCapture = hasScreenCapture,
            hasBatteryOptimizationIgnored = hasBattery,
            hasRecordAudio = hasAudio
        )
    }

    // ── Connection tests ──────────────────────────────────────────────────────

    fun testGeminiConnection() {
        val apiKey = preferences.getEffectiveApiKey()
        val model = preferences.selectedModel.value

        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val result = geminiRepository.testConnection(apiKey, model)
            if (result.isSuccess) {
                _connectionTestState.value = ConnectionTestState.Success("Model connected: ${result.getOrNull()}")
            } else {
                _connectionTestState.value = ConnectionTestState.Error(result.exceptionOrNull()?.message ?: "Failed")
            }
        }
    }

    fun testOpenRouterConnection() {
        val apiKey = preferences.getEffectiveOpenRouterKey()
        val model = preferences.openRouterModel.value

        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val result = openRouterRepository.testConnection(apiKey, model)
            if (result.isSuccess) {
                _connectionTestState.value = ConnectionTestState.Success("OpenRouter connected: ${result.getOrNull()}")
            } else {
                _connectionTestState.value = ConnectionTestState.Error(result.exceptionOrNull()?.message ?: "Failed")
            }
        }
    }

    fun resetConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    // ── Model fetching ────────────────────────────────────────────────────────

    fun fetchGeminiModels() {
        viewModelScope.launch {
            _geminiModelsLoading.value = true
            val apiKey = preferences.getEffectiveApiKey()
            val result = geminiRepository.fetchGeminiModels(apiKey)
            result.onSuccess { models ->
                if (models.isNotEmpty()) _geminiModels.value = models
            }
            _geminiModelsLoading.value = false
        }
    }

    fun fetchOpenRouterModels() {
        viewModelScope.launch {
            _openRouterModelsLoading.value = true
            val result = openRouterRepository.fetchModels()
            result.onSuccess { models ->
                if (models.isNotEmpty()) _openRouterModels.value = models
            }
            _openRouterModelsLoading.value = false
        }
    }

    // ── Agent service ─────────────────────────────────────────────────────────

    fun toggleAgentService() {
        if (FloatingOverlayService.isServiceActive.value) {
            FloatingOverlayService.stop(context)
        } else {
            FloatingOverlayService.start(context)
        }
    }

    fun runRoutine(routine: RoutineEntity) {
        agentController.startGoal(routine.promptGoal)
        // If overlay is not running, launch it
        if (!FloatingOverlayService.isServiceActive.value) {
            FloatingOverlayService.start(context)
        }
    }

    fun addRoutine(title: String, description: String, promptGoal: String, iconKey: String) {
        viewModelScope.launch {
            database.routineDao().insertRoutine(
                RoutineEntity(
                    title = title,
                    description = description,
                    promptGoal = promptGoal,
                    stepsJson = "[]",
                    iconKey = iconKey,
                    isFavorite = true
                )
            )
        }
    }

    fun deleteRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            database.routineDao().deleteRoutine(routine)
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    fun runHierarchyDiagnostic() {
        val accessibility = AgentAccessibilityService.getInstance()
        if (accessibility == null) {
            _quickTestOutput.value = "⚠️ Accessibility Service not active. Please enable it in Settings."
            return
        }
        val privacyShield = preferences.privacyShield.value
        val nodes = accessibility.getSanitizedNodes(privacyShield)
        _quickTestOutput.value = "Inspected ${nodes.size} UI elements on current screen:\n" +
                nodes.take(6).joinToString("\n") { it.toPromptString() }
    }

    fun clearDiagnosticOutput() {
        _quickTestOutput.value = null
    }

    // ── Settings navigation helpers ───────────────────────────────────────────

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }
}

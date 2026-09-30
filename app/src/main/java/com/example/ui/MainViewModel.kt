package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAgentRepository
import com.example.data.db.ActionLogEntity
import com.example.data.db.AppDatabase
import com.example.data.db.RoutineEntity
import com.example.data.prefs.AgentPreferences
import com.example.engine.AgentExecutionController
import com.example.engine.ScreenCaptureManager
import com.example.service.AgentAccessibilityService
import com.example.service.FloatingOverlayService
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
    val preferences = AgentPreferences(context)
    val database = AppDatabase.getDatabase(context)
    val agentController = AgentExecutionController.getInstance(context)
    private val repository = GeminiAgentRepository()

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

    fun refreshPermissions() {
        _permissions.value = checkPermissions()
    }

    private fun checkPermissions(): PermissionState {
        val hasOverlay = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        val hasAccessibility = AgentAccessibilityService.isServiceRunning()
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

    fun testGeminiConnection() {
        val apiKey = preferences.getEffectiveApiKey()
        val model = preferences.selectedModel.value

        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val result = repository.testConnection(apiKey, model)
            if (result.isSuccess) {
                _connectionTestState.value = ConnectionTestState.Success("Model connected: ${result.getOrNull()}")
            } else {
                _connectionTestState.value = ConnectionTestState.Error(result.exceptionOrNull()?.message ?: "Failed")
            }
        }
    }

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

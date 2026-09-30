package com.example.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.ai.AgentAction
import com.example.ai.GeminiAgentRepository
import com.example.data.db.ActionLogEntity
import com.example.data.db.AppDatabase
import com.example.data.prefs.AgentPreferences
import com.example.service.AgentAccessibilityService
import com.example.service.FloatingOverlayService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user", "agent", "system", "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HUDPointerEvent(
    val x: Int,
    val y: Int,
    val label: String? = null
)

sealed class AgentExecutionState {
    object Idle : AgentExecutionState()
    data class ReadingScreen(val step: Int) : AgentExecutionState()
    data class Thinking(val step: Int, val thought: String) : AgentExecutionState()
    data class Executing(val step: Int, val actionSummary: String, val x: Int? = null, val y: Int? = null) : AgentExecutionState()
    data class AwaitingConfirmation(val actionDescription: String) : AgentExecutionState()
    data class Completed(val message: String) : AgentExecutionState()
    data class Failed(val error: String) : AgentExecutionState()
}

class AgentExecutionController(
    private val context: Context,
    private val repository: GeminiAgentRepository,
    private val preferences: AgentPreferences,
    private val screenCaptureManager: ScreenCaptureManager
) {
    companion object {
        private const val TAG = "AgentController"
        private const val MAX_STEPS = 12

        @Volatile
        private var INSTANCE: AgentExecutionController? = null

        fun getInstance(context: Context): AgentExecutionController {
            return INSTANCE ?: synchronized(this) {
                val prefs = AgentPreferences(context)
                val repo = GeminiAgentRepository()
                val capture = ScreenCaptureManager(context)
                val inst = AgentExecutionController(context, repo, prefs, capture)
                INSTANCE = inst
                inst
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var currentExecutionJob: Job? = null

    private val _state = MutableStateFlow<AgentExecutionState>(AgentExecutionState.Idle)
    val state: StateFlow<AgentExecutionState> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageItem>>(emptyList())
    val messages: StateFlow<List<ChatMessageItem>> = _messages.asStateFlow()

    private val _hudEvents = MutableSharedFlow<HUDPointerEvent>(extraBufferCapacity = 8)
    val hudEvents: SharedFlow<HUDPointerEvent> = _hudEvents.asSharedFlow()

    private var pendingConfirmationContinuation: Continuation<Boolean>? = null

    private val database = AppDatabase.getDatabase(context)

    fun startGoal(goal: String) {
        if (goal.isBlank()) return
        stopCurrentExecution()

        addMessage("user", goal)

        currentExecutionJob = scope.launch {
            runAgentLoop(goal)
        }
    }

    fun stopCurrentExecution() {
        currentExecutionJob?.cancel()
        currentExecutionJob = null
        pendingConfirmationContinuation?.resume(false)
        pendingConfirmationContinuation = null
        screenCaptureManager.stopSession()
        _state.value = AgentExecutionState.Idle
    }

    fun confirmAction(approved: Boolean) {
        val cont = pendingConfirmationContinuation
        pendingConfirmationContinuation = null
        cont?.resume(approved)
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private suspend fun runAgentLoop(userGoal: String) {
        val apiKey = preferences.getEffectiveApiKey()
        if (apiKey.isBlank()) {
            _state.value = AgentExecutionState.Failed("API Key is missing. Please configure your Gemini API Key in Settings.")
            addMessage("system", "⚠️ Gemini API Key is missing. Please enter your key in Settings.")
            return
        }

        val accessibility = AgentAccessibilityService.getInstance()
        if (accessibility == null) {
            _state.value = AgentExecutionState.Failed("Accessibility Service is not enabled. Please enable it in Settings.")
            addMessage("system", "⚠️ Accessibility Service is not active. Enable it from System Settings.")
            return
        }

        val model = preferences.selectedModel.value
        val privacyShield = preferences.privacyShield.value
        val safetyGuardrails = preferences.safetyGuardrails.value

        val actionHistory = mutableListOf<String>()

        addMessage("agent", "Analyzing screen and planning steps for: \"$userGoal\"...")

        try {
            for (step in 1..MAX_STEPS) {
                _state.value = AgentExecutionState.ReadingScreen(step)

                // Step 1: Read sanitized DOM node hierarchy
                val sanitizedNodes = accessibility.getSanitizedNodes(privacyShield)
                val hierarchyText = accessibility.getHierarchyPromptText(privacyShield)

                // Step 2: Screen capture fallback if DOM tree is sparse or if MediaProjection is enabled
                var screenshotBitmap: Bitmap? = null
                val needsVisionFallback = sanitizedNodes.size < 5 || sanitizedNodes.none { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }
                if (FloatingOverlayService.isServiceActive.value && (needsVisionFallback || ScreenCaptureManager.hasProjectionPermission())) {
                    val rawBitmap = screenCaptureManager.captureScreen()
                    if (rawBitmap != null) {
                        val sensitiveBounds = if (privacyShield) {
                            sanitizedNodes.filter { it.isSensitive }.map { it.bounds }
                        } else emptyList<Rect>()
                        screenshotBitmap = PrivacyEngine.maskSensitiveRegions(rawBitmap, sensitiveBounds)
                    }
                }

                // Step 3: Ask Gemini
                _state.value = AgentExecutionState.Thinking(step, "Evaluating next UI action...")

                val geminiResult = repository.getNextAgentAction(
                    apiKey = apiKey,
                    model = model,
                    userGoal = userGoal,
                    screenHierarchyText = hierarchyText,
                    screenBitmap = screenshotBitmap,
                    actionHistory = actionHistory
                )

                if (geminiResult.isFailure) {
                    val err = geminiResult.exceptionOrNull()?.message ?: "Unknown AI reasoning error"
                    _state.value = AgentExecutionState.Failed(err)
                    addMessage("system", "❌ $err")
                    return
                }

                val stepResult = geminiResult.getOrThrow()
                if (!stepResult.thought.isNullOrBlank()) {
                    addMessage("agent", "Step $step: ${stepResult.thought}")
                }

                val action = stepResult.action
                if (action == null || stepResult.isFinished) {
                    val finalMsg = (action as? AgentAction.Finished)?.summaryMessage
                        ?: stepResult.thought
                        ?: "Goal completed."
                    _state.value = AgentExecutionState.Completed(finalMsg)
                    addMessage("agent", "✅ $finalMsg")
                    logAction("FINISHED", userGoal, "SUCCESS", finalMsg)
                    return
                }

                // Step 4: Check Safety Guardrails
                if (safetyGuardrails && requiresSafetyCheck(action)) {
                    val desc = getActionDescription(action)
                    _state.value = AgentExecutionState.AwaitingConfirmation(desc)
                    addMessage("system", "🔒 Sensitive Action Intercepted: $desc. Awaiting your approval...")

                    val approved = suspendCoroutine { cont ->
                        pendingConfirmationContinuation = cont
                    }

                    if (!approved) {
                        _state.value = AgentExecutionState.Idle
                        addMessage("system", "✋ Action cancelled by user.")
                        logAction("INTERCEPTED", desc, "CANCELLED", "User denied permission")
                        return
                    }
                    logAction("INTERCEPTED", desc, "CONFIRMED", "User approved")
                }

                // Step 5: Execute action
                val actionSummary = getActionDescription(action)
                actionHistory.add("Step $step: $actionSummary")

                val executionSuccess = executeAgentAction(action, accessibility, step)
                logAction(action.javaClass.simpleName, actionSummary, if (executionSuccess) "SUCCESS" else "FAILED")

                // Short delay for UI stabilization
                delay(800)
            }

            _state.value = AgentExecutionState.Completed("Reached maximum step limit (12). Task completed or paused.")
            addMessage("system", "Execution paused: Completed maximum sequence steps.")
        } finally {
            screenCaptureManager.stopSession()
        }
    }

    private fun requiresSafetyCheck(action: AgentAction): Boolean {
        return when (action) {
            is AgentAction.RequestConfirmation -> true
            is AgentAction.ClickElementByText -> PrivacyEngine.isActionSensitive(action.text)
            is AgentAction.TypeText -> PrivacyEngine.isActionSensitive(action.text)
            else -> false
        }
    }

    private fun getActionDescription(action: AgentAction): String {
        return when (action) {
            is AgentAction.OpenApp -> "Open app (${action.packageName})"
            is AgentAction.ClickElementByText -> "Click \"${action.text}\""
            is AgentAction.ClickAtCoordinates -> "Tap at screen (${action.x}, ${action.y})"
            is AgentAction.TypeText -> "Type \"${action.text}\""
            is AgentAction.Scroll -> "Scroll ${action.direction}"
            is AgentAction.PressBack -> "Press Back"
            is AgentAction.PressHome -> "Press Home"
            is AgentAction.RequestConfirmation -> action.actionDescription
            is AgentAction.ShowHUD -> "Highlight coordinate (${action.x}, ${action.y})"
            is AgentAction.SaveRoutineMacro -> "Save routine macro \"${action.title}\""
            is AgentAction.Finished -> "Complete: ${action.summaryMessage}"
            is AgentAction.Unknown -> "Unknown action: ${action.rawName}"
        }
    }

    private suspend fun executeAgentAction(
        action: AgentAction,
        accessibility: AgentAccessibilityService,
        step: Int
    ): Boolean {
        return try {
            when (action) {
                is AgentAction.OpenApp -> {
                    _state.value = AgentExecutionState.Executing(step, "Launching ${action.packageName}")
                    val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        triggerHapticFeedback()
                        true
                    } else {
                        addMessage("system", "App not found: ${action.packageName}")
                        false
                    }
                }

                is AgentAction.ClickElementByText -> {
                    _state.value = AgentExecutionState.Executing(step, "Clicking \"${action.text}\"")
                    val clicked = accessibility.clickElementByText(action.text)
                    triggerHapticFeedback()
                    clicked
                }

                is AgentAction.ClickAtCoordinates -> {
                    _state.value = AgentExecutionState.Executing(step, "Tapping at (${action.x}, ${action.y})", action.x, action.y)
                    _hudEvents.emit(HUDPointerEvent(action.x, action.y, "Tap"))
                    triggerHapticFeedback()
                    accessibility.clickCoordinates(action.x.toFloat(), action.y.toFloat())
                }

                is AgentAction.TypeText -> {
                    _state.value = AgentExecutionState.Executing(step, "Typing text into field")
                    accessibility.typeTextIntoField(action.text, action.elementTextOrId)
                }

                is AgentAction.Scroll -> {
                    _state.value = AgentExecutionState.Executing(step, "Scrolling ${action.direction}")
                    accessibility.scroll(action.direction)
                }

                is AgentAction.PressBack -> {
                    _state.value = AgentExecutionState.Executing(step, "Pressing Back")
                    accessibility.pressBack()
                }

                is AgentAction.PressHome -> {
                    _state.value = AgentExecutionState.Executing(step, "Returning to Home")
                    accessibility.pressHome()
                }

                is AgentAction.ShowHUD -> {
                    _hudEvents.emit(HUDPointerEvent(action.x, action.y, "Pointer"))
                    true
                }

                is AgentAction.SaveRoutineMacro -> {
                    database.routineDao().insertRoutine(
                        com.example.data.db.RoutineEntity(
                            title = action.title,
                            description = "Automated routine generated by OmniAgent",
                            promptGoal = action.title,
                            stepsJson = action.stepsJson,
                            iconKey = "bolt"
                        )
                    )
                    addMessage("system", "💾 Routine saved: \"${action.title}\"")
                    true
                }

                is AgentAction.Finished -> true
                is AgentAction.RequestConfirmation -> true
                is AgentAction.Unknown -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action: ${e.message}", e)
            false
        }
    }

    private fun addMessage(sender: String, content: String) {
        val list = _messages.value.toMutableList()
        list.add(ChatMessageItem(sender = sender, content = content))
        _messages.value = list
    }

    private fun triggerHapticFeedback() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(30)
            }
        } catch (e: Exception) {
            // Ignore vibration error on emulators
        }
    }

    private fun logAction(actionType: String, target: String, status: String, details: String = "") {
        scope.launch(Dispatchers.IO) {
            database.routineDao().insertLog(
                ActionLogEntity(
                    actionType = actionType,
                    targetDescription = target,
                    status = status,
                    details = details
                )
            )
        }
    }
}

package com.prodev.omniagent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.prodev.omniagent.engine.PrivacyEngine
import com.prodev.omniagent.engine.SanitizedNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentAccessibility"

        @Volatile
        private var instance: AgentAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun getInstance(): AgentAccessibilityService? = instance

        fun isServiceRunning(): Boolean = instance != null && _isConnected.value

        /**
         * Cross-checks the Android OS accessibility service registry to determine if
         * this service is actually enabled at the system level. This prevents the ghost
         * "Active" state that occurs when the app crashes without calling onDestroy(),
         * leaving the in-process [instance] reference stale.
         *
         * Use this together with [isServiceRunning] for a reliable dual-gate check.
         */
        fun isEnabledInSystem(context: Context): Boolean {
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val targetComponent = ComponentName(context, AgentAccessibilityService::class.java)
                enabledServices.split(":").any { entry ->
                    ComponentName.unflattenFromString(entry.trim()) == targetComponent
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read accessibility service system state: ${e.message}")
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(TAG, "OmniAgent Accessibility Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events can be monitored for window state transitions
    }

    override fun onInterrupt() {
        Log.w(TAG, "OmniAgent Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isConnected.value = false
        Log.i(TAG, "OmniAgent Accessibility Service destroyed.")
    }

    /**
     * Inspects current screen window hierarchy and returns sanitized node list.
     */
    fun getSanitizedNodes(privacyShieldEnabled: Boolean): List<SanitizedNode> {
        val root = rootInActiveWindow ?: return emptyList()
        return PrivacyEngine.sanitizeHierarchy(root, privacyShieldEnabled)
    }

    /**
     * Returns a structured text representation of the UI tree for Gemini LLM context.
     */
    fun getHierarchyPromptText(privacyShieldEnabled: Boolean): String {
        val nodes = getSanitizedNodes(privacyShieldEnabled)
        if (nodes.isEmpty()) {
            return "No accessible UI nodes found on current screen. Visual screenshot fallback recommended."
        }
        return buildString {
            nodes.forEach { node ->
                appendLine(node.toPromptString())
            }
        }
    }

    /**
     * Simulates a tap gesture at the specified screen coordinates using dispatchGesture.
     */
    suspend fun clickCoordinates(x: Float, y: Float): Boolean = suspendCoroutine { cont ->
        if (x < 0 || y < 0) {
            cont.resume(false)
            return@suspendCoroutine
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }, null)

        if (!dispatched) {
            cont.resume(false)
        }
    }

    /**
     * Searches for a node matching the provided text or description and clicks it.
     */
    suspend fun clickElementByText(targetText: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matchingNodes = mutableListOf<AccessibilityNodeInfo>()
        findNodesByTextRecursive(root, targetText.lowercase().trim(), matchingNodes)

            val target = matchingNodes.firstOrNull { it.isClickable }
                ?: matchingNodes.firstOrNull()

            if (target != null) {
                // If the node itself is clickable, trigger ACTION_CLICK
                var current: AccessibilityNodeInfo? = target
                while (current != null) {
                    if (current.isClickable) {
                        val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) return true
                    }
                    current = current.parent
                }

                // Fallback: tap at bounds center coordinates
                val bounds = Rect()
                target.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0) {
                    return clickCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
                }
            }
            return false
    }

    /**
     * Types text into a focused editable field, or searches for a field matching label/id.
     */
    fun typeTextIntoField(text: String, labelOrId: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false
        var targetField: AccessibilityNodeInfo? = null

        if (!labelOrId.isNullOrBlank()) {
            val matches = mutableListOf<AccessibilityNodeInfo>()
            findNodesByTextRecursive(root, labelOrId.lowercase(), matches)
            targetField = matches.firstOrNull { it.isEditable }
        }

        if (targetField == null) {
            // Look for currently focused editable element safely
            targetField = try {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } catch (t: Throwable) {
                null
            }
        }

        if (targetField == null) {
            // Find any editable node in window
            val editables = mutableListOf<AccessibilityNodeInfo>()
            findEditableNodesRecursive(root, editables)
            targetField = editables.firstOrNull()
        }

        if (targetField != null) {
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            return targetField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        return false
    }

    /**
     * Performs scroll or swipe in the given direction.
     */
    suspend fun scroll(direction: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (direction.uppercase()) {
            "UP" -> { // Swipe up moves view down
                startX = width * 0.5f
                startY = height * 0.75f
                endX = width * 0.5f
                endY = height * 0.25f
            }
            "DOWN" -> { // Swipe down moves view up
                startX = width * 0.5f
                startY = height * 0.25f
                endX = width * 0.5f
                endY = height * 0.75f
            }
            "LEFT" -> {
                startX = width * 0.8f
                startY = height * 0.5f
                endX = width * 0.2f
                endY = height * 0.5f
            }
            "RIGHT" -> {
                startX = width * 0.2f
                startY = height * 0.5f
                endX = width * 0.8f
                endY = height * 0.5f
            }
            else -> {
                startX = width * 0.5f
                startY = height * 0.7f
                endX = width * 0.5f
                endY = height * 0.3f
            }
        }

        return swipeCoordinates(startX, startY, endX, endY, 350)
    }

    suspend fun swipeCoordinates(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300
    ): Boolean = suspendCoroutine { cont ->
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                cont.resume(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                cont.resume(false)
            }
        }, null)

        if (!dispatched) cont.resume(false)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    private fun findNodesByTextRecursive(
        node: AccessibilityNodeInfo,
        query: String,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (text.contains(query) || desc.contains(query) || viewId.contains(query)) {
            outList.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByTextRecursive(child, query, outList)
        }
    }

    private fun findEditableNodesRecursive(
        node: AccessibilityNodeInfo,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isEditable) {
            outList.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodesRecursive(child, outList)
        }
    }
}

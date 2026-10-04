package com.prodev.omniagent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.prodev.omniagent.MainActivity
import com.prodev.omniagent.R
import com.prodev.omniagent.engine.AgentExecutionController
import com.prodev.omniagent.engine.AgentExecutionState
import com.prodev.omniagent.engine.ChatMessageItem
import com.prodev.omniagent.engine.HUDPointerEvent
import com.prodev.omniagent.engine.ScreenCaptureManager
import com.prodev.omniagent.engine.SpeechInputManager
import com.prodev.omniagent.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.prodev.omniagent.service.ACTION_START"
        const val ACTION_STOP = "com.prodev.omniagent.service.ACTION_STOP"
        const val ACTION_UPDATE_FGS_TYPE = "com.prodev.omniagent.service.ACTION_UPDATE_FGS_TYPE"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "omniagent_overlay_service_channel"
        private const val LONG_PRESS_THRESHOLD_MS = 500L

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateForegroundType(context: Context) {
            if (_isServiceActive.value) {
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    action = ACTION_UPDATE_FGS_TYPE
                }
                context.startService(intent)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null

    private var bubbleComposeView: ComposeView? = null
    private var chatComposeView: ComposeView? = null
    private var hudComposeView: ComposeView? = null

    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams
    private lateinit var chatLayoutParams: WindowManager.LayoutParams
    private lateinit var hudLayoutParams: WindowManager.LayoutParams

    private val isChatOpen = MutableStateFlow(false)
    private val activeHudPointer = MutableStateFlow<HUDPointerEvent?>(null)

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var agentController: AgentExecutionController
    private lateinit var speechManager: SpeechInputManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        agentController = AgentExecutionController.getInstance(this)
        speechManager = SpeechInputManager(this)

        serviceLifecycleOwner = ServiceLifecycleOwner().apply {
            start()
        }

        createNotificationChannel()
        setupOverlayViews()
        observeAgentEvents()
        _isServiceActive.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }

            // Only request mediaProjection FGS type if user has already granted projection token
            if (ScreenCaptureManager.hasProjectionPermission()) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }

            try {
                if (type != 0) {
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (se: SecurityException) {
                // If mediaProjection was rejected by OS, fall back to specialUse only
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechManager.stopListening()
        serviceLifecycleOwner?.destroy()
        removeViews()
        _isServiceActive.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniAgent AI Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent overlay notification for OmniAgent phone assistant"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniAgent AI is Active")
            .setContentText("Floating assistant bubble is running on top of your screen")
            .setSmallIcon(R.drawable.omni_agent_icon)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Agent", pendingStop)
            .setOngoing(true)
            .build()
    }

    private fun setupOverlayViews() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Draggable Bubble Layout Params
        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 300
        }

        // 2. Chat Panel Layout Params
        chatLayoutParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.65f).toInt(),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 3. HUD Pointer Layout Params
        hudLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val lifecycleOwner = serviceLifecycleOwner ?: return

        // Create Bubble View
        bubbleComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                MyApplicationTheme {
                    val state by agentController.state.collectAsState()
                    val chatOpen by isChatOpen.collectAsState()

                    FloatingBubbleView(
                        state = state,
                        isChatOpen = chatOpen,
                        onBubbleClick = {
                            toggleChatWindow()
                        },
                        onBubbleLongClick = {
                            // Reopen main dashboard
                            val intent = Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        }
                    )
                }
            }

            setupBubbleTouchListener(this)
        }

        // Create Chat Overlay View
        chatComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                MyApplicationTheme {
                    val state by agentController.state.collectAsState()
                    val messages by agentController.messages.collectAsState()
                    val isListening by speechManager.isListening.collectAsState()

                    FloatingChatWindow(
                        state = state,
                        messages = messages,
                        isListening = isListening,
                        onSendMessage = { text ->
                            agentController.startGoal(text)
                        },
                        onVoiceClick = {
                            if (isListening) {
                                speechManager.stopListening()
                            } else {
                                speechManager.startListening { spokenText ->
                                    agentController.startGoal(spokenText)
                                }
                            }
                        },
                        onConfirmAction = { approved ->
                            agentController.confirmAction(approved)
                        },
                        onEmergencyStop = {
                            agentController.stopCurrentExecution()
                        },
                        onClose = {
                            closeChatWindow()
                        }
                    )
                }
            }
        }

        // Create HUD Pointer Overlay View
        hudComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)

            setContent {
                MyApplicationTheme {
                    val pointer by activeHudPointer.collectAsState()
                    HUDTouchVisualizer(pointer = pointer)
                }
            }
        }

        // Add Bubble to Window
        windowManager.addView(bubbleComposeView, bubbleLayoutParams)
        // Add HUD overlay to Window
        windowManager.addView(hudComposeView, hudLayoutParams)
    }

    private fun setupBubbleTouchListener(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var pressStartTime = 0L

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleLayoutParams.x
                    initialY = bubbleLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    pressStartTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        bubbleLayoutParams.x = initialX + dx
                        bubbleLayoutParams.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(bubbleComposeView, bubbleLayoutParams)
                        } catch (e: Exception) {
                            // View might be detaching
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - pressStartTime
                    if (!isDragging) {
                        if (clickDuration < LONG_PRESS_THRESHOLD_MS) {
                            // Short tap -> toggle the chat panel
                            v.performClick()
                        } else {
                            // Long press -> reopen the main dashboard
                            val intent = Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun toggleChatWindow() {
        if (isChatOpen.value) {
            closeChatWindow()
        } else {
            openChatWindow()
        }
    }

    private fun openChatWindow() {
        if (!isChatOpen.value) {
            isChatOpen.value = true
            try {
                windowManager.addView(chatComposeView, chatLayoutParams)
            } catch (e: Exception) {
                // If already added
            }
        }
    }

    private fun closeChatWindow() {
        if (isChatOpen.value) {
            isChatOpen.value = false
            try {
                windowManager.removeView(chatComposeView)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun observeAgentEvents() {
        serviceScope.launch {
            agentController.hudEvents.collect { event ->
                activeHudPointer.value = event
                kotlinx.coroutines.delay(1200)
                if (activeHudPointer.value == event) {
                    activeHudPointer.value = null
                }
            }
        }

        // Auto-open chat overlay when user confirmation is required so they don't miss it
        serviceScope.launch {
            agentController.state.collect { state ->
                if (state is AgentExecutionState.AwaitingConfirmation) {
                    openChatWindow()
                }
            }
        }
    }

    private fun removeViews() {
        closeChatWindow()
        bubbleComposeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        hudComposeView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        bubbleComposeView = null
        hudComposeView = null
        chatComposeView = null
    }
}

// ==========================================
// Jetpack Compose Components for Overlay
// ==========================================

@Composable
fun FloatingBubbleView(
    state: AgentExecutionState,
    isChatOpen: Boolean,
    onBubbleClick: () -> Unit,
    onBubbleLongClick: () -> Unit
) {
    val statusText = when (state) {
        is AgentExecutionState.Idle -> "Idle"
        is AgentExecutionState.ReadingScreen -> "Reading #${state.step}"
        is AgentExecutionState.Thinking -> "Thinking..."
        is AgentExecutionState.Executing -> "Acting #${state.step}"
        is AgentExecutionState.AwaitingConfirmation -> "Approval Needed!"
        is AgentExecutionState.Completed -> "Done"
        is AgentExecutionState.Failed -> "Error"
    }

    val bubbleGlowColor = when (state) {
        is AgentExecutionState.AwaitingConfirmation -> Color(0xFFFF5252)
        is AgentExecutionState.Executing -> Color(0xFF00E5FF)
        is AgentExecutionState.Thinking -> Color(0xFF7C4DFF)
        is AgentExecutionState.ReadingScreen -> Color(0xFF00B0FF)
        is AgentExecutionState.Completed -> Color(0xFF00E676)
        is AgentExecutionState.Failed -> Color(0xFFFF1744)
        is AgentExecutionState.Idle -> Color(0xFF38BDF8)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        // Outer glow circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape, spotColor = bubbleGlowColor)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    ),
                    CircleShape
                )
                .border(2.dp, bubbleGlowColor, CircleShape)
                .clickable { onBubbleClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    is AgentExecutionState.AwaitingConfirmation -> Icons.Default.Warning
                    is AgentExecutionState.Thinking -> Icons.Default.Psychology
                    is AgentExecutionState.Executing -> Icons.Default.TouchApp
                    is AgentExecutionState.ReadingScreen -> Icons.Default.Visibility
                    is AgentExecutionState.Completed -> Icons.Default.CheckCircle
                    else -> Icons.Default.SmartToy
                },
                contentDescription = "OmniAgent Bubble",
                tint = bubbleGlowColor,
                modifier = Modifier.size(28.dp)
            )
        }

        // Compact Status pill badge
        if (state !is AgentExecutionState.Idle) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, bubbleGlowColor.copy(alpha = 0.6f)),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FloatingChatWindow(
    state: AgentExecutionState,
    messages: List<ChatMessageItem>,
    isListening: Boolean,
    onSendMessage: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onEmergencyStop: () -> Unit,
    onClose: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF80F172A)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF334155)),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (state is AgentExecutionState.Idle) Color(0xFF10B981) else Color(0xFF00E5FF),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OmniAgent AI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Emergency Stop button (Visible if running)
                    if (state !is AgentExecutionState.Idle) {
                        Button(
                            onClick = onEmergencyStop,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ABORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Overlay", tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Real-Time Thought Stream Bar
            if (state !is AgentExecutionState.Idle) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (state) {
                                is AgentExecutionState.ReadingScreen -> "Reading screen node tree (Step ${state.step})..."
                                is AgentExecutionState.Thinking -> state.thought
                                is AgentExecutionState.Executing -> "Action: ${state.actionSummary}"
                                is AgentExecutionState.AwaitingConfirmation -> "⚠️ Sensitive action pending your approval!"
                                else -> "Processing..."
                            },
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Floating Confirmation Dialog Banner (Safety Guardrails)
            if (state is AgentExecutionState.AwaitingConfirmation) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = "Safety", tint = Color(0xFFFCA5A5), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Safety Guardrail Intercept", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI wants to perform: ${state.actionDescription}",
                            color = Color(0xFFFEE2E2),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onConfirmAction(false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Deny", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onConfirmAction(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Conversation history list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Text(
                                text = "What smartphone task would you like OmniAgent to perform?",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(msg)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Row with Voice Microphone
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask OmniAgent...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Voice mic button
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isListening) Color(0xFFEF4444) else Color(0xFF1E293B),
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (isListening) Color(0xFFFCA5A5) else Color(0xFF334155),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isListening) Color.White else Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Send button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && state is AgentExecutionState.Idle,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (inputText.isNotBlank()) Color(0xFF0284C7) else Color(0xFF1E293B),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessageItem) {
    val isUser = msg.sender == "user"
    val isSystem = msg.sender == "system"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = when {
                isUser -> Color(0xFF0284C7)
                isSystem -> Color(0xFF1E293B)
                else -> Color(0xFF334155)
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = msg.content,
                color = Color.White,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun HUDTouchVisualizer(pointer: HUDPointerEvent?) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = pointer != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            pointer?.let { p ->
                val density = androidx.compose.ui.platform.LocalDensity.current
                val xDp = with(density) { p.x.toDp() }
                val yDp = with(density) { p.y.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = xDp - 28.dp, y = yDp - 28.dp)
                        .size(56.dp)
                ) {
                    // Outer pulsating ripple ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.8f), CircleShape)
                    )
                    // Inner glowing dot
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center)
                            .background(Color(0xFF00E5FF), CircleShape)
                            .shadow(8.dp, CircleShape, spotColor = Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}

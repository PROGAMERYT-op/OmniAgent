package com.prodev.omniagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val highlightBadge: String
)

@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Welcome to OmniAgent AI",
                description = "OmniAgent is your autonomous smartphone assistant that stays as a compact floating bubble on top of any app and executes multi-step tasks across your device.",
                icon = Icons.Default.SmartToy,
                highlightBadge = "Autonomous Agent"
            ),
            OnboardingStep(
                title = "Hybrid UI Parsing Engine",
                description = "OmniAgent reads Android Accessibility node hierarchy for instantaneous element interaction, and switches to Gemini Multimodal Vision when inspecting custom graphics and canvas views.",
                icon = Icons.Default.Visibility,
                highlightBadge = "DOM + Vision"
            ),
            OnboardingStep(
                title = "Privacy Shield & Sensitive Masking",
                description = "Your passwords, PINs, and financial data are sanitized directly on your device. Any screenshots transmitted for AI vision are blacked out over sensitive credential fields.",
                icon = Icons.Default.Shield,
                highlightBadge = "Zero-Trust Privacy"
            ),
            OnboardingStep(
                title = "Safety Guardrails & Macro Hub",
                description = "Sensitive actions (like purchases or deletions) trigger an explicit floating approval dialog. Save recurring multi-step workflows as 1-tap macro shortcuts on your dashboard.",
                icon = Icons.Default.Bolt,
                highlightBadge = "Guardrails & Macros"
            )
        )
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = steps[currentStepIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
                .testTag("onboarding_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Badge
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = step.highlightBadge,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Hero Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = step.title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = step.description,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Step dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentStepIndex) 10.dp else 6.dp)
                                .background(
                                    if (index == currentStepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        TextButton(
                            onClick = { currentStepIndex-- },
                            modifier = Modifier.testTag("onboarding_prev_button")
                        ) {
                            Text("Previous")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Skip")
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("onboarding_next_button")
                    ) {
                        Text(
                            text = if (currentStepIndex == steps.size - 1) "Get Started" else "Next",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

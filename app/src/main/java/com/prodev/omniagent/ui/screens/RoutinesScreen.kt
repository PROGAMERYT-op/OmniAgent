package com.prodev.omniagent.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prodev.omniagent.data.db.RoutineEntity
import com.prodev.omniagent.ui.MainViewModel

@Composable
fun RoutinesScreen(
    viewModel: MainViewModel
) {
    val routines by viewModel.routines.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Routine", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF0F172A),
                modifier = Modifier.testTag("add_routine_button")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Macro Routines Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Save multi-step AI agent workflows into 1-tap automation triggers. OmniAgent will inspect your screen and run them autonomously.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (routines.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No saved routines yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tap 'New Routine' to create your first smartphone macro.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(routines, key = { it.id }) { routine ->
                    RoutineDetailCard(
                        routine = routine,
                        onRun = { viewModel.runRoutine(routine) },
                        onDelete = { viewModel.deleteRoutine(routine) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoutineDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, goal, iconKey ->
                viewModel.addRoutine(title, desc, goal, iconKey)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun RoutineDetailCard(
    routine: RoutineEntity,
    onRun: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine_card_${routine.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (routine.iconKey) {
                                "music" -> Icons.Default.MusicNote
                                "alarm" -> Icons.Default.Alarm
                                "note" -> Icons.Default.EditNote
                                else -> Icons.Default.Bolt
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(routine.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(routine.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("AI Goal Instruction:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        routine.promptGoal,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRun,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("run_routine_btn_${routine.id}")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Execute Macro", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, goal: String, iconKey: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("bolt") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Automation Routine", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Title") },
                    placeholder = { Text("e.g. Check Reddit & Twitter") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_routine_title")
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Short Description") },
                    placeholder = { Text("e.g. Opens social apps sequentially") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_routine_desc")
                )

                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Agent Goal / Prompt") },
                    placeholder = { Text("e.g. Open YouTube, search for relaxing rain sounds, and play first video") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("new_routine_goal")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Icon:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("bolt", "music", "alarm", "note").forEach { iconKey ->
                        FilterChip(
                            selected = selectedIcon == iconKey,
                            onClick = { selectedIcon = iconKey },
                            label = { Text(iconKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && goal.isNotBlank()) {
                        onSave(title, desc.ifBlank { title }, goal, selectedIcon)
                    }
                },
                enabled = title.isNotBlank() && goal.isNotBlank(),
                modifier = Modifier.testTag("save_routine_confirm_btn")
            ) {
                Text("Save Routine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

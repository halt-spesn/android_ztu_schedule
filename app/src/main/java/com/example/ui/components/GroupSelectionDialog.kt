package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScheduleGroup

@Composable
fun GroupSelectionDialog(
    currentGroupId: String,
    groups: List<ScheduleGroup>,
    isLoadingGroups: Boolean,
    isOledMode: Boolean = false,
    onGroupSelected: (groupId: String, groupName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var customIdInput by remember { mutableStateOf("") }

    val isDarkTheme = isSystemInDarkTheme()
    val isOledActive = isOledMode && isDarkTheme

    val filteredGroups = remember(searchQuery, groups) {
        if (searchQuery.isBlank()) {
            groups
        } else {
            val query = searchQuery.trim().lowercase()
            groups.filter {
                it.name.lowercase().contains(query) || it.id.contains(query)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = if (isOledActive) Color.Black else AlertDialogDefaults.containerColor,
        modifier = if (isOledActive) Modifier.border(1.dp, Color(0xFF222222), RoundedCornerShape(28.dp)) else Modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Вибір групи ЖТУ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Оберіть академічну групу або вкажіть ID (за замовчуванням 612 — КІ-26-1):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_search_input"),
                    placeholder = { Text("Пошук (наприклад КІ-26-1 або 612)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Очистити")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = if (isOledActive) {
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingGroups && groups.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Завантаження списку груп ЖТУ...", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(filteredGroups) { group ->
                            val isCurrent = group.id == currentGroupId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onGroupSelected(group.id, group.name)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = group.name,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "ID: ${group.id}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Обрано",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }

                        if (filteredGroups.isEmpty()) {
                            item {
                                Text(
                                    text = "Групу не знайдено за запитом",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom ID / URL input option
                OutlinedTextField(
                    value = customIdInput,
                    onValueChange = { customIdInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Вказати інший ID або посилання") },
                    placeholder = { Text("Наприклад: 612 або https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = if (isOledActive) {
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F0F0F),
                            unfocusedContainerColor = Color(0xFF0F0F0F)
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors()
                    }
                )
            }
        },
        confirmButton = {
            if (customIdInput.isNotBlank()) {
                Button(
                    onClick = {
                        // Extract ID if URL was pasted
                        val raw = customIdInput.trim()
                        val id = if (raw.contains("id=")) {
                            raw.substringAfter("id=").substringBefore("&")
                        } else {
                            raw
                        }
                        if (id.isNotBlank()) {
                            onGroupSelected(id, "Група $id")
                            onDismiss()
                        }
                    }
                ) {
                    Text("Застосувати ID")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Закрити")
                }
            }
        },
        dismissButton = {
            if (customIdInput.isNotBlank()) {
                TextButton(onClick = onDismiss) {
                    Text("Скасувати")
                }
            }
        }
    )
}

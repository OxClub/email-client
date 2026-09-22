package com.example.emailclient.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.emailclient.data.Account
import com.example.emailclient.data.EmailMessage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    accounts: List<Account>,
    selectedAccountId: Long?,
    messages: List<EmailMessage>,
    isSyncing: Boolean,
    onSelectAccount: (Long) -> Unit,
    onRefresh: () -> Unit,
    onOpenMessage: (EmailMessage) -> Unit,
    onToggleStar: (EmailMessage) -> Unit,
    onCompose: () -> Unit,
    onAddAccount: () -> Unit
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val currentAccount = accounts.find { it.id == selectedAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { accountMenuExpanded = true }) {
                            Text(
                                currentAccount?.emailAddress ?: "Inbox",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.emailAddress) }, onClick = {
                                    onSelectAccount(acc.id)
                                    accountMenuExpanded = false
                                })
                            }
                            Divider()
                            DropdownMenuItem(text = { Text("Add account") }, onClick = {
                                accountMenuExpanded = false
                                onAddAccount()
                            })
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isSyncing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCompose) {
                Icon(Icons.Default.Create, contentDescription = "Compose")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (isSyncing) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (messages.isEmpty() && !isSyncing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No messages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn {
                    items(messages, key = { it.localId }) { message ->
                        MessageRow(message, onOpenMessage, onToggleStar)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(message: EmailMessage, onOpen: (EmailMessage) -> Unit, onToggleStar: (EmailMessage) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(message) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarColor(message.fromName)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                message.fromName.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                message.fromName,
                fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message.subject,
                fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                message.bodyPreview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTimestamp(message.dateEpochMillis), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { onToggleStar(message) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Star",
                    tint = if (message.isStarred) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun avatarColor(name: String): Color {
    val palette = listOf(
        Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A),
        Color(0xFFAB47BC), Color(0xFFFFA726), Color(0xFF26A69A)
    )
    val idx = (name.hashCode().let { if (it < 0) -it else it }) % palette.size
    return palette[idx]
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    return fmt.format(Date(epochMillis))
}


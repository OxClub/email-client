package com.example.emailclient.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.emailclient.data.EmailMessage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailDetailScreen(
    message: EmailMessage,
    isLoadingBody: Boolean,
    onBack: () -> Unit,
    onReply: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onReply) { Icon(Icons.Default.Reply, contentDescription = "Reply") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(message.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(message.fromName, fontWeight = FontWeight.Medium)
                    Text(message.fromAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(message.dateEpochMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("To: ${message.toAddresses}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Divider(Modifier.padding(vertical = 16.dp))

            if (isLoadingBody) {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Text(
                    message.bodyPlainText.ifBlank { message.bodyPreview },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

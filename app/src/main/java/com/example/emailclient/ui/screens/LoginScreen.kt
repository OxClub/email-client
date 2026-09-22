package com.example.emailclient.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.emailclient.data.Account
import com.example.emailclient.data.ProviderPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    busy: Boolean,
    onAddAccount: (Account, String, (Result<Unit>) -> Unit) -> Unit,
    onDone: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf(ProviderPresets.presets.first()) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var imapHost by remember { mutableStateOf(selectedPreset.imapHost) }
    var imapPort by remember { mutableStateOf(selectedPreset.imapPort.toString()) }
    var smtpHost by remember { mutableStateOf(selectedPreset.smtpHost) }
    var smtpPort by remember { mutableStateOf(selectedPreset.smtpPort.toString()) }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Add email account", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Use an app password if your provider requires one (Gmail, Yahoo, iCloud all do when 2FA is on).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
            OutlinedTextField(
                value = selectedPreset.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                ProviderPresets.presets.forEach { preset ->
                    DropdownMenuItem(text = { Text(preset.label) }, onClick = {
                        selectedPreset = preset
                        imapHost = preset.imapHost
                        imapPort = preset.imapPort.toString()
                        smtpHost = preset.smtpHost
                        smtpPort = preset.smtpPort.toString()
                        menuExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(displayName, { displayName = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            email, { email = it; if (username.isBlank()) username = it },
            label = { Text("Email address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            password, { password = it },
            label = { Text("Password (or app password)") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(20.dp))
        Text("Server settings", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(imapHost, { imapHost = it }, label = { Text("IMAP host") }, modifier = Modifier.weight(2f))
            OutlinedTextField(
                imapPort, { imapPort = it.filter(Char::isDigit) },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(smtpHost, { smtpHost = it }, label = { Text("SMTP host") }, modifier = Modifier.weight(2f))
            OutlinedTextField(
                smtpPort, { smtpPort = it.filter(Char::isDigit) },
                label = { Text("Port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        errorText?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank() && imapHost.isNotBlank() && smtpHost.isNotBlank(),
            onClick = {
                errorText = null
                val account = Account(
                    displayName = displayName.ifBlank { email },
                    emailAddress = email,
                    imapHost = imapHost,
                    imapPort = imapPort.toIntOrNull() ?: 993,
                    smtpHost = smtpHost,
                    smtpPort = smtpPort.toIntOrNull() ?: 587,
                    username = username.ifBlank { email }
                )
                onAddAccount(account, password) { result ->
                    result.onSuccess { onDone() }
                    result.onFailure { errorText = "Couldn't sign in: ${it.message}" }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Sign in")
            }
        }
    }
}

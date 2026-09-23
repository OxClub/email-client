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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.emailclient.data.Account
import com.example.emailclient.data.ProviderPresets

/**
 * Sign-in screen. Only asks for name, email, and password — IMAP/SMTP host
 * and port are looked up automatically from the email domain
 * (ProviderPresets.forEmail / guessFromDomain), so there's no server
 * settings step for the common case. An "Advanced" section is still
 * available, collapsed by default, for the rare case where the auto-guess
 * is wrong (e.g. a company mail server on a nonstandard hostname).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    busy: Boolean,
    onAddAccount: (Account, String, (Result<Unit>) -> Unit) -> Unit,
    onGoogleSignIn: () -> Unit,
    onDone: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var advancedExpanded by remember { mutableStateOf(false) }

    // Auto-detected (or guessed) server settings, recalculated whenever the
    // email address changes. Shown read-only unless the user expands
    // "Advanced" to override them.
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("587") }
    var userEditedServerSettings by remember { mutableStateOf(false) }

    LaunchedEffect(email) {
        if (!userEditedServerSettings && email.contains("@")) {
            val preset = ProviderPresets.forEmail(email) ?: ProviderPresets.guessFromDomain(email)
            imapHost = preset.imapHost
            imapPort = preset.imapPort.toString()
            smtpHost = preset.smtpHost
            smtpPort = preset.smtpPort.toString()
        }
    }

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

        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Continue with Google")
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Divider(Modifier.weight(1f))
            Text(
                "  or use email + password  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            displayName, { displayName = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            email, { email = it },
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

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
            Text(if (advancedExpanded) "Hide advanced settings" else "Advanced (server settings)")
        }
        if (advancedExpanded) {
            Text(
                "Auto-filled from your email address. Only change these if sign-in fails and you know your provider's server details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    imapHost, { imapHost = it; userEditedServerSettings = true },
                    label = { Text("IMAP host") }, modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    imapPort, { imapPort = it.filter(Char::isDigit); userEditedServerSettings = true },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    smtpHost, { smtpHost = it; userEditedServerSettings = true },
                    label = { Text("SMTP host") }, modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    smtpPort, { smtpPort = it.filter(Char::isDigit); userEditedServerSettings = true },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
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
                    username = email
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

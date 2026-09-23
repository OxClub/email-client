package com.example.emailclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.emailclient.data.EmailMessage
import com.example.emailclient.oauth.GoogleAuthManager
import com.example.emailclient.ui.EmailViewModel
import com.example.emailclient.ui.INBOX
import com.example.emailclient.ui.screens.ComposeScreen
import com.example.emailclient.ui.screens.EmailDetailScreen
import com.example.emailclient.ui.screens.InboxScreen
import com.example.emailclient.ui.screens.LoginScreen
import com.example.emailclient.ui.theme.EmailClientTheme
import kotlinx.coroutines.launch

private sealed interface Screen {
    object Login : Screen
    object Inbox : Screen
    data class Detail(val message: EmailMessage) : Screen
    data class Compose(val replyTo: EmailMessage? = null) : Screen
}

class MainActivity : ComponentActivity() {
    private val viewModel: EmailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EmailClientTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: EmailViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val loginBusy by viewModel.loginBusy.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
    var sending by remember { mutableStateOf(false) }
    var loadingBody by remember { mutableStateOf(false) }
    var googleSignInError by remember { mutableStateOf<String?>(null) }

    // Handles the result of the Google sign-in browser screen: exchanges
    // the returned authorization code for tokens, then saves the account.
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data == null) {
            googleSignInError = "Sign-in was cancelled"
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val outcome = GoogleAuthManager.handleSignInResult(context, data)
            outcome.onSuccess { (authStateJson, email) ->
                viewModel.addGoogleAccount(email, authStateJson) { addResult ->
                    addResult.onSuccess { screen = Screen.Inbox }
                    addResult.onFailure { googleSignInError = it.message ?: "Couldn't add account" }
                }
            }
            outcome.onFailure { googleSignInError = it.message ?: "Google sign-in failed" }
        }
    }

    // Once an account exists, default to Inbox.
    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty() && screen is Screen.Login) screen = Screen.Inbox
    }

    val messages by (selectedAccountId?.let { viewModel.messagesFor(it, INBOX) }
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())

    errorMessage?.let {
        LaunchedEffect(it) {
            // In a full app this would show a Snackbar; kept minimal here.
            viewModel.clearError()
        }
    }

    when (val current = screen) {
        is Screen.Login -> LoginScreen(
            busy = loginBusy,
            onAddAccount = { account, password, callback -> viewModel.addAccount(account, password, callback) },
            onGoogleSignIn = {
                googleSignInError = null
                googleSignInLauncher.launch(GoogleAuthManager.buildSignInIntent(context))
            },
            onDone = { screen = Screen.Inbox }
        )

        is Screen.Inbox -> InboxScreen(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            messages = messages,
            isSyncing = isSyncing,
            onSelectAccount = { viewModel.selectAccount(it) },
            onRefresh = { viewModel.syncSelected() },
            onOpenMessage = { message ->
                loadingBody = true
                screen = Screen.Detail(message)
                viewModel.openMessage(message) { loaded ->
                    loadingBody = false
                    screen = Screen.Detail(loaded)
                }
            },
            onToggleStar = { viewModel.toggleStar(it) },
            onCompose = { screen = Screen.Compose() },
            onAddAccount = { screen = Screen.Login }
        )

        is Screen.Detail -> EmailDetailScreen(
            message = current.message,
            isLoadingBody = loadingBody,
            onBack = { screen = Screen.Inbox },
            onReply = { screen = Screen.Compose(replyTo = current.message) }
        )

        is Screen.Compose -> ComposeScreen(
            initialTo = current.replyTo?.fromAddress ?: "",
            initialSubject = current.replyTo?.let { "Re: ${it.subject}" } ?: "",
            initialBody = current.replyTo?.let { "\n\n---\nOn ${it.fromName} wrote:\n${it.bodyPlainText}" } ?: "",
            sending = sending,
            onSend = { to, subject, body ->
                val accountId = selectedAccountId ?: return@ComposeScreen
                sending = true
                viewModel.sendMessage(
                    accountId = accountId,
                    to = to.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() },
                    cc = emptyList(),
                    subject = subject,
                    body = body,
                    inReplyToMessageId = current.replyTo?.messageId
                ) { result ->
                    sending = false
                    if (result.isSuccess) screen = Screen.Inbox
                }
            },
            onCancel = { screen = Screen.Inbox }
        )
    }
}

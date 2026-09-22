package com.example.emailclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.emailclient.data.Account
import com.example.emailclient.data.EmailMessage
import com.example.emailclient.data.EmailRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

const val INBOX = "INBOX"

sealed interface UiState {
    object Loading : UiState
    object NoAccounts : UiState
    data class Ready(val accounts: List<Account>) : UiState
}

class EmailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = EmailRepository(app)

    val accounts: StateFlow<List<Account>> =
        repo.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy

    init {
        viewModelScope.launch {
            accounts.collect { list ->
                if (_selectedAccountId.value == null && list.isNotEmpty()) {
                    _selectedAccountId.value = list.first().id
                    syncSelected()
                }
            }
        }
    }

    fun selectAccount(id: Long) {
        _selectedAccountId.value = id
        syncSelected()
    }

    fun messagesFor(accountId: Long, folder: String = INBOX): Flow<List<EmailMessage>> =
        repo.observeFolder(accountId, folder)

    fun syncSelected(folder: String = INBOX) {
        val account = accounts.value.find { it.id == _selectedAccountId.value } ?: return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repo.syncFolder(account, folder)
            result.onFailure { _errorMessage.value = it.message ?: "Sync failed" }
            _isSyncing.value = false
        }
    }

    fun addAccount(account: Account, password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _loginBusy.value = true
            val test = repo.testLogin(account, password)
            if (test.isSuccess) {
                val id = repo.addAccount(account, password)
                _selectedAccountId.value = id
                syncSelected()
            }
            _loginBusy.value = false
            onResult(test)
        }
    }

    fun removeAccount(account: Account) {
        viewModelScope.launch {
            repo.removeAccount(account)
            if (_selectedAccountId.value == account.id) _selectedAccountId.value = null
        }
    }

    fun openMessage(message: EmailMessage, onLoaded: (EmailMessage) -> Unit) {
        val account = accounts.value.find { it.id == message.accountId } ?: return
        viewModelScope.launch {
            val result = repo.openMessage(account, message)
            result.onSuccess { onLoaded(it) }
            result.onFailure { _errorMessage.value = it.message ?: "Couldn't load message" }
        }
    }

    fun toggleStar(message: EmailMessage) {
        viewModelScope.launch { repo.toggleStar(message) }
    }

    fun sendMessage(
        accountId: Long,
        to: List<String>,
        cc: List<String>,
        subject: String,
        body: String,
        inReplyToMessageId: String? = null,
        onResult: (Result<Unit>) -> Unit
    ) {
        val account = accounts.value.find { it.id == accountId } ?: return
        viewModelScope.launch {
            val result = repo.sendMessage(account, to, cc, subject, body, inReplyToMessageId)
            onResult(result)
        }
    }

    fun clearError() { _errorMessage.value = null }
}

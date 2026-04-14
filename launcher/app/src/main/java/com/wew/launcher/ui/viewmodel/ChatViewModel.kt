package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.sms.SmsMessage
import com.wew.launcher.sms.SmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── UI state ─────────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages: List<SmsMessage> = emptyList(),
    val isLoading: Boolean = true,
    val inputText: String = "",
    val isSending: Boolean = false,
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val tokensExhausted: Boolean = false,
    val recipientName: String = "",
    val recipientAddress: String = ""
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChatViewModel(
    application: Application,
    initialThreadId: Long,
    private val recipientAddress: String,
    recipientName: String
) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val smsRepo = SmsRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    /** Mutable so we can update after sending the first message of a new thread. */
    private var currentThreadId: Long = initialThreadId

    private val _uiState = MutableStateFlow(
        ChatUiState(recipientName = recipientName, recipientAddress = recipientAddress)
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        load()
        observeLive()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    fun load() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                val messages = if (currentThreadId != -1L)
                    smsRepo.getMessages(currentThreadId)
                else
                    emptyList()
                val device = repo.getDevice(deviceId)
                _uiState.update {
                    it.copy(
                        messages = messages,
                        isLoading = false,
                        currentTokens = device.currentTokens,
                        dailyTokenBudget = device.dailyTokenBudget,
                        tokensExhausted = device.currentTokens <= 0
                    )
                }
            }.onFailure { e ->
                Log.e("ChatVM", "load failed: ${e.message}")
                val messages = if (currentThreadId != -1L)
                    runCatching { smsRepo.getMessages(currentThreadId) }.getOrElse { emptyList() }
                else
                    emptyList()
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    // ── Live updates ──────────────────────────────────────────────────────────

    private fun observeLive() {
        viewModelScope.launch {
            smsRepo.observeSmsChanges().collect {
                if (currentThreadId == -1L) return@collect
                runCatching {
                    val messages = smsRepo.getMessages(currentThreadId)
                    _uiState.update { s -> s.copy(messages = messages) }
                }.onFailure { Log.w("ChatVM", "live refresh failed: ${it.message}") }
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isEmpty() || state.isSending || state.tokensExhausted) return
        val deviceId = prefs.getString("device_id", null) ?: return

        _uiState.update { it.copy(isSending = true, inputText = "") }

        viewModelScope.launch {
            // Deduct tokens before sending — if the balance is insufficient, abort.
            val tokenResult = repo.consumeTokens(
                deviceId = deviceId,
                amount = 10,
                actionType = ActionType.SMS_SENT.value,
                appPackage = null,
                appName = "Messages"
            )
            if (tokenResult.isFailure) {
                _uiState.update { it.copy(isSending = false, inputText = text, tokensExhausted = true) }
                return@launch
            }

            smsRepo.sendSms(to = recipientAddress, body = text)

            // Resolve real thread ID if this was a new conversation
            if (currentThreadId == -1L) {
                currentThreadId = smsRepo.getThreadIdForAddress(recipientAddress)
            }

            // Reload messages + refresh token balance
            runCatching {
                val messages = if (currentThreadId != -1L)
                    smsRepo.getMessages(currentThreadId)
                else
                    emptyList()
                val device = repo.getDevice(deviceId)
                _uiState.update {
                    it.copy(
                        messages = messages,
                        isSending = false,
                        currentTokens = device.currentTokens,
                        tokensExhausted = device.currentTokens <= 0
                    )
                }
            }.onFailure {
                _uiState.update { s -> s.copy(isSending = false) }
            }
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(
            app: Application,
            threadId: Long,
            recipientAddress: String,
            recipientName: String
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(app, threadId, recipientAddress, recipientName) as T
        }
    }
}

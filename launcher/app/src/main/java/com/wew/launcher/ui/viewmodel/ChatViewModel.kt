package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.composedDisplayName
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.sms.MessagingCapability
import com.wew.launcher.sms.SmsDirection
import com.wew.launcher.sms.SmsMessage
import com.wew.launcher.sms.SmsRepository
import com.wew.launcher.sms.smsSendPreconditionMessage
import com.wew.launcher.sms.userMessageForSmsSendFailure
import com.wew.launcher.token.TokenEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// ── Chat rows (local SMS + server system summaries) ───────────────────────────

sealed interface ChatBubbleItem {
    val sortKey: Long
    val stableKey: String

    data class Local(val message: SmsMessage) : ChatBubbleItem {
        override val sortKey: Long get() = message.date
        override val stableKey: String get() = "sms-${message.id}"
    }

    data class System(
        val id: String,
        val body: String,
        val createdAtMs: Long,
        val messageType: String
    ) : ChatBubbleItem {
        override val sortKey: Long get() = createdAtMs
        override val stableKey: String get() = "sys-$id"
    }
}

// ── UI state ─────────────────────────────────────────────────────────────────

data class ChatUiState(
    val chatItems: List<ChatBubbleItem> = emptyList(),
    val isLoading: Boolean = true,
    val inputText: String = "",
    val isSending: Boolean = false,
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val tokensExhausted: Boolean = false,
    val recipientName: String = "",
    val recipientAddress: String = "",
    val selectedRecipients: List<com.wew.launcher.data.model.WewContact> = emptyList(),
    /** Show call-confirmation dialog. */
    val showCallConfirm: Boolean = false,
    /** Set to the address to dial; UI observes and launches the intent, then clears. */
    val pendingCall: String? = null,
    /** Content URI of an image the user has staged to attach to the next message. */
    val attachedImageUri: String? = null,
    /** MIME type of [attachedImageUri], resolved at attach time. */
    val attachedImageMime: String = "image/jpeg",
    /** URI to display in the full-screen image viewer; null = viewer closed. */
    val fullScreenImageUri: String? = null,
    /** Shown above the composer when sending fails or setup is incomplete. */
    val sendError: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChatViewModel(
    application: Application,
    initialThreadId: Long,
    private val recipientAddress: String,
    recipientName: String,
    private val mergeSystemSummaries: Boolean,
    initialRecipients: List<com.wew.launcher.data.model.WewContact> = emptyList()
) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val smsRepo = SmsRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    /** Mutable so we can update after sending the first message of a new thread. */
    private var currentThreadId: Long = initialThreadId

    private val composeMode: Boolean = initialThreadId == -1L

    private var recipientBindJob: Job? = null

    private val _uiState = MutableStateFlow(
        ChatUiState(
            recipientName = when {
                initialRecipients.size == 1 ->
                    initialRecipients.first().composedDisplayName().ifBlank { recipientName }
                initialRecipients.size > 1 -> "${initialRecipients.size} people"
                else -> recipientName
            },
            recipientAddress = when {
                initialRecipients.size == 1 ->
                    initialRecipients.first().phone?.trim().orEmpty()
                        .ifEmpty { recipientAddress }
                else -> recipientAddress
            },
            selectedRecipients = initialRecipients
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        load()
        observeLive()
    }

    override fun onCleared() {
        recipientBindJob?.cancel()
        super.onCleared()
    }

    private fun parseCreatedAt(iso: String): Long {
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            System.currentTimeMillis()
        }
    }

    private suspend fun buildChatItems(deviceId: String): List<ChatBubbleItem> {
        val sms = if (currentThreadId != -1L) {
            smsRepo.getMessages(currentThreadId)
        } else {
            emptyList()
        }
        val systemRows = if (mergeSystemSummaries && currentThreadId != -1L) {
            repo.getSystemMessagesForThread(deviceId, currentThreadId)
        } else {
            emptyList()
        }
        val systemItems = systemRows.mapNotNull { row ->
            val body = row.body?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ChatBubbleItem.System(
                id = row.id,
                body = body,
                createdAtMs = parseCreatedAt(row.createdAt),
                messageType = row.messageType
            )
        }
        val localItems = sms.map { ChatBubbleItem.Local(it) }
        return (localItems + systemItems).sortedBy { it.sortKey }
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    fun load() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                val items = buildChatItems(deviceId)
                val device = repo.getDevice(deviceId)
                _uiState.update {
                    it.copy(
                        chatItems = items,
                        isLoading = false,
                        currentTokens = device.currentTokens,
                        dailyTokenBudget = device.dailyTokenBudget,
                        tokensExhausted = device.currentTokens <= 0
                    )
                }
            }.onFailure { e ->
                Log.e("ChatVM", "load failed: ${e.message}")
                _uiState.update { it.copy(chatItems = emptyList(), isLoading = false) }
            }
        }
    }

    // ── Live updates ──────────────────────────────────────────────────────────

    private fun observeLive() {
        viewModelScope.launch {
            smsRepo.observeSmsChanges().collect {
                val deviceId = prefs.getString("device_id", null) ?: return@collect
                if (currentThreadId == -1L) {
                    if (!composeMode) return@collect
                    val phone = _uiState.value.selectedRecipients.firstOrNull()?.phone?.trim().orEmpty()
                        .ifEmpty { _uiState.value.recipientAddress.trim() }
                    if (phone.isEmpty()) return@collect
                    runCatching {
                        val tid = smsRepo.resolveThreadIdForContact(phone)
                        if (tid != -1L) {
                            currentThreadId = tid
                            val items = buildChatItems(deviceId)
                            _uiState.update { s -> s.copy(chatItems = items) }
                        }
                    }.onFailure { Log.w("ChatVM", "compose live bind failed: ${it.message}") }
                    return@collect
                }
                runCatching {
                    val items = buildChatItems(deviceId)
                    _uiState.update { s -> s.copy(chatItems = items) }
                }.onFailure { Log.w("ChatVM", "live refresh failed: ${it.message}") }
            }
        }
    }

    // ── Recipients (group compose) ────────────────────────────────────────────

    fun setRecipients(contacts: List<com.wew.launcher.data.model.WewContact>) {
        recipientBindJob?.cancel()
        if (contacts.isEmpty()) {
            if (composeMode) {
                currentThreadId = -1L
                _uiState.update {
                    it.copy(
                        selectedRecipients = emptyList(),
                        recipientAddress = "",
                        recipientName = ""
                    )
                }
                load()
            }
            return
        }
        val primary = contacts.first()
        _uiState.update { st ->
            st.copy(
                selectedRecipients = contacts,
                recipientAddress = primary.phone?.trim().orEmpty(),
                recipientName = when {
                    contacts.size > 1 -> "${contacts.size} people"
                    else -> primary.composedDisplayName().ifBlank { primary.name }
                }
            )
        }
        if (!composeMode) return
        recipientBindJob = viewModelScope.launch {
            val tid = if (contacts.size == 1) {
                val phone = contacts.first().phone?.trim().orEmpty()
                if (phone.isEmpty()) -1L else smsRepo.resolveThreadIdForContact(phone)
            } else {
                -1L
            }
            currentThreadId = tid
            load()
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, sendError = null) }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendMessage() {
        val state = _uiState.value
        val text = state.inputText.trim()
        val hasImage = state.attachedImageUri != null
        if ((text.isEmpty() && !hasImage) || state.isSending || state.tokensExhausted) return
        val deviceId = prefs.getString("device_id", null) ?: return

        val targets = if (state.selectedRecipients.isNotEmpty()) {
            state.selectedRecipients.mapNotNull { it.phone?.trim()?.takeIf { p -> p.isNotEmpty() } }
        } else {
            listOf(recipientAddress.trim()).filter { it.isNotEmpty() }
        }
        if (targets.isEmpty()) return

        if (!MessagingCapability.canSendAndReceiveSms(getApplication())) {
            _uiState.update { it.copy(sendError = smsSendPreconditionMessage()) }
            return
        }

        val isMms = hasImage
        val perMsgCost = TokenEngine.calculateCost(
            if (isMms) ActionType.MMS_SENT else ActionType.SMS_SENT
        )
        val totalCost = perMsgCost * targets.size
        if (state.currentTokens < totalCost) {
            _uiState.update { it.copy(tokensExhausted = true) }
            return
        }

        val actionType = if (isMms) ActionType.MMS_SENT.value else ActionType.SMS_SENT.value
        val stagedAttach = state.attachedImageUri
        val stagedMime = state.attachedImageMime

        _uiState.update {
            it.copy(isSending = true, inputText = "", attachedImageUri = null, sendError = null)
        }

        viewModelScope.launch {
            var sendFailed: String? = null
            for (to in targets) {
                val tokenResult = repo.consumeTokens(
                    deviceId = deviceId,
                    amount = perMsgCost,
                    actionType = actionType,
                    appPackage = null,
                    appName = "Messages"
                )
                if (tokenResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            inputText = text,
                            attachedImageUri = stagedAttach,
                            tokensExhausted = true
                        )
                    }
                    return@launch
                }
                val sendResult = if (isMms) {
                    smsRepo.sendMms(
                        to = to,
                        body = text,
                        imageUri = stagedAttach,
                        imageMimeType = stagedMime
                    )
                } else {
                    smsRepo.sendSms(to = to, body = text)
                }
                if (sendResult.isFailure) {
                    repo.addTokens(deviceId, perMsgCost, "sms_send_failed_refund")
                    sendFailed = userMessageForSmsSendFailure(
                        sendResult.exceptionOrNull() ?: IllegalStateException("send failed")
                    )
                    break
                }
            }

            if (sendFailed != null) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        inputText = text,
                        attachedImageUri = stagedAttach,
                        sendError = sendFailed
                    )
                }
                return@launch
            }

            // Outgoing rows and thread_id are written asynchronously by the telephony stack.
            // Resolve thread with the same heuristics as the conversation list, then poll briefly.
            delay(80)
            if (currentThreadId == -1L && targets.isNotEmpty()) {
                val targetPhone = targets.first()
                repeat(24) {
                    val tid = smsRepo.resolveThreadIdForContact(targetPhone)
                    if (tid != -1L) {
                        currentThreadId = tid
                        return@repeat
                    }
                    delay(75)
                }
                if (currentThreadId == -1L) {
                    Log.w("ChatVM", "no thread id after send; SMS DB may lag or app is not default SMS")
                }
            }

            runCatching {
                var items = buildChatItems(deviceId)
                if (currentThreadId != -1L && !isMms && text.isNotEmpty()) {
                    var foundOutgoing = items.filterIsInstance<ChatBubbleItem.Local>().any { row ->
                        row.message.direction == SmsDirection.OUTGOING && row.message.body == text
                    }
                    repeat(10) { attempt ->
                        if (foundOutgoing) return@repeat
                        delay(100)
                        items = buildChatItems(deviceId)
                        foundOutgoing = items.filterIsInstance<ChatBubbleItem.Local>().any { row ->
                            row.message.direction == SmsDirection.OUTGOING && row.message.body == text
                        }
                        if (attempt == 9 && !foundOutgoing) {
                            items = buildChatItems(deviceId)
                        }
                    }
                } else {
                    repeat(3) { n ->
                        if (n > 0) delay(120)
                        items = buildChatItems(deviceId)
                    }
                }
                val device = repo.getDevice(deviceId)
                val missingLocalEcho = composeMode && currentThreadId == -1L && text.isNotEmpty() && !isMms &&
                    items.filterIsInstance<ChatBubbleItem.Local>().none { row ->
                        row.message.direction == SmsDirection.OUTGOING && row.message.body == text
                    }
                val echoHint = if (missingLocalEcho) {
                    "Your message may still be sending. If it never shows up, open WeW's permission screen and make sure WeW is the default SMS app."
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        chatItems = items,
                        isSending = false,
                        currentTokens = device.currentTokens,
                        tokensExhausted = device.currentTokens <= 0,
                        sendError = echoHint
                    )
                }
            }.onFailure {
                _uiState.update { s -> s.copy(isSending = false) }
            }
        }
    }

    // ── Attachment ────────────────────────────────────────────────────────────

    fun attachImage(uri: String, mimeType: String) =
        _uiState.update { it.copy(attachedImageUri = uri, attachedImageMime = mimeType) }

    fun clearAttachment() =
        _uiState.update { it.copy(attachedImageUri = null, sendError = null) }

    // ── Full-screen viewer ────────────────────────────────────────────────────

    fun showFullScreenImage(uri: String) =
        _uiState.update { it.copy(fullScreenImageUri = uri) }

    fun hideFullScreenImage() =
        _uiState.update { it.copy(fullScreenImageUri = null) }

    // ── Call ──────────────────────────────────────────────────────────────────

    fun onCallClick() = _uiState.update { it.copy(showCallConfirm = true) }
    fun dismissCallConfirm() = _uiState.update { it.copy(showCallConfirm = false) }

    fun confirmCall() {
        val deviceId = prefs.getString("device_id", null) ?: return
        val addr = _uiState.value.recipientAddress.trim()
        if (addr.isEmpty()) return
        dismissCallConfirm()
        viewModelScope.launch {
            val cost = TokenEngine.calculateCost(ActionType.CALL_MADE, durationUnits = 0)
            repo.consumeTokens(
                deviceId = deviceId,
                amount = cost,
                actionType = ActionType.CALL_MADE.value,
                appName = "Phone"
            )
            val device = runCatching { repo.getDevice(deviceId) }.getOrNull()
            _uiState.update {
                it.copy(
                    pendingCall = addr,
                    currentTokens = device?.currentTokens ?: it.currentTokens,
                    tokensExhausted = (device?.currentTokens ?: it.currentTokens) <= 0
                )
            }
        }
    }

    fun clearPendingCall() = _uiState.update { it.copy(pendingCall = null) }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        fun factory(
            app: Application,
            threadId: Long,
            recipientAddress: String,
            recipientName: String,
            mergeSystemSummaries: Boolean,
            initialRecipients: List<com.wew.launcher.data.model.WewContact> = emptyList()
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    app,
                    threadId,
                    recipientAddress,
                    recipientName,
                    mergeSystemSummaries,
                    initialRecipients
                ) as T
        }
    }
}

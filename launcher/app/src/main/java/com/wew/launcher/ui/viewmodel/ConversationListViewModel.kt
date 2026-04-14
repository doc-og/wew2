package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.ConversationMeta
import com.wew.launcher.data.model.WewContact
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.sms.SmsRepository
import com.wew.launcher.sms.SmsThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ── UI state ─────────────────────────────────────────────────────────────────

data class ConversationItem(
    val thread: SmsThread,
    /** Display name resolved from WewContacts, or formatted phone number. */
    val resolvedName: String,
    /** The matching approved contact, if any. */
    val contact: WewContact? = null,
    /** True if address matches an approved WewContact or the parent number. */
    val isApproved: Boolean = false,
    /** True if this is the WeW Parent thread. */
    val isParent: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val avatarColor: Color = Color(0xFF6B4EFF)
)

data class ConversationListUiState(
    val pinned: List<ConversationItem> = emptyList(),
    val conversations: List<ConversationItem> = emptyList(),
    /** Threads from unknown/unapproved senders — shown as a quarantine badge only. */
    val quarantineCount: Int = 0,
    val isLoading: Boolean = true,
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val tokensExhausted: Boolean = false,
    val deviceId: String = "",
    val parentPhoneNumber: String? = null,
    /** Thread currently selected for context-menu actions (long-press). */
    val contextMenuThread: ConversationItem? = null,
    val showNavMenu: Boolean = false,
    val showNewConversationSheet: Boolean = false,
    val approvedContacts: List<WewContact> = emptyList(),
    /** Show SOS confirmation dialog. */
    val showSosConfirm: Boolean = false,
    /** Set to the number to dial; UI observes and launches the intent, then clears. */
    val pendingEmergencyCall: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ConversationListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val smsRepo = SmsRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        load()
        observeSmsChanges()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    fun load() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                val device = repo.getDevice(deviceId)
                val contacts = repo.getContacts(deviceId)
                val approvedContacts = contacts.filter { it.isAuthorized }
                val conversationMeta = repo.getConversationMeta(deviceId)
                val metaByThread = conversationMeta.associateBy { it.threadId }

                // Parent phone number stored in prefs during setup (future: from Supabase)
                val parentPhone = prefs.getString("parent_phone", null)

                val threads = smsRepo.getThreads()
                val items = buildConversationItems(
                    threads, approvedContacts, parentPhone, metaByThread
                )
                val (pinned, rest) = items.partition { it.isPinned }
                val (approved, quarantine) = rest.partition { it.isApproved }

                _uiState.update {
                    it.copy(
                        pinned = pinned,
                        conversations = approved.sortedByDescending { c -> c.thread.date },
                        quarantineCount = quarantine.size,
                        isLoading = false,
                        currentTokens = device.currentTokens,
                        dailyTokenBudget = device.dailyTokenBudget,
                        tokensExhausted = device.currentTokens <= 0,
                        deviceId = deviceId,
                        parentPhoneNumber = parentPhone,
                        approvedContacts = approvedContacts
                    )
                }
            }.onFailure {
                Log.e("ConvListVM", "load failed: ${it.message}")
                // Fallback: show threads without contact resolution
                val threads = runCatching { smsRepo.getThreads() }.getOrElse { emptyList() }
                _uiState.update { s ->
                    s.copy(
                        conversations = threads.map { ConversationItem(it, it.address) },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun buildConversationItems(
        threads: List<SmsThread>,
        approvedContacts: List<WewContact>,
        parentPhone: String?,
        metaByThread: Map<String, ConversationMeta>
    ): List<ConversationItem> {
        val contactByPhone = approvedContacts
            .filter { !it.phone.isNullOrBlank() }
            .associateBy { normalizePhone(it.phone!!) }

        return threads.map { thread ->
            val normalizedAddr = normalizePhone(thread.address)
            val contact = contactByPhone[normalizedAddr]
            val isParent = parentPhone != null &&
                normalizePhone(parentPhone) == normalizedAddr
            val isApproved = contact != null || isParent
            val displayName = when {
                isParent -> "WeW Parent"
                contact != null -> contact.name
                else -> formatPhoneDisplay(thread.address)
            }
            val meta = metaByThread[thread.threadId.toString()]
            ConversationItem(
                thread = thread,
                resolvedName = displayName,
                contact = contact,
                isApproved = isApproved,
                isParent = isParent,
                isPinned = meta?.isPinned ?: isParent,  // parent thread always pinned
                isMuted = meta?.isMuted ?: false,
                avatarColor = avatarColorFor(displayName)
            )
        }
    }

    // ── Live SMS observation ──────────────────────────────────────────────────

    private fun observeSmsChanges() {
        viewModelScope.launch {
            smsRepo.observeSmsChanges().collect {
                val deviceId = prefs.getString("device_id", null) ?: return@collect
                runCatching {
                    val contacts = repo.getContacts(deviceId).filter { it.isAuthorized }
                    val metaByThread = repo.getConversationMeta(deviceId)
                        .associateBy { it.threadId }
                    val parentPhone = prefs.getString("parent_phone", null)
                    val threads = smsRepo.getThreads()
                    val items = buildConversationItems(threads, contacts, parentPhone, metaByThread)
                    val (pinned, rest) = items.partition { it.isPinned }
                    val (approved, quarantine) = rest.partition { it.isApproved }
                    _uiState.update { s ->
                        s.copy(
                            pinned = pinned,
                            conversations = approved.sortedByDescending { c -> c.thread.date },
                            quarantineCount = quarantine.size
                        )
                    }
                }.onFailure { Log.w("ConvListVM", "refresh failed: ${it.message}") }
            }
        }
    }

    // ── Thread actions ────────────────────────────────────────────────────────

    fun onLongPress(item: ConversationItem) {
        _uiState.update { it.copy(contextMenuThread = item) }
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(contextMenuThread = null) }
    }

    fun pinThread(item: ConversationItem) {
        viewModelScope.launch {
            val state = _uiState.value
            val newMeta = ConversationMeta(
                deviceId = state.deviceId,
                threadId = item.thread.threadId.toString(),
                displayName = item.resolvedName,
                isPinned = true,
                isMuted = item.isMuted
            )
            repo.upsertConversationMeta(newMeta)
            dismissContextMenu()
            load()
        }
    }

    fun unpinThread(item: ConversationItem) {
        viewModelScope.launch {
            val state = _uiState.value
            repo.upsertConversationMeta(
                ConversationMeta(
                    deviceId = state.deviceId,
                    threadId = item.thread.threadId.toString(),
                    displayName = item.resolvedName,
                    isPinned = false,
                    isMuted = item.isMuted
                )
            )
            dismissContextMenu()
            load()
        }
    }

    fun muteThread(item: ConversationItem) {
        viewModelScope.launch {
            val state = _uiState.value
            repo.upsertConversationMeta(
                ConversationMeta(
                    deviceId = state.deviceId,
                    threadId = item.thread.threadId.toString(),
                    displayName = item.resolvedName,
                    isPinned = item.isPinned,
                    isMuted = true
                )
            )
            dismissContextMenu()
            load()
        }
    }

    fun unmuteThread(item: ConversationItem) {
        viewModelScope.launch {
            val state = _uiState.value
            repo.upsertConversationMeta(
                ConversationMeta(
                    deviceId = state.deviceId,
                    threadId = item.thread.threadId.toString(),
                    displayName = item.resolvedName,
                    isPinned = item.isPinned,
                    isMuted = false
                )
            )
            dismissContextMenu()
            load()
        }
    }

    fun markRead(item: ConversationItem) {
        viewModelScope.launch {
            smsRepo.markThreadRead(item.thread.threadId)
            dismissContextMenu()
            load()
        }
    }

    fun deleteThread(item: ConversationItem) {
        viewModelScope.launch {
            // Protect parent thread from deletion
            if (item.isParent) { dismissContextMenu(); return@launch }
            smsRepo.deleteThread(item.thread.threadId)
            dismissContextMenu()
            load()
        }
    }

    // ── Nav sheet ─────────────────────────────────────────────────────────────

    fun showNavMenu() = _uiState.update { it.copy(showNavMenu = true) }
    fun hideNavMenu() = _uiState.update { it.copy(showNavMenu = false) }

    fun showNewConversation() = _uiState.update { it.copy(showNewConversationSheet = true) }
    fun hideNewConversation() = _uiState.update { it.copy(showNewConversationSheet = false) }

    // ── SOS ───────────────────────────────────────────────────────────────────

    fun showSosDialog() = _uiState.update { it.copy(showSosConfirm = true) }
    fun hideSosDialog() = _uiState.update { it.copy(showSosConfirm = false) }

    fun confirmSos() {
        val phone = _uiState.value.parentPhoneNumber ?: return  // no-op if parent not configured
        _uiState.update { it.copy(showSosConfirm = false, pendingEmergencyCall = phone) }
    }

    fun clearPendingEmergencyCall() = _uiState.update { it.copy(pendingEmergencyCall = null) }

    // ── Token request ─────────────────────────────────────────────────────────

    fun requestMoreTokens() {
        val state = _uiState.value
        if (state.deviceId.isEmpty()) return
        viewModelScope.launch {
            repo.submitTokenRequest(
                com.wew.launcher.data.model.TokenRequest(
                    deviceId = state.deviceId,
                    reason = "daily limit reached"
                )
            )
            repo.logActivity(
                ActivityLog(
                    deviceId = state.deviceId,
                    actionType = ActionType.TOKEN_REQUEST.value
                )
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun normalizePhone(phone: String): String =
        phone.replace(Regex("[^\\d+]"), "").let {
            // Strip leading country code for US numbers for fuzzy matching
            if (it.startsWith("+1") && it.length == 12) it.substring(2) else it
        }

    private fun formatPhoneDisplay(raw: String): String {
        val digits = raw.replace(Regex("[^\\d]"), "")
        return if (digits.length == 10)
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        else raw
    }

    companion object {
        val AvatarPalette = listOf(
            Color(0xFF6B4EFF),   // violet
            Color(0xFF4E9FFF),   // blue
            Color(0xFF4EFFB0),   // mint
            Color(0xFFFF6B6B),   // coral
            Color(0xFFFFB84E),   // amber
            Color(0xFFFF4ECD),   // pink
            Color(0xFF4EFFEF),   // cyan
            Color(0xFFB0FF4E)    // lime
        )

        fun avatarColorFor(name: String): Color =
            AvatarPalette[name.hashCode().absoluteValue % AvatarPalette.size]
    }
}

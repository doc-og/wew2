package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.ConversationMeta
import com.wew.launcher.data.model.WewContact
import com.wew.launcher.data.model.isApprovedForComms
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.sms.SmsRepository
import com.wew.launcher.sms.SmsThread
import com.wew.launcher.telecom.DeviceLine
import com.wew.launcher.telecom.PhoneMatch
import com.wew.launcher.telecom.WewPhoneAllowlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.absoluteValue

// ── UI state ─────────────────────────────────────────────────────────────────

data class ConversationItem(
    val thread: SmsThread,
    /** Display name resolved from WewContacts, or formatted phone number. */
    val resolvedName: String,
    /** The matching approved contact, if any. */
    val contact: WewContact? = null,
    /** True if every participant is parent or an authorized contact. */
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
    /** Count of threads removed as unapproved (informational; list is purged from device). */
    val quarantineCount: Int = 0,
    val isLoading: Boolean = true,
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val tokensExhausted: Boolean = false,
    val deviceId: String = "",
    val parentPhoneNumber: String? = null,
    val parentName: String? = null,
    /** Thread currently selected for context-menu actions (long-press). */
    val contextMenuThread: ConversationItem? = null,
    val showNavMenu: Boolean = false,
    val approvedContacts: List<WewContact> = emptyList(),
    /** Show SOS confirmation dialog. */
    val showSosConfirm: Boolean = false,
    /** Set to the number to dial; UI observes and launches the intent, then clears. */
    val pendingEmergencyCall: String? = null,
    /** Show the passcode dialog for parent app access. */
    val showParentPasscode: Boolean = false,
    /** Remaining attempts before the passcode dialog auto-dismisses. */
    val passcodeAttemptsLeft: Int = 3,
    /** Set to true when passcode verified — UI launches parent app then clears. */
    val pendingLaunchParentApp: Boolean = false,
    /** Package name of the whitelisted calendar app, null if not approved. */
    val approvedCalendarPackage: String? = null,
    /** Package name of the whitelisted weather app, null if not approved. */
    val approvedWeatherPackage: String? = null,
    /**
     * Generic whitelisted apps (parent-approved, installed on device, with a launch intent).
     * Excludes the launcher itself and any packages already surfaced as dedicated nav items
     * (Calendar, Weather). Rendered inside the hamburger menu under "Apps".
     */
    val approvedApps: List<ApprovedApp> = emptyList(),
)

/** Minimal UI model for a launchable, parent-approved app shown in the hamburger. */
data class ApprovedApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class)
class ConversationListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val smsRepo = SmsRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    /** Only one list refresh at a time — SMS observer + load() must not overlap. */
    private val listRefreshMutex = Mutex()

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        load()
        observeSmsChanges()
        startApprovedAppsPolling()
    }

    /**
     * Lightweight polling of the parent-approved whitelist so the hamburger's "Apps"
     * section reflects parent-side changes without needing a full conversation reload.
     * Cheap: one Supabase select every POLL_INTERVAL_MS.
     */
    private fun startApprovedAppsPolling() {
        viewModelScope.launch {
            while (true) {
                delay(APPROVED_APPS_POLL_MS)
                refreshApprovedApps()
            }
        }
    }

    /** Re-fetch the parent-approved app whitelist and push it into UI state. */
    fun refreshApprovedApps() {
        val deviceId = prefs.getString("device_id", null)
        if (deviceId.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching {
                val whitelistedRecords = repo.getWhitelistedApps(deviceId)
                val whitelistedPkgs = whitelistedRecords.map { it.packageName }.toSet()
                val calendarPkg = CALENDAR_PACKAGES.firstOrNull { it in whitelistedPkgs }
                val weatherPkg = WEATHER_PACKAGES.firstOrNull { it in whitelistedPkgs }
                val approvedApps = buildApprovedApps(whitelistedRecords, calendarPkg, weatherPkg)
                _uiState.update {
                    it.copy(
                        approvedCalendarPackage = calendarPkg,
                        approvedWeatherPackage = weatherPkg,
                        approvedApps = approvedApps
                    )
                }
            }.onFailure { Log.w("ConvListVM", "refreshApprovedApps failed: ${it.message}") }
        }
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    fun load() {
        val deviceId = prefs.getString("device_id", null)
        if (deviceId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    pinned = emptyList(),
                    conversations = emptyList(),
                    deviceId = ""
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                repo.syncAppListIfStale(deviceId, getApplication())
                listRefreshMutex.withLock {
                    applyConversationList(deviceId)
                }
            }.onFailure {
                Log.e("ConvListVM", "load failed: ${it.message}")
                _uiState.update { s ->
                    s.copy(
                        pinned = emptyList(),
                        conversations = emptyList(),
                        quarantineCount = 0,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Loads threads, keeps only conversations where every remote participant is the parent
     * or a contact the parent approved in the parent app; deletes other threads from the device.
     */
    private suspend fun applyConversationList(deviceId: String) {
        val device = repo.getDevice(deviceId)
        val approvedContacts = repo.getContacts(deviceId).filter { it.isApprovedForComms() }
        val conversationMeta = repo.getConversationMeta(deviceId)
        val metaByThread = conversationMeta.associateBy { it.threadId }

        val parentPhone = device.parentPhone?.takeIf { it.isNotBlank() }
            ?: prefs.getString("parent_phone", null)
        val parentName = device.parentDisplayName?.takeIf { it.isNotBlank() }
            ?: prefs.getString("parent_name", null)
        prefs.edit().apply {
            device.parentPhone?.takeIf { it.isNotBlank() }?.let { putString("parent_phone", it) }
            device.parentDisplayName?.takeIf { it.isNotBlank() }?.let { putString("parent_name", it) }
            device.timezone.takeIf { it.isNotBlank() }?.let { putString("device_timezone", it) }
        }.apply()

        var threads = smsRepo.getThreads()
        var participantRaw = resolveParticipantRaw(threads, parentPhone, approvedContacts)
        var items = buildConversationItems(
            threads, approvedContacts, parentPhone, metaByThread, participantRaw
        )
        var totalPurged = 0
        repeat(8) { pass ->
            val quarantine = items.filter { !it.isApproved }
            if (quarantine.isEmpty()) return@repeat
            totalPurged += quarantine.size
            Log.w("ConvListVM", "purging ${quarantine.size} unapproved thread(s), pass=$pass")
            for (q in quarantine) {
                smsRepo.deleteThread(q.thread.threadId)
            }
            delay(120)
            threads = smsRepo.getThreads()
            participantRaw = resolveParticipantRaw(threads, parentPhone, approvedContacts)
            items = buildConversationItems(
                threads, approvedContacts, parentPhone, metaByThread, participantRaw
            )
        }

        val approvedOnly = items.filter { it.isApproved }
        val (pinned, rest) = approvedOnly.partition { it.isPinned }

        val whitelistedRecords = repo.getWhitelistedApps(deviceId)
        val whitelistedPkgs = whitelistedRecords.map { it.packageName }.toSet()
        val calendarPkg = CALENDAR_PACKAGES.firstOrNull { it in whitelistedPkgs }
        val weatherPkg = WEATHER_PACKAGES.firstOrNull { it in whitelistedPkgs }
        val approvedApps = buildApprovedApps(whitelistedRecords, calendarPkg, weatherPkg)

        val parentItem = pinned.find { it.isParent }
        if (parentItem != null) {
            val localTid = parentItem.thread.threadId.toString()
            if (device.parentSmsThreadId != localTid) {
                repo.updateParentSmsThreadId(deviceId, parentItem.thread.threadId)
            }
        }

        syncCallAllowlist(parentPhone, approvedContacts)

        _uiState.update {
            it.copy(
                pinned = pinned,
                conversations = rest.sortedByDescending { c -> c.thread.date },
                quarantineCount = totalPurged,
                isLoading = false,
                currentTokens = device.currentTokens,
                dailyTokenBudget = device.dailyTokenBudget,
                tokensExhausted = device.currentTokens <= 0,
                deviceId = deviceId,
                parentPhoneNumber = parentPhone,
                parentName = parentName,
                approvedContacts = approvedContacts,
                approvedCalendarPackage = calendarPkg,
                approvedWeatherPackage = weatherPkg,
                approvedApps = approvedApps
            )
        }
    }

    /**
     * Turn whitelisted [com.wew.launcher.data.model.AppRecord]s into launchable nav-sheet entries.
     * Drops: the launcher itself, anything already surfaced as Calendar/Weather, and packages
     * that aren't actually installed on this device (no launch intent available).
     */
    private fun buildApprovedApps(
        records: List<com.wew.launcher.data.model.AppRecord>,
        calendarPkg: String?,
        weatherPkg: String?
    ): List<ApprovedApp> {
        val ctx = getApplication<Application>()
        val pm = ctx.packageManager
        val selfPkg = ctx.packageName
        val hidden = setOfNotNull(selfPkg, calendarPkg, weatherPkg)

        return records
            .asSequence()
            .filter { it.packageName !in hidden }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { record ->
                val resolvedName = runCatching {
                    pm.getApplicationInfo(record.packageName, 0).loadLabel(pm).toString()
                }.getOrNull().takeIf { !it.isNullOrBlank() } ?: record.appName
                val icon = runCatching { pm.getApplicationIcon(record.packageName) }.getOrNull()
                ApprovedApp(
                    packageName = record.packageName,
                    appName = resolvedName,
                    icon = icon
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    /**
     * Resolves thread participants. When there is no parent phone and no approved contacts yet,
     * every non-empty thread is treated as unapproved without scanning SMS/MMS (fast first-run).
     * Otherwise merges conversation metadata with rows from the SMS/MMS providers (parallel, bounded).
     */
    private suspend fun resolveParticipantRaw(
        threads: List<SmsThread>,
        parentPhone: String?,
        approvedContacts: List<WewContact>
    ): Map<Long, Set<String>> {
        if (threads.isEmpty()) return emptyMap()
        val noAllowlistYet = parentPhone.isNullOrBlank() && approvedContacts.isEmpty()
        if (noAllowlistYet) {
            return threads.associate { t ->
                val fromMeta = t.address.split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                t.threadId to fromMeta
            }
        }
        val t0 = SystemClock.elapsedRealtime()
        val out = coroutineScope {
            val sem = Semaphore(6)
            threads.map { t ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val fromDb = smsRepo.getParticipantAddressesForThread(t.threadId)
                        val fromMeta = t.address.split(',')
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .toSet()
                        t.threadId to (fromDb + fromMeta)
                    }
                }
            }.awaitAll().toMap()
        }
        Log.i("ConvListVM", "resolveParticipantRaw: ${threads.size} threads in ${SystemClock.elapsedRealtime() - t0}ms")
        return out
    }

    private fun buildConversationItems(
        threads: List<SmsThread>,
        approvedContacts: List<WewContact>,
        parentPhone: String?,
        metaByThread: Map<String, ConversationMeta>,
        participantRawByThread: Map<Long, Set<String>>
    ): List<ConversationItem> {
        val ctx = getApplication<Application>()

        return threads.map { thread ->
            val raw = (participantRawByThread[thread.threadId] ?: emptySet())
                .filterNot { isNoiseAddress(it) }
            val remoteParties = distinctSubscriberAddresses(
                raw.filterNot { DeviceLine.isLikelyOwnNumber(ctx, it) }
            )
            val matchingContacts = remoteParties.flatMap { addr ->
                approvedContacts.filter { c ->
                    c.phone != null && PhoneMatch.sameSubscriber(c.phone, addr)
                }
            }.distinctBy { it.id ?: it.phone }

            val isApproved = remoteParties.isNotEmpty() &&
                remoteParties.all { addr ->
                    (parentPhone != null && PhoneMatch.sameSubscriber(parentPhone, addr)) ||
                        approvedContacts.any { c ->
                            c.phone != null && PhoneMatch.sameSubscriber(c.phone, addr)
                        }
                }

            val isParent = parentPhone != null &&
                remoteParties.size == 1 &&
                PhoneMatch.sameSubscriber(remoteParties.first(), parentPhone)

            val displayName = when {
                isParent -> "WeW Parent"
                matchingContacts.size == 1 -> matchingContacts.first().name
                matchingContacts.size > 1 ->
                    matchingContacts.take(3).joinToString(", ") { it.name } +
                        if (matchingContacts.size > 3) ", …" else ""
                parentPhone != null && remoteParties.isNotEmpty() &&
                    remoteParties.all { PhoneMatch.sameSubscriber(it, parentPhone) } -> "WeW Parent"
                else -> formatPhoneDisplay(remoteParties.firstOrNull() ?: raw.firstOrNull() ?: thread.address)
            }

            val primaryContact = matchingContacts.firstOrNull()
            val meta = metaByThread[thread.threadId.toString()]
            ConversationItem(
                thread = thread,
                resolvedName = displayName,
                contact = primaryContact,
                isApproved = isApproved,
                isParent = isParent,
                isPinned = meta?.isPinned ?: isParent,
                isMuted = meta?.isMuted ?: false,
                avatarColor = avatarColorFor(displayName)
            )
        }
    }

    // ── Live SMS observation ──────────────────────────────────────────────────

    private fun observeSmsChanges() {
        viewModelScope.launch {
            smsRepo.observeSmsChanges()
                .debounce(400L)
                .collect {
                    val deviceId = prefs.getString("device_id", null) ?: return@collect
                    runCatching {
                        listRefreshMutex.withLock {
                            applyConversationList(deviceId)
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
            if (item.isParent) {
                dismissContextMenu()
                return@launch
            }
            smsRepo.deleteThread(item.thread.threadId)
            dismissContextMenu()
            load()
        }
    }

    // ── Nav sheet ─────────────────────────────────────────────────────────────

    fun showNavMenu() {
        // Refresh immediately so newly-approved apps show without waiting for the poll tick.
        refreshApprovedApps()
        _uiState.update { it.copy(showNavMenu = true) }
    }
    fun hideNavMenu() = _uiState.update { it.copy(showNavMenu = false) }

    // ── SOS ───────────────────────────────────────────────────────────────────

    fun showSosDialog() = _uiState.update { it.copy(showSosConfirm = true) }
    fun hideSosDialog() = _uiState.update { it.copy(showSosConfirm = false) }

    fun confirmSos() {
        val phone = _uiState.value.parentPhoneNumber ?: return
        _uiState.update { it.copy(showSosConfirm = false, pendingEmergencyCall = phone) }
    }

    fun clearPendingEmergencyCall() = _uiState.update { it.copy(pendingEmergencyCall = null) }

    // ── Parent app access ─────────────────────────────────────────────────────

    fun showParentPasscodeDialog() =
        _uiState.update { it.copy(showParentPasscode = true, passcodeAttemptsLeft = 3) }

    fun dismissParentPasscode() =
        _uiState.update { it.copy(showParentPasscode = false) }

    fun verifyPasscodeForParentApp(pin: String) {
        val state = _uiState.value
        if (state.deviceId.isEmpty()) return
        viewModelScope.launch {
            val record = repo.getDevicePasscode(state.deviceId)
            if (record == null) {
                _uiState.update { it.copy(showParentPasscode = false, pendingLaunchParentApp = true) }
                return@launch
            }
            val hash = hashPin(state.deviceId, pin)
            if (hash == record.passcodeHash) {
                _uiState.update { it.copy(showParentPasscode = false, pendingLaunchParentApp = true) }
            } else {
                val remaining = state.passcodeAttemptsLeft - 1
                if (remaining <= 0) {
                    _uiState.update { it.copy(showParentPasscode = false, passcodeAttemptsLeft = 3) }
                } else {
                    _uiState.update { it.copy(passcodeAttemptsLeft = remaining) }
                }
            }
        }
    }

    fun clearPendingLaunchParentApp() =
        _uiState.update { it.copy(pendingLaunchParentApp = false) }

    private fun hashPin(deviceId: String, pin: String): String {
        val input = deviceId + pin
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

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

    private fun syncCallAllowlist(parentPhone: String?, approvedContacts: List<WewContact>) {
        val callAllow = buildSet {
            parentPhone?.let { add(PhoneMatch.canonicalForAllowlist(it)) }
            for (c in approvedContacts.filter { it.isApprovedForComms() }) {
                c.phone?.let { add(PhoneMatch.canonicalForAllowlist(it)) }
            }
        }
        WewPhoneAllowlist.write(prefs, callAllow)
    }

    private fun isNoiseAddress(raw: String): Boolean {
        val t = raw.trim().lowercase()
        if (t.isBlank()) return true
        return t.contains("insert-address-token")
    }

    private fun distinctSubscriberAddresses(addresses: Collection<String>): List<String> {
        val out = mutableListOf<String>()
        for (a in addresses) {
            if (out.none { PhoneMatch.sameSubscriber(it, a) }) out.add(a)
        }
        return out
    }

    private fun formatPhoneDisplay(raw: String): String {
        val digits = raw.replace(Regex("[^\\d]"), "")
        return if (digits.length == 10)
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        else raw
    }

    companion object {
        /** How often to silently re-check the parent-approved app whitelist. */
        private const val APPROVED_APPS_POLL_MS = 15_000L

        val CALENDAR_PACKAGES = listOf(
            "com.google.android.calendar",
            "com.android.calendar"
        )
        val WEATHER_PACKAGES = listOf(
            "com.google.android.apps.weather",
            "com.weather.Weather",
            "com.yahoo.mobile.client.android.weather",
            "com.samsung.android.app.weather"
        )

        val AvatarPalette = listOf(
            Color(0xFF6B4EFF),
            Color(0xFF4E9FFF),
            Color(0xFF4EFFB0),
            Color(0xFFFF6B6B),
            Color(0xFFFFB84E),
            Color(0xFFFF4ECD),
            Color(0xFF4EFFEF),
            Color(0xFFB0FF4E)
        )

        fun avatarColorFor(name: String): Color =
            AvatarPalette[name.hashCode().absoluteValue % AvatarPalette.size]
    }
}

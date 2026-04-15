package com.wew.launcher.telecom

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.token.TokenEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.Manifest
import android.content.pm.PackageManager

enum class InCallDisplayMode {
    OUTGOING_ACTIVE,
    UNKNOWN_BLOCKED
}

data class CallParticipant(
    val displayName: String,
    val phone: String
)

data class WewInCallUiState(
    val displayName: String,
    val phoneNumber: String,
    val speakerOn: Boolean,
    /** Tokens after the initial call charge (null = not loaded). */
    val currentTokens: Int?,
    /** Wall-clock seconds since outgoing call was placed (ring + talk). */
    val elapsedSeconds: Int,
    /** Seconds counted only while the cellular leg is active (off-hook). */
    val activeElapsedSeconds: Int,
    /** Contacts in the same thread / group besides the dialed line. */
    val otherParticipants: List<CallParticipant>,
    val mode: InCallDisplayMode,
    /** When false (e.g. SOS), no per-minute metering from here. */
    val chargeMetering: Boolean
)

/**
 * Registers a self-managed [PhoneAccount] and places cellular calls through [TelecomManager]
 * so the system dialer UI is never shown. In-call controls are handled in-app.
 */
object WewCallManager {

    private const val TAG = "WewCall"
    private const val ACCOUNT_ID = "wew_self_managed"

    private var appContext: Context? = null

    private var phoneAccountHandle: PhoneAccountHandle? = null

    private var pendingDisplayLabel: String? = null

    private data class PendingSession(
        val participants: List<CallParticipant>,
        val chargeMetering: Boolean,
        val tokenBalanceHint: Int?,
        val normalizedPrimary: String
    )

    private var pendingSession: PendingSession? = null

    private var activeConnection: android.telecom.Connection? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var callRadioActive: Boolean = false
    private var lastMeteredActiveMinute: Int = 0
    private var costOverrides: Map<String, Int> = emptyMap()

    private val _uiState = MutableStateFlow<WewInCallUiState?>(null)
    val uiState: StateFlow<WewInCallUiState?> = _uiState.asStateFlow()

    fun ensurePhoneAccountRegistered(context: Context) {
        val app = context.applicationContext.also { appContext = it }
        val telecom = app.getSystemService(TelecomManager::class.java) ?: return
        if (phoneAccountHandle != null) return

        val component = ComponentName(app, WewConnectionService::class.java)
        val handle = PhoneAccountHandle(component, ACCOUNT_ID)
        val account = PhoneAccount.Builder(handle, "WeW")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        telecom.registerPhoneAccount(account)
        phoneAccountHandle = handle
    }

    /**
     * @param groupMembers Full set of thread participants with phones (e.g. group MMS). The dialed
     * [rawNumber] should be included; others are shown as "also on this thread".
     * @param chargeMetering When false, no per-minute token charges (SOS to parent).
     * @param tokenBalanceHint Balance after the upfront call charge, if known.
     */
    fun placeCall(
        context: Context,
        rawNumber: String,
        displayLabel: String,
        groupMembers: List<CallParticipant> = emptyList(),
        chargeMetering: Boolean = true,
        tokenBalanceHint: Int? = null
    ) {
        val app = context.applicationContext.also { appContext = it }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "CALL_PHONE not granted; cannot place call")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.MANAGE_OWN_CALLS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "MANAGE_OWN_CALLS not granted; cannot place self-managed call")
            return
        }

        ensurePhoneAccountRegistered(app)
        val handle = phoneAccountHandle ?: return
        val telecom = app.getSystemService(TelecomManager::class.java) ?: return

        val normalized = normalizeDialString(rawNumber)
        if (normalized.isBlank()) {
            Log.w(TAG, "empty dial string")
            return
        }

        pendingDisplayLabel = displayLabel.ifBlank { normalized }
        val participants = buildParticipantRoster(displayLabel, normalized, groupMembers)
        pendingSession = PendingSession(
            participants = participants,
            chargeMetering = chargeMetering,
            tokenBalanceHint = tokenBalanceHint,
            normalizedPrimary = normalized
        )

        val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, normalized, null)
        val extras = android.os.Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }

        runCatching {
            telecom.placeCall(uri, extras)
        }.onFailure {
            Log.e(TAG, "placeCall failed", it)
            pendingDisplayLabel = null
            pendingSession = null
            _uiState.value = null
        }
    }

    private fun buildParticipantRoster(
        displayLabel: String,
        normalizedPrimary: String,
        groupMembers: List<CallParticipant>
    ): List<CallParticipant> {
        if (groupMembers.isNotEmpty()) return groupMembers
        return listOf(
            CallParticipant(
                displayName = displayLabel.ifBlank { normalizedPrimary },
                phone = normalizedPrimary
            )
        )
    }

    internal fun onOutgoingConnectionCreated(connection: android.telecom.Connection, number: String) {
        activeConnection = connection
        val label = pendingDisplayLabel?.takeIf { it.isNotBlank() } ?: number
        pendingDisplayLabel = null
        val pend = pendingSession
        pendingSession = null
        val participants = pend?.participants ?: listOf(CallParticipant(label, number))
        val primaryNorm = normalizeDialString(pend?.normalizedPrimary ?: number)
        val others = participants.filter { normalizeDialString(it.phone) != primaryNorm }
        callRadioActive = false
        lastMeteredActiveMinute = 0
        costOverrides = emptyMap()

        _uiState.value = WewInCallUiState(
            displayName = label,
            phoneNumber = number,
            speakerOn = false,
            currentTokens = pend?.tokenBalanceHint,
            elapsedSeconds = 0,
            activeElapsedSeconds = 0,
            otherParticipants = others,
            mode = InCallDisplayMode.OUTGOING_ACTIVE,
            chargeMetering = pend?.chargeMetering ?: true
        )

        prefetchTokenCostsAndBalance()
        startTicker()
    }

    internal fun onCallRadioActive() {
        callRadioActive = true
    }

    private fun prefetchTokenCostsAndBalance() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: return
        scope.launch(Dispatchers.IO) {
            val repo = DeviceRepository(ctx)
            val overrides = runCatching { repo.getTokenCostOverrides(deviceId) }.getOrElse { emptyMap() }
            val balance = runCatching { repo.getDevice(deviceId).currentTokens }.getOrNull()
            withContext(Dispatchers.Main.immediate) {
                costOverrides = overrides
                if (balance != null) {
                    _uiState.update { s -> s?.copy(currentTokens = balance) }
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _uiState.value != null) {
                delay(1000)
                val s = _uiState.value ?: break
                if (s.mode != InCallDisplayMode.OUTGOING_ACTIVE) continue

                val newElapsed = s.elapsedSeconds + 1
                val newActive = if (callRadioActive) s.activeElapsedSeconds + 1 else s.activeElapsedSeconds
                _uiState.value = s.copy(
                    elapsedSeconds = newElapsed,
                    activeElapsedSeconds = newActive
                )
                maybeBillPerActiveMinute(newActive, s.chargeMetering)
            }
        }
    }

       private fun maybeBillPerActiveMinute(activeSeconds: Int, chargeMetering: Boolean) {
        if (!chargeMetering) return
        val minute = activeSeconds / 60
        if (minute <= lastMeteredActiveMinute) return
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: return

        val perMinute = TokenEngine.calculateCost(
            ActionType.CALL_MADE,
            durationUnits = 1,
            overrides = costOverrides
        ) - TokenEngine.calculateCost(
            ActionType.CALL_MADE,
            durationUnits = 0,
            overrides = costOverrides
        )
        if (perMinute <= 0) {
            lastMeteredActiveMinute = minute
            return
        }

        val fromMinute = lastMeteredActiveMinute + 1
        lastMeteredActiveMinute = minute
        scope.launch(Dispatchers.IO) {
            val repo = DeviceRepository(ctx)
            var lastBal: Int? = null
            for (m in fromMinute..minute) {
                val result = repo.consumeTokens(
                    deviceId = deviceId,
                    amount = perMinute,
                    actionType = ActionType.CALL_MADE.value,
                    appName = "Phone",
                    contextMetadata = mapOf("per_minute" to "1", "active_minute_index" to m.toString())
                )
                lastBal = result.getOrNull() ?: lastBal
            }
            val newBal = lastBal
            withContext(Dispatchers.Main.immediate) {
                if (newBal != null) {
                    _uiState.update { st -> st?.copy(currentTokens = newBal) }
                }
            }
        }
    }

    /** Brief UI after [WewCallScreeningService] blocks an unknown caller. */
    fun showBlockedUnknownCall(incomingNumber: String?) {
        scope.launch {
            tickerJob?.cancel()
            activeConnection = null
            callRadioActive = false
            pendingSession = null
            pendingDisplayLabel = null

            _uiState.value = WewInCallUiState(
                displayName = "unknown caller",
                phoneNumber = incomingNumber?.ifBlank { "withheld" } ?: "withheld",
                speakerOn = false,
                currentTokens = null,
                elapsedSeconds = 0,
                activeElapsedSeconds = 0,
                otherParticipants = emptyList(),
                mode = InCallDisplayMode.UNKNOWN_BLOCKED,
                chargeMetering = false
            )
            delay(8_000)
            if (_uiState.value?.mode == InCallDisplayMode.UNKNOWN_BLOCKED) {
                _uiState.value = null
            }
        }
    }

    internal fun onConnectionDisposed() {
        tickerJob?.cancel()
        tickerJob = null
        activeConnection = null
        callRadioActive = false
        lastMeteredActiveMinute = 0
        appContext?.let { applySpeakerRoute(it, false) }
        _uiState.value = null
    }

    fun hangUp() {
        val mode = _uiState.value?.mode
        if (mode == InCallDisplayMode.UNKNOWN_BLOCKED) {
            tickerJob?.cancel()
            _uiState.value = null
            return
        }
        runCatching { activeConnection?.disconnect() }
            .onFailure { Log.e(TAG, "hangUp failed", it) }
    }

    fun toggleSpeaker(context: Context) {
        val current = _uiState.value ?: return
        if (current.mode != InCallDisplayMode.OUTGOING_ACTIVE) return
        val newSpeaker = !current.speakerOn
        applySpeakerRoute(context.applicationContext, newSpeaker)
        _uiState.value = current.copy(speakerOn = newSpeaker)
    }

    private fun applySpeakerRoute(context: Context, speakerOn: Boolean) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        audio.mode = AudioManager.MODE_IN_CALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (speakerOn) {
                val speaker = audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                speaker?.let { audio.setCommunicationDevice(it) }
            } else {
                val earpiece = audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    audio.setCommunicationDevice(earpiece)
                } else {
                    audio.clearCommunicationDevice()
                }
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = speakerOn
        }
    }

    private fun normalizeDialString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val stripped = PhoneNumberUtils.normalizeNumber(trimmed)
        val base = if (stripped.isNullOrBlank()) trimmed else stripped
        return WewPhoneAllowlist.normalize(base)
    }
}

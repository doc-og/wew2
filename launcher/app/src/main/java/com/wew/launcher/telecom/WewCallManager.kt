package com.wew.launcher.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
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
 * Places cellular calls via Intent.ACTION_CALL and surfaces in-app overlay state.
 * The overlay is rendered in WewInCallOverlayService (WindowManager TYPE_APPLICATION_OVERLAY)
 * so it floats over the system dialer.
 */
object WewCallManager {

    private const val TAG = "WewCall"

    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null
    private var callRadioActive: Boolean = false
    private var lastMeteredActiveMinute: Int = 0
    private var costOverrides: Map<String, Int> = emptyMap()
    private var callEnded: Boolean = true

    private var callStateCallback: TelephonyCallback? = null
    private var callStateListenerLegacy: PhoneStateListener? = null

    private val _uiState = MutableStateFlow<WewInCallUiState?>(null)
    val uiState: StateFlow<WewInCallUiState?> = _uiState.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

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

        val normalized = normalizeDialString(rawNumber)
        if (normalized.isBlank()) {
            Log.w(TAG, "empty dial string")
            return
        }

        val participants = buildParticipantRoster(displayLabel, normalized, groupMembers)
        val others = participants.filter { !PhoneMatch.sameSubscriber(it.phone, normalized) }

        callEnded = false
        callRadioActive = false
        lastMeteredActiveMinute = 0
        costOverrides = emptyMap()

        _uiState.value = WewInCallUiState(
            displayName = displayLabel.ifBlank { normalized },
            phoneNumber = normalized,
            speakerOn = false,
            currentTokens = tokenBalanceHint,
            elapsedSeconds = 0,
            activeElapsedSeconds = 0,
            otherParticipants = others,
            mode = InCallDisplayMode.OUTGOING_ACTIVE,
            chargeMetering = chargeMetering
        )

        prefetchTokenCostsAndBalance()
        startTicker()
        registerCallStateTracking(app)

        // Show overlay over system dialer
        app.startService(Intent(app, WewInCallOverlayService::class.java))

        // Place real cellular call
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$normalized")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { app.startActivity(callIntent) }
            .onFailure {
                Log.e(TAG, "ACTION_CALL failed", it)
                onCallEnded()
            }
    }

    private fun buildParticipantRoster(
        displayLabel: String,
        normalizedPrimary: String,
        groupMembers: List<CallParticipant>
    ): List<CallParticipant> {
        if (groupMembers.isNotEmpty()) return groupMembers
        return listOf(CallParticipant(displayLabel.ifBlank { normalizedPrimary }, normalizedPrimary))
    }

    private fun registerCallStateTracking(context: Context) {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            callStateCallback = cb
            telephony.registerTelephonyCallback(context.mainExecutor, cb)
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    handleCallState(state)
            }
            callStateListenerLegacy = listener
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun unregisterCallStateTracking() {
        val ctx = appContext ?: return
        val telephony = ctx.getSystemService(TelephonyManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callStateCallback?.let { telephony.unregisterTelephonyCallback(it) }
            callStateCallback = null
        } else {
            callStateListenerLegacy?.let {
                @Suppress("DEPRECATION")
                telephony.listen(it, PhoneStateListener.LISTEN_NONE)
            }
            callStateListenerLegacy = null
        }
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!callRadioActive) callRadioActive = true
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (_uiState.value?.mode == InCallDisplayMode.OUTGOING_ACTIVE) {
                    onCallEnded()
                }
            }
        }
    }

    private fun onCallEnded() {
        if (callEnded) return
        callEnded = true
        unregisterCallStateTracking()
        tickerJob?.cancel()
        tickerJob = null
        callRadioActive = false
        lastMeteredActiveMinute = 0
        appContext?.let { applySpeakerRoute(it, false) }
        _uiState.value = null
        appContext?.let { ctx ->
            ctx.stopService(Intent(ctx, WewInCallOverlayService::class.java))
        }
    }

    /** Brief UI after [com.wew.launcher.callscreening.WewCallScreeningService] blocks an unknown caller. */
    fun showBlockedUnknownCall(incomingNumber: String?) {
        scope.launch {
            tickerJob?.cancel()
            callEnded = true
            callRadioActive = false

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
            // Show overlay briefly for the blocked-call banner
            appContext?.let { ctx ->
                ctx.startService(Intent(ctx, WewInCallOverlayService::class.java))
            }
            delay(8_000)
            if (_uiState.value?.mode == InCallDisplayMode.UNKNOWN_BLOCKED) {
                _uiState.value = null
                appContext?.let { ctx ->
                    ctx.stopService(Intent(ctx, WewInCallOverlayService::class.java))
                }
            }
        }
    }

    fun hangUp() {
        val mode = _uiState.value?.mode
        if (mode == InCallDisplayMode.UNKNOWN_BLOCKED) {
            tickerJob?.cancel()
            _uiState.value = null
            appContext?.let { ctx ->
                ctx.stopService(Intent(ctx, WewInCallOverlayService::class.java))
            }
            return
        }
        val ctx = appContext ?: return
        val telecom = ctx.getSystemService(TelecomManager::class.java)
        runCatching {
            @Suppress("DEPRECATION")
            telecom?.endCall()
        }.onFailure { Log.e(TAG, "endCall failed", it) }
        onCallEnded()
    }

    fun toggleSpeaker(context: Context) {
        val current = _uiState.value ?: return
        if (current.mode != InCallDisplayMode.OUTGOING_ACTIVE) return
        val newSpeaker = !current.speakerOn
        applySpeakerRoute(context.applicationContext, newSpeaker)
        _uiState.value = current.copy(speakerOn = newSpeaker)
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
                _uiState.value = s.copy(elapsedSeconds = newElapsed, activeElapsedSeconds = newActive)
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

        val perMinute = TokenEngine.calculateCost(ActionType.CALL_MADE, durationUnits = 1, overrides = costOverrides) -
            TokenEngine.calculateCost(ActionType.CALL_MADE, durationUnits = 0, overrides = costOverrides)
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
                if (newBal != null) _uiState.update { st -> st?.copy(currentTokens = newBal) }
            }
        }
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
                if (earpiece != null) audio.setCommunicationDevice(earpiece)
                else audio.clearCommunicationDevice()
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

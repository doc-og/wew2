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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.Manifest
import android.content.pm.PackageManager

/**
 * Registers a self-managed [PhoneAccount] and places cellular calls through [TelecomManager]
 * so the system dialer UI is never shown. In-call controls are handled in-app.
 */
object WewCallManager {

    private const val TAG = "WewCall"
    private const val ACCOUNT_ID = "wew_self_managed"

    /** Retained for audio route reset when a call ends. */
    private var appContext: Context? = null

    private var phoneAccountHandle: PhoneAccountHandle? = null

    private var pendingDisplayLabel: String? = null

    private var activeConnection: android.telecom.Connection? = null

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
     * Places an outgoing call without opening the platform dialer. Requires [Manifest.permission.CALL_PHONE].
     */
    fun placeCall(context: Context, rawNumber: String, displayLabel: String) {
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

        val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, normalized, null)
        val extras = android.os.Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        }

        runCatching {
            telecom.placeCall(uri, extras)
        }.onFailure {
            Log.e(TAG, "placeCall failed", it)
            pendingDisplayLabel = null
            _uiState.value = null
        }
    }

    internal fun onOutgoingConnectionCreated(connection: android.telecom.Connection, number: String) {
        activeConnection = connection
        val label = pendingDisplayLabel?.takeIf { it.isNotBlank() } ?: number
        pendingDisplayLabel = null
        _uiState.value = WewInCallUiState(
            displayName = label,
            phoneNumber = number,
            speakerOn = false
        )
    }

    internal fun onConnectionDisposed() {
        activeConnection = null
        appContext?.let { applySpeakerRoute(it, false) }
        _uiState.value = null
    }

    fun hangUp() {
        runCatching { activeConnection?.disconnect() }
            .onFailure { Log.e(TAG, "hangUp failed", it) }
    }

    fun toggleSpeaker(context: Context) {
        val current = _uiState.value ?: return
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
        return if (stripped.isNullOrBlank()) trimmed else stripped
    }
}

data class WewInCallUiState(
    val displayName: String,
    val phoneNumber: String,
    val speakerOn: Boolean
)

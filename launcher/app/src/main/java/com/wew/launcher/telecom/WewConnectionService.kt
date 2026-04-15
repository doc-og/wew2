package com.wew.launcher.telecom

import android.content.Context
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

/**
 * Self-managed [ConnectionService] so outgoing calls stay inside WeW (no platform dialer).
 */
class WewConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        if (request?.address == null) {
            return Connection.createFailedConnection(
                DisconnectCause(DisconnectCause.ERROR, "invalid request")
            )
        }
        return WewOutgoingConnection(applicationContext, request)
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        WewCallManager.onConnectionDisposed()
    }

    private class WewOutgoingConnection(
        private val appContext: Context,
        private val request: ConnectionRequest
    ) : Connection() {

        private val telephony = appContext.getSystemService(TelephonyManager::class.java)!!
        private var listenerLegacy: PhoneStateListener? = null
        private var callbackApi31: TelephonyCallback? = null

        private var released = false
        private var activated = false

        init {
            setConnectionProperties(PROPERTY_SELF_MANAGED)
            setAddress(request.address, TelecomManager.PRESENTATION_ALLOWED)
            val number = request.address.schemeSpecificPart.orEmpty()
            WewCallManager.onOutgoingConnectionCreated(this, number)
            setInitializing()
            setDialing()
            registerCallStateTracking()
        }

        private fun registerCallStateTracking() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                callbackApi31 = cb
                telephony.registerTelephonyCallback(appContext.mainExecutor, cb)
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                listenerLegacy = listener
                @Suppress("DEPRECATION")
                telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }

        private fun handleCallState(state: Int) {
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (!activated && !released) {
                        activated = true
                        setActive()
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (activated && !released) {
                        release(DisconnectCause(DisconnectCause.REMOTE))
                    }
                }
            }
        }

        private fun unregisterTracking() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callbackApi31?.let { telephony.unregisterTelephonyCallback(it) }
                callbackApi31 = null
            } else {
                listenerLegacy?.let {
                    @Suppress("DEPRECATION")
                    telephony.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                listenerLegacy = null
            }
        }

        private fun release(cause: DisconnectCause) {
            if (released) return
            released = true
            unregisterTracking()
            setDisconnected(cause)
            destroy()
            WewCallManager.onConnectionDisposed()
        }

        override fun onDisconnect() {
            super.onDisconnect()
            release(DisconnectCause(DisconnectCause.LOCAL))
        }

        override fun onAbort() {
            super.onAbort()
            release(DisconnectCause(DisconnectCause.CANCELED))
        }
    }
}

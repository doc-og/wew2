package com.wew.launcher.telecom

import android.telecom.CallScreeningService
import android.util.Log

/**
 * When WeW is the user's [CallScreeningService], unknown callers (not parent / approved contacts)
 * are rejected before the child sees the system dialer. The parent receives an FCM alert.
 */
class WewCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: android.telecom.CallScreeningService.CallDetails) {
        val prefs = applicationContext.getSharedPreferences("wew_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null).orEmpty()
        val handle = callDetails.handle
        val raw = handle?.schemeSpecificPart

        val allowed = WewPhoneAllowlist.isAllowed(prefs, raw)
        if (allowed) {
            respondToCall(
                callDetails,
                android.telecom.CallScreeningService.CallResponse.Builder().build()
            )
            return
        }

        Log.i(TAG, "rejecting unknown incoming: $raw")
        ParentPushNotifier.notifyUnknownIncomingCall(deviceId, raw)
        respondToCall(
            callDetails,
            android.telecom.CallScreeningService.CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .build()
        )
        WewCallManager.showBlockedUnknownCall(raw)
    }

    companion object {
        private const val TAG = "WewCallScreening"
    }
}

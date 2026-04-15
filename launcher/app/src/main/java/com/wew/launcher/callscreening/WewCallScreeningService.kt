package com.wew.launcher.callscreening

import android.telecom.CallScreeningService
import android.util.Log
import com.wew.launcher.telecom.ParentPushNotifier
import com.wew.launcher.telecom.WewCallManager
import com.wew.launcher.telecom.WewPhoneAllowlist

/**
 * When WeW is the user's call screening app, unknown callers (not parent / approved contacts)
 * are rejected before the child sees the system dialer. The parent receives an FCM alert.
 *
 * Lives in [com.wew.launcher.callscreening] (not …telecom) so Kotlin resolves
 * [CallScreeningService.CallDetails] correctly.
 */
class WewCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: CallScreeningService.CallDetails) {
        val prefs = applicationContext.getSharedPreferences("wew_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null).orEmpty()
        val handle = callDetails.handle
        val raw = handle?.schemeSpecificPart

        val allowed = WewPhoneAllowlist.isAllowed(prefs, raw)
        if (allowed) {
            respondToCall(
                callDetails,
                CallScreeningService.CallResponse.Builder().build()
            )
            return
        }

        Log.i(TAG, "rejecting unknown incoming: $raw")
        ParentPushNotifier.notifyUnknownIncomingCall(deviceId, raw)
        respondToCall(
            callDetails,
            CallScreeningService.CallResponse.Builder()
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

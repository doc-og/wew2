package com.wew.launcher.callscreening

import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallDetails
import android.telecom.CallScreeningService.CallResponse
import android.util.Log
import com.wew.launcher.telecom.ParentPushNotifier
import com.wew.launcher.telecom.WewCallManager
import com.wew.launcher.telecom.WewPhoneAllowlist

/**
 * Unknown callers (not on parent + approved allowlist) are rejected; parent gets FCM.
 * Uses explicit nested imports so Kotlin resolves [CallDetails] / [CallResponse].
 */
class WewCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: CallDetails) {
        val prefs = applicationContext.getSharedPreferences("wew_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null).orEmpty()
        val handle = callDetails.handle
        val raw = handle?.schemeSpecificPart

        if (WewPhoneAllowlist.isAllowed(prefs, raw)) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        Log.i(TAG, "rejecting unknown incoming: $raw")
        ParentPushNotifier.notifyUnknownIncomingCall(deviceId, raw)
        respondToCall(
            callDetails,
            CallResponse.Builder()
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

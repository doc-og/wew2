package com.wew.launcher.callscreening;

import android.net.Uri;
import android.telecom.CallScreeningService;
import android.telecom.CallScreeningService.CallDetails;
import android.telecom.CallScreeningService.CallResponse;
import android.util.Log;

import com.wew.launcher.telecom.ParentPushNotifier;
import com.wew.launcher.telecom.WewCallManager;
import com.wew.launcher.telecom.WewPhoneAllowlist;

/**
 * When WeW is the call screening app, unknown callers are rejected and the parent gets FCM.
 * Implemented in Java because Kotlin does not resolve {@link CallDetails} in this module.
 */
public final class WewCallScreeningService extends CallScreeningService {

    private static final String TAG = "WewCallScreening";

    @Override
    public void onScreenCall(CallDetails callDetails) {
        var prefs = getApplicationContext().getSharedPreferences("wew_prefs", MODE_PRIVATE);
        String deviceId = prefs.getString("device_id", "");
        if (deviceId == null) deviceId = "";

        Uri handle = callDetails.getHandle();
        String raw = handle != null ? handle.getSchemeSpecificPart() : null;

        if (WewPhoneAllowlist.INSTANCE.isAllowed(prefs, raw)) {
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        Log.i(TAG, "rejecting unknown incoming: " + raw);
        ParentPushNotifier.INSTANCE.notifyUnknownIncomingCall(deviceId, raw);
        respondToCall(
                callDetails,
                new CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .build()
        );
        WewCallManager.INSTANCE.showBlockedUnknownCall(raw);
    }
}

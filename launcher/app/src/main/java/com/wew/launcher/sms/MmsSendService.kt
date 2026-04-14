package com.wew.launcher.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * MmsSendService — stub HeadlessSmsSendService required by Android to register
 * this app as a default SMS app candidate.
 *
 * Android requires an app to declare a service with the RESPOND_VIA_MESSAGE intent
 * filter. In practice this is invoked when the user tries to "respond via message"
 * from an incoming call screen. The actual send is delegated to SmsRepository.
 */
class MmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MmsSendService", "onStartCommand: ${intent?.action}")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

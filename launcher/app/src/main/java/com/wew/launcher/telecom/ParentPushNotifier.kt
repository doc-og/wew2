package com.wew.launcher.telecom

import android.util.Log
import com.wew.launcher.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Fire-and-forget FCM to parent via Supabase edge function (same contract as check-in). */
object ParentPushNotifier {

    private const val TAG = "WewParentPush"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(Android)

    fun notifyUnknownIncomingCall(deviceId: String, callerNumber: String?) {
        if (deviceId.isBlank()) return
        scope.launch {
            runCatching {
                val base = BuildConfig.SUPABASE_URL.trimEnd('/')
                val key = BuildConfig.SUPABASE_ANON_KEY
                val payload = buildJsonObject {
                    put("deviceId", deviceId)
                    put("type", "unknown_incoming_call")
                    put(
                        "data",
                        buildJsonObject {
                            put("caller", callerNumber ?: "unknown")
                        }
                    )
                }
                client.post("$base/functions/v1/send-fcm-notification") {
                    header("apikey", key)
                    header("Authorization", "Bearer $key")
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }
            }.onFailure { Log.e(TAG, "notify failed: ${it.message}", it) }
        }
    }
}

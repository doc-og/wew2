package com.wew.launcher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.AppRecord
import com.wew.launcher.data.model.ContactAuthRequest
import com.wew.launcher.data.model.ConversationMeta
import com.wew.launcher.data.model.Device
import com.wew.launcher.data.model.DevicePasscodeRecord
import com.wew.launcher.data.model.LocationLog
import com.wew.launcher.data.model.MessageLog
import com.wew.launcher.data.model.SupabaseSystemMessage
import com.wew.launcher.data.model.Schedule
import com.wew.launcher.data.model.TempAppAccess
import com.wew.launcher.data.model.TokenActionCost
import com.wew.launcher.data.model.TokenLedger
import com.wew.launcher.data.model.TokenRequest
import com.wew.launcher.data.model.UrlAccessRequest
import com.wew.launcher.data.model.UrlFilter
import com.wew.launcher.data.model.WewContact
import com.wew.launcher.sms.MessagingCapability
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceRepository(private val context: Context) {

    private val supabase get() = SupabaseClient.client

    // ── Device ────────────────────────────────────────────────────────────────

    suspend fun getDevice(deviceId: String): Device {
        return supabase.postgrest["devices"]
            .select(Columns.ALL) { filter { eq("id", deviceId) } }
            .decodeSingle()
    }

    suspend fun updateFcmToken(deviceId: String, token: String) {
        supabase.postgrest["devices"].update(
            buildJsonObject { put("fcm_token", token) }
        ) { filter { eq("id", deviceId) } }
    }

    suspend fun updateLastSeen(deviceId: String) {
        supabase.postgrest["devices"].update(
            buildJsonObject { put("last_seen_at", "now()") }
        ) { filter { eq("id", deviceId) } }
    }

    /** Reports default-SMS role and SMS runtime permissions for the parent app. */
    suspend fun syncMessagingHealth(deviceId: String) {
        runCatching {
            val defaultSms = MessagingCapability.isDefaultSmsApp(context)
            val permsOk = MessagingCapability.hasCoreSmsRuntimePermissions(context)
            supabase.postgrest["devices"].update(
                buildJsonObject {
                    put("child_default_sms_app", defaultSms)
                    put("child_sms_permissions_ok", permsOk)
                    put("child_messaging_health_at", "now()")
                }
            ) { filter { eq("id", deviceId) } }
        }.onFailure { Log.e("WewSync", "syncMessagingHealth: ${it.message}") }
    }

    /** Sync Android SMS thread_id for the WeW Parent conversation (enables server daily summaries). */
    suspend fun updateParentSmsThreadId(deviceId: String, threadId: Long) {
        runCatching {
            supabase.postgrest["devices"].update(
                buildJsonObject { put("parent_sms_thread_id", threadId.toString()) }
            ) { filter { eq("id", deviceId) } }
        }.onFailure { Log.e("WewSync", "updateParentSmsThreadId: ${it.message}") }
    }

    /** System rows (e.g. daily_summary) for merging into the on-device parent SMS thread. */
    suspend fun getSystemMessagesForThread(deviceId: String, threadId: Long): List<SupabaseSystemMessage> {
        return runCatching {
            supabase.postgrest["messages"]
                .select(Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("thread_id", threadId.toString())
                        eq("sender_type", "system")
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<SupabaseSystemMessage>()
        }.getOrElse {
            Log.w("WewSync", "getSystemMessagesForThread: ${it.message}")
            emptyList()
        }
    }

    fun observeDevice(deviceId: String): Flow<Device> = flow {
        val channel = supabase.realtime.channel("device-$deviceId")
        channel.subscribe()
        emit(getDevice(deviceId))
    }

    // ── Token system ──────────────────────────────────────────────────────────

    /**
     * Consume [amount] tokens for an action on [deviceId].
     *
     * Writes a token_ledger entry and updates devices.current_tokens atomically
     * (as close as we can get without an RPC in supabase-kt 2.1.4).
     * Also logs to activity_log for the parent dashboard.
     *
     * @return The new token balance, or null on failure.
     */
    suspend fun consumeTokens(
        deviceId: String,
        amount: Int,
        actionType: String,
        appPackage: String? = null,
        appName: String? = null,
        contextMetadata: Map<String, String> = emptyMap()
    ): Result<Int> = runCatching {
        val device = getDevice(deviceId)
        val newBalance = maxOf(0, device.currentTokens - amount)

        // Ledger entry
        supabase.postgrest["token_ledger"].insert(
            buildJsonObject {
                put("device_id", deviceId)
                put("action_type", actionType)
                put("tokens_consumed", amount)
                put("balance_after", newBalance)
                if (contextMetadata.isNotEmpty()) {
                    put(
                        "context_metadata",
                        buildJsonObject { contextMetadata.forEach { (k, v) -> put(k, v) } }
                    )
                }
            }
        )

        // Update device token balance
        supabase.postgrest["devices"].update(
            buildJsonObject { put("current_tokens", newBalance) }
        ) { filter { eq("id", deviceId) } }

        // Activity log (parent dashboard visibility)
        supabase.postgrest["activity_log"].insert(
            ActivityLog(
                deviceId = deviceId,
                actionType = actionType,
                appPackage = appPackage,
                appName = appName,
                tokensConsumed = amount
            )
        )

        newBalance
    }

    /** Add tokens to a device (parent grant or daily reset). */
    suspend fun addTokens(deviceId: String, amount: Int, reason: String) {
        runCatching {
            val device = getDevice(deviceId)
            val newBalance = device.currentTokens + amount
            supabase.postgrest["devices"].update(
                buildJsonObject { put("current_tokens", newBalance) }
            ) { filter { eq("id", deviceId) } }
            supabase.postgrest["token_ledger"].insert(
                buildJsonObject {
                    put("device_id", deviceId)
                    put("action_type", reason)
                    put("tokens_consumed", -amount)   // negative = credit
                    put("balance_after", newBalance)
                }
            )
        }.onFailure { Log.e("WewTokens", "addTokens failed: ${it.message}") }
    }

    /** Fetch per-device token cost overrides from Supabase. */
    suspend fun getTokenActionCosts(deviceId: String): List<TokenActionCost> {
        return runCatching {
            supabase.postgrest["token_action_costs"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<TokenActionCost>()
        }.getOrElse { Log.e("WewTokens", "getTokenActionCosts failed: ${it.message}"); emptyList() }
    }

    /**
     * Convert per-device TokenActionCost rows into the flat override map
     * expected by TokenEngine.calculateCost().
     */
    suspend fun getTokenCostOverrides(deviceId: String): Map<String, Int> {
        return getTokenActionCosts(deviceId).flatMap { cost ->
            listOf(
                "${cost.actionType}_base" to cost.baseCost,
                "${cost.actionType}_per_unit" to cost.costPerUnit
            )
        }.toMap()
    }

    /** Submit a child's request for additional tokens (shown in parent dashboard). */
    suspend fun requestTokens(
        deviceId: String,
        appPackage: String?,
        appName: String?,
        tokensRequested: Int,
        reason: String?
    ) {
        runCatching {
            supabase.postgrest["token_requests"].insert(
                buildJsonObject {
                    put("device_id", deviceId)
                    appPackage?.let { put("app_package", it) }
                    appName?.let { put("app_name", it) }
                    put("tokens_requested", tokensRequested)
                    reason?.let { put("reason", it) }
                    put("status", "pending")
                }
            )
        }.onFailure { Log.e("WewTokens", "requestTokens failed: ${it.message}") }
    }

    // ── Apps ──────────────────────────────────────────────────────────────────

    suspend fun getWhitelistedApps(deviceId: String): List<AppRecord> {
        return supabase.postgrest["apps"]
            .select(Columns.ALL) {
                filter {
                    eq("device_id", deviceId)
                    eq("is_whitelisted", true)
                }
            }
            .decodeList()
    }

    suspend fun getAppPolicies(deviceId: String): List<AppRecord> {
        return runCatching {
            supabase.postgrest["apps"]
                .select(Columns.ALL) {
                    filter { eq("device_id", deviceId) }
                }
                .decodeList<AppRecord>()
        }.getOrElse {
            Log.e("WewSync", "getAppPolicies failed: ${it.message}", it)
            emptyList()
        }
    }

    suspend fun getAppMediaActionType(deviceId: String, packageName: String): String? {
        return runCatching {
            supabase.postgrest["apps"]
                .select(Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("package_name", packageName)
                    }
                }
                .decodeList<AppRecord>()
                .firstOrNull()
                ?.mediaActionType
        }.getOrNull()
    }

    suspend fun syncAppList(deviceId: String, context: Context) {
        val pm = context.packageManager
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        // Refresh inventory (name, system flag). Only touch is_whitelisted for built-in default
        // packages so the launcher keeps working; omit the field for everything else so parent
        // approvals in the dashboard are not wiped on each sync.
        val jsonRecords = installedApps.map { appInfo ->
            buildJsonObject {
                put("device_id", deviceId)
                put("package_name", appInfo.packageName)
                put("app_name", pm.getApplicationLabel(appInfo).toString())
                put("is_system_app", (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                put("credit_cost", 1)
                if (DEFAULT_WHITELIST.contains(appInfo.packageName)) {
                    put("is_whitelisted", true)
                }
            }
        }

        jsonRecords.chunked(50).forEach { chunk ->
            supabase.postgrest["apps"].upsert(chunk, onConflict = "device_id,package_name")
        }
    }

    /**
     * Pushes the full installed-app list for the parent dashboard.
     * Throttled to avoid scanning PackageManager on every conversation-list refresh.
     *
     * @param force when true, sync immediately (e.g. opening the app grid).
     */
    suspend fun syncAppListIfStale(
        deviceId: String,
        context: Context,
        force: Boolean = false,
        minIntervalMs: Long = 10 * 60_000L
    ) {
        val prefs = context.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_LAST_APP_LIST_SYNC_MS, 0L)
        if (!force && last > 0L && now - last < minIntervalMs) return
        runCatching {
            syncAppList(deviceId, context)
            prefs.edit().putLong(PREF_LAST_APP_LIST_SYNC_MS, now).apply()
        }.onFailure { Log.e("WewSync", "syncAppListIfStale failed", it) }
    }

    // ── Activity + location logs ──────────────────────────────────────────────

    suspend fun logActivity(log: ActivityLog) {
        supabase.postgrest["activity_log"].insert(log)
    }

    suspend fun logLocation(log: LocationLog) {
        supabase.postgrest["location_log"].insert(log)
    }

    // ── Schedules ─────────────────────────────────────────────────────────────

    suspend fun getSchedules(deviceId: String): List<Schedule> {
        return supabase.postgrest["schedules"]
            .select(Columns.ALL) {
                filter {
                    eq("device_id", deviceId)
                    eq("is_enabled", true)
                }
            }
            .decodeList()
    }

    suspend fun getAccessSchedule(deviceId: String): List<com.wew.launcher.data.model.AccessScheduleDay> {
        return runCatching {
            supabase.postgrest["access_schedule"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<com.wew.launcher.data.model.AccessScheduleDay>()
        }.getOrElse { e ->
            Log.w("WewSchedule", "getAccessSchedule: ${e.message}")
            emptyList()
        }
    }

    // ── Passcode + temp access ────────────────────────────────────────────────

    suspend fun getDevicePasscode(deviceId: String): DevicePasscodeRecord? {
        return runCatching {
            supabase.postgrest["device_passcode"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<DevicePasscodeRecord>()
                .firstOrNull()
        }.getOrElse { Log.e("WewSync", "getDevicePasscode failed: ${it.message}"); null }
    }

    suspend fun grantTempAccess(deviceId: String, packageName: String, expiresAt: String) {
        supabase.postgrest["temporary_app_access"].upsert(
            buildJsonObject {
                put("device_id", deviceId)
                put("package_name", packageName)
                put("expires_at", expiresAt)
                put("granted_by", "passcode")
            },
            onConflict = "device_id,package_name"
        )
    }

    suspend fun getActiveTempAccess(deviceId: String): List<TempAppAccess> {
        return runCatching {
            supabase.postgrest["temporary_app_access"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<TempAppAccess>()
        }.getOrElse { Log.e("WewSync", "getTempAccess failed: ${it.message}"); emptyList() }
    }

    // ── Contacts ──────────────────────────────────────────────────────────────

    suspend fun getContacts(deviceId: String): List<WewContact> {
        return runCatching {
            supabase.postgrest["contacts"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<WewContact>()
        }.getOrElse { Log.e("WewContacts", "getContacts failed: ${it.message}"); emptyList() }
    }

    suspend fun createContact(
        deviceId: String,
        name: String,
        phone: String?,
        email: String?,
        address: String?,
        photoUrl: String?,
        notes: String?
    ): String? {
        return runCatching {
            supabase.postgrest["contacts"].insert(
                buildJsonObject {
                    put("device_id", deviceId)
                    put("name", name)
                    phone?.let { put("phone", it) }
                    email?.let { put("email", it) }
                    address?.let { put("address", it) }
                    photoUrl?.let { put("photo_url", it) }
                    notes?.let { put("notes", it) }
                    put("is_authorized", false)
                }
            )
            supabase.postgrest["contacts"]
                .select(Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("name", name)
                    }
                }
                .decodeList<WewContact>()
                .maxByOrNull { it.createdAt ?: "" }
                ?.id
        }.getOrElse { Log.e("WewContacts", "createContact failed: ${it.message}"); null }
    }

    suspend fun requestContactAuthorization(deviceId: String, contactId: String) {
        runCatching {
            supabase.postgrest["contact_auth_requests"].upsert(
                buildJsonObject {
                    put("device_id", deviceId)
                    put("contact_id", contactId)
                    put("status", "pending")
                },
                onConflict = "device_id,contact_id"
            )
        }.onFailure { Log.e("WewContacts", "requestAuth failed: ${it.message}") }
    }

    suspend fun getAuthRequests(deviceId: String): List<ContactAuthRequest> {
        return runCatching {
            supabase.postgrest["contact_auth_requests"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<ContactAuthRequest>()
        }.getOrElse { Log.e("WewContacts", "getAuthRequests failed: ${it.message}"); emptyList() }
    }

    // ── Conversation metadata (SMS thread metadata in Supabase) ───────────────

    suspend fun upsertConversationMeta(meta: ConversationMeta) {
        runCatching {
            supabase.postgrest["conversations"].upsert(
                buildJsonObject {
                    put("device_id", meta.deviceId)
                    put("thread_id", meta.threadId)
                    meta.displayName?.let { put("display_name", it) }
                    put("is_pinned", meta.isPinned)
                    put("is_muted", meta.isMuted)
                },
                onConflict = "device_id,thread_id"
            )
        }.onFailure { Log.e("WewChat", "upsertConversationMeta failed: ${it.message}") }
    }

    suspend fun getConversationMeta(deviceId: String): List<ConversationMeta> {
        return runCatching {
            supabase.postgrest["conversations"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<ConversationMeta>()
        }.getOrElse { Log.e("WewChat", "getConversationMeta failed: ${it.message}"); emptyList() }
    }

    /** Mirror message metadata to Supabase for parent dashboard visibility. */
    suspend fun logMessageMirror(log: MessageLog) {
        runCatching {
            supabase.postgrest["messages"].insert(log)
        }.onFailure { Log.e("WewChat", "logMessageMirror failed: ${it.message}") }
    }

    // ── URL filtering ─────────────────────────────────────────────────────────

    suspend fun getUrlFilters(deviceId: String): List<UrlFilter> {
        return runCatching {
            supabase.postgrest["url_filters"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<UrlFilter>()
        }.getOrElse { Log.e("WewWeb", "getUrlFilters failed: ${it.message}"); emptyList() }
    }

    suspend fun submitUrlAccessRequest(deviceId: String, url: String, pageTitle: String?) {
        runCatching {
            supabase.postgrest["url_access_requests"].insert(
                buildJsonObject {
                    put("device_id", deviceId)
                    put("url", url)
                    pageTitle?.let { put("page_title", it) }
                    put("status", "pending")
                }
            )
        }.onFailure { Log.e("WewWeb", "submitUrlAccessRequest failed: ${it.message}") }
    }

    suspend fun getPendingUrlAccessRequests(deviceId: String): List<UrlAccessRequest> {
        return runCatching {
            supabase.postgrest["url_access_requests"]
                .select(Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("status", "pending")
                    }
                }
                .decodeList<UrlAccessRequest>()
        }.getOrElse { Log.e("WewWeb", "getPendingUrlRequests failed: ${it.message}"); emptyList() }
    }

    // ── Token request ─────────────────────────────────────────────────────────

    suspend fun submitTokenRequest(request: TokenRequest) {
        runCatching {
            supabase.postgrest["token_requests"].insert(
                buildJsonObject {
                    put("device_id", request.deviceId)
                    request.appPackage?.let { put("app_package", it) }
                    request.appName?.let { put("app_name", it) }
                    put("tokens_requested", request.tokensRequested)
                    request.reason?.let { put("reason", it) }
                    put("status", "pending")
                }
            )
        }.onFailure { Log.e("WewTokens", "submitTokenRequest failed: ${it.message}") }
    }

    companion object {
        private const val PREF_LAST_APP_LIST_SYNC_MS = "last_app_list_sync_ms"

        val DEFAULT_WHITELIST = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.google.android.apps.maps",
            "com.wew.parent",
            "com.wew.launcher.contacts"
        )
    }
}

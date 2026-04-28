package com.wew.launcher.token

import com.wew.launcher.data.model.ActionType
import kotlin.math.max

/**
 * TokenEngine — default costs match [TOKEN_SYSTEM.md] (parent overrides via token_action_costs).
 *
 * Default costs burn the daily budget faster — ~75 discrete average actions per 10,000-token
 * day after the latest 2× multiplier (parent overrides in `token_action_costs` still apply).
 */
object TokenEngine {

    data class ActionCost(
        val baseTokens: Int,
        val tokensPerUnit: Int = 0,
        val unitType: UnitType = UnitType.FLAT
    )

    enum class UnitType { FLAT, PER_MINUTE, PER_MB, PER_MESSAGE }

    data class ConsumeResult(
        val success: Boolean,
        val cost: Int,
        val newBalance: Int
    )

    /** Engine defaults match [TOKEN_SYSTEM.md] — 2× the prior table so each action consumes more. */
    val defaults: Map<ActionType, ActionCost> = mapOf(
        ActionType.SMS_SENT to ActionCost(100),
        ActionType.MMS_SENT to ActionCost(250),
        ActionType.CALL_MADE to ActionCost(1000, 500, UnitType.PER_MINUTE),
        ActionType.CALL_RECEIVED to ActionCost(0, 500, UnitType.PER_MINUTE),
        ActionType.VIDEO_CALL_MADE to ActionCost(750, 750, UnitType.PER_MINUTE),
        ActionType.PHOTO_TAKEN to ActionCost(500),
        ActionType.WEB_SESSION to ActionCost(300, 200, UnitType.PER_MINUTE),
        ActionType.APP_OPEN to ActionCost(130),
        ActionType.TEMP_ACCESS_GRANTED to ActionCost(5000),
        ActionType.VIDEO_WATCHED to ActionCost(1500, 1000, UnitType.PER_MINUTE),
        ActionType.GAME_SESSION to ActionCost(750, 400, UnitType.PER_MINUTE),
        ActionType.SOCIAL_SCROLL to ActionCost(500, 250, UnitType.PER_MINUTE),
        ActionType.AUDIO_STREAMED to ActionCost(200, 80, UnitType.PER_MINUTE),
        ActionType.CHECK_IN to ActionCost(0),
        ActionType.TOKEN_REQUEST to ActionCost(0),
        ActionType.URL_BLOCKED to ActionCost(0),
        ActionType.APP_BLOCKED to ActionCost(0),
        ActionType.SMS_THREAD_MARK_READ to ActionCost(130),
        ActionType.SMS_THREAD_MARK_UNREAD to ActionCost(130),
        ActionType.CONVERSATION_META_CHANGED to ActionCost(130),
        ActionType.SMS_THREAD_DELETE to ActionCost(130),
        ActionType.CHAT_SURFACE_OPEN to ActionCost(130),
        ActionType.LAUNCHER_OVERLAY_OPEN to ActionCost(130),
        ActionType.MAP_SESSION to ActionCost(130),
        ActionType.CONTACT_ATTENTION_ACTION to ActionCost(130),
        ActionType.SETTINGS_TAMPER to ActionCost(0),
        ActionType.DEVICE_ADMIN_REVOKED to ActionCost(0),
        ActionType.LOCK_ACTIVATED to ActionCost(0),
        ActionType.LOCK_DEACTIVATED to ActionCost(0),
        ActionType.TOKEN_EXHAUSTED to ActionCost(0),
        ActionType.CONTACT_REQUESTED to ActionCost(0),
        ActionType.CONTACT_QUARANTINED to ActionCost(0)
    )

    fun calculateCost(
        actionType: ActionType,
        durationUnits: Int = 0,
        overrides: Map<String, Int> = emptyMap()
    ): Int {
        val default = defaults[actionType] ?: ActionCost(0)
        val base = overrides["${actionType.value}_base"] ?: default.baseTokens
        val perUnit = overrides["${actionType.value}_per_unit"] ?: default.tokensPerUnit
        return base + (perUnit * max(0, durationUnits))
    }

    fun canAfford(
        actionType: ActionType,
        currentBalance: Int,
        durationUnits: Int = 0,
        overrides: Map<String, Int> = emptyMap()
    ): Boolean = currentBalance >= calculateCost(actionType, durationUnits, overrides)

    fun consume(
        actionType: ActionType,
        currentBalance: Int,
        durationUnits: Int = 0,
        overrides: Map<String, Int> = emptyMap()
    ): ConsumeResult {
        val cost = calculateCost(actionType, durationUnits, overrides)
        return if (currentBalance >= cost) {
            ConsumeResult(success = true, cost = cost, newBalance = currentBalance - cost)
        } else {
            ConsumeResult(success = false, cost = cost, newBalance = currentBalance)
        }
    }
}

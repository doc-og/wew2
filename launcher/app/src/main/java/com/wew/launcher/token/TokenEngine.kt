package com.wew.launcher.token

import com.wew.launcher.data.model.ActionType
import kotlin.math.max

/**
 * TokenEngine — default costs match [TOKEN_SYSTEM.md] (parent overrides via token_action_costs).
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

    val defaults: Map<ActionType, ActionCost> = mapOf(
        ActionType.SMS_SENT to ActionCost(10),
        ActionType.MMS_SENT to ActionCost(25),
        ActionType.CALL_MADE to ActionCost(100, 50, UnitType.PER_MINUTE),
        ActionType.CALL_RECEIVED to ActionCost(0, 50, UnitType.PER_MINUTE),
        ActionType.VIDEO_CALL_MADE to ActionCost(75, 75, UnitType.PER_MINUTE),
        ActionType.PHOTO_TAKEN to ActionCost(50),
        ActionType.WEB_SESSION to ActionCost(30, 20, UnitType.PER_MINUTE),
        ActionType.APP_OPEN to ActionCost(13),
        ActionType.TEMP_ACCESS_GRANTED to ActionCost(500),
        ActionType.VIDEO_WATCHED to ActionCost(150, 100, UnitType.PER_MINUTE),
        ActionType.GAME_SESSION to ActionCost(75, 40, UnitType.PER_MINUTE),
        ActionType.SOCIAL_SCROLL to ActionCost(50, 25, UnitType.PER_MINUTE),
        ActionType.AUDIO_STREAMED to ActionCost(20, 8, UnitType.PER_MINUTE),
        ActionType.CHECK_IN to ActionCost(0),
        ActionType.TOKEN_REQUEST to ActionCost(0),
        ActionType.URL_BLOCKED to ActionCost(0),
        ActionType.APP_BLOCKED to ActionCost(0),
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

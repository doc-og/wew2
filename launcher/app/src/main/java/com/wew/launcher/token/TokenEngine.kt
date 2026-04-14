package com.wew.launcher.token

import com.wew.launcher.data.model.ActionType

/**
 * TokenEngine — calculates token costs for child actions.
 *
 * Modeled on agentic usage limits: each action type has a flat base cost
 * plus an optional per-unit rate (per minute, per MB, etc.).
 * 10,000 tokens/day is the default budget, providing limited-but-sufficient
 * use during parent-scheduled hours.
 *
 * Default cost table (parent can override per-device via token_action_costs):
 *
 *  Action              Base    Per-unit    Unit
 *  ─────────────────   ─────   ────────    ──────────
 *  SMS sent            10      —           —
 *  MMS sent            50      —           —
 *  Call made           100     50          per minute
 *  Call received        0      50          per minute
 *  Video call          100     100         per minute
 *  Photo taken         100     —           —
 *  Web session open     50     10          per minute
 *  App open              5     —           —
 *  Temp access grant   500     —           —
 *  Check-in              0     —           —
 *  Token request         0     —           —
 *  URL blocked           0     —           —
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

    /** Default costs keyed by ActionType. Used when no per-device override exists. */
    val defaults: Map<ActionType, ActionCost> = mapOf(
        ActionType.SMS_SENT            to ActionCost(10),
        ActionType.MMS_SENT            to ActionCost(50),
        ActionType.CALL_MADE           to ActionCost(100, 50, UnitType.PER_MINUTE),
        ActionType.CALL_RECEIVED       to ActionCost(0,   50, UnitType.PER_MINUTE),
        ActionType.VIDEO_CALL_MADE     to ActionCost(100, 100, UnitType.PER_MINUTE),
        ActionType.PHOTO_TAKEN         to ActionCost(100),
        ActionType.WEB_SESSION         to ActionCost(50,  10, UnitType.PER_MINUTE),
        ActionType.APP_OPEN            to ActionCost(5),
        ActionType.TEMP_ACCESS_GRANTED to ActionCost(500),
        ActionType.CHECK_IN            to ActionCost(0),
        ActionType.TOKEN_REQUEST       to ActionCost(0),
        ActionType.URL_BLOCKED         to ActionCost(0),
        ActionType.APP_BLOCKED         to ActionCost(0),
        ActionType.SETTINGS_TAMPER     to ActionCost(0),
        ActionType.DEVICE_ADMIN_REVOKED to ActionCost(0),
        ActionType.LOCK_ACTIVATED      to ActionCost(0),
        ActionType.LOCK_DEACTIVATED    to ActionCost(0),
        ActionType.TOKEN_EXHAUSTED     to ActionCost(0),
        ActionType.CONTACT_REQUESTED   to ActionCost(0),
        ActionType.CONTACT_QUARANTINED to ActionCost(0)
    )

    /**
     * Calculate total token cost for an action.
     *
     * @param actionType    The action being performed.
     * @param durationUnits Duration in units matching the action's UnitType (e.g. minutes for calls).
     *                      Ignored for FLAT actions.
     * @param overrides     Per-device cost overrides fetched from token_action_costs table.
     *                      Keys are "${actionType.value}_base" and "${actionType.value}_per_unit".
     */
    fun calculateCost(
        actionType: ActionType,
        durationUnits: Int = 0,
        overrides: Map<String, Int> = emptyMap()
    ): Int {
        val default = defaults[actionType] ?: ActionCost(0)
        val base = overrides["${actionType.value}_base"] ?: default.baseTokens
        val perUnit = overrides["${actionType.value}_per_unit"] ?: default.tokensPerUnit
        return base + (perUnit * maxOf(0, durationUnits))
    }

    /**
     * Returns true if [currentBalance] can cover the action cost.
     */
    fun canAfford(
        actionType: ActionType,
        currentBalance: Int,
        durationUnits: Int = 0,
        overrides: Map<String, Int> = emptyMap()
    ): Boolean = currentBalance >= calculateCost(actionType, durationUnits, overrides)

    /**
     * Attempt to consume tokens for an action.
     * Returns a [ConsumeResult] with success=false if balance is insufficient;
     * the balance is never driven below zero.
     */
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

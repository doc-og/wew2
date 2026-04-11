package com.wew.launcher.credit

import com.wew.launcher.data.model.ActionType
import kotlin.math.max

/**
 * CreditEngine — implements the scaling credit cost formula.
 *
 * Base costs:
 *   open app=1, send message=1, call=2, photo taken=2, share photo=5, web link=2
 *
 * Scaling: after 50% of daily budget is consumed, every additional 10% consumed
 * adds +1 to each subsequent action cost.
 *   e.g. at 60% used: costs +1; at 70%: costs +2; at 80%: costs +3, etc.
 */
object CreditEngine {

    /**
     * Calculate the actual credit cost for an action given current usage state.
     *
     * @param actionType The type of action being performed.
     * @param currentBalance Remaining credits.
     * @param dailyBudget Total daily credit budget.
     * @return The credit cost (may be 0 for free actions).
     */
    fun calculateCost(
        actionType: ActionType,
        currentBalance: Int,
        dailyBudget: Int
    ): Int {
        if (actionType.baseCost == 0) return 0

        val usedCredits = dailyBudget - currentBalance
        val usedFraction = if (dailyBudget > 0) usedCredits.toFloat() / dailyBudget else 0f

        val scalingBonus = if (usedFraction > 0.5f) {
            // For every 10% above 50%, add +1 to cost
            val percentageOver50 = ((usedFraction - 0.5f) * 100).toInt()
            percentageOver50 / 10
        } else {
            0
        }

        return max(1, actionType.baseCost + scalingBonus)
    }

    /**
     * Check whether the current balance can afford the action.
     */
    fun canAfford(
        actionType: ActionType,
        currentBalance: Int,
        dailyBudget: Int
    ): Boolean {
        return currentBalance >= calculateCost(actionType, currentBalance, dailyBudget)
    }

    /**
     * Compute new balance after deducting the cost for the action.
     * Returns the new balance, or -1 if insufficient credits.
     */
    fun deduct(
        actionType: ActionType,
        currentBalance: Int,
        dailyBudget: Int
    ): DeductResult {
        val cost = calculateCost(actionType, currentBalance, dailyBudget)
        return if (currentBalance >= cost) {
            DeductResult(success = true, cost = cost, newBalance = currentBalance - cost)
        } else {
            DeductResult(success = false, cost = cost, newBalance = currentBalance)
        }
    }

    data class DeductResult(
        val success: Boolean,
        val cost: Int,
        val newBalance: Int
    )
}

package com.wew.launcher.data

import android.content.SharedPreferences

/**
 * Persists the last-known server token counters so foreground UI can show a faithful balance
 * on cold start before any network completes. Writes happen whenever [DeviceRepository] observes
 * a definitive balance ([getDevice], [DeviceRepository.consumeTokens], [DeviceRepository.addTokens]).
 */
object TokenBalanceCache {

    private const val PREF_CURRENT = "cached_current_tokens"
    private const val PREF_DAILY = "cached_daily_token_budget"
    private const val UNSET = -1

    /** Fallback when nothing has ever been cached (fresh install path). */
    private const val DEFAULT_FOR_FIRST_RUN = 10_000

    fun readSnapshot(prefs: SharedPreferences): Pair<Int, Int> {
        val c = prefs.getInt(PREF_CURRENT, UNSET)
        val d = prefs.getInt(PREF_DAILY, UNSET)
        return when {
            c >= 0 && d >= 0 -> c to d
            c >= 0 -> c to DEFAULT_FOR_FIRST_RUN
            d >= 0 -> d to d
            else -> DEFAULT_FOR_FIRST_RUN to DEFAULT_FOR_FIRST_RUN
        }
    }

    fun write(prefs: SharedPreferences, current: Int, daily: Int) {
        prefs.edit().putInt(PREF_CURRENT, current).putInt(PREF_DAILY, daily).apply()
    }
}

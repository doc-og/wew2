package com.wew.launcher.telecom

import android.content.SharedPreferences

/** Normalized numbers (digits + optional leading +) for matching parent + approved contacts. */
object WewPhoneAllowlist {

    const val PREF_NUMBERS: String = "wew_call_allowlist_normalized"
    const val PREF_INITIALIZED: String = "wew_call_allowlist_initialized"

    fun normalize(phone: String): String =
        phone.replace(Regex("[^\\d+]"), "").let {
            if (it.startsWith("+1") && it.length == 12) it.substring(2) else it
        }

    fun write(prefs: SharedPreferences, numbers: Set<String>) {
        prefs.edit()
            .putStringSet(PREF_NUMBERS, numbers)
            .putBoolean(PREF_INITIALIZED, true)
            .apply()
    }

    /**
     * If the allowlist has never been synced from the app, allow all (avoid blocking before first load).
     * Empty number is never allowed once initialized.
     */
    fun isAllowed(prefs: SharedPreferences, rawNumber: String?): Boolean {
        if (!prefs.getBoolean(PREF_INITIALIZED, false)) return true
        if (rawNumber.isNullOrBlank()) return false
        val set = prefs.getStringSet(PREF_NUMBERS, emptySet()) ?: emptySet()
        return normalize(rawNumber) in set
    }
}

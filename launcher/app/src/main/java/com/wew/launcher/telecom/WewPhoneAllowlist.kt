package com.wew.launcher.telecom

import android.content.SharedPreferences

/** Parent + parent-approved contacts for incoming call screening. */
object WewPhoneAllowlist {

    const val PREF_NUMBERS: String = "wew_call_allowlist_normalized"
    const val PREF_INITIALIZED: String = "wew_call_allowlist_initialized"

    /** Kept for dial-string helpers; matches [PhoneMatch.canonicalForAllowlist]. */
    fun normalize(phone: String): String = PhoneMatch.canonicalForAllowlist(phone)

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
        return set.any { allow -> PhoneMatch.sameSubscriber(allow, rawNumber) }
    }
}

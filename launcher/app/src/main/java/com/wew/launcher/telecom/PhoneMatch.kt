package com.wew.launcher.telecom

/**
 * Matches numbers from the SMS/call stack to parent-approved contacts.
 * Different string forms (+1…, 1…, 10 digits, spaces) must still match the same subscriber.
 */
object PhoneMatch {

    fun digitsOnly(s: String): String = s.filter { it.isDigit() }

    /**
     * True when [a] and [b] are the same subscriber for allowlist / thread checks.
     * Exact digit match, or same last 10 digits when both are long enough (typical NANP case).
     */
    fun sameSubscriber(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val da = digitsOnly(a)
        val db = digitsOnly(b)
        if (da.isEmpty() || db.isEmpty()) return false
        if (da == db) return true
        if (da.length >= 10 && db.length >= 10 && da.takeLast(10) == db.takeLast(10)) return true
        return false
    }

    /** Stored in the call allowlist prefs; [isAllowed] compares with [sameSubscriber]. */
    fun canonicalForAllowlist(phone: String): String {
        val d = digitsOnly(phone)
        return when {
            d.length == 11 && d.startsWith("1") -> d.substring(1)
            else -> d
        }
    }

    /**
     * One entry per subscriber for SMS/MMS send lists. The Telephony provider often
     * stores the same person twice (e.g. +1… and 10-digit); treating them as two
     * recipients sends duplicate messages to the parent.
     */
    fun uniqueSubscribersPreserveOrder(addresses: List<String>): List<String> {
        if (addresses.size <= 1) return addresses
        val out = mutableListOf<String>()
        for (a in addresses) {
            val t = a.trim()
            if (t.isEmpty()) continue
            if (out.none { sameSubscriber(it, t) }) out.add(t)
        }
        return out
    }
}

package com.wew.launcher.telecom

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * Best-effort detection of this device's own number(s) so thread participant lists
 * that include "me" do not fail the parent-approval check.
 */
object DeviceLine {

    fun isLikelyOwnNumber(context: Context, rawAddress: String): Boolean {
        val digits = PhoneMatch.digitsOnly(rawAddress)
        if (digits.length < 10) return false
        val known = collectKnownSelfDigits(context)
        if (known.isEmpty()) return false
        return known.any { self -> PhoneMatch.sameSubscriber(self, rawAddress) }
    }

    private fun collectKnownSelfDigits(context: Context): Set<String> {
        val out = linkedSetOf<String>()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = context.getSystemService(SubscriptionManager::class.java) ?: return@runCatching
                sm.activeSubscriptionInfoList?.forEach { info ->
                    val n = info.number
                    if (!n.isNullOrBlank()) {
                        val d = PhoneMatch.digitsOnly(n)
                        if (d.length >= 10) out.add(d)
                    }
                }
            }
            val tm = context.getSystemService(TelephonyManager::class.java)
            val line1 = tm?.line1Number
            if (!line1.isNullOrBlank()) {
                val d = PhoneMatch.digitsOnly(line1)
                if (d.length >= 10) out.add(d)
            }
        }
        return out
    }
}

package com.wew.launcher.sms

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat

object MessagingCapability {

    fun isDefaultSmsApp(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        }

    /** READ + RECEIVE + SEND — required for reliable in-app SMS. */
    fun hasCoreSmsRuntimePermissions(context: Context): Boolean {
        val perms = listOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        )
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun canSendAndReceiveSms(context: Context): Boolean =
        isDefaultSmsApp(context) && hasCoreSmsRuntimePermissions(context)
}

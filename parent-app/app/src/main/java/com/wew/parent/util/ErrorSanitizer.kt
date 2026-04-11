package com.wew.parent.util

import android.util.Log

private const val TAG = "WewParent"

/**
 * Logs the full exception (including stack trace and raw SDK message) to logcat
 * and returns a safe, user-facing string that never exposes credentials or internals.
 */
fun Throwable.toUserMessage(default: String): String {
    Log.e(TAG, "Error: $default", this)
    val msg = message ?: return default
    return when {
        // Messages thrown by our own code are intentionally safe to surface
        msg.startsWith("Account created") -> msg
        msg.startsWith("Session expired") -> msg

        // Supabase / GoTrue known error strings (these come from the API response body,
        // not from the SDK internals, so they don't contain keys or URLs)
        msg.contains("Invalid login credentials", ignoreCase = true) ->
            "incorrect email or password"
        msg.contains("Email not confirmed", ignoreCase = true) ->
            "please confirm your email then sign in"
        msg.contains("User already registered", ignoreCase = true) ->
            "an account with this email already exists — please sign in"
        msg.contains("Password should be at least", ignoreCase = true) ->
            "password must be at least 6 characters"
        msg.contains("Unable to validate email address", ignoreCase = true) ->
            "please enter a valid email address"

        // Network / connectivity
        msg.contains("UnknownHostException", ignoreCase = true) ||
        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("SocketTimeoutException", ignoreCase = true) ||
        msg.contains("ConnectException", ignoreCase = true) ->
            "network error — check your connection"

        // Anything else: return the caller's default, never the raw SDK message
        else -> default
    }
}

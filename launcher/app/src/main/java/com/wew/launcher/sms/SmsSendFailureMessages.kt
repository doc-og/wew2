package com.wew.launcher.sms

fun smsSendPreconditionMessage(): String =
    "WeW can't send texts yet. Open WeW, finish every step on the permission screen, " +
        "allow SMS when Android asks, and choose WeW as the default messaging app."

fun userMessageForSmsSendFailure(throwable: Throwable): String =
    when (throwable) {
        is SecurityException ->
            "WeW isn't allowed to send this message. Open WeW and complete the permission screen " +
                "(including default SMS app). If it still fails, open system Settings → Apps → WeW → Permissions."
        is IllegalArgumentException ->
            "That phone number doesn't look valid. Check the number and try again."
        else ->
            "This message couldn't be sent. Check signal or Wi‑Fi (for MMS), confirm WeW is the default SMS app, then try again."
    }

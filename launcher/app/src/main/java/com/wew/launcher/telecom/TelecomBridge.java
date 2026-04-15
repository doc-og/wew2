package com.wew.launcher.telecom;

import android.telecom.Connection;

/**
 * Kotlin in package {@code com.wew.launcher.telecom} can mis-resolve {@link android.telecom}
 * types; this Java shim calls {@link Connection#disconnect()} unambiguously.
 */
public final class TelecomBridge {
    private TelecomBridge() {}

    public static void disconnect(Connection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }
}

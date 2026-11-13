/*
 * Ghost Mode integration adapted from AyuGram4A.
 * Original project: https://github.com/AyuGram/AyuGram4A
 * Copyright (C) AyuGram contributors.
 * NimarkoGram integration.
 */
package app.nimarkogram.messenger.ayugram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public final class GhostModeConfig {
    private static final String PREFS = "nimarko_ayu_ghost";
    private static volatile SharedPreferences preferences;

    public static volatile boolean sendReadPackets;
    public static volatile boolean sendOnlinePackets;
    public static volatile boolean sendTypingPackets;
    public static volatile boolean useScheduledMessages;

    static {
        SharedPreferences prefs = getPreferences();
        sendReadPackets = prefs.getBoolean("sendReadPackets", true);
        sendOnlinePackets = prefs.getBoolean("sendOnlinePackets", true);
        sendTypingPackets = prefs.getBoolean("sendTypingPackets", true);
        useScheduledMessages = prefs.getBoolean("useScheduledMessages", false);
    }

    private GhostModeConfig() {
    }

    private static SharedPreferences getPreferences() {
        SharedPreferences local = preferences;
        if (local == null) {
            synchronized (GhostModeConfig.class) {
                local = preferences;
                if (local == null) {
                    local = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                    preferences = local;
                }
            }
        }
        return local;
    }

    public static boolean isGhostModeEnabled() {
        return !sendReadPackets && !sendOnlinePackets && !sendTypingPackets;
    }

    public static synchronized void setGhostMode(boolean enabled) {
        sendReadPackets = !enabled;
        sendOnlinePackets = !enabled;
        sendTypingPackets = !enabled;
        getPreferences().edit()
                .putBoolean("sendReadPackets", sendReadPackets)
                .putBoolean("sendOnlinePackets", sendOnlinePackets)
                .putBoolean("sendTypingPackets", sendTypingPackets)
                .apply();
    }

    public static synchronized void setUseScheduledMessages(boolean enabled) {
        useScheduledMessages = enabled;
        getPreferences().edit().putBoolean("useScheduledMessages", enabled).apply();
    }

    public static boolean shouldScheduleMessages(long peer, int scheduleDate, boolean retry) {
        return isGhostModeEnabled()
                && useScheduledMessages
                && scheduleDate == 0
                && !retry
                && !org.telegram.messenger.DialogObject.isEncryptedDialog(peer);
    }
}

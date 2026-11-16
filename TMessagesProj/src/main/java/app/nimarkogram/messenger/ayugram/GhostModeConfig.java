/*
 * Ghost Mode bridge for the original AyuGram configuration.
 * Keeps NimarkoGram's network hooks and AyuGram's settings on one source of truth.
 */
package app.nimarkogram.messenger.ayugram;

public final class GhostModeConfig {
    private GhostModeConfig() { }
    public static boolean isGhostModeEnabled() { return AyuConfig.isGhostModeActive(); }
    public static void setGhostMode(boolean enabled) { AyuConfig.setGhostMode(enabled); }
    public static void setUseScheduledMessages(boolean enabled) {
        AyuConfig.editor.putBoolean("useScheduledMessages", AyuConfig.useScheduledMessages = enabled).apply();
    }
    public static boolean shouldScheduleMessages(long peer, int scheduleDate, boolean retry) {
        return isGhostModeEnabled() && AyuConfig.useScheduledMessages && scheduleDate == 0
                && !retry && !org.telegram.messenger.DialogObject.isEncryptedDialog(peer);
    }
}

/* Modifications Copyright (C) 2026 Ettacent */

package app.nimarkogram.messenger.badges;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local-only custom emoji statuses assigned by the current user to other users.
 * They are deliberately stored separately from server and plugin badges.
 */
public final class CustomStatusController {
    private static final String PREFS_PREFIX = "nimarko_custom_statuses_";
    private static final String KEY_ENTRIES = "entries";
    private static final Map<Integer, Map<Long, Entry>> CACHE = new HashMap<>();

    private CustomStatusController() {}

    public static final class Entry {
        public final long userId;
        public final String displayName;
        public final String username;
        public final long documentId;
        public final String customText;

        public Entry(long userId, String displayName, String username, long documentId) {
            this(userId, displayName, username, documentId, "Кастомный статус");
        }

        public Entry(long userId, String displayName, String username, long documentId, String customText) {
            this.userId = userId;
            this.displayName = displayName == null ? "" : displayName;
            this.username = username == null ? "" : username;
            this.documentId = documentId;
            this.customText = customText == null || customText.trim().isEmpty() ? "Кастомный статус" : customText.trim();
        }
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                PREFS_PREFIX + account, Context.MODE_PRIVATE);
    }

    private static Map<Long, Entry> entries(int account) {
        synchronized (CACHE) {
            Map<Long, Entry> cached = CACHE.get(account);
            if (cached != null) return cached;

            Map<Long, Entry> loaded = new HashMap<>();
            try {
                JSONArray array = new JSONArray(preferences(account).getString(KEY_ENTRIES, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) continue;
                    long userId = object.optLong("userId");
                    long documentId = object.optLong("documentId");
                    if (userId == 0L) continue;
                    loaded.put(userId, new Entry(userId,
                            object.optString("displayName", ""),
                            object.optString("username", ""), documentId,
                            object.optString("customText", "Кастомный статус")));
                }
            } catch (Throwable ignored) {}
            CACHE.put(account, loaded);
            return loaded;
        }
    }

    private static void persist(int account, Map<Long, Entry> values) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : values.values()) {
                JSONObject object = new JSONObject();
                object.put("userId", entry.userId);
                object.put("displayName", entry.displayName);
                object.put("username", entry.username);
                object.put("documentId", entry.documentId);
                object.put("customText", entry.customText);
                array.put(object);
            }
            preferences(account).edit().putString(KEY_ENTRIES, array.toString()).apply();
        } catch (Throwable ignored) {}
    }

    public static Entry getEntry(int account, long userId) {
        return entries(account).get(userId);
    }

    public static long getDocumentId(int account, long userId) {
        Entry entry = entries(account).get(userId);
        return entry == null ? 0L : entry.documentId;
    }

    public static List<Entry> getAll(int account) {
        return new ArrayList<>(entries(account).values());
    }

    public static void addPending(int account, long userId, String displayName, String username) {
        if (userId == 0L) return;
        boolean changed = false;
        synchronized (CACHE) {
            Map<Long, Entry> values = entries(account);
            if (!values.containsKey(userId)) {
                values.put(userId, new Entry(userId, displayName, username, 0L));
                persist(account, values);
                changed = true;
            }
        }
        if (changed) notifyStatusChanged();
    }

    public static void put(int account, long userId, String displayName, String username, long documentId) {
        if (userId == 0L || documentId == 0L) return;
        synchronized (CACHE) {
            Map<Long, Entry> values = entries(account);
            Entry old = values.get(userId);
            String customText = old == null ? "Кастомный статус" : old.customText;
            values.put(userId, new Entry(userId, displayName, username, documentId, customText));
            persist(account, values);
        }
        notifyStatusChanged();
    }

    public static void setCustomText(int account, long userId, String customText) {
        synchronized (CACHE) {
            Map<Long, Entry> values = entries(account);
            Entry old = values.get(userId);
            if (old == null) return;
            values.put(userId, new Entry(old.userId, old.displayName, old.username, old.documentId, customText));
            persist(account, values);
        }
        notifyStatusChanged();
    }

    public static void remove(int account, long userId) {
        boolean changed;
        synchronized (CACHE) {
            Map<Long, Entry> values = entries(account);
            changed = values.remove(userId) != null;
            if (changed) persist(account, values);
        }
        if (changed) notifyStatusChanged();
    }

    private static void notifyStatusChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (UserConfig.isValidAccount(account)) {
                    NotificationCenter.getInstance(account).postNotificationName(
                            NotificationCenter.updateInterfaces,
                            MessagesController.UPDATE_MASK_EMOJI_STATUS);
                }
            }
        });
    }
}

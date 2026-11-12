package app.nimarkogram.messenger.deletedmessages;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.nimarkogram.messenger.AppConfig;

/**
 * Keeps a small local text snapshot cache so messages can be retained when a
 * Telegram deletion update arrives. This is device-local and never uploaded.
 */
public final class DeletedMessagesController {
    private static final String PREFS = "ayu_deleted_message_history";
    private static final String PENDING_PREFIX = "pending_";
    private static final String DELETED_PREFIX = "deleted_";
    private static final int MAX_PENDING = 2500;
    private static final int MAX_DELETED = 300;

    private DeletedMessagesController() {
    }

    public static final class DeletedMessage {
        public final long dialogId;
        public final int messageId;
        public final int date;
        public final long deletedAt;
        public final String text;

        private DeletedMessage(JSONObject json) {
            dialogId = json.optLong("dialogId");
            messageId = json.optInt("messageId");
            date = json.optInt("date");
            deletedAt = json.optLong("deletedAt");
            text = json.optString("text", "");
        }
    }

    private static SharedPreferences preferences() {
        Context context = ApplicationLoader.applicationContext;
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void onNotification(int account, int eventId, Object... args) {
        if (!AppConfig.showDeletedMessages) {
            return;
        }
        if (eventId == NotificationCenter.didReceiveNewMessages
                || eventId == NotificationCenter.messagesDidLoad
                || eventId == NotificationCenter.messagesDidLoadWithoutProcess) {
            cacheMessages(account, args);
        } else if (eventId == NotificationCenter.messagesDeleted) {
            archiveDeleted(account, args);
        } else if (eventId == NotificationCenter.historyCleared) {
            // A full history clear does not provide a reliable message-id list.
            // Keep already archived copies; only pending snapshots are discarded.
            clearPending(account);
        }
    }

    private static void cacheMessages(int account, Object[] args) {
        SharedPreferences prefs = preferences();
        if (prefs == null || args == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (Object arg : args) {
            if (!(arg instanceof Collection)) {
                continue;
            }
            for (Object value : (Collection<?>) arg) {
                if (!(value instanceof MessageObject)) {
                    continue;
                }
                MessageObject message = (MessageObject) value;
                if (message.messageOwner == null || message.getId() == 0) {
                    continue;
                }
                String text = message.messageOwner.message;
                if (text == null || text.trim().isEmpty()) {
                    continue;
                }
                long dialogId = message.getDialogId();
                JSONObject json = new JSONObject();
                try {
                    json.put("account", account);
                    json.put("dialogId", dialogId);
                    json.put("messageId", message.getId());
                    json.put("date", message.messageOwner.date);
                    json.put("text", text);
                    editor.putString(pendingKey(account, dialogId, message.getId()), json.toString());
                    changed = true;
                } catch (JSONException ignored) {
                }
            }
        }
        if (changed) {
            editor.apply();
            trim(prefs, PENDING_PREFIX + account + "_", MAX_PENDING, "date");
        }
    }

    private static void archiveDeleted(int account, Object[] args) {
        SharedPreferences prefs = preferences();
        if (prefs == null || args == null) {
            return;
        }
        Set<Integer> deletedIds = new HashSet<>();
        long channelId = 0;
        for (Object arg : args) {
            if (arg instanceof Collection) {
                for (Object value : (Collection<?>) arg) {
                    if (value instanceof Number) {
                        deletedIds.add(((Number) value).intValue());
                    }
                }
            } else if (arg instanceof Number && channelId == 0) {
                channelId = ((Number) arg).longValue();
            }
        }
        if (deletedIds.isEmpty()) {
            return;
        }

        Map<String, ?> all = prefs.getAll();
        SharedPreferences.Editor editor = prefs.edit();
        long deletedAt = System.currentTimeMillis();
        boolean changed = false;
        String prefix = PENDING_PREFIX + account + "_";
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                JSONObject json = new JSONObject((String) entry.getValue());
                int messageId = json.optInt("messageId");
                long dialogId = json.optLong("dialogId");
                if (!deletedIds.contains(messageId)) {
                    continue;
                }
                if (channelId != 0 && dialogId != -channelId) {
                    continue;
                }
                json.put("deletedAt", deletedAt);
                String archivedKey = DELETED_PREFIX + account + "_" + deletedAt + "_" + dialogId + "_" + messageId;
                editor.putString(archivedKey, json.toString());
                editor.remove(entry.getKey());
                changed = true;
            } catch (JSONException ignored) {
            }
        }
        if (changed) {
            editor.apply();
            trim(prefs, DELETED_PREFIX + account + "_", MAX_DELETED, "deletedAt");
        }
    }

    private static String pendingKey(int account, long dialogId, int messageId) {
        return PENDING_PREFIX + account + "_" + dialogId + "_" + messageId;
    }

    private static void clearPending(int account) {
        SharedPreferences prefs = preferences();
        if (prefs == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        String prefix = PENDING_PREFIX + account + "_";
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private static void trim(SharedPreferences prefs, String prefix, int maxCount, String dateField) {
        Map<String, ?> all = prefs.getAll();
        ArrayList<Map.Entry<String, ?>> entries = new ArrayList<>();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof String) {
                entries.add(entry);
            }
        }
        if (entries.size() <= maxCount) {
            return;
        }
        Collections.sort(entries, Comparator.comparingLong(entry -> {
            try {
                return new JSONObject((String) entry.getValue()).optLong(dateField);
            } catch (JSONException ignored) {
                return 0L;
            }
        }));
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < entries.size() - maxCount; i++) {
            editor.remove(entries.get(i).getKey());
        }
        editor.apply();
    }

    public static synchronized List<DeletedMessage> getRecentMessages(int account, int limit) {
        SharedPreferences prefs = preferences();
        if (prefs == null) {
            return Collections.emptyList();
        }
        ArrayList<DeletedMessage> result = new ArrayList<>();
        String prefix = DELETED_PREFIX + account + "_";
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !(entry.getValue() instanceof String)) {
                continue;
            }
            try {
                result.add(new DeletedMessage(new JSONObject((String) entry.getValue())));
            } catch (JSONException ignored) {
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.deletedAt, a.deletedAt));
        if (result.size() > Math.max(0, limit)) {
            return new ArrayList<>(result.subList(0, Math.max(0, limit)));
        }
        return result;
    }

    public static synchronized void clearDeletedMessages(int account) {
        SharedPreferences prefs = preferences();
        if (prefs == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        String prefix = DELETED_PREFIX + account + "_";
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}

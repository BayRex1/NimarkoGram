package app.nimarkogram.messenger.gifts;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Puts MoGram gifts into private chats as native gift action messages, so the animation plays there.
 * Sender: the message is added as soon as the gift is sent.
 * Recipient: a poller asks api.mogram for transfers received since the last one shown.
 */
public final class MoGramChat {

    private static final long POLL_MS = 30_000;
    private static final String PREFS = "mogram_gifts_chat";
    private static final String KEY_CURSOR = "cursor";

    private static boolean started;

    private MoGramChat() {
    }

    /** Starts the inbox poller once. Safe to call from every launch. */
    public static void start(int account) {
        synchronized (MoGramChat.class) {
            if (started) {
                return;
            }
            started = true;
        }
        poll(account);
    }

    private static void poll(int account) {
        new Thread(() -> {
            try {
                syncInbox(account);
            } catch (Throwable ignored) {
                // Offline or api asleep: try again on the next tick.
            }
        }).start();
        AndroidUtilities.runOnUIThread(() -> poll(account), POLL_MS);
    }

    private static void syncInbox(int account) throws Exception {
        UserConfig config = UserConfig.getInstance(account);
        if (!config.isClientActivated()) {
            return;
        }
        Context context = org.telegram.messenger.ApplicationLoader.applicationContext;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long me = config.getClientUserId();
        String cursor = prefs.getString(KEY_CURSOR, null);

        if (cursor == null) {
            // First run on this device: remember where the inbox is, do not replay old gifts into chats.
            List<MoGramGiftsApi.Transfer> existing = MoGramGiftsApi.fetchInbox(me, null);
            if (!existing.isEmpty()) {
                prefs.edit().putString(KEY_CURSOR, existing.get(existing.size() - 1).id).apply();
            } else {
                prefs.edit().putString(KEY_CURSOR, "").apply();
            }
            return;
        }

        List<MoGramGiftsApi.Transfer> incoming = MoGramGiftsApi.fetchInbox(me, cursor.isEmpty() ? null : cursor);
        for (MoGramGiftsApi.Transfer t : incoming) {
            TL_stars.StarGift gift = MoGramStarGifts.giftFor(t.giftId);
            if (gift == null) {
                // Gift file not synced yet: stop here and retry the same transfer next time.
                break;
            }
            long fromId = Long.parseLong(t.fromId);
            int date = (int) (System.currentTimeMillis() / 1000);
            AndroidUtilities.runOnUIThread(() -> deliver(account, fromId, false, gift, t.id, date));
            prefs.edit().putString(KEY_CURSOR, t.id).apply();
        }
    }

    /**
     * Adds a gift message to the private chat with otherUserId.
     * outgoing=true for the sender, false for the recipient.
     */
    public static void deliver(int account, long otherUserId, boolean outgoing, TL_stars.StarGift gift, String transferId, int date) {
        long me = UserConfig.getInstance(account).getClientUserId();

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = messageIdFor(transferId);
        message.out = outgoing;
        message.date = date;
        message.message = "";

        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = otherUserId;
        message.peer_id = peer;

        TLRPC.TL_peerUser from = new TLRPC.TL_peerUser();
        from.user_id = outgoing ? me : otherUserId;
        message.from_id = from;

        TLRPC.TL_messageActionStarGift action = new TLRPC.TL_messageActionStarGift();
        action.gift = gift;
        action.name_hidden = false;
        message.action = action;

        ArrayList<MessageObject> list = new ArrayList<>();
        list.add(new MessageObject(account, message, new HashMap<Long, TLRPC.User>(), false, false));
        MessagesController.getInstance(account).updateInterfaceWithMessages(otherUserId, list, 0);
    }

    /** Stable negative id per transfer: unique in the chat and never clashes with server messages. */
    private static int messageIdFor(String transferId) {
        return -1_000_000 - (Math.abs(transferId.hashCode()) % 1_000_000_000);
    }

}

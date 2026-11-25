package app.nimarkogram.messenger.gifts;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Adds MoGram gifts received by a user to that user's gift list in the profile, next to Telegram gifts.
 * The rows are SavedStarGift objects, so the regular gift grid draws them with the same cell.
 */
public final class MoGramProfileGifts {

    private MoGramProfileGifts() {
    }

    /** Fetches the user's MoGram gifts and merges them into the list. Runs off the UI thread. */
    public static void attach(StarsController.GiftsList list) {
        // dialogId 0 is the viewer's own profile; positive ids are users, negative ids are channels.
        final long userId = list.dialogId == 0 ? UserConfig.getInstance(list.currentAccount).getClientUserId() : list.dialogId;
        if (userId <= 0) {
            return;
        }
        new Thread(() -> {
            try {
                List<MoGramGiftsApi.Transfer> transfers = MoGramGiftsApi.fetchReceived(userId);
                ArrayList<TL_stars.SavedStarGift> items = new ArrayList<>();
                for (MoGramGiftsApi.Transfer t : transfers) {
                    TL_stars.StarGift gift = MoGramStarGifts.giftFor(t.giftId);
                    if (gift == null) {
                        continue;
                    }
                    TL_stars.SavedStarGift saved = new TL_stars.SavedStarGift();
                    saved.gift = gift;
                    TLRPC.TL_peerUser from = new TLRPC.TL_peerUser();
                    from.user_id = Long.parseLong(t.fromId);
                    saved.from_id = from;
                    saved.date = parseSeconds(t.createdAt);
                    saved.name_hidden = false;
                    // Negative id: never clashes with Telegram's saved gift ids.
                    saved.saved_id = -1_000_000_000L - Math.abs((long) t.id.hashCode());
                    items.add(saved);
                }
                AndroidUtilities.runOnUIThread(() -> merge(list, userId, items));
            } catch (Throwable ignored) {
                // Offline or api asleep: the profile keeps showing Telegram gifts only.
            }
        }).start();
    }

    private static void merge(StarsController.GiftsList list, long userId, ArrayList<TL_stars.SavedStarGift> items) {
        // Drop the MoGram rows from the previous load, then add the fresh ones.
        Iterator<TL_stars.SavedStarGift> it = list.gifts.iterator();
        while (it.hasNext()) {
            if (it.next().saved_id < 0) {
                it.remove();
            }
        }
        list.gifts.addAll(items);
        // Newest first, same as Telegram. Integer.compare keeps this working on older Android versions.
        Collections.sort(list.gifts, (a, b) -> Integer.compare(b.date, a.date));
        NotificationCenter.getInstance(list.currentAccount).postNotificationName(NotificationCenter.starUserGiftsLoaded, list.dialogId, list);
    }

    /** "2026-11-19T16:21:18..." -> unix seconds (UTC). Falls back to now if the format is unexpected. */
    private static int parseSeconds(String iso) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            return (int) (format.parse(iso.substring(0, 19)).getTime() / 1000);
        } catch (Throwable e) {
            return (int) (System.currentTimeMillis() / 1000);
        }
    }
}

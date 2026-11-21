package app.nimarkogram.messenger.gifts;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns synced MoGram gifts into native TL_stars.StarGift objects so they show up in the regular
 * gift catalog. Files come from MoGramGiftSync (Downloads/MoGram), so the shop works offline.
 */
public final class MoGramStarGifts {

    // Telegram gift ids are small positive longs; this base keeps ours clear of them.
    private static final long ID_BASE = 9_000_000_000_000L;
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private static final Object lock = new Object();
    private static final List<TL_stars.StarGift> gifts = new ArrayList<>();
    private static final Map<Long, String> catalogIdByTelegramId = new HashMap<>();
    private static volatile boolean refreshing;
    private static volatile long lastRefreshOk;

    private MoGramStarGifts() {
    }

    static long telegramIdFor(String catalogId) {
        return ID_BASE + (Math.abs((long) catalogId.hashCode()) % 1_000_000_000L);
    }

    public static boolean isMoGram(TL_stars.StarGift gift) {
        synchronized (lock) {
            return gift != null && catalogIdByTelegramId.containsKey(gift.id);
        }
    }

    /** Telegram gifts only: this is what goes into the on-disk catalog cache. */
    public static ArrayList<TL_stars.StarGift> withoutMoGram(List<TL_stars.StarGift> list) {
        ArrayList<TL_stars.StarGift> result = new ArrayList<>();
        for (TL_stars.StarGift g : list) {
            if (!isMoGram(g)) {
                result.add(g);
            }
        }
        return result;
    }

    /** The native gift object for a catalog id, or null if it is not synced on this device yet. */
    public static TL_stars.StarGift giftFor(String catalogId) {
        long id = telegramIdFor(catalogId);
        synchronized (lock) {
            for (TL_stars.StarGift g : gifts) {
                if (g.id == id) {
                    return g;
                }
            }
        }
        return null;
    }

    /** Adds MoGram gifts to a catalog list that Telegram just built from its own data. */
    public static void inject(List<TL_stars.StarGift> target) {
        loadLocalIfEmpty(ApplicationLoader.applicationContext);
        synchronized (lock) {
            for (TL_stars.StarGift g : gifts) {
                boolean exists = false;
                for (TL_stars.StarGift t : target) {
                    if (t.id == g.id) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    target.add(g);
                }
            }
        }
    }

    /** Syncs Downloads/MoGram with the server. At most once per few minutes; retries on failure. */
    public static void refreshOnce(Context context, int account) {
        synchronized (lock) {
            if (refreshing || System.currentTimeMillis() - lastRefreshOk < REFRESH_INTERVAL_MS) {
                return;
            }
            refreshing = true;
        }
        new Thread(() -> {
            boolean ok = false;
            try {
                List<MoGramGiftSync.Ready> ready = MoGramGiftSync.sync(context);
                Map<Long, String> ids = new HashMap<>();
                setGifts(buildGifts(account, ready, ids), ids);
                lastRefreshOk = System.currentTimeMillis();
                ok = true;
            } catch (Throwable ignored) {
                // Keep what we have; the next shop open tries again.
            } finally {
                refreshing = false;
            }
            if (ok) {
                AndroidUtilities.runOnUIThread(() -> StarsController.getInstance(account).invalidateStarGifts());
            }
        }).start();
    }

    private static void loadLocalIfEmpty(Context context) {
        synchronized (lock) {
            if (!gifts.isEmpty() || context == null) {
                return;
            }
        }
        try {
            Map<Long, String> ids = new HashMap<>();
            setGifts(buildGifts(UserConfig.selectedAccount, MoGramGiftSync.loadLocal(context), ids), ids);
        } catch (Throwable ignored) {
            // Nothing on disk yet; the network sync fills it in.
        }
    }

    private static List<TL_stars.StarGift> buildGifts(int account, List<MoGramGiftSync.Ready> ready, Map<Long, String> ids) {
        List<TL_stars.StarGift> built = new ArrayList<>();
        for (MoGramGiftSync.Ready r : ready) {
            long id = telegramIdFor(r.id);
            ids.put(id, r.id);
            // The catalog cell only draws the sticker for TL_starGift (the regular gift type).
            TL_stars.TL_starGift sg = new TL_stars.TL_starGift();
            sg.id = id;
            sg.gift_id = id;
            sg.stars = r.priceStars;
            sg.title = r.title;
            sg.limited = false;
            sg.sold_out = false;
            sg.sticker = animationDocument(account, id, r.renderFile);
            built.add(sg);
        }
        return built;
    }

    private static void setGifts(List<TL_stars.StarGift> built, Map<Long, String> ids) {
        synchronized (lock) {
            gifts.clear();
            gifts.addAll(built);
            catalogIdByTelegramId.clear();
            catalogIdByTelegramId.putAll(ids);
        }
    }

    private static String catalogIdFor(long telegramId) {
        synchronized (lock) {
            return catalogIdByTelegramId.get(telegramId);
        }
    }

    private static TLRPC.Document animationDocument(int account, long id, File file) {
        TLRPC.TL_document doc = new TLRPC.TL_document();
        doc.id = id;
        doc.access_hash = 0;
        doc.file_reference = new byte[0];
        doc.date = (int) (System.currentTimeMillis() / 1000);
        doc.mime_type = "application/x-tgsticker";
        doc.size = file.length();
        doc.dc_id = 0;
        doc.file_name = file.getName();
        doc.attributes.add(new TLRPC.TL_documentAttributeAnimated());
        // Telegram only treats a .tgs document as an animated sticker when it has a thumb entry.
        TLRPC.TL_photoSizeEmpty thumb = new TLRPC.TL_photoSizeEmpty();
        thumb.type = "m";
        doc.thumbs.add(thumb);
        doc.localPath = file.getAbsolutePath();
        try {
            // Also place a copy where Telegram's cache lookup expects it.
            File cacheFile = FileLoader.getInstance(account).getPathToAttach(doc, true);
            if (cacheFile != null && (!cacheFile.exists() || cacheFile.length() != file.length())) {
                File parent = cacheFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                copyFile(file, cacheFile);
            }
        } catch (Throwable ignored) {
            // The shop renders from localPath anyway.
        }
        return doc;
    }

    private static void copyFile(File from, File to) throws Exception {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    /** Purchase goes to api.mogram instead of Telegram payments. The price is 0 stars. */
    public static void buy(int account, TL_stars.StarGift gift, long toUserId, Utilities.Callback2<Boolean, String> whenDone) {
        final String catalogId = catalogIdFor(gift.id);
        final long fromId = UserConfig.getInstance(account).getClientUserId();
        new Thread(() -> {
            try {
                MoGramGiftsApi.Transfer transfer = MoGramGiftsApi.send(fromId, catalogId, toUserId);
                AndroidUtilities.runOnUIThread(() -> {
                    // The gift appears in the chat right away, and the animation plays there.
                    MoGramChat.deliver(account, toUserId, true, gift, transfer.id, (int) (System.currentTimeMillis() / 1000));
                    // Same success banner Telegram shows after a real gift, with the animated sticker.
                    BulletinFactory.global().createEmojiBulletin(gift.sticker, LocaleController.getString(R.string.StarsGiftCompleted), gift.title).show();
                    whenDone.run(true, null);
                });
            } catch (MoGramGiftsApi.ApiException e) {
                final String msg = e.getMessage();
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, msg));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "network"));
            }
        }).start();
    }
}

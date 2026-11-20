package app.nimarkogram.messenger.gifts;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the MoGram catalog into native TL_stars.StarGift objects so they appear in the
 * regular gift catalog with the same sheet, animation and purchase flow as Telegram gifts.
 * Animation files are attached through TLRPC.Document.localPath, so Telegram never downloads them.
 */
public final class MoGramStarGifts {

    // Telegram gift ids are small positive longs; this base keeps ours clear of them.
    private static final long ID_BASE = 9_000_000_000_000L;

    private static final Object lock = new Object();
    private static final List<TL_stars.StarGift> gifts = new ArrayList<>();
    private static final Map<Long, MoGramGiftsApi.Gift> catalogById = new HashMap<>();
    private static volatile boolean requested;

    private MoGramStarGifts() {
    }

    static long telegramIdFor(String catalogId) {
        return ID_BASE + (Math.abs((long) catalogId.hashCode()) % 1_000_000_000L);
    }

    public static boolean isMoGram(TL_stars.StarGift gift) {
        synchronized (lock) {
            return gift != null && catalogById.containsKey(gift.id);
        }
    }

    /** Adds MoGram gifts to a catalog list that Telegram just built from its own server response. */
    public static void inject(List<TL_stars.StarGift> target) {
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

    /** Loads the catalog once per app start and asks StarsController to rebuild its list. */
    public static void refreshOnce(Context context, int account) {
        if (requested) {
            return;
        }
        requested = true;
        new Thread(() -> {
            try {
                List<MoGramGiftsApi.Gift> catalog = MoGramGiftsApi.fetchCatalog();
                List<TL_stars.StarGift> built = new ArrayList<>();
                Map<Long, MoGramGiftsApi.Gift> map = new HashMap<>();
                for (MoGramGiftsApi.Gift g : catalog) {
                    File file = MoGramGiftsApi.ensureFile(context, g);
                    long id = telegramIdFor(g.id);
                    TL_stars.StarGift sg = new TL_stars.StarGift();
                    sg.id = id;
                    sg.gift_id = id;
                    sg.stars = g.priceStars;
                    sg.title = g.title;
                    sg.limited = false;
                    sg.sold_out = false;
                    sg.sticker = animationDocument(account, id, file);
                    built.add(sg);
                    map.put(id, g);
                }
                synchronized (lock) {
                    gifts.clear();
                    gifts.addAll(built);
                    catalogById.clear();
                    catalogById.putAll(map);
                }
                AndroidUtilities.runOnUIThread(() -> StarsController.getInstance(account).invalidateStarGifts());
            } catch (Throwable ignored) {
                // Catalog stays as it was; Telegram gifts still work.
            }
        }).start();
    }

    private static TLRPC.Document animationDocument(int account, long id, File file) throws java.io.IOException {
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
        doc.localPath = file.getAbsolutePath();
        // ImageLoader looks for the animation in the cache folder under Telegram's own name,
        // so copy the .tgs there. Failure here must not break the whole catalog.
        try {
            File cacheFile = org.telegram.messenger.FileLoader.getInstance(account).getPathToAttach(doc, true);
            if (cacheFile != null && (!cacheFile.exists() || cacheFile.length() != file.length())) {
                File parent = cacheFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                copyFile(file, cacheFile);
            }
        } catch (Throwable ignored) {
            // The sticker stays unavailable in the cache, but the rest of the catalog still loads.
        }
        return doc;
    }

    private static void copyFile(File from, File to) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(from);
             java.io.OutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    /** Purchase goes to api.mogram instead of Telegram payments. The price is 0 stars. */
    public static void buy(int account, TL_stars.StarGift gift, long toUserId, Utilities.Callback2<Boolean, String> whenDone) {
        final MoGramGiftsApi.Gift catalogGift;
        synchronized (lock) {
            catalogGift = catalogById.get(gift.id);
        }
        final long fromId = org.telegram.messenger.UserConfig.getInstance(account).getClientUserId();
        new Thread(() -> {
            try {
                MoGramGiftsApi.send(fromId, catalogGift.id, toUserId);
                AndroidUtilities.runOnUIThread(() -> whenDone.run(true, null));
            } catch (MoGramGiftsApi.ApiException e) {
                final String msg = e.getMessage();
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, msg));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> whenDone.run(false, "network"));
            }
        }).start();
    }
}

package app.nimarkogram.messenger.gifts;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.io.ByteArrayOutputStream;
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
 * Turns the MoGram catalog into native TL_stars.StarGift objects so they show up in the regular
 * gift catalog. The catalog is kept on disk, so the shop shows gifts even if api.mogram is asleep.
 */
public final class MoGramStarGifts {

    // Telegram gift ids are small positive longs; this base keeps ours clear of them.
    private static final long ID_BASE = 9_000_000_000_000L;
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final String CATALOG_FILE = "catalog.json";

    private static final Object lock = new Object();
    private static final List<TL_stars.StarGift> gifts = new ArrayList<>();
    private static final Map<Long, MoGramGiftsApi.Gift> catalogById = new HashMap<>();
    private static volatile boolean refreshing;
    private static volatile long lastRefreshOk;

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

    /** Adds MoGram gifts to a catalog list that Telegram just built from its own data. */
    public static void inject(List<TL_stars.StarGift> target) {
        loadFromDiskIfEmpty(ApplicationLoader.applicationContext);
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

    /** Asks api.mogram for the catalog. Runs at most once per few minutes and retries on failure. */
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
                String json = MoGramGiftsApi.fetchCatalogJson();
                List<MoGramGiftsApi.Gift> catalog = MoGramGiftsApi.parseCatalog(json);
                List<TL_stars.StarGift> built = buildGifts(context, account, catalog, true);
                saveCatalogJson(context, json);
                setGifts(built, catalog);
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

    private static void loadFromDiskIfEmpty(Context context) {
        synchronized (lock) {
            if (!gifts.isEmpty() || context == null) {
                return;
            }
        }
        try {
            File f = new File(new File(context.getCacheDir(), "mogram_gifts"), CATALOG_FILE);
            if (!f.exists()) {
                return;
            }
            List<MoGramGiftsApi.Gift> catalog = MoGramGiftsApi.parseCatalog(readFile(f));
            setGifts(buildGifts(context, UserConfig.selectedAccount, catalog, false), catalog);
        } catch (Throwable ignored) {
            // No usable disk copy; the network refresh will fill it in.
        }
    }

    private static List<TL_stars.StarGift> buildGifts(Context context, int account, List<MoGramGiftsApi.Gift> catalog, boolean download) throws Exception {
        List<TL_stars.StarGift> built = new ArrayList<>();
        for (MoGramGiftsApi.Gift g : catalog) {
            File file = download ? MoGramGiftsApi.ensureFile(context, g) : MoGramGiftsApi.cachedFile(context, g);
            if (file == null) {
                continue;
            }
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
        }
        return built;
    }

    private static void setGifts(List<TL_stars.StarGift> built, List<MoGramGiftsApi.Gift> catalog) {
        Map<Long, MoGramGiftsApi.Gift> map = new HashMap<>();
        for (MoGramGiftsApi.Gift g : catalog) {
            map.put(telegramIdFor(g.id), g);
        }
        synchronized (lock) {
            gifts.clear();
            gifts.addAll(built);
            catalogById.clear();
            catalogById.putAll(map);
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

    private static void saveCatalogJson(Context context, String json) throws Exception {
        File dir = new File(context.getCacheDir(), "mogram_gifts");
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        try (OutputStream out = new FileOutputStream(new File(dir, CATALOG_FILE))) {
            out.write(json.getBytes("UTF-8"));
        }
    }

    private static String readFile(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
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
        final MoGramGiftsApi.Gift catalogGift;
        synchronized (lock) {
            catalogGift = catalogById.get(gift.id);
        }
        final long fromId = UserConfig.getInstance(account).getClientUserId();
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

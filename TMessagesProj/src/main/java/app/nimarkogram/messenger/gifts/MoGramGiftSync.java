package app.nimarkogram.messenger.gifts;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps MoGram gift files in Downloads/MoGram and syncs them with the server manifest.
 * Only new or changed files are downloaded. Without network the last synced set is used.
 * On Android 10+ files go through MediaStore, which needs no storage permission.
 * Older systems write to the app's external folder, so no permission prompt is needed there either.
 */
public final class MoGramGiftSync {

    private static final String PREFS = "mogram_gifts_sync";
    private static final String KEY_INDEX = "index";
    private static final String KEY_MANIFEST = "manifest";
    private static final String FOLDER = "MoGram";
    private static final String MIME = "application/octet-stream";

    /** A gift whose file is on disk and ready to draw. renderFile is a plain copy the UI can open. */
    public static final class Ready {
        public final String id;
        public final String title;
        public final int priceStars;
        public final File renderFile;

        Ready(String id, String title, int priceStars, File renderFile) {
            this.id = id;
            this.title = title;
            this.priceStars = priceStars;
            this.renderFile = renderFile;
        }
    }

    /** Where one gift file lives: a MediaStore uri (Android 10+) or a plain path (older). */
    private static final class Stored {
        String sha256;
        String name;
        String uri;
        String path;
    }

    private MoGramGiftSync() {
    }

    /** Gifts already on disk, no network. */
    public static List<Ready> loadLocal(Context context) throws Exception {
        String manifest = prefs(context).getString(KEY_MANIFEST, null);
        if (manifest == null) {
            return new ArrayList<>();
        }
        return buildReady(context, MoGramGiftsApi.parseManifest(manifest), loadIndex(context));
    }

    /** Brings storage in line with the server and returns the gifts ready to show. */
    public static List<Ready> sync(Context context) throws Exception {
        List<MoGramGiftsApi.ManifestItem> items;
        String manifestJson;
        try {
            manifestJson = MoGramGiftsApi.fetchManifestJson();
            items = MoGramGiftsApi.parseManifest(manifestJson);
        } catch (Exception e) {
            // Server asleep or offline: show what we already have.
            return loadLocal(context);
        }

        Map<String, Stored> index = loadIndex(context);
        Set<String> wanted = new HashSet<>();
        for (MoGramGiftsApi.ManifestItem item : items) {
            if (item.sha256 == null) {
                continue;
            }
            wanted.add(item.id);
            Stored old = index.get(item.id);
            if (old != null && item.sha256.equals(old.sha256) && exists(context, old)) {
                continue;
            }
            byte[] bytes = MoGramGiftsApi.download(item.fileUrl);
            if (!item.sha256.equals(sha256(bytes))) {
                throw new Exception("hash mismatch for " + item.id);
            }
            if (old != null) {
                delete(context, old);
            }
            Stored fresh = write(context, item.id + ".tgs", bytes);
            fresh.sha256 = item.sha256;
            index.put(item.id, fresh);
        }

        // Gifts removed on the server: remove their files too.
        Iterator<Map.Entry<String, Stored>> it = index.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Stored> e = it.next();
            if (!wanted.contains(e.getKey())) {
                delete(context, e.getValue());
                it.remove();
            }
        }

        saveIndex(context, index);
        prefs(context).edit().putString(KEY_MANIFEST, manifestJson).apply();
        return buildReady(context, items, index);
    }

    private static List<Ready> buildReady(Context context, List<MoGramGiftsApi.ManifestItem> items, Map<String, Stored> index) throws Exception {
        List<Ready> ready = new ArrayList<>();
        for (MoGramGiftsApi.ManifestItem item : items) {
            Stored s = index.get(item.id);
            if (item.sha256 == null || s == null || !item.sha256.equals(s.sha256)) {
                continue;
            }
            File render = renderCopy(context, item.id, s);
            if (render != null) {
                ready.add(new Ready(item.id, item.title, item.priceStars, render));
            }
        }
        return ready;
    }

    /** Plain copy in the app cache for drawing. Refreshed when the stored file changes. */
    private static File renderCopy(Context context, String id, Stored s) throws Exception {
        File dir = new File(context.getCacheDir(), "mogram_render");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        String shortSha = s.sha256.substring(0, 8);
        File target = new File(dir, id + "-" + shortSha + ".tgs");
        if (target.exists() && target.length() > 0) {
            return target;
        }
        byte[] bytes = read(context, s);
        if (bytes == null) {
            return null;
        }
        // Drop older copies of this gift before writing the new one.
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.getName().startsWith(id + "-")) {
                    f.delete();
                }
            }
        }
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(bytes);
        }
        return target;
    }

    private static Stored write(Context context, String name, byte[] bytes) throws Exception {
        Stored s = new Stored();
        s.name = name;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = context.getContentResolver();
            String rel = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/";
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            // Replace an older row with the same name, so the folder never holds duplicates.
            cr.delete(collection,
                    MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                    new String[]{name, rel});
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, MIME);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
            Uri uri = cr.insert(collection, values);
            if (uri == null) {
                throw new Exception("MediaStore insert failed");
            }
            try (OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) {
                    throw new Exception("MediaStore stream failed");
                }
                out.write(bytes);
            }
            s.uri = uri.toString();
        } else {
            File base = context.getExternalFilesDir(FOLDER);
            if (base == null) {
                base = new File(context.getCacheDir(), FOLDER);
            }
            if (!base.exists() && !base.mkdirs()) {
                throw new Exception("folder failed");
            }
            File file = new File(base, name);
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            s.path = file.getAbsolutePath();
        }
        return s;
    }

    private static boolean exists(Context context, Stored s) {
        try (InputStream in = open(context, s)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] read(Context context, Stored s) throws Exception {
        try (InputStream in = open(context, s)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static InputStream open(Context context, Stored s) throws Exception {
        if (s.uri != null) {
            return context.getContentResolver().openInputStream(Uri.parse(s.uri));
        }
        if (s.path != null) {
            File f = new File(s.path);
            return f.exists() ? new FileInputStream(f) : null;
        }
        return null;
    }

    private static void delete(Context context, Stored s) {
        if (s.uri != null) {
            context.getContentResolver().delete(Uri.parse(s.uri), null, null);
        } else if (s.path != null) {
            new File(s.path).delete();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Map<String, Stored> loadIndex(Context context) throws Exception {
        Map<String, Stored> map = new HashMap<>();
        String raw = prefs(context).getString(KEY_INDEX, null);
        if (raw == null) {
            return map;
        }
        JSONObject o = new JSONObject(raw);
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String id = keys.next();
            JSONObject e = o.getJSONObject(id);
            Stored s = new Stored();
            s.sha256 = e.optString("sha", null);
            s.name = e.optString("name", null);
            s.uri = e.isNull("uri") ? null : e.getString("uri");
            s.path = e.isNull("path") ? null : e.getString("path");
            map.put(id, s);
        }
        return map;
    }

    private static void saveIndex(Context context, Map<String, Stored> index) throws Exception {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, Stored> e : index.entrySet()) {
            JSONObject item = new JSONObject();
            item.put("sha", e.getValue().sha256);
            item.put("name", e.getValue().name);
            item.put("uri", e.getValue().uri == null ? JSONObject.NULL : e.getValue().uri);
            item.put("path", e.getValue().path == null ? JSONObject.NULL : e.getValue().path);
            o.put(e.getKey(), item);
        }
        prefs(context).edit().putString(KEY_INDEX, o.toString()).apply();
    }
}

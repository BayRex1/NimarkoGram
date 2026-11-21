package app.nimarkogram.messenger.gifts;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Network side of MoGram gifts. Blocking calls: run them off the UI thread.
 * Catalog and transfers come from api.mogram; .tgs files come from mogram-gifts.
 */
public final class MoGramGiftsApi {

    public static final String API_BASE = "https://api-mogram.onrender.com";
    private static final int TIMEOUT_MS = 20000;

    public static final class Gift {
        public final String id;
        public final String title;
        public final String fileUrl;
        public final int priceStars;

        Gift(String id, String title, String fileUrl, int priceStars) {
            this.id = id;
            this.title = title;
            this.fileUrl = fileUrl;
            this.priceStars = priceStars;
        }
    }

    public static final class Transfer {
        public final String id;
        public final String fromId;
        public final String toId;
        public final String createdAt;
        public final String giftId;
        public final String giftTitle;
        public final String giftFileUrl;

        Transfer(String id, String fromId, String toId, String createdAt, String giftId, String giftTitle, String giftFileUrl) {
            this.id = id;
            this.fromId = fromId;
            this.toId = toId;
            this.createdAt = createdAt;
            this.giftId = giftId;
            this.giftTitle = giftTitle;
            this.giftFileUrl = giftFileUrl;
        }
    }

    public static class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    private MoGramGiftsApi() {
    }

    private static String http(String method, String path, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API_BASE + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = is == null ? "" : readAll(is);
            if (code >= 400) {
                String error = "http_" + code;
                try {
                    error = new JSONObject(text).optString("error", error);
                } catch (Exception ignored) {
                }
                throw new ApiException(error);
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        try (InputStream in = is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    public static List<Gift> fetchCatalog() throws Exception {
        JSONArray arr = new JSONObject(http("GET", "/v1/gifts/catalog", null)).optJSONArray("gifts");
        List<Gift> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new Gift(
                    o.getString("id"),
                    o.getString("title"),
                    o.getString("file_url"),
                    o.optInt("price_stars", 0)));
        }
        return list;
    }

    public static Transfer send(long fromId, String giftId, long toId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("from_id", String.valueOf(fromId));
        body.put("gift_id", giftId);
        body.put("to_id", String.valueOf(toId));
        JSONObject response = new JSONObject(http("POST", "/v1/gifts/send", body.toString()));
        return parseTransfer(response.optJSONObject("transfer"));
    }

    /** Gifts this user received (from, to and gift title are in every row). */
    public static List<Transfer> fetchReceived(long userId) throws Exception {
        return fetchTransfers("/v1/users/" + userId + "/gifts?limit=20");
    }

    /** New gifts since the last acknowledged transfer ID; used by the in-client inbox poller. */
    public static List<Transfer> fetchInbox(long userId, String afterId) throws Exception {
        String path = "/v1/users/" + userId + "/gifts/inbox?limit=100";
        if (afterId != null && !afterId.isEmpty()) {
            path += "&after=" + java.net.URLEncoder.encode(afterId, "UTF-8");
        }
        return fetchTransfers(path);
    }

    private static Transfer parseTransfer(JSONObject o) throws Exception {
        if (o == null) return null;
        JSONObject gift = o.optJSONObject("gift");
        return new Transfer(
                o.optString("id"),
                o.optString("from_id"),
                o.optString("to_id"),
                o.optString("created_at"),
                gift != null ? gift.optString("id") : "",
                gift != null ? gift.optString("title") : "",
                gift != null ? gift.optString("file_url") : "");
    }

    private static List<Transfer> fetchTransfers(String path) throws Exception {
        JSONArray arr = new JSONObject(http("GET", path, null)).optJSONArray("gifts");
        List<Transfer> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseTransfer(arr.getJSONObject(i)));
        }
        return list;
    }

    /** Downloads the original catalog/transfer .tgs once and keeps it in the app cache. */
    public static File ensureFile(Context context, Gift gift) throws Exception {
        return ensureFile(context, gift.id, gift.fileUrl);
    }

    public static File ensureFile(Context context, Transfer transfer) throws Exception {
        return ensureFile(context, transfer.id, transfer.giftFileUrl);
    }

    public static File ensureFile(Context context, String id, String fileUrl) throws Exception {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new ApiException("missing_gift_file_url");
        }
        URL source = new URL(fileUrl);
        if (!"https".equalsIgnoreCase(source.getProtocol())) {
            throw new ApiException("invalid_gift_file_url");
        }
        String safeId = id == null ? "gift" : id.replaceAll("[^A-Za-z0-9._-]", "_");
        File dir = new File(context.getCacheDir(), "mogram_gifts");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new ApiException("cache_dir");
        }
        File target = new File(dir, safeId + ".tgs");
        if (target.exists() && target.length() > 0) {
            return target;
        }
        HttpURLConnection c = (HttpURLConnection) source.openConnection();
        File tmp = new File(dir, safeId + ".part");
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            int code = c.getResponseCode();
            if (code != 200) {
                throw new ApiException("file_http_" + code);
            }
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete();
                throw new ApiException("rename_failed");
            }
            return target;
        } finally {
            c.disconnect();
        }
    }

    /** Raw catalog JSON from api.mogram (kept on disk so the shop works without network). */
    public static String fetchCatalogJson() throws Exception {
        return http("GET", "/v1/gifts/catalog", null);
    }

    public static List<Gift> parseCatalog(String json) throws Exception {
        JSONArray arr = new JSONObject(json).optJSONArray("gifts");
        List<Gift> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new Gift(
                    o.getString("id"),
                    o.getString("title"),
                    o.getString("file_url"),
                    o.optInt("price_stars", 0)));
        }
        return list;
    }

    /** Returns the downloaded .tgs if it is already on disk, otherwise null. */
    public static File cachedFile(Context context, Gift gift) {
        File f = new File(new File(context.getCacheDir(), "mogram_gifts"), gift.id + ".tgs");
        return f.exists() && f.length() > 0 ? f : null;
    }

    public static final class ManifestItem {
        public final String id;
        public final String title;
        public final int priceStars;
        public final String fileUrl;
        public final String sha256;

        ManifestItem(String id, String title, int priceStars, String fileUrl, String sha256) {
            this.id = id;
            this.title = title;
            this.priceStars = priceStars;
            this.fileUrl = fileUrl;
            this.sha256 = sha256;
        }
    }

    public static String fetchManifestJson() throws Exception {
        return http("GET", "/v1/gifts/manifest", null);
    }

    public static List<ManifestItem> parseManifest(String json) throws Exception {
        JSONArray arr = new JSONObject(json).optJSONArray("gifts");
        List<ManifestItem> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new ManifestItem(
                    o.getString("id"),
                    o.getString("title"),
                    o.optInt("price_stars", 0),
                    o.getString("file_url"),
                    o.isNull("sha256") ? null : o.getString("sha256")));
        }
        return list;
    }

    /** Downloads a file fully into memory (.tgs files are small). */
    public static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            int code = c.getResponseCode();
            if (code != 200) {
                throw new ApiException("file_http_" + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}

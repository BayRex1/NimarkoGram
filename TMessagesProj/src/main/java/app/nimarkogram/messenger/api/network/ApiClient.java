/* Modifications Copyright (C) 2026 Ettacent */

package app.nimarkogram.messenger.api.network;

import app.nimarkogram.messenger.api.dto.BadgeDTO;
import app.nimarkogram.messenger.api.dto.ProfileDTO;
import app.nimarkogram.messenger.badges.ExteraBadgeRole;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.telegram.messenger.FileLog;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ApiClient {

    public static final class BadgeFetchResult {
        public final boolean success;
        public final Map<Long, BadgeDTO> badges;

        private BadgeFetchResult(boolean success, Map<Long, BadgeDTO> badges) {
            this.success = success;
            this.badges = badges;
        }
    }

    private static final String NIMARKO_BASE_URL = org.telegram.messenger.BuildConfig.NIMARKO_API_BASE_URL;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "Telegram-Android/NimarkoGram okhttp/4.12.0")
                    .header("Accept", "application/json")
                    .build()))
            .build();

    private static final Gson GSON = new Gson();

    private ApiClient() {}


    public static final class ExteraBadgeFetchResult {
        public final boolean success;
        public final Map<Long, BadgeDTO> badges;
        public final java.util.Set<Long> arrowUsers;
        public final java.util.Set<Long> arrowChats;
        public final Map<Long, ExteraBadgeRole> userRoles;
        public final Map<Long, ExteraBadgeRole> chatRoles;

        private ExteraBadgeFetchResult(boolean success, Map<Long, BadgeDTO> badges, java.util.Set<Long> arrowUsers, java.util.Set<Long> arrowChats, Map<Long, ExteraBadgeRole> userRoles, Map<Long, ExteraBadgeRole> chatRoles) {
            this.success = success;
            this.badges = badges;
            this.arrowUsers = arrowUsers;
            this.arrowChats = arrowChats;
            this.userRoles = userRoles;
            this.chatRoles = chatRoles;
        }
    }

    private static final String EXTERA_PROFILES_URL =
            "https://api.exteragram.app/api/v1/profiles/compact";

    private static void addRoleIds(JsonObject root, String key, java.util.Set<Long> target,
                                   Map<Long, ExteraBadgeRole> roles, ExteraBadgeRole role) {
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) return;
        for (JsonElement value : root.getAsJsonArray(key)) {
            try {
                long id = value.getAsLong();
                if (id > 0L) {
                    target.add(id);
                    roles.put(id, role);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static ExteraBadgeFetchResult fetchExteraBadges() {
        Request req = new Request.Builder().url(EXTERA_PROFILES_URL).get().build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                return new ExteraBadgeFetchResult(false, Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());
            }
            JsonObject root = GSON.fromJson(resp.body().charStream(), JsonObject.class);
            if (root == null || !root.has("customBadges")
                    || !root.get("customBadges").isJsonArray()) {
                return new ExteraBadgeFetchResult(false, Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());
            }
            Map<Long, BadgeDTO> out = new HashMap<>();
            java.util.Set<Long> arrowUsers = new java.util.HashSet<>();
            java.util.Set<Long> arrowChats = new java.util.HashSet<>();
            Map<Long, ExteraBadgeRole> userRoles = new HashMap<>();
            Map<Long, ExteraBadgeRole> chatRoles = new HashMap<>();
            // Apply broad supporter roles first so the more specific developer/official role wins on overlap.
            addRoleIds(root, "supporters", arrowUsers, userRoles, ExteraBadgeRole.SUPPORTER);
            addRoleIds(root, "developers", arrowUsers, userRoles, ExteraBadgeRole.DEVELOPER);
            addRoleIds(root, "supporterChannels", arrowChats, chatRoles, ExteraBadgeRole.SUPPORTER_CHANNEL);
            addRoleIds(root, "officialChannels", arrowChats, chatRoles, ExteraBadgeRole.OFFICIAL_CHANNEL);
            for (JsonElement item : root.getAsJsonArray("customBadges")) {
                if (!item.isJsonObject()) continue;
                JsonObject row = item.getAsJsonObject();
                if (!row.has("id") || !row.has("badge") || !row.get("badge").isJsonObject()) continue;
                try {
                    long userId = row.get("id").getAsLong();
                    JsonObject badge = row.getAsJsonObject("badge");
                    if (userId == 0L || !badge.has("documentId")) continue;
                    long documentId = badge.get("documentId").getAsLong();
                    if (documentId <= 0L) continue;
                    String text = null;
                    if (badge.has("text") && !badge.get("text").isJsonNull()
                            && badge.get("text").isJsonPrimitive()) {
                        text = badge.get("text").getAsString();
                    }
                    out.put(userId, new BadgeDTO(documentId, text));
                } catch (Throwable malformedBadge) {
                    // Ignore an individual malformed entry; keep the remaining valid badges.
                }
            }
            return new ExteraBadgeFetchResult(true, out, arrowUsers, arrowChats,
                    Collections.unmodifiableMap(userRoles), Collections.unmodifiableMap(chatRoles));
        } catch (IOException e) {
            FileLog.e("extera-badges: fetch failed", e);
            return new ExteraBadgeFetchResult(false, Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());
        } catch (Throwable t) {
            FileLog.e("extera-badges: parse failed", t);
            return new ExteraBadgeFetchResult(false, Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    public static List<ProfileDTO> getAllProfiles() {
        return Collections.emptyList();
    }

    public static Map<Long, BadgeDTO> getNimarkoBadges() {
        BadgeFetchResult result = fetchNimarkoBadges();
        return result.success ? result.badges : Collections.emptyMap();
    }

    public static BadgeFetchResult fetchNimarkoBadges() {
        if (NIMARKO_BASE_URL == null || NIMARKO_BASE_URL.trim().isEmpty()) {
            return new BadgeFetchResult(false, Collections.emptyMap());
        }
        Request req = new Request.Builder().url(NIMARKO_BASE_URL + "badges").get().build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                return new BadgeFetchResult(false, Collections.emptyMap());
            }
            JsonObject root = GSON.fromJson(resp.body().charStream(), JsonObject.class);
            if (root == null || !root.has("badges") || !root.get("badges").isJsonObject()) {
                return new BadgeFetchResult(false, Collections.emptyMap());
            }
            JsonObject badges = root.getAsJsonObject("badges");
            Map<Long, BadgeDTO> out = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : badges.entrySet()) {
                try {
                    long uid = Long.parseLong(entry.getKey());
                    if (uid == 0L || !entry.getValue().isJsonObject()) {
                        return new BadgeFetchResult(false, Collections.emptyMap());
                    }
                    JsonObject row = entry.getValue().getAsJsonObject();
                    if (!row.has("emoji_doc_id") || !row.get("emoji_doc_id").isJsonPrimitive()) {
                        return new BadgeFetchResult(false, Collections.emptyMap());
                    }
                    long docId = row.get("emoji_doc_id").getAsLong();
                    if (docId <= 0L) return new BadgeFetchResult(false, Collections.emptyMap());
                    String text = null;
                    if (row.has("text") && !row.get("text").isJsonNull()) {
                        if (!row.get("text").isJsonPrimitive()) {
                            return new BadgeFetchResult(false, Collections.emptyMap());
                        }
                        text = row.get("text").getAsString();
                    }
                    out.put(uid, new BadgeDTO(docId, text));
                } catch (Throwable malformedRow) {
                    return new BadgeFetchResult(false, Collections.emptyMap());
                }
            }
            return new BadgeFetchResult(true, out);
        } catch (IOException e) {
            FileLog.e("nimarko-badges: getNimarkoBadges failed", e);
            return new BadgeFetchResult(false, Collections.emptyMap());
        } catch (Throwable t) {
            FileLog.e("nimarko-badges: getNimarkoBadges parse failed", t);
            return new BadgeFetchResult(false, Collections.emptyMap());
        }
    }
}

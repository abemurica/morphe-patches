/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3447
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches.originaltitles;

import static app.morphe.extension.youtube.patches.utils.requests.DeArrowRequester.DEARROW_API_URL;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.shared.requests.Route;
import app.morphe.extension.youtube.patches.utils.requests.DeArrowRequester;

/**
 * Fetches the video titles submitted to DeArrow (<a href="https://dearrow.ajay.app">...</a>).
 */
final class DeArrowTitleRequest {

    /**
     * Videos are requested by the start of the SHA-256 hash of the video id,
     * so the server does not know which video is shown.
     */
    private static final Route GET_BRANDING = new Route(Route.Method.GET, "/api/branding/{hash_prefix}");

    private static final int HASH_PREFIX_LENGTH = 4;

    /**
     * The title is shown as loading until DeArrow responds, so DeArrow is not waited for long.
     */
    private static final int CONNECTION_TIMEOUT_MILLISECONDS = 2 * 1000;

    private DeArrowTitleRequest() {
    }

    /**
     * @return The DeArrow title, or null if the video has no DeArrow title,
     *         DeArrow keeps the original title, or DeArrow is not available.
     */
    @Nullable
    static String fetchTitle(String videoId) {
        if (!DeArrowRequester.canUseDeArrowAPI()) {
            return null;
        }

        try {
            Route.CompiledRoute route = GET_BRANDING.compile(getHashPrefix(videoId));
            try {
                HttpURLConnection connection = Requester.getConnectionFromCompiledRoute(DEARROW_API_URL, route);
                connection.setConnectTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
                connection.setReadTimeout(CONNECTION_TIMEOUT_MILLISECONDS);

                final int responseCode = connection.getResponseCode();
                if (responseCode == Requester.HTTP_STATUS_CODE_SUCCESS) {
                    JSONObject branding = Requester.parseJSONObject(connection).optJSONObject(videoId);
                    return branding == null ? null : findTitle(branding.optJSONArray("titles"));
                }
                // No video with the hash prefix has DeArrow data.
                if (responseCode != 404) {
                    DeArrowRequester.handleDeArrowError(
                            DEARROW_API_URL + route.getCompiledRoute(), responseCode);
                }
            } catch (IOException ex) {
                Logger.printInfo(() -> "Could not fetch DeArrow title of: " + videoId, ex);
                DeArrowRequester.handleDeArrowError(DEARROW_API_URL + route.getCompiledRoute(), 0);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "fetchTitle failure", ex);
        }

        return null;
    }

    /**
     * The server sorts the titles by votes. As done by the DeArrow extension, the first title
     * that is locked or not downvoted is used. If that title is the original title,
     * then DeArrow keeps the original title.
     */
    @Nullable
    private static String findTitle(@Nullable JSONArray titles) {
        if (titles == null) {
            return null;
        }

        for (int i = 0, length = titles.length(); i < length; i++) {
            JSONObject title = titles.optJSONObject(i);
            if (title == null || (!title.optBoolean("locked") && title.optInt("votes") < 0)) {
                continue;
            }
            if (title.optBoolean("original")) {
                return null;
            }

            String text = title.optString("title").trim();
            // Titles that are not auto formatted by the DeArrow extension start with '>'.
            if (text.startsWith(">")) {
                text = text.substring(1).trim();
            }
            return text.isEmpty() ? null : text;
        }

        return null;
    }

    private static String getHashPrefix(String videoId) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(videoId.getBytes(StandardCharsets.UTF_8));
        StringBuilder prefix = new StringBuilder(HASH_PREFIX_LENGTH);
        for (int i = 0; prefix.length() < HASH_PREFIX_LENGTH; i++) {
            prefix.append(String.format(Locale.US, "%02x", hash[i]));
        }
        return prefix.substring(0, HASH_PREFIX_LENGTH);
    }
}

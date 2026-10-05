/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import static app.morphe.extension.shared.StringRef.str;

import android.app.Activity;
import android.view.View;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.requests.Requester;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.shared.ShortsPlayerState;

@SuppressWarnings("unused")
public final class LocalQueuePatch {

    public static final class Item {
        public final String videoId;
        @Nullable
        public volatile String title;

        Item(String videoId, @Nullable String title) {
            this.videoId = videoId;
            this.title = title;
        }
    }

    private static final int MAX_ITEMS = 200;

    /**
     * End of a video is signaled by more than one hook, only the first one may start a video.
     */
    private static final long ADVANCE_GUARD_MILLISECONDS = 5000;

    private static final long END_OF_VIDEO_TOLERANCE_MILLISECONDS = 5000;
    private static final long NATIVE_FALLBACK_DELAY_MILLISECONDS = 2500;

    private static final Object LOCK = new Object();
    private static final List<Item> items = new ArrayList<>();
    private static boolean loaded;
    private static long lastAdvanceTime;

    @Nullable
    private static volatile Runnable changeListener;

    public static boolean isEnabled() {
        return Settings.LOCAL_QUEUE.get();
    }

    public static void setChangeListener(@Nullable Runnable listener) {
        changeListener = listener;
    }

    public static List<Item> getItems() {
        NativeQueuePatch.reconcile();
        synchronized (LOCK) {
            load();
            return new ArrayList<>(items);
        }
    }

    public static int size() {
        NativeQueuePatch.reconcile();
        synchronized (LOCK) {
            load();
            return items.size();
        }
    }

    /**
     * Adds a video to the queue. If nothing is playing and the queue is empty, the video starts playing.
     */
    public static void add(String videoId, boolean playNext) {
        try {
            if (videoId == null || videoId.isEmpty()) {
                return;
            }

            final boolean playNow = PlayerType.getCurrent().isNoneOrHidden() && size() == 0;
            if (playNow) {
                if (!startPlayback(videoId)) {
                    Utils.showToastShort(str("morphe_local_queue_play_failed"));
                }
                return;
            }

            final int position;
            final boolean alreadyQueued;
            synchronized (LOCK) {
                load();
                int existing = indexOf(videoId);
                alreadyQueued = existing >= 0;
                Item item;
                if (alreadyQueued) {
                    item = items.remove(existing);
                } else {
                    if (items.size() >= MAX_ITEMS) {
                        Utils.showToastShort(str("morphe_local_queue_full"));
                        return;
                    }
                    item = new Item(videoId, null);
                }

                if (playNext) {
                    items.add(0, item);
                } else if (alreadyQueued) {
                    items.add(existing, item);
                } else {
                    items.add(item);
                }
                position = items.indexOf(item) + 1;
                save();

                if (!alreadyQueued) {
                    fetchTitle(item);
                }
            }

            Utils.showToastShort(alreadyQueued
                    ? str("morphe_local_queue_already_added", position)
                    : (playNext
                    ? str("morphe_local_queue_added_next")
                    : str("morphe_local_queue_added", position)));
            notifyChanged();
        } catch (Exception ex) {
            Logger.printException(() -> "add failure", ex);
        }
    }

    @Nullable
    static String peekFirstVideoId() {
        synchronized (LOCK) {
            load();
            return items.isEmpty() ? null : items.get(0).videoId;
        }
    }

    static void removeVideoId(String videoId) {
        synchronized (LOCK) {
            load();
            int index = indexOf(videoId);
            if (index < 0) {
                return;
            }
            items.remove(index);
            save();
        }
        notifyChanged();
    }

    public static void remove(int index) {
        synchronized (LOCK) {
            load();
            if (index < 0 || index >= items.size()) return;
            items.remove(index);
            save();
        }
        notifyChanged();
    }

    public static void move(int index, int offset) {
        synchronized (LOCK) {
            load();
            int target = index + offset;
            if (index < 0 || index >= items.size() || target < 0 || target >= items.size()) return;
            items.add(target, items.remove(index));
            save();
        }
        notifyChanged();
    }

    public static void clear() {
        synchronized (LOCK) {
            load();
            items.clear();
            save();
        }
        notifyChanged();
    }

    /**
     * Plays a queued video now and removes it from the queue.
     */
    public static void playItem(int index) {
        final Item item;
        synchronized (LOCK) {
            load();
            if (index < 0 || index >= items.size()) return;
            item = items.get(index);
        }

        if (startPlayback(item.videoId)) {
            lastAdvanceTime = System.currentTimeMillis();
            remove(index);
        } else {
            Utils.showToastShort(str("morphe_local_queue_play_failed"));
        }
    }

    /**
     * Injection point.
     *
     * @return If the navigation to another video was handled and must be canceled.
     */
    public static boolean shouldCancelNavigation(Enum<?> navigationIntent) {
        try {
            if (!isEnabled() || navigationIntent == null || NativeQueuePatch.isEnabled()) {
                return false;
            }

            final String name = navigationIntent.name();
            final boolean next = "NEXT".equals(name);
            if (!next && !"AUTOPLAY".equals(name) && !"AUTONAV".equals(name)) {
                return false;
            }

            if (!next && isAdvanceGuardActive()) {
                return true;
            }

            return advance();
        } catch (Exception ex) {
            Logger.printException(() -> "shouldCancelNavigation failure", ex);
            return false;
        }
    }

    /**
     * Injection point.
     *
     * @return If the end of the video was handled by starting the next queued video.
     */
    public static boolean shouldCancelEndOfVideo(Enum<?> playerStatus) {
        try {
            if (!isEnabled() || playerStatus == null || !"ENDED".equals(playerStatus.name())) {
                return false;
            }

            if (Settings.LOOP_VIDEO.get() || LoopVideoPatch.isSleepTimerEndingVideo()
                    || ShortsPlayerState.isOpen()) {
                return false;
            }

            // Ads and Shorts also report the ended state, only the end of the video itself counts.
            final long videoLength = VideoInformation.getVideoLength();
            if (videoLength > 0 && VideoInformation.getVideoTime() + END_OF_VIDEO_TOLERANCE_MILLISECONDS < videoLength) {
                return false;
            }

            if (NativeQueuePatch.isEnabled()) {
                // YouTube normally starts the next video itself. Start it here if that did not happen.
                final String endedVideoId = VideoInformation.getVideoId();
                Utils.runOnMainThreadDelayed(() -> {
                    if (endedVideoId.equals(VideoInformation.getVideoId()) && size() > 0
                            && !isAdvanceGuardActive()) {
                        advance();
                    }
                }, NATIVE_FALLBACK_DELAY_MILLISECONDS);
                return false;
            }

            if (isAdvanceGuardActive()) {
                return true;
            }

            return advance();
        } catch (Exception ex) {
            Logger.printException(() -> "shouldCancelEndOfVideo failure", ex);
            return false;
        }
    }

    private static boolean isAdvanceGuardActive() {
        return System.currentTimeMillis() - lastAdvanceTime < ADVANCE_GUARD_MILLISECONDS;
    }

    private static boolean advance() {
        final Item next;
        synchronized (LOCK) {
            load();
            if (items.isEmpty()) {
                return false;
            }

            if (!canStartPlayback()) {
                return false;
            }

            next = items.remove(0);
            save();
        }

        lastAdvanceTime = System.currentTimeMillis();
        Utils.runOnMainThreadNowOrLater(() -> {
            if (!startPlayback(next.videoId)) {
                synchronized (LOCK) {
                    items.add(0, next);
                    save();
                }
                lastAdvanceTime = 0;
            }
            notifyChanged();
        });
        return true;
    }

    private static boolean canStartPlayback() {
        Activity activity = Utils.getActivity();
        // Android blocks starting an activity from the background, so a video can only be started
        // while the app is on screen.
        return activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && activity.getWindow().getDecorView().getWindowVisibility() == View.VISIBLE
                && LoadVideoPatch.isPlayerInterfaceAvailable();
    }

    private static boolean startPlayback(String videoId) {
        try {
            if (!canStartPlayback()) {
                Logger.printDebug(() -> "Cannot start queued video, no active player interface");
                return false;
            }

            Logger.printDebug(() -> "Starting queued video: " + videoId);
            LoadVideoPatch.openVideoIntentWithInternalContext(videoId);
            return true;
        } catch (Exception ex) {
            Logger.printException(() -> "startPlayback failure", ex);
            return false;
        }
    }

    private static int indexOf(String videoId) {
        for (int i = 0, size = items.size(); i < size; i++) {
            if (items.get(i).videoId.equals(videoId)) {
                return i;
            }
        }
        return -1;
    }

    private static void notifyChanged() {
        Runnable listener = changeListener;
        if (listener != null) {
            Utils.runOnMainThread(listener);
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;

        try {
            String saved = Settings.LOCAL_QUEUE_ITEMS.get();
            if (saved.isEmpty()) return;

            JSONArray array = new JSONArray(saved);
            for (int i = 0, length = array.length(); i < length; i++) {
                JSONObject object = array.getJSONObject(i);
                String videoId = object.optString("id");
                if (videoId.isEmpty()) continue;

                String title = object.optString("title");
                Item item = new Item(videoId, title.isEmpty() ? null : title);
                items.add(item);
                if (item.title == null) {
                    fetchTitle(item);
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to load queue", ex);
            items.clear();
        }
    }

    private static void save() {
        try {
            JSONArray array = new JSONArray();
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("id", item.videoId);
                String title = item.title;
                if (title != null) {
                    object.put("title", title);
                }
                array.put(object);
            }
            Settings.LOCAL_QUEUE_ITEMS.save(array.toString());
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to save queue", ex);
        }
    }

    private static void fetchTitle(Item item) {
        Utils.runOnBackgroundThread(() -> {
            try {
                String watchUrl = URLEncoder.encode("https://www.youtube.com/watch?v=" + item.videoId, "UTF-8");
                HttpURLConnection connection = Requester.openConnection(
                        "https://www.youtube.com/oembed?format=json&url=" + watchUrl);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                String title = Requester.parseJSONObjectAndDisconnect(connection).optString("title");
                if (title.isEmpty()) return;

                item.title = title;
                synchronized (LOCK) {
                    save();
                }
                notifyChanged();
            } catch (Exception ex) {
                Logger.printDebug(() -> "Could not fetch title for " + item.videoId, ex);
            }
        });
    }
}

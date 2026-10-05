/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import android.os.Parcelable;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class NativeQueuePatch {

    /**
     * Interface to use obfuscated methods.
     */
    public interface NativeQueueInterface {
        // Method is added during patching.
        boolean patch_insertAfterCurrent(Parcelable watchParcelable);
    }

    /**
     * Video that was handed to YouTube's playback queue, and the video that was playing when that happened.
     */
    @Nullable
    private static String handedOffVideoId;
    @Nullable
    private static String handedOffForVideoId;

    public static boolean isEnabled() {
        return Settings.NATIVE_QUEUE.get() && LocalQueuePatch.isEnabled();
    }

    /**
     * Removes a handed off video from the local queue once it plays, and forgets it if the user went elsewhere.
     */
    static synchronized void reconcile() {
        if (handedOffVideoId == null) {
            return;
        }

        final String current = VideoInformation.getVideoId();
        if (current.equals(handedOffVideoId)) {
            final String played = handedOffVideoId;
            handedOffVideoId = null;
            handedOffForVideoId = null;
            LocalQueuePatch.removeVideoId(played);
        } else if (!current.equals(handedOffForVideoId)) {
            handedOffVideoId = null;
            handedOffForVideoId = null;
        }
    }

    /**
     * Injection point.
     * Called whenever YouTube's own playback queue resolves where a navigation goes, which happens more than
     * once per navigation. The first queued video is put after the current one so YouTube picks it as the next.
     */
    public static synchronized void onResolveNavigation(Object queue, Enum<?> navigationType) {
        try {
            if (!isEnabled() || navigationType == null || !(queue instanceof NativeQueueInterface)) {
                return;
            }

            final String name = navigationType.name();
            if (!"NEXT".equals(name) && !"AUTOPLAY".equals(name) && !"AUTONAV".equals(name)) {
                return;
            }

            reconcile();
            final String current = VideoInformation.getVideoId();
            if (handedOffVideoId != null && current.equals(handedOffForVideoId)) {
                return;
            }

            final String videoId = LocalQueuePatch.peekFirstVideoId();
            if (videoId == null || videoId.equals(current)) {
                return;
            }

            final Parcelable watchParcelable = LoadVideoPatch.getWatchParcelable(videoId);
            if (watchParcelable == null) {
                return;
            }

            if (((NativeQueueInterface) queue).patch_insertAfterCurrent(watchParcelable)) {
                Logger.printDebug(() -> "Handed " + videoId + " to the playback queue for " + name);
                handedOffVideoId = videoId;
                handedOffForVideoId = current;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onResolveNavigation failure", ex);
        }
    }
}

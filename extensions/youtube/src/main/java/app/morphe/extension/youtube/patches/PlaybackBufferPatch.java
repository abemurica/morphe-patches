/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public final class PlaybackBufferPatch {

    public enum PlaybackBufferSize {
        DEFAULT(1),
        X2(2),
        X4(4),
        X8(8);

        private final int multiplier;

        PlaybackBufferSize(int multiplier) {
            this.multiplier = multiplier;
        }

        public int getMultiplier() {
            return multiplier;
        }
    }

    private static final int MAX_BUFFER_MS = 600_000;

    private PlaybackBufferPatch() {
    }

    /** Injection point. */
    public static int scale(int bufferMs) {
        try {
            int multiplier = Settings.PLAYBACK_BUFFER_SIZE.get().getMultiplier();
            if (multiplier <= 1) {
                return bufferMs;
            }
            long scaled = (long) bufferMs * multiplier;
            return (int) Math.min(scaled, MAX_BUFFER_MS);
        } catch (Throwable ignored) {
            return bufferMs;
        }
    }
}

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public class PlaybackBufferPatch {

    public enum PlaybackBufferSize {
        DEFAULT(1),
        LOW(2),
        MEDIUM(4),
        MAXIMUM(8);

        private final int multiplier;

        PlaybackBufferSize(int multiplier) {
            this.multiplier = multiplier;
        }

        public int getMultiplier() {
            return multiplier;
        }
    }

    public enum PlaybackBufferMemory {
        MB_128(128),
        MB_256(256),
        MB_512(512);

        private final int megabytes;

        PlaybackBufferMemory(int megabytes) {
            this.megabytes = megabytes;
        }
    }

    public static final class MemoryAvailability implements Setting.Availability {
        @Override
        public boolean isAvailable() {
            return Settings.PLAYBACK_BUFFER_SIZE.get() != PlaybackBufferSize.DEFAULT;
        }

        @Override
        public List<Setting<?>> getParentSettings() {
            return List.of(Settings.PLAYBACK_BUFFER_SIZE);
        }
    }

    /**
     * Injection point.
     * <p>
     * Dividing the buffered duration is the same as multiplying the duration limits.
     */
    public static long scaleBufferedDurationUs(long bufferedUs) {
        try {
            int multiplier = Settings.PLAYBACK_BUFFER_SIZE.get().getMultiplier();
            return multiplier > 1 ? bufferedUs / multiplier : bufferedUs;
        } catch (Exception ex) {
            Logger.printException(() -> "scaleBufferedDurationUs failure", ex);
            return bufferedUs;
        }
    }

    /**
     * Injection point.
     * <p>
     * The limit is never more than half of what the app is allowed to use.
     */
    public static int scaleByteLimit(int bytes) {
        try {
            if (Settings.PLAYBACK_BUFFER_SIZE.get() == PlaybackBufferSize.DEFAULT) {
                return bytes;
            }
            long limit = Settings.PLAYBACK_BUFFER_MEMORY.get().megabytes * 1024L * 1024L;
            limit = Math.min(limit, Runtime.getRuntime().maxMemory() / 2);
            return (int) Math.max(bytes, limit);
        } catch (Exception ex) {
            Logger.printException(() -> "scaleByteLimit failure", ex);
            return bytes;
        }
    }
}

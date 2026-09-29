package app.morphe.extension.youtube.patches;

import java.util.concurrent.TimeUnit;

import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings("unused")
public class DisableAutoFeedRefreshPatch {

    private static final long DELAY_MILLIS = TimeUnit.DAYS.toMillis(365);

    /**
     * Injection point.
     */
    public static long getFeedExpirationTime(long expirationTime) {
        if (Settings.DISABLE_AUTO_FEED_REFRESH.get()) {
            return expirationTime + DELAY_MILLIS;
        }

        return expirationTime;
    }
}

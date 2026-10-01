package app.morphe.patches.backdrops.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// Ported from BholeyKaBhakt/android-patches-xtra's shared Constants.kt (COMPATIBILITY_BACKDROPS).
internal object Constants {
    val COMPATIBILITY_BACKDROPS = Compatibility(
        name = "Backdrops",
        packageName = "com.backdrops.wallpapers",
        targets = listOf(
            AppTarget(version = "6.1.2"),
        ),
    )
}

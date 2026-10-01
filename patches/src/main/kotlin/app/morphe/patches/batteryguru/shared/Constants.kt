package app.morphe.patches.batteryguru.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

// Ported from rushiranpise/morphe-patches's shared Constants.kt
// (BATTERYGURU_COMPATIBILITY / BATTERYGURU_COMMUNITY_COMPATIBILITY).
internal object Constants {
    val BATTERYGURU_COMPATIBILITY = Compatibility(
        name = "Battery Guru",
        packageName = "com.paget96.batteryguru",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1B7080,
        targets = listOf(AppTarget(version = "2.5.0.7", versionCode = 728)),
    )

    val BATTERYGURU_COMMUNITY_COMPATIBILITY = Compatibility(
        name = "Battery Guru",
        packageName = "com.paget96.batteryguru",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1B7080,
        targets = listOf(AppTarget(version = "2.5.0.7", versionCode = 728)),
    )
}

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.buffer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.settings.preference.ListPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE

private const val PLAYBACK_BUFFER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/patches/PlaybackBufferPatch;"

@Suppress("unused")
val playbackBufferPatch = bytecodePatch(
    name = "Playback buffer",
    description = "Adds an option to change the video playback buffer size."
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.VIDEO.addPreferences(
            ListPreference("morphe_playback_buffer_size")
        )

        DefaultLoadControlConstructorFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range { p2 .. p2 }, $PLAYBACK_BUFFER_CLASS_DESCRIPTOR->scale(I)I
                move-result p2
                invoke-static/range { p4 .. p4 }, $PLAYBACK_BUFFER_CLASS_DESCRIPTOR->scale(I)I
                move-result p4
            """
        )
    }
}

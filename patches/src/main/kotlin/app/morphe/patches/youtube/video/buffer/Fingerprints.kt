/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.video.buffer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

internal object DefaultLoadControlConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters = listOf(
        string("bufferForPlaybackMs"),
        string("minBufferMs"),
        string("maxBufferMs")
    )
)

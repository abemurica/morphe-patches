/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.misc.debugging

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

private val commandRouterClass = Fingerprint(
    filters = listOf(string("Unknown command not resolved %s"))
)

/**
 * Resolves a command to the resolver that handles it.
 */
internal object CommandRouteFingerprint : Fingerprint(
    classFingerprint = commandRouterClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("L", "Ljava/util/Map;"),
    filters = listOf(string("Unknown command not resolved %s"))
)

/**
 * Asks one resolver to handle a command, returns if it did.
 */
internal object CommandResolverRunFingerprint : Fingerprint(
    classFingerprint = commandRouterClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("L", "L", "Ljava/util/Map;")
)

private val navigableQueueClass = Fingerprint(
    filters = listOf(string("Navigation committed to a video that is not expected by the navigable queue"))
)

internal object QueueInsertFingerprint : Fingerprint(
    classFingerprint = navigableQueueClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = listOf("I", "L")
)

internal object QueueRangeEditFingerprint : Fingerprint(
    classFingerprint = navigableQueueClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "I", "Ljava/util/Collection;")
)

internal object QueueClearFingerprint : Fingerprint(
    classFingerprint = navigableQueueClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf()
)

internal object QueueMoveFingerprint : Fingerprint(
    classFingerprint = navigableQueueClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "I", "I", "I")
)

internal object QueueMoveThreeFingerprint : Fingerprint(
    classFingerprint = navigableQueueClass,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("I", "I", "I")
)

/**
 * Resolves a navigation request (next, previous, autoplay, jump, insert) to a queue entry.
 */
internal object QueueNavigateFingerprint : Fingerprint(
    classFingerprint = navigableQueueClass,
    accessFlags = listOf(AccessFlags.PROTECTED, AccessFlags.FINAL),
    returnType = "L",
    parameters = listOf("L")
)

/**
 * Error shown by a page when loading fails because of an authentication error.
 */
internal object AuthErrorPageFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Throwable;", "L"),
    filters = listOf(resourceLiteral(ResourceType.STRING, "auth_error_help_message"))
)

internal object AuthErrorViewFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/lang/Throwable;"),
    filters = listOf(resourceLiteral(ResourceType.STRING, "auth_error_help_message"))
)

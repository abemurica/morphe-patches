/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.misc.debugging

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.youtube.layout.flyout.FeedFlyoutButtonsInitializerFingerprint
import app.morphe.patches.youtube.layout.flyout.addToQueuePatch
import app.morphe.patches.youtube.misc.engagement.engagementPanelHookPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/morphe/extension/youtube/patches/QueueDebugPatch;"

@Suppress("unused")
val queueDebugPatch = bytecodePatch(
    name = "Queue debug",
    description = "Adds an option to log queue related menus, commands and list changes to the debug log.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        enableDebuggingPatch,
        addToQueuePatch,
        engagementPanelHookPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        FeedFlyoutButtonsInitializerFingerprint.method.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->onFeedMenuItemModel(Ljava/lang/Object;)V"
        )

        CommandRouteFingerprint.method.addInstruction(
            0,
            "invoke-static { p1, p2 }, $EXTENSION_CLASS->onCommand(Ljava/lang/Object;Ljava/util/Map;)V"
        )

        CommandResolverRunFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static { p1, p2 }, $EXTENSION_CLASS->onResolverTry(Ljava/lang/Object;Ljava/lang/Object;)V"
            )

            findInstructionIndicesReversedOrThrow(Opcode.RETURN).forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA

                addInstructionsAtControlFlowLabel(
                    index,
                    "invoke-static { v$register }, $EXTENSION_CLASS->onResolverResult(Z)V"
                )
            }
        }

        QueueInsertFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1, p2 }, $EXTENSION_CLASS->onQueueInsert(Ljava/lang/Object;ILjava/lang/Object;)V"
        )

        QueueRangeEditFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1, p2, p3 }, $EXTENSION_CLASS->onQueueRangeEdit(Ljava/lang/Object;IILjava/util/Collection;)V"
        )

        QueueClearFingerprint.method.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->onQueueClear(Ljava/lang/Object;)V"
        )

        QueueMoveFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1, p2, p3, p4 }, $EXTENSION_CLASS->onQueueMove(Ljava/lang/Object;IIII)V"
        )

        QueueMoveThreeFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1, p2, p3 }, $EXTENSION_CLASS->onQueueMove3(Ljava/lang/Object;III)V"
        )

        QueueNavigateFingerprint.method.apply {
            addInstruction(
                0,
                "invoke-static { p0, p1 }, $EXTENSION_CLASS->onQueueNavigate(Ljava/lang/Object;Ljava/lang/Object;)V"
            )

            findInstructionIndicesReversedOrThrow(Opcode.RETURN_OBJECT).forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA

                addInstructionsAtControlFlowLabel(
                    index,
                    "invoke-static { v$register }, $EXTENSION_CLASS->onQueueNavigated(Ljava/lang/Object;)V"
                )
            }
        }

        AuthErrorPageFingerprint.method.addInstruction(
            0,
            "invoke-static { p1 }, $EXTENSION_CLASS->onAuthErrorPage(Ljava/lang/Throwable;)V"
        )

        AuthErrorViewFingerprint.method.addInstruction(
            0,
            "invoke-static { p1 }, $EXTENSION_CLASS->onAuthErrorView(Ljava/lang/Throwable;)V"
        )
    }
}

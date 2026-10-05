/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/1837
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.layout.flyout


import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.noTitleUnsortedPreferenceCategory
import app.morphe.patches.youtube.interaction.reload.OpenNewVideoIntentParcelableFingerprint
import app.morphe.patches.youtube.interaction.reload.reloadVideoButtonPatch
import app.morphe.patches.youtube.layout.hide.general.ContextualMenuItemBuilderFingerprint
import app.morphe.patches.youtube.layout.hide.general.ContextualMenuItemBuilderOnClickFingerprint
import app.morphe.patches.youtube.layout.playlistautoplay.NavigationIntentEnumFingerprint
import app.morphe.patches.youtube.layout.playlistautoplay.navigationIntentHook
import app.morphe.patches.youtube.misc.auth.authHookPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.playservice.is_21_05_or_greater
import app.morphe.patches.youtube.misc.proto.elementProtoParserHookPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.playerStatusMethodRef
import app.morphe.util.cloneParameters
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.util.MethodUtil

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/AddToQueuePatch;"

private const val EXTENSION_LOCAL_QUEUE_CLASS =
    "Lapp/morphe/extension/youtube/patches/LocalQueuePatch;"

private const val EXTENSION_NATIVE_QUEUE_CLASS =
    "Lapp/morphe/extension/youtube/patches/NativeQueuePatch;"

private const val EXTENSION_NATIVE_QUEUE_INTERFACE =
    $$"Lapp/morphe/extension/youtube/patches/NativeQueuePatch$NativeQueueInterface;"

private const val EXTENSION_UTILS_CLASS =
    "Lapp/morphe/extension/youtube/patches/utils/FlyoutUtils;"

@Suppress("unused")
val addToQueuePatch = bytecodePatch(
    name = "Add to queue",
    description = "Overrides the feed flyout 'Play next in queue' with the Morphe video queue."
) {
    dependsOn(
        flyoutPatch,
        settingsPatch,
        sharedExtensionPatch,
        elementProtoParserHookPatch,
        authHookPatch,
        reloadVideoButtonPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.FEED.addPreferences(
            noTitleUnsortedPreferenceCategory(
                SwitchPreference("morphe_local_queue", summary = true),
                SwitchPreference("morphe_native_queue", summary = true),
                SwitchPreference("morphe_queue_override_flyout_menu", summary = true),
                SwitchPreference("morphe_queue_add_flyout_menu", summary = true),
                SwitchPreference("morphe_ads_channel_whitelist_flyout_menu", summary = true),
                SwitchPreference("morphe_playback_speed_channel_whitelist_flyout_menu", summary = true)
            )
        )


        FeedFlyoutButtonsInitializerFingerprint.let { mainFingerprint ->
            val mainFingerprintMatches = mainFingerprint.instructionMatches
            val getCharSequenceReference = mainFingerprintMatches.first().getInstruction<ReferenceInstruction>().reference
            val enumMethodRegister = mainFingerprintMatches[1].getInstruction<OneRegisterInstruction>().registerA
            val charCheckIndex = mainFingerprintMatches[4].index
            val enumIntField = mainFingerprintMatches[6].getInstruction<ReferenceInstruction>().reference
            val enumMethodCall = mainFingerprintMatches[7].getInstruction<ReferenceInstruction>().reference
            val runnableIndex = mainFingerprintMatches.last().index
            val charCheckRegister = mainFingerprintMatches.last().getInstruction<OneRegisterInstruction>().registerA

            mainFingerprint.method.apply {
                val runnableRegister = getInstruction<TwoRegisterInstruction>(runnableIndex).registerA
                addInstructions(
                    runnableIndex,
                    """
                        invoke-static { v$runnableRegister }, $EXTENSION_CLASS->replaceButtonRunnable(Ljava/lang/Runnable;)Ljava/lang/Runnable;
                        move-result-object v$runnableRegister
                    """
                )

                val freeRegister = findFreeRegister(charCheckIndex, charCheckRegister, enumMethodRegister)
                addInstructions(
                    charCheckIndex,
                    """
                        iget v$freeRegister, v$enumMethodRegister, $enumIntField
                        invoke-static { v$freeRegister }, $enumMethodCall
                        move-result-object v$freeRegister
                        invoke-static { v$freeRegister, v$charCheckRegister }, $EXTENSION_UTILS_CLASS->setCurrentButtonInfo(Ljava/lang/Enum;Ljava/lang/Object;)V
                    """
                )
            }

            ContextualMenuItemBuilderFingerprint.let {
                it.method.cloneParameters().apply {
                    val targetInstructionIndex = it.instructionMatches[3].index + numberOfParameterRegisters
                    val targetInstructionRegister = it.instructionMatches[3]
                        .getInstruction<FiveRegisterInstruction>().registerC
                    val secondButtonInfoParameterRegister = it.instructionMatches[2]
                        .getInstruction<FiveRegisterInstruction>().registerC

                    addInstructions(
                        targetInstructionIndex,
                            """
                            invoke-static { v$targetInstructionRegister }, $getCharSequenceReference
                            move-result-object p0
                            iget p0, p0, $enumIntField
                            invoke-static { p0 }, $enumMethodCall
                            move-result-object p0
                            invoke-static { p0, v$secondButtonInfoParameterRegister }, $EXTENSION_UTILS_CLASS->setCurrentButtonInfo(Ljava/lang/Enum;Ljava/lang/Object;)V
                        """
                    )
                }
            }

            fun getReplaceOnItemClickPatch(
                targetInstructionRegister: String,
                freeRegister: String
            ): String = """
                invoke-static { $targetInstructionRegister }, $EXTENSION_CLASS->replaceOnItemClick(Ljava/lang/Object;)Z
                move-result $freeRegister
                if-eqz $freeRegister, :block_item_click
                return-void
                :block_item_click
                nop
            """

            ContextualMenuItemBuilderOnClickFingerprint.let {
                val enumMethodParameterClassReference = it.instructionMatches.first()
                    .getInstruction<ReferenceInstruction>().reference
                val enumMethodParameterClassName = it.instructionMatches[1]
                    .getInstruction<ReferenceInstruction>().reference

                it.method.addInstructions(
                    0,
                    """
                        iget-object v0, p0, $enumMethodParameterClassReference
                        check-cast v0, $enumMethodParameterClassName
                        invoke-static { v0 }, $getCharSequenceReference
                        move-result-object v0
                        iget v0, v0, $enumIntField
                        invoke-static { v0 }, $enumMethodCall
                        move-result-object v0
                        invoke-virtual {v0}, Ljava/lang/Enum;->name()Ljava/lang/String;
                        move-result-object v0
                    """ + getReplaceOnItemClickPatch("v0", "v0")
                )
            }

            if (!is_21_05_or_greater) {
                FeedFlyoutButtonsInitializerOnItemClickFingerprint.method.addInstructionsWithLabels(
                    0,
                    """
                        invoke-static { p3 }, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
                        move-result-object p2
                    """ + getReplaceOnItemClickPatch("p2", "p2")
                )
            }
        }

        navigationIntentHook(EXTENSION_LOCAL_QUEUE_CLASS, "shouldCancelNavigation")

        playerStatusMethodRef.get()!!.apply {
            val insertIndex = indexOfFirstInstructionOrThrow(Opcode.SGET_OBJECT)
            val freeRegister = getInstruction<OneRegisterInstruction>(insertIndex).registerA

            addInstructionsWithLabels(
                insertIndex,
                """
                    invoke-static/range { p1 .. p1 }, $EXTENSION_LOCAL_QUEUE_CLASS->shouldCancelEndOfVideo(Ljava/lang/Enum;)Z
                    move-result v$freeRegister
                    if-eqz v$freeRegister, :continue_end_of_video
                    return-void
                    :continue_end_of_video
                    nop
                """
            )
        }

        // Lets the queued videos be handed to YouTube's own playback queue, so it also advances
        // with the screen off.
        NavigableQueueResolveNavigationFingerprint.let {
            val resolveMethod = it.method
            val queueClassDef = it.classDef
            val queueType = queueClassDef.type
            val entryType = resolveMethod.returnType

            val entryClassDef = classDefBy(entryType)
            val entryConstructor = entryClassDef.methods.single { method ->
                MethodUtil.isConstructor(method) &&
                        method.parameterTypes.size == 2 &&
                        method.parameterTypes.first() == "Ljava/util/UUID;" &&
                        classDefBy(method.parameterTypes.last().toString()).interfaces
                            .contains("Landroid/os/Parcelable;")
            }
            val startDescriptorType = entryConstructor.parameterTypes.last().toString()

            val watchParcelableClassDef = OpenNewVideoIntentParcelableFingerprint.originalClassDef
            val watchParcelableType = watchParcelableClassDef.type
            val startDescriptorField = watchParcelableClassDef.fields.single { field ->
                field.type == startDescriptorType
            }

            val insertMethod = queueClassDef.methods.single { method ->
                method.returnType == "V" &&
                        method.parameterTypes.map(CharSequence::toString) ==
                        listOf("I", "I", "Ljava/util/Collection;")
            }
            val currentIndexMethod = queueClassDef.methods.single { method ->
                AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                        AccessFlags.FINAL.isSet(method.accessFlags) &&
                        method.returnType == "I" &&
                        method.parameterTypes.isEmpty()
            }
            val sectionSizeMethod = queueClassDef.methods.single { method ->
                AccessFlags.PUBLIC.isSet(method.accessFlags) &&
                        AccessFlags.FINAL.isSet(method.accessFlags) &&
                        method.returnType == "I" &&
                        method.parameterTypes.map(CharSequence::toString) == listOf("I")
            }

            queueClassDef.interfaces.add(EXTENSION_NATIVE_QUEUE_INTERFACE)
            queueClassDef.methods.add(
                ImmutableMethod(
                    queueType,
                    "patch_insertAfterCurrent",
                    listOf(ImmutableMethodParameter("Landroid/os/Parcelable;", null, null)),
                    "Z",
                    AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                    null,
                    null,
                    MutableMethodImplementation(7),
                ).toMutable().apply {
                    addInstructions(
                        0,
                        """
                            check-cast p1, $watchParcelableType
                            iget-object v0, p1, $startDescriptorField
                            new-instance v1, $entryType
                            invoke-static { }, Ljava/util/UUID;->randomUUID()Ljava/util/UUID;
                            move-result-object v2
                            invoke-direct { v1, v2, v0 }, $entryConstructor
                            invoke-virtual { p0 }, $currentIndexMethod
                            move-result v2
                            add-int/lit8 v2, v2, 0x1
                            const/4 v3, 0x0
                            invoke-virtual { p0, v3 }, $sectionSizeMethod
                            move-result v4
                            invoke-static { v2, v4 }, Ljava/lang/Math;->min(II)I
                            move-result v2
                            invoke-static { v3, v2 }, Ljava/lang/Math;->max(II)I
                            move-result v2
                            invoke-static { v1 }, Ljava/util/Collections;->singleton(Ljava/lang/Object;)Ljava/util/Set;
                            move-result-object v1
                            invoke-virtual { p0, v3, v2, v1 }, $insertMethod
                            const/4 v0, 0x1
                            return v0
                        """
                    )
                }
            )

            val enumType = NavigationIntentEnumFingerprint.originalClassDef.type
            val requestType = resolveMethod.parameterTypes.single().toString()
            val enumField = classDefBy(requestType).fields.first { field -> field.type == enumType }

            resolveMethod.addInstructions(
                0,
                """
                    iget-object v0, p1, $enumField
                    invoke-static { p0, v0 }, $EXTENSION_NATIVE_QUEUE_CLASS->onResolveNavigation(Ljava/lang/Object;Ljava/lang/Enum;)V
                """
            )
        }
    }
}

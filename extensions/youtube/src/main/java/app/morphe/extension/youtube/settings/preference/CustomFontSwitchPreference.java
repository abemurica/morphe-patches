/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/3673
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.settings.preference;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.CustomFontPatch;

/**
 * Custom font switch that can only be turned on after a font file is picked.
 */
@SuppressWarnings({"unused", "deprecation"})
public class CustomFontSwitchPreference extends SwitchPreference {

    public CustomFontSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        installChangeGate();
    }

    public CustomFontSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        installChangeGate();
    }

    public CustomFontSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        installChangeGate();
    }

    public CustomFontSwitchPreference(Context context) {
        super(context);
        installChangeGate();
    }

    private void installChangeGate() {
        setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue) && !CustomFontPatch.isFontFileSet(preference.getContext())) {
                Utils.showToastLong(str("morphe_custom_font_pick_file_first"));
                return false;
            }
            return true;
        });
    }
}

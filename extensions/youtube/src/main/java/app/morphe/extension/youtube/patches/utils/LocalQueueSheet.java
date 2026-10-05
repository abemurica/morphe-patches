/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.utils;

import static app.morphe.extension.shared.StringRef.str;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.theme.ThemeUtils;
import app.morphe.extension.shared.ui.Dim;
import app.morphe.extension.shared.ui.SheetBottomDialog;
import app.morphe.extension.youtube.patches.LocalQueuePatch;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.shared.PlayerType;

public final class LocalQueueSheet {

    public static void show(Context context) {
        try {
            SheetBottomDialog.DraggableLinearLayout mainLayout =
                    SheetBottomDialog.createMainLayout(context, null);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(Dim.dp16, Dim.dp12, Dim.dp8, Dim.dp8);

            TextView title = new TextView(context);
            title.setTextColor(ThemeUtils.getAppForegroundColor());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(title.getTypeface(), Typeface.BOLD);
            header.addView(title, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            header.addView(createButton(context, str("morphe_local_queue_clear"), LocalQueuePatch::clear));
            mainLayout.addView(header);

            ScrollView scrollView = SheetBottomDialog.createCappedScrollView(context);
            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(list);
            mainLayout.addView(scrollView);

            SheetBottomDialog.SlideDialog dialog = SheetBottomDialog.createSlideDialog(context, mainLayout, 300);
            Runnable render = () -> render(context, dialog, title, list);
            render.run();

            LocalQueuePatch.setChangeListener(render);
            dialog.setOnDismissListener(d -> LocalQueuePatch.setChangeListener(null));
            dialog.show();
        } catch (Exception ex) {
            Logger.printException(() -> "show failure", ex);
        }
    }

    private static void render(Context context, SheetBottomDialog.SlideDialog dialog,
                               TextView title, LinearLayout list) {
        list.removeAllViews();

        List<LocalQueuePatch.Item> items = LocalQueuePatch.getItems();
        title.setText(str("morphe_local_queue_sheet_title") + (items.isEmpty() ? "" : " (" + items.size() + ")"));

        String nowPlayingId = VideoInformation.getVideoId();
        if (!PlayerType.getCurrent().isNoneOrHidden() && !nowPlayingId.isEmpty()) {
            String nowPlayingTitle = VideoInformation.getVideoTitle();
            list.addView(createRow(context,
                    str("morphe_local_queue_now_playing") + ": "
                            + (nowPlayingTitle.isEmpty() ? nowPlayingId : nowPlayingTitle),
                    true, null));
        }

        if (items.isEmpty()) {
            list.addView(createRow(context, str("morphe_local_queue_empty"), true, null));
            return;
        }

        final int lastIndex = items.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            final int index = i;
            LocalQueuePatch.Item item = items.get(i);
            String text = item.title != null
                    ? item.title
                    : str("morphe_local_queue_untitled", item.videoId);

            LinearLayout row = createRow(context, text, false, v -> {
                dialog.dismiss();
                LocalQueuePatch.playItem(index);
            });
            if (index > 0) {
                row.addView(createButton(context, "▲", () -> LocalQueuePatch.move(index, -1)));
            }
            if (index < lastIndex) {
                row.addView(createButton(context, "▼", () -> LocalQueuePatch.move(index, 1)));
            }
            row.addView(createButton(context, "✕", () -> LocalQueuePatch.remove(index)));
            list.addView(row);
        }
    }

    private static LinearLayout createRow(Context context, String text, boolean dim,
                                          @Nullable View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Dim.dp16, Dim.dp4, Dim.dp8, Dim.dp4);

        if (onClick != null) {
            TypedValue ripple = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
                row.setBackgroundResource(ripple.resourceId);
            }
            row.setClickable(true);
            row.setOnClickListener(onClick);
        }

        TextView textView = new TextView(context);
        int color = ThemeUtils.getAppForegroundColor();
        textView.setTextColor(dim ? Color.argb(160, Color.red(color), Color.green(color), Color.blue(color)) : color);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(text);
        textView.setPadding(0, Dim.dp8, Dim.dp8, Dim.dp8);
        row.addView(textView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private static TextView createButton(Context context, String text, Runnable action) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextColor(ThemeUtils.getAppForegroundColor());
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setGravity(Gravity.CENTER);
        button.setPadding(Dim.dp12, Dim.dp12, Dim.dp12, Dim.dp12);
        button.setClickable(true);
        button.setOnClickListener(v -> action.run());
        return button;
    }
}

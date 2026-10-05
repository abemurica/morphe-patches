/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import androidx.annotation.Nullable;

import com.google.protobuf.MessageLite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.NavigationBar.NavigationButton;
import app.morphe.extension.youtube.shared.PlayerType;

/**
 * Logs queue related menus, commands and list changes. Every line starts with "QDBG".
 */
@SuppressWarnings("unused")
public final class QueueDebugPatch {

    private static final int PROTO_MAX_CHARS = 1500;
    private static final int SHORT_MAX_CHARS = 300;
    private static final int MAX_COMMAND_BYTES = 20_000;
    private static final int STACK_FRAMES = 6;
    private static final long MODEL_MAX_AGE_NANOS = 50_000_000L;
    private static final long CLICK_WINDOW_MILLIS = 3000;

    private static String lastItemModel;
    private static long lastItemModelNanos;
    private static String lastClickName = "";
    private static long lastClickMillis;

    private static boolean enabled() {
        return BaseSettings.DEBUG.get() && Settings.QUEUE_DEBUG.get();
    }

    private static String truncate(@Nullable String text, int max) {
        if (text == null) {
            return "null";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(" + text.length() + " chars)";
    }

    private static String name(@Nullable Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    private static String safeToString(@Nullable Object object, int max) {
        try {
            return truncate(String.valueOf(object), max);
        } catch (Exception ex) {
            return "<toString failed: " + ex + ">";
        }
    }

    private static String stack(StackTraceElement[] trace) {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            if (className.equals(QueueDebugPatch.class.getName()) || className.startsWith("java.lang.Thread")
                    || className.startsWith("dalvik.system.VMStack")) {
                continue;
            }
            builder.append(className).append('.').append(element.getMethodName())
                    .append(':').append(element.getLineNumber()).append(" < ");
            if (++count >= STACK_FRAMES) {
                break;
            }
        }
        return builder.toString();
    }

    private static String currentStack() {
        return stack(Thread.currentThread().getStackTrace());
    }

    private static String surface() {
        String tab;
        try {
            NavigationButton button = NavigationButton.getSelectedNavigationButton();
            tab = button == null ? "null" : button.name();
        } catch (Exception ex) {
            tab = "?";
        }
        return "player=" + PlayerType.getCurrent() + " tab=" + tab;
    }

    private static String sinceClick() {
        long age = System.currentTimeMillis() - lastClickMillis;
        return age <= CLICK_WINDOW_MILLIS ? " [" + age + "ms after click on " + lastClickName + "]" : "";
    }

    /**
     * Describes an object by its fields, including any proto messages it holds (one nested level).
     */
    private static String describe(@Nullable Object object, int max) {
        if (object == null) {
            return "null";
        }
        if (object instanceof MessageLite) {
            return name(object) + " " + safeToString(object, max);
        }

        StringBuilder builder = new StringBuilder(name(object)).append(" {");
        try {
            for (Field field : object.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(object);
                builder.append(field.getName()).append('=');
                if (value instanceof MessageLite) {
                    builder.append(name(value)).append(' ').append(safeToString(value, max));
                } else if (value == null || value instanceof CharSequence || value instanceof Number
                        || value instanceof Boolean || value instanceof Enum) {
                    builder.append(value);
                } else {
                    builder.append(name(value));
                    for (Field inner : value.getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(inner.getModifiers())) {
                            continue;
                        }
                        inner.setAccessible(true);
                        Object innerValue = inner.get(value);
                        if (innerValue instanceof MessageLite) {
                            builder.append('.').append(inner.getName()).append('=')
                                    .append(name(innerValue)).append(' ').append(safeToString(innerValue, max));
                        }
                    }
                }
                builder.append("; ");
                if (builder.length() > max * 2) {
                    break;
                }
            }
        } catch (Exception ex) {
            builder.append("<reflection failed: ").append(ex).append('>');
        }
        return builder.append('}').toString();
    }

    private static String sizes(@Nullable Object queue) {
        if (queue == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        try {
            int found = 0;
            for (Method method : queue.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == int.class
                        && method.getDeclaringClass() != Object.class && !method.getName().equals("hashCode")) {
                    builder.append(method.getName()).append('=').append(method.invoke(queue)).append(' ');
                    if (++found >= 4) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            builder.append("<size failed: ").append(ex).append('>');
        }
        return builder.toString().trim();
    }

    private static boolean isQueueName(String enumName) {
        String upper = enumName.toUpperCase(Locale.ROOT);
        return upper.contains("QUEUE") || upper.contains("PLAY_NEXT");
    }

    /**
     * Injection point.
     * Called with the model of each feed flyout item as its row is built.
     */
    public static void onFeedMenuItemModel(@Nullable Object model) {
        if (!enabled()) {
            return;
        }
        try {
            lastItemModel = describe(model, PROTO_MAX_CHARS);
            lastItemModelNanos = System.nanoTime();
        } catch (Exception ex) {
            Logger.printException(() -> "onFeedMenuItemModel failure", ex);
        }
    }

    /**
     * Called for each flyout button (feed and watch page flyouts) as it is built.
     */
    public static void onFlyoutButton(@Nullable Enum<?> buttonEnum, @Nullable Object buttonInfo) {
        if (!enabled() || buttonEnum == null) {
            return;
        }
        try {
            final String buttonName = buttonEnum.name();
            final String label = buttonInfo instanceof CharSequence
                    ? "\"" + buttonInfo + "\"" : name(buttonInfo);
            final boolean recentModel = lastItemModel != null
                    && System.nanoTime() - lastItemModelNanos < MODEL_MAX_AGE_NANOS;
            final String model = recentModel ? lastItemModel : null;
            lastItemModel = null;

            Logger.printDebug(() -> "QDBG flyout item: " + buttonName + " label=" + label
                    + " " + surface() + " model=" + (model != null));
            if (isQueueName(buttonName)) {
                Logger.printDebug(() -> "QDBG flyout queue item model: " + model);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onFlyoutButton failure", ex);
        }
    }

    /**
     * Wraps the click action of a feed flyout item so the click and what it holds are logged.
     */
    public static Runnable wrapRunnable(@Nullable Runnable original, @Nullable String buttonName) {
        if (original == null || !enabled()) {
            return original;
        }
        final String name = buttonName == null ? "" : buttonName;
        final String description = describe(original, PROTO_MAX_CHARS);
        return () -> {
            try {
                lastClickName = name;
                lastClickMillis = System.currentTimeMillis();
                Logger.printDebug(() -> "QDBG flyout click: " + name + " " + surface());
                Logger.printDebug(() -> "QDBG flyout click runnable: " + description);
            } catch (Exception ex) {
                Logger.printException(() -> "wrapRunnable failure", ex);
            }
            original.run();
        };
    }

    /**
     * Injection point.
     * Click on an item of the watch page flyout (the 21.04 and older click hook is also used).
     */
    public static void onContextClick(@Nullable Object item) {
        if (!enabled()) {
            return;
        }
        lastClickName = String.valueOf(item);
        lastClickMillis = System.currentTimeMillis();
        Logger.printDebug(() -> "QDBG context flyout click: " + item + " " + surface());
    }

    /**
     * Injection point.
     * Every command that goes through the command router.
     */
    public static void onCommand(@Nullable Object command, @Nullable Map<?, ?> args) {
        if (!enabled()) {
            return;
        }
        try {
            final String className = name(command);
            final String argKeys = args == null ? "" : String.valueOf(args.keySet());
            final String click = sinceClick();
            Logger.printDebug(() -> "QDBG command: " + className + " args=" + argKeys + click);

            if (command instanceof MessageLite messageLite) {
                final int bytes = messageLite.getSerializedSize();
                if (bytes > MAX_COMMAND_BYTES) {
                    Logger.printDebug(() -> "QDBG command too large to print: " + bytes + " bytes");
                    return;
                }
            }
            final String text = String.valueOf(command);
            final String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("queue") || lower.contains("sign")) {
                Logger.printDebug(() -> "QDBG command proto: " + className + " " + truncate(text, PROTO_MAX_CHARS));
            }
        } catch (Exception ex) {
            Logger.printException(() -> "onCommand failure", ex);
        }
    }

    /**
     * Injection point.
     * A command resolver is asked to handle a command.
     */
    public static void onResolverTry(@Nullable Object resolver, @Nullable Object command) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG resolver try: " + name(resolver) + " for " + name(command));
    }

    /**
     * Injection point.
     */
    public static void onResolverResult(boolean handled) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG resolver result: handled=" + handled);
    }

    /**
     * Injection point.
     */
    public static void onQueueInsert(@Nullable Object queue, int index, @Nullable Object item) {
        if (!enabled()) {
            return;
        }
        try {
            final String stack = currentStack();
            Logger.printDebug(() -> "QDBG queue insert: index=" + index + " item=" + safeToString(item, SHORT_MAX_CHARS)
                    + " queue=" + name(queue) + " " + sizes(queue) + sinceClick());
            Logger.printDebug(() -> "QDBG queue insert stack: " + stack);
        } catch (Exception ex) {
            Logger.printException(() -> "onQueueInsert failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static void onQueueRangeEdit(@Nullable Object queue, int start, int end, @Nullable Collection<?> items) {
        if (!enabled()) {
            return;
        }
        try {
            final List<String> parts = new ArrayList<>();
            if (items != null) {
                for (Object item : items) {
                    parts.add(safeToString(item, 120));
                    if (parts.size() >= 5) {
                        break;
                    }
                }
            }
            final String stack = currentStack();
            Logger.printDebug(() -> "QDBG queue range edit: " + start + ".." + end + " items="
                    + (items == null ? "null" : items.size()) + " " + parts + " queue=" + name(queue) + " "
                    + sizes(queue) + sinceClick());
            Logger.printDebug(() -> "QDBG queue range edit stack: " + stack);
        } catch (Exception ex) {
            Logger.printException(() -> "onQueueRangeEdit failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static void onQueueClear(@Nullable Object queue) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG queue clear: " + name(queue) + " " + sizes(queue) + " stack: " + currentStack());
    }

    /**
     * Injection point.
     */
    public static void onQueueMove(@Nullable Object queue, int a, int b, int c, int d) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG queue move: " + a + "," + b + "," + c + "," + d + " " + sizes(queue));
    }

    /**
     * Injection point.
     */
    public static void onQueueMove3(@Nullable Object queue, int a, int b, int c) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG queue move: " + a + "," + b + "," + c + " " + sizes(queue));
    }

    /**
     * Injection point.
     * A navigation request (next, previous, autoplay, jump, insert) is resolved in the queue.
     */
    public static void onQueueNavigate(@Nullable Object queue, @Nullable Object request) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG queue navigate: request=" + name(request) + " "
                + safeToString(request, SHORT_MAX_CHARS) + " queue=" + name(queue) + " " + sizes(queue));
    }

    /**
     * Injection point.
     */
    public static void onQueueNavigated(@Nullable Object result) {
        if (!enabled()) {
            return;
        }
        Logger.printDebug(() -> "QDBG queue navigate result: " + safeToString(result, SHORT_MAX_CHARS));
    }

    private static void logAuthError(String site, @Nullable Throwable throwable) {
        if (!enabled()) {
            return;
        }
        try {
            final String cause = throwable == null ? "null" : throwable + " cause=" + throwable.getCause();
            final String throwableStack = throwable == null ? "" : stack(throwable.getStackTrace());
            final String threadStack = currentStack();
            Logger.printDebug(() -> "QDBG auth error shown (" + site + "): " + cause + sinceClick());
            Logger.printDebug(() -> "QDBG auth error throwable stack: " + throwableStack);
            Logger.printDebug(() -> "QDBG auth error ui stack: " + threadStack);
        } catch (Exception ex) {
            Logger.printException(() -> "logAuthError failure", ex);
        }
    }

    /**
     * Injection point.
     */
    public static void onAuthErrorPage(@Nullable Throwable throwable) {
        logAuthError("page", throwable);
    }

    /**
     * Injection point.
     */
    public static void onAuthErrorView(@Nullable Throwable throwable) {
        logAuthError("view", throwable);
    }
}

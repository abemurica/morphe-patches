/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.utils;

import java.util.List;

/**
 * List operations of the on-device queue. Entries before {@code firstMovable} are locked in place.
 */
public final class QueueListLogic {

    /**
     * @return The index the item was inserted at.
     */
    public static <T> int insertPlayNext(List<T> list, T item, int firstMovable) {
        final int index = Math.max(0, Math.min(firstMovable, list.size()));
        list.add(index, item);
        return index;
    }

    /**
     * @return If the list changed.
     */
    public static <T> boolean move(List<T> list, int from, int to, int firstMovable) {
        final int size = list.size();
        if (from < firstMovable || from >= size) {
            return false;
        }

        to = Math.max(firstMovable, Math.min(to, size - 1));
        if (to == from) {
            return false;
        }

        list.add(to, list.remove(from));
        return true;
    }

    /**
     * @return If the list changed.
     */
    public static <T> boolean remove(List<T> list, int index, int firstMovable) {
        if (index < firstMovable || index >= list.size()) {
            return false;
        }

        list.remove(index);
        return true;
    }

    /**
     * @return If the list changed.
     */
    public static <T> boolean clear(List<T> list, int firstMovable) {
        final int size = list.size();
        if (firstMovable >= size) {
            return false;
        }

        list.subList(Math.max(0, firstMovable), size).clear();
        return true;
    }

    /**
     * Index a dragged row ends at, from the center of the dragged row and the centers of the other
     * movable rows in their current order.
     */
    public static int dropIndex(float draggedCenter, float[] otherCenters, int firstMovable) {
        int before = 0;
        for (float center : otherCenters) {
            if (center < draggedCenter) {
                before++;
            }
        }
        return firstMovable + before;
    }
}

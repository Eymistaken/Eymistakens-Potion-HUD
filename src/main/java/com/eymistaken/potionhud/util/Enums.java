package com.eymistaken.potionhud.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Glue between our enum-typed settings and the HUD's {@code CycleSetting}, which speaks
 * in list indices.
 */
public final class Enums {

    private Enums() {}

    /** Enum constant names as Title Case labels, in declaration order. */
    public static <E extends Enum<E>> List<String> labels(Class<E> type, String... overrides) {
        E[] values = type.getEnumConstants();
        List<String> out = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            out.add(i < overrides.length ? overrides[i] : title(values[i].name()));
        }
        return out;
    }

    /**
     * Index back to a constant, tolerating an index a future (or past) build wrote.
     *
     * @return the constant at {@code index}, or {@code fallback} when out of range
     */
    public static <E extends Enum<E>> E byOrdinal(Class<E> type, int index, E fallback) {
        E[] values = type.getEnumConstants();
        if (index < 0 || index >= values.length) return fallback;
        return values[index];
    }

    /** Ordinal of {@code value}, or 0 when it is null (a config naming a removed constant). */
    public static int ordinal(Enum<?> value) {
        return value == null ? 0 : value.ordinal();
    }

    private static String title(String constant) {
        String[] words = constant.toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}

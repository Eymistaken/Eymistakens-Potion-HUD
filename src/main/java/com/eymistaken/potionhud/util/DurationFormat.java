package com.eymistaken.potionhud.util;

import com.eymistaken.potionhud.PotionHudConfig.LevelStyle;
import com.eymistaken.potionhud.PotionHudConfig.TimeFormat;

/** Turning effect ticks and amplifiers into the short strings the HUD draws. */
public final class DurationFormat {

    private static final String[] ROMAN = {
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private DurationFormat() {}

    /**
     * @param ticks    remaining duration
     * @param format   how to render it
     * @param infinite true for an infinite-duration effect
     * @param infiniteLabel what to print instead of a number when infinite
     * @return the formatted time, or an empty string when the format is HIDDEN
     */
    public static String time(int ticks, TimeFormat format, boolean infinite, String infiniteLabel) {
        if (format == TimeFormat.HIDDEN) return "";
        if (infinite) return infiniteLabel == null ? "" : infiniteLabel;
        if (ticks < 0) ticks = 0;

        int totalSeconds = ticks / 20;
        switch (format) {
            case SECONDS:
                return String.valueOf(totalSeconds);
            case TENTHS:
                // Tenths only matter in the last few seconds; above that they are noise.
                if (totalSeconds < 10) {
                    int tenths = (ticks % 20) / 2;
                    return totalSeconds + "." + tenths;
                }
                return clock(totalSeconds);
            case CLOCK:
            default:
                return clock(totalSeconds);
        }
    }

    private static String clock(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + two(minutes) + ":" + two(seconds);
        }
        return minutes + ":" + two(seconds);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    /**
     * Amplifier as it appears next to the effect name. Amplifier 0 is level I, which
     * vanilla leaves unwritten — {@code showLevelOne} opts back into printing it.
     *
     * @return the level text, or an empty string when nothing should be drawn
     */
    public static String level(int amplifier, LevelStyle style, boolean showLevelOne) {
        if (style == LevelStyle.NONE) return "";
        if (amplifier <= 0 && !showLevelOne) return "";

        int level = amplifier + 1;
        if (style == LevelStyle.NUMBER) return String.valueOf(level);
        // Roman numerals stop being readable well before this; fall back to digits.
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }
}

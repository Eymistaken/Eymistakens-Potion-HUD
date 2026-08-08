package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.EffectSnapshot;
import com.eymistaken.potionhud.PotionHudConfig.BarStyle;
import com.eymistaken.potionhud.PotionHudConfig.Direction;
import com.eymistaken.potionhud.PotionHudConfig.IconStyle;
import com.eymistaken.potionhud.PotionHudConfig.ListSettings;
import com.eymistaken.potionhud.PotionHudConfig.SortOrder;
import com.eymistaken.potionhud.PotionHudConfig.Style;
import com.eymistaken.potionhud.util.DurationFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Where every pixel of the effect list is decided.
 *
 * <p>{@code getWidth()}, {@code getHeight()} and the render pass all call {@link #build}
 * and read the same {@link Result}, so the editor's drag box can never drift away from
 * what is actually drawn — the one thing the HUD's API is strict about.
 *
 * <p>Cells are uniform: every entry gets the widest and tallest box in the list. That is
 * what lets names start and timers end on the same column down the whole list, and it
 * makes the four growth directions fall out of one grid instead of four layouts.
 */
public final class EntryLayout {

    /** Space between an icon and the text beside it. */
    private static final int GAP = 3;

    private EntryLayout() {}

    /** One laid-out effect. Sub-positions are relative to {@link #x}/{@link #y}. */
    public static final class Entry {
        public final EffectSnapshot snap;
        public final String label;
        public final String time;
        public final String badge;

        /** Cell origin inside the module box. */
        public int x, y;

        public int iconX, iconY, iconSize;
        public int labelX, labelY;
        public int timeX, timeY;
        public int barX, barY, barW, barH;
        /** True when the bar is a full-cell wash behind the row rather than a strip. */
        public boolean barBehind;

        Entry(EffectSnapshot snap, String label, String time, String badge) {
            this.snap = snap;
            this.label = label;
            this.time = time;
            this.badge = badge;
        }
    }

    public static final class Result {
        public final List<Entry> entries = new ArrayList<>();
        public int width;
        public int height;
        public int cellW;
        public int cellH;
    }

    // --- Filtering and ordering ---------------------------------------------

    /** Apply the user's filters and sort, leaving the list the HUD should actually draw. */
    public static List<EffectSnapshot> visible(ListSettings cfg, List<EffectSnapshot> source) {
        List<String> hidden = parseHidden(cfg.hiddenEffects);
        List<EffectSnapshot> out = new ArrayList<>(source.size());

        for (EffectSnapshot snap : source) {
            // An effect that asked not to have an icon is opting out of HUDs entirely,
            // not just vanilla's. Respected unconditionally, like vanilla does.
            if (!snap.showIcon()) continue;
            if (cfg.hideAmbient && snap.ambient()) continue;
            if (cfg.hideParticleless && !snap.visible()) continue;
            if (cfg.hideInfinite && snap.infinite()) continue;
            if (cfg.hidePositive && snap.beneficial()) continue;
            if (cfg.hideNegative && snap.harmful()) continue;
            if (isHidden(hidden, snap)) continue;
            out.add(snap);
        }

        sort(out, cfg);
        return out;
    }

    private static void sort(List<EffectSnapshot> list, ListSettings cfg) {
        SortOrder order = cfg.sortOrder == null ? SortOrder.TIME_LEFT : cfg.sortOrder;
        Comparator<EffectSnapshot> comparator;
        switch (order) {
            case TIME_LEFT ->
                // Infinite effects have no time left to run out of, so they sit at the
                // bottom instead of pretending to be the most urgent thing on screen.
                comparator = Comparator.<EffectSnapshot>comparingInt(snap -> snap.infinite() ? 1 : 0)
                    .thenComparingInt(EffectSnapshot::duration);
            case TIME_LEFT_DESC ->
                comparator = Comparator.<EffectSnapshot>comparingInt(snap -> snap.infinite() ? 0 : 1)
                    .thenComparing(Comparator.<EffectSnapshot>comparingInt(EffectSnapshot::duration).reversed());
            case NAME ->
                comparator = Comparator.<EffectSnapshot, String>comparing(
                    snap -> snap.displayName().getString(), String.CASE_INSENSITIVE_ORDER);
            case CATEGORY ->
                comparator = Comparator.<EffectSnapshot>comparingInt(EntryLayout::categoryRank)
                    .thenComparingInt(EffectSnapshot::duration);
            case VANILLA ->
                // Vanilla orders by descending duration; close enough that the list does
                // not visibly reshuffle when you switch our display off.
                comparator = Comparator.<EffectSnapshot>comparingInt(EffectSnapshot::duration).reversed();
            default ->
                comparator = Comparator.<EffectSnapshot>comparingInt(EffectSnapshot::duration);
        }
        list.sort(comparator);

        // Applied last and stably, so it reorders groups without disturbing the sort
        // the user picked inside them.
        if (cfg.harmfulLast) {
            list.sort(Comparator.<EffectSnapshot>comparingInt(snap -> snap.harmful() ? 1 : 0));
        }
    }

    private static int categoryRank(EffectSnapshot snap) {
        if (snap.beneficial()) return 0;
        if (snap.harmful()) return 2;
        return 1;
    }

    private static List<String> parseHidden(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    /** Matches either the full id or just the path, so "poison" works as well as "minecraft:poison". */
    private static boolean isHidden(List<String> hidden, EffectSnapshot snap) {
        if (hidden.isEmpty()) return false;
        String full = snap.id().toString().toLowerCase(Locale.ROOT);
        String path = snap.id().getPath().toLowerCase(Locale.ROOT);
        for (String entry : hidden) {
            if (entry.equals(full) || entry.equals(path)) return true;
        }
        return false;
    }

    // --- Layout --------------------------------------------------------------

    /**
     * Lay the given effects out in the module's own unscaled pixel grid.
     *
     * @param measure    string width in the active HUD font (the module's {@code textWidth})
     * @param lineHeight the font's line height
     */
    public static Result build(ListSettings cfg, List<EffectSnapshot> effects,
                               ToIntFunction<String> measure, int lineHeight) {
        Result result = new Result();

        Style style = cfg.style == null ? Style.FULL : cfg.style;
        boolean usesIcon = style != Style.TEXT && cfg.iconStyle != IconStyle.NONE;
        int iconSize = usesIcon ? Math.max(4, cfg.iconSize) : 0;
        int barH = cfg.showBar ? Math.max(1, cfg.barHeight) : 0;
        BarStyle barStyle = cfg.barStyle == null ? BarStyle.UNDER_TEXT : cfg.barStyle;
        boolean barStrip = cfg.showBar && barStyle != BarStyle.BEHIND_ROW;

        if (effects.isEmpty()) {
            return result;
        }

        // Pass 1 — natural sizes, so the uniform cell can be the largest of them.
        int cellW = 0;
        int cellH = 0;
        for (EffectSnapshot snap : effects) {
            String level = DurationFormat.level(snap.amplifier(), cfg.levelStyle, cfg.showLevelOne);
            String name = cfg.showName ? snap.displayName().getString() : "";
            String time = DurationFormat.time(snap.duration(), cfg.timeFormat, snap.infinite(), cfg.infiniteLabel);

            String label;
            String badge;
            if (style == Style.FULL || style == Style.TEXT) {
                label = level.isEmpty() ? name : (name.isEmpty() ? level : name + " " + level);
                badge = "";
            } else {
                // Icon-led styles have no room for a name, so the level rides on the
                // icon itself as a small corner badge.
                label = "";
                badge = level;
            }
            if (style == Style.ICONS) time = "";

            Entry entry = new Entry(snap, label, time, badge);
            entry.iconSize = iconSize;
            result.entries.add(entry);

            int labelW = label.isEmpty() ? 0 : measure.applyAsInt(label);
            int timeW = time.isEmpty() ? 0 : measure.applyAsInt(time);

            int w;
            int h;
            switch (style) {
                case ICONS -> {
                    w = iconSize;
                    h = iconSize;
                }
                case GRID -> {
                    w = Math.max(iconSize, timeW);
                    h = iconSize + (timeW > 0 ? 1 + lineHeight : 0);
                }
                case TEXT -> {
                    w = labelW + (timeW > 0 ? GAP + timeW : 0);
                    h = lineHeight;
                }
                case COMPACT -> {
                    w = iconSize + (iconSize > 0 && timeW > 0 ? GAP : 0) + timeW;
                    h = Math.max(iconSize, lineHeight);
                }
                case FULL -> {
                    w = iconSize + (iconSize > 0 && (labelW > 0 || timeW > 0) ? GAP : 0)
                        + labelW + (timeW > 0 ? GAP + timeW : 0);
                    h = Math.max(iconSize, lineHeight);
                }
                default -> {
                    w = labelW;
                    h = lineHeight;
                }
            }
            if (barStrip) h += barH + 1;

            cellW = Math.max(cellW, Math.max(1, w));
            cellH = Math.max(cellH, Math.max(1, h));
        }

        result.cellW = cellW;
        result.cellH = cellH;

        // Pass 2 — positions, now that every cell shares one size.
        int rowH = cellH - (barStrip ? barH + 1 : 0);
        for (Entry entry : result.entries) {
            int timeW = entry.time.isEmpty() ? 0 : measure.applyAsInt(entry.time);

            if (style == Style.GRID) {
                entry.iconX = (cellW - iconSize) / 2;
                entry.iconY = 0;
                entry.timeX = (cellW - timeW) / 2;
                entry.timeY = iconSize + 1;
                entry.labelX = 0;
                entry.labelY = 0;
            } else {
                entry.iconX = 0;
                entry.iconY = (rowH - iconSize) / 2;
                entry.labelX = iconSize + (iconSize > 0 ? GAP : 0);
                entry.labelY = (rowH - lineHeight) / 2;
                // Right-aligned against the cell so the timers form a clean column.
                entry.timeX = cellW - timeW;
                entry.timeY = entry.labelY;
            }
            entry.barBehind = cfg.showBar && barStyle == BarStyle.BEHIND_ROW;
            if (cfg.showBar) {
                if (entry.barBehind) {
                    entry.barX = 0;
                    entry.barY = 0;
                    entry.barW = cellW;
                    entry.barH = rowH;
                } else if (barStyle == BarStyle.UNDER_ICON && iconSize > 0) {
                    entry.barX = entry.iconX;
                    entry.barY = rowH + 1;
                    entry.barW = iconSize;
                    entry.barH = barH;
                } else {
                    // Icon-only rows have no text column to sit under, so the bar spans
                    // the whole cell rather than starting past a label that is not there.
                    int start = (style == Style.GRID || style == Style.ICONS) ? 0 : entry.labelX;
                    entry.barX = start;
                    entry.barY = rowH + 1;
                    entry.barW = cellW - start;
                    entry.barH = barH;
                }
            }
        }

        place(result, cfg);
        return result;
    }

    /** Arrange the cells into lines and give the whole block its width and height. */
    private static void place(Result result, ListSettings cfg) {
        int count = result.entries.size();
        int perLine = Math.max(1, Math.min(8, cfg.columns));
        int spacing = Math.max(0, cfg.spacing);
        Direction direction = cfg.direction == null ? Direction.DOWN : cfg.direction;
        boolean horizontalFlow = direction == Direction.DOWN || direction == Direction.UP;

        int alongLine = Math.min(count, perLine);
        int lines = (count + perLine - 1) / perLine;

        int stepX = result.cellW + spacing;
        int stepY = result.cellH + spacing;

        if (horizontalFlow) {
            result.width = alongLine * result.cellW + (alongLine - 1) * spacing;
            result.height = lines * result.cellH + (lines - 1) * spacing;
        } else {
            result.width = lines * result.cellW + (lines - 1) * spacing;
            result.height = alongLine * result.cellH + (alongLine - 1) * spacing;
        }

        for (int i = 0; i < count; i++) {
            Entry entry = result.entries.get(i);
            int line = i / perLine;
            int index = i % perLine;

            if (horizontalFlow) {
                entry.x = index * stepX;
                // UP grows away from the anchor, which is what a bottom-anchored HUD
                // needs: the newest line stays put and the block extends upward.
                entry.y = direction == Direction.DOWN
                    ? line * stepY
                    : result.height - result.cellH - line * stepY;
            } else {
                entry.y = index * stepY;
                entry.x = direction == Direction.RIGHT
                    ? line * stepX
                    : result.width - result.cellW - line * stepX;
            }
        }
    }

    /**
     * Size of the stand-in row drawn when the editor is open and nothing is active.
     * A module with no size cannot be grabbed, so there has to be something to grab.
     */
    public static int[] placeholderSize(ListSettings cfg, ToIntFunction<String> measure, int lineHeight, String text) {
        boolean usesIcon = cfg.style != Style.TEXT && cfg.iconStyle != IconStyle.NONE;
        int iconSize = usesIcon ? Math.max(4, cfg.iconSize) : 0;
        int w = iconSize + (iconSize > 0 ? GAP : 0) + measure.applyAsInt(text);
        int h = Math.max(iconSize, lineHeight);
        return new int[] { Math.max(1, w), Math.max(1, h) };
    }

    public static int gap() {
        return GAP;
    }
}

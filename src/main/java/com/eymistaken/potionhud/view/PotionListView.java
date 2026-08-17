package com.eymistaken.potionhud.view;

import com.eymistaken.potionhud.EffectSnapshot;
import com.eymistaken.potionhud.HudCompat;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.ColorMode;
import com.eymistaken.potionhud.PotionHudConfig.Direction;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
import com.eymistaken.potionhud.PotionHudConfig.IconStyle;
import com.eymistaken.potionhud.PotionHudConfig.ListSettings;
import com.eymistaken.potionhud.PotionHudConfig.Style;
import com.eymistaken.potionhud.render.Bars;
import com.eymistaken.potionhud.render.EntryLayout;
import com.eymistaken.potionhud.render.PotionIcons;
import com.eymistaken.potionhud.render.TextSink;
import com.eymistaken.potionhud.util.Tween;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The effect list itself: layout, color, animation and drawing, with nothing in it that
 * knows whether it is being shown as a HUD module or as a standalone overlay.
 *
 * <p>Everything is measured in an unscaled local grid and the grid is scaled as a whole,
 * so icons, text and bars keep lining up at any size. {@link #frame(TextSink)} is the
 * single source of geometry — sizes and drawing both come out of it, which is what keeps
 * the HUD editor's drag box exactly on top of what is actually rendered.
 */
public final class PotionListView {

    private static final int PADDING = 2;
    private static final String PLACEHOLDER = "Potion HUD";

    /**
     * Where the effects to draw come from. The live view reads the tracker; a preview
     * reads stand-ins, so it has something to show with no world loaded.
     */
    private final Supplier<List<EffectSnapshot>> source;

    /**
     * True for a view that is illustrating the settings rather than reporting the game.
     *
     * <p>It buys two things a live view must not have: something to draw when the filters
     * have eliminated everything, so the preview card can never collapse to nothing; and
     * groups drawn stacked, ignoring where they were dragged to, because a card is about
     * how the list looks and a group dropped across the screen would make it mostly empty.
     */
    private final boolean standIn;

    /** Per-effect arrival animation, keyed by {@link EffectSnapshot#key()}. */
    private final Map<String, Tween> entryAnims = new HashMap<>();

    /** Last frame with something in it, redrawn while the display fades out. */
    private Frame lastFrame;

    private long comboPulseUntil = 0L;

    /**
     * Per view rather than static: with the settings screen open the list is drawn twice a
     * frame, once on the HUD and once in the preview card, and a shared hue would be
     * stepped by both — so the live rainbow would visibly speed up whenever the menu was
     * up. The host's own modules keep a separate preview hue for the same reason.
     */
    private float rainbowHue = 0f;

    public PotionListView(Supplier<List<EffectSnapshot>> source, boolean standIn) {
        this.source = source;
        this.standIn = standIn;
    }

    /** The effects this view is drawing, as of the last tick. Never null. */
    private List<EffectSnapshot> effects() {
        List<EffectSnapshot> effects = source.get();
        return effects == null ? List.of() : effects;
    }

    private static ListSettings cfg() {
        return PotionHudConfig.instance.list;
    }

    /** The expiry warning is one idea shared with the vanilla-icon overlay, so it lives there. */
    private static DisplaySettings warn() {
        return PotionHudConfig.instance.display;
    }

    /** One frame's geometry: a single list, or two independently placed groups. */
    public static final class Frame {
        public EntryLayout.Result merged;
        public EntryLayout.Result positive;
        public EntryLayout.Result negative;
        public int positiveX, positiveY, negativeX, negativeY;
        public int width = 1;
        public int height = 1;
        public boolean placeholder;

        public boolean isEmpty() {
            if (merged != null) return merged.entries.isEmpty();
            boolean p = positive == null || positive.entries.isEmpty();
            boolean n = negative == null || negative.entries.isEmpty();
            return p && n;
        }
    }

    // --- Geometry ---------------------------------------------------------------

    public float scale() {
        return Math.max(0.1f, cfg().scale / 100f);
    }

    /** True when there is something worth showing right now. */
    public boolean hasContent() {
        ListSettings cfg = cfg();
        if (cfg.alwaysShow || standIn || HudCompat.editorOpen()) return true;
        return !EntryLayout.visible(cfg, effects()).isEmpty();
    }

    public Frame frame(TextSink sink) {
        ListSettings cfg = cfg();
        Frame f = new Frame();
        int lineHeight = sink.lineHeight();
        List<EffectSnapshot> all = EntryLayout.visible(cfg, effects());

        if (!cfg.splitGroups) {
            f.merged = EntryLayout.build(cfg, all, sink::width, lineHeight);
            f.width = Math.max(1, f.merged.width);
            f.height = Math.max(1, f.merged.height);
        } else {
            List<EffectSnapshot> good = new ArrayList<>();
            List<EffectSnapshot> bad = new ArrayList<>();
            for (EffectSnapshot snap : all) {
                (snap.harmful() ? bad : good).add(snap);
            }
            f.positive = EntryLayout.build(cfg, good, sink::width, lineHeight);
            f.negative = EntryLayout.build(cfg, bad, sink::width, lineHeight);
            placeGroups(f, cfg);
        }

        if (f.isEmpty()) {
            // A module with no size cannot be grabbed in the editor, so there has to be
            // something there to grab.
            f.placeholder = standIn || HudCompat.editorOpen() || cfg.alwaysShow;
            if (f.placeholder) {
                int[] size = EntryLayout.placeholderSize(cfg, sink::width, lineHeight, PLACEHOLDER);
                f.width = size[0] + (cfg.showBackground ? PADDING * 2 : 0);
                f.height = size[1] + (cfg.showBackground ? PADDING * 2 : 0);
            }
        }

        return f;
    }

    public int scaledWidth(TextSink sink) {
        return Math.max(1, Math.round(frame(sink).width * scale()));
    }

    public int scaledHeight(TextSink sink) {
        return Math.max(1, Math.round(frame(sink).height * scale()));
    }

    /**
     * Stack the two groups, unless one has been dragged off on its own.
     *
     * <p>Drag offsets are stored in screen pixels because that is what an editor moves
     * them by; they are divided back down here so a detached group stays where it was
     * dropped at any scale.
     */
    private void placeGroups(Frame f, ListSettings cfg) {
        float s = scale();
        boolean stackVertically = cfg.direction == Direction.DOWN || cfg.direction == Direction.UP;
        int gap = Math.max(0, cfg.spacing) + 2;

        // A stand-in stacks them whatever the offsets say: see the field's javadoc.
        boolean posDetached = !standIn && (cfg.positiveXOffset != 0 || cfg.positiveYOffset != 0);
        boolean negDetached = !standIn && (cfg.negativeXOffset != 0 || cfg.negativeYOffset != 0);

        if (posDetached) {
            f.positiveX = Math.round(cfg.positiveXOffset / s);
            f.positiveY = Math.round(cfg.positiveYOffset / s);
        }

        if (negDetached) {
            f.negativeX = Math.round(cfg.negativeXOffset / s);
            f.negativeY = Math.round(cfg.negativeYOffset / s);
        } else if (!posDetached && !f.positive.entries.isEmpty()) {
            if (stackVertically) {
                f.negativeY = f.positive.height + gap;
            } else {
                f.negativeX = f.positive.width + gap;
            }
        }

        // Extents measured from the anchor. Negative local coordinates are clamped away
        // rather than shifting the origin: the box has to stay anchored where it was put,
        // or dragging one group would drag the whole display with it.
        f.width = Math.max(1, Math.max(
            f.positive.entries.isEmpty() ? 0 : f.positiveX + f.positive.width,
            f.negative.entries.isEmpty() ? 0 : f.negativeX + f.negative.width));
        f.height = Math.max(1, Math.max(
            f.positive.entries.isEmpty() ? 0 : f.positiveY + f.positive.height,
            f.negative.entries.isEmpty() ? 0 : f.negativeY + f.negative.height));
    }

    // --- Drawing ------------------------------------------------------------------

    /**
     * Draw the list with its top-left corner at {@code (screenX, screenY)}.
     *
     * @param frame geometry from {@link #frame(TextSink)}; pass the same one the caller
     *              measured with so sizes and pixels cannot disagree
     */
    public void draw(GuiGraphicsExtractor context, TextSink sink, Frame frame, int screenX, int screenY) {
        ListSettings cfg = cfg();

        // Ghost fade-out: the effect is already gone by the time the fade starts, so
        // redraw the last real frame rather than fading an empty box.
        if (frame.isEmpty() && !frame.placeholder && sink.alpha() < 1f && lastFrame != null) {
            frame = lastFrame;
        } else if (!frame.isEmpty()) {
            lastFrame = frame;
        }

        float s = scale();
        context.pose().pushMatrix();
        context.pose().translate((float) screenX, (float) screenY);
        context.pose().scale(s, s);

        if (frame.isEmpty()) {
            if (frame.placeholder) drawPlaceholder(context, sink, cfg);
            context.pose().popMatrix();
            return;
        }

        if (frame.merged != null) {
            drawResult(context, sink, frame.merged, 0, 0, cfg);
        } else {
            drawResult(context, sink, frame.positive, frame.positiveX, frame.positiveY, cfg);
            drawResult(context, sink, frame.negative, frame.negativeX, frame.negativeY, cfg);
        }

        context.pose().popMatrix();
        pruneAnimations();
    }

    private void drawPlaceholder(GuiGraphicsExtractor context, TextSink sink, ListSettings cfg) {
        int inset = cfg.showBackground ? PADDING : 0;
        int[] size = EntryLayout.placeholderSize(cfg, sink::width, sink.lineHeight(), PLACEHOLDER);
        if (cfg.showBackground) {
            drawBackground(context, sink, 0, 0, size[0] + PADDING * 2, size[1] + PADDING * 2, cfg);
        }
        int iconSize = iconWidth(cfg);
        if (iconSize > 0) {
            Bars.roundedRect(context, inset, inset, iconSize, iconSize,
                Math.max(2, iconSize / 3), sink.col(0x60FFFFFF));
        }
        sink.text(context, PLACEHOLDER, inset + iconSize + (iconSize > 0 ? EntryLayout.gap() : 0),
            inset + Math.max(0, (iconSize - sink.lineHeight()) / 2), 0xFFFFFFFF);
    }

    private static int iconWidth(ListSettings cfg) {
        boolean usesIcon = cfg.style != Style.TEXT && cfg.iconStyle != IconStyle.NONE;
        return usesIcon ? Math.max(4, cfg.iconSize) : 0;
    }

    private void drawResult(GuiGraphicsExtractor context, TextSink sink, EntryLayout.Result result,
                            int originX, int originY, ListSettings cfg) {
        if (result == null || result.entries.isEmpty()) return;

        if (cfg.showBackground) {
            drawBackground(context, sink, originX - PADDING, originY - PADDING,
                result.width + PADDING * 2, result.height + PADDING * 2, cfg);
        }

        int warnTicks = Math.max(1, warn().warnThresholdSeconds) * 20;
        boolean pulsing = cfg.pulseOnCombo && System.currentTimeMillis() < comboPulseUntil;

        for (EntryLayout.Entry entry : result.entries) {
            drawEntry(context, sink, entry, originX, originY, cfg, warnTicks, pulsing);
        }
    }

    private void drawBackground(GuiGraphicsExtractor context, TextSink sink,
                                int x, int y, int w, int h, ListSettings cfg) {
        int color = sink.col(Bars.packBg(cfg.backgroundColor, cfg.backgroundOpacity));
        if (cfg.roundedBackground) {
            Bars.roundedRect(context, x, y, w, h, 3, color);
        } else {
            Bars.rect(context, x, y, w, h, color);
        }
    }

    private void drawEntry(GuiGraphicsExtractor context, TextSink sink, EntryLayout.Entry entry,
                           int originX, int originY, ListSettings cfg, int warnTicks, boolean pulsing) {
        EffectSnapshot snap = entry.snap;
        boolean warning = warn().warnLowTime && !snap.infinite() && snap.duration() <= warnTicks;

        float appear = appearFactor(cfg, snap);
        float alphaFactor = appear;
        if (warning && warn().warnBlink) alphaFactor *= blink();

        int slide = Math.round((1f - appear) * 6f);
        int dx = originX + entry.x + slideX(cfg, slide);
        int dy = originY + entry.y + slideY(cfg, slide);

        if (warning && warn().warnShake) {
            // A sub-pixel jitter that reads as urgency without making the text unreadable.
            dx += (int) Math.round(Math.random() * 2 - 1);
            dy += (int) Math.round(Math.random() * 2 - 1);
        }

        int base = entryColor(cfg, snap, warning, pulsing);
        // Text colors carry only this entry's own factor — the sink applies the ambient
        // fade on top. Anything drawn straight to the screen has to carry both.
        int textColor = Bars.withAlpha(base, alphaFactor);
        int timeColor = Bars.withAlpha(warning ? base : (cfg.timeColor | 0xFF000000), alphaFactor);

        if (cfg.showBar) {
            drawBar(context, sink, entry, dx, dy, cfg, snap, base, alphaFactor);
        }

        if (entry.iconSize > 0 && cfg.iconStyle != IconStyle.NONE && cfg.style != Style.TEXT) {
            PotionIcons.draw(context, snap, dx + entry.iconX, dy + entry.iconY,
                entry.iconSize, cfg.iconStyle, sink.col(Bars.withAlpha(0xFFFFFFFF, alphaFactor)));
        }

        if (!entry.label.isEmpty()) {
            sink.text(context, entry.label, dx + entry.labelX, dy + entry.labelY, textColor);
        }
        if (!entry.time.isEmpty()) {
            sink.text(context, entry.time, dx + entry.timeX, dy + entry.timeY, timeColor);
        }
        if (!entry.badge.isEmpty()) {
            drawBadge(context, sink, entry, dx, dy, textColor);
        }
    }

    /** The level, drawn small in the icon's bottom-right corner for the icon-led styles. */
    private void drawBadge(GuiGraphicsExtractor context, TextSink sink,
                           EntryLayout.Entry entry, int dx, int dy, int color) {
        final float badgeScale = 0.6f;
        int width = sink.width(entry.badge);
        float bx = dx + entry.iconX + entry.iconSize - width * badgeScale;
        float by = dy + entry.iconY + entry.iconSize - sink.lineHeight() * badgeScale;

        context.pose().pushMatrix();
        context.pose().translate(bx, by);
        context.pose().scale(badgeScale, badgeScale);
        sink.text(context, entry.badge, 0, 0, color);
        context.pose().popMatrix();
    }

    private void drawBar(GuiGraphicsExtractor context, TextSink sink, EntryLayout.Entry entry,
                         int dx, int dy, ListSettings cfg, EffectSnapshot snap,
                         int base, float alphaFactor) {
        if (entry.barW <= 0 || entry.barH <= 0) return;

        int fillRgb = cfg.barColor & 0x00FFFFFF;
        if (cfg.colorMode == ColorMode.CATEGORY || cfg.colorMode == ColorMode.RAINBOW) {
            fillRgb = base & 0x00FFFFFF;
        }

        if (entry.barBehind) {
            // The bar becomes the row's own background, draining from full width to none.
            int wash = sink.col(Bars.withAlpha(Bars.packBg(fillRgb, 90), alphaFactor));
            int filled = Math.max(1, Math.round(entry.barW * snap.progress()));
            Bars.rect(context, dx + entry.barX, dy + entry.barY, filled, entry.barH, wash);
            return;
        }

        int track = sink.col(Bars.withAlpha(Bars.packBg(cfg.barTrackColor, cfg.barTrackOpacity), alphaFactor));
        int fill = sink.col(Bars.withAlpha(0xFF000000 | fillRgb, alphaFactor));
        Bars.progress(context, dx + entry.barX, dy + entry.barY, entry.barW, entry.barH,
            snap.progress(), track, fill);
    }

    private int entryColor(ListSettings cfg, EffectSnapshot snap, boolean warning, boolean pulsing) {
        if (warning) return warn().warnColor | 0xFF000000;

        int color;
        ColorMode mode = cfg.colorMode == null ? ColorMode.SINGLE : cfg.colorMode;
        switch (mode) {
            case CATEGORY -> color = snap.beneficial() ? cfg.positiveColor
                : snap.harmful() ? cfg.negativeColor : cfg.neutralColor;
            case RAINBOW -> color = rainbow();
            default -> color = cfg.textColor;
        }
        if (pulsing && snap.beneficial()) {
            color = Bars.lerpColor(color & 0x00FFFFFF, 0xFFFFFF, 0.6f);
        }
        return color | 0xFF000000;
    }

    /** Cycles hue per call, matching how the host mod's rainbow modules behave. */
    private int rainbow() {
        rainbowHue += 0.5f;
        if (rainbowHue > 360f) rainbowHue = 0f;
        return Mth.hsvToRgb(rainbowHue / 360f, 1.0f, 1.0f) | 0xFF000000;
    }

    private static float blink() {
        double phase = (System.currentTimeMillis() % 800L) / 800.0;
        return 0.4f + 0.6f * (float) Math.abs(Math.sin(phase * Math.PI));
    }

    /** 0 to 1 as an entry slides into the list; always 1 when animation is off. */
    private float appearFactor(ListSettings cfg, EffectSnapshot snap) {
        if (!cfg.animate) return 1f;
        Tween tween = entryAnims.computeIfAbsent(snap.key(), key -> {
            Tween fresh = new Tween(0f);
            fresh.snap(0f);
            return fresh;
        });
        float duration = Math.max(1, cfg.animationSpeed) / 100f;
        return Tween.clamp01(tween.update(1f, duration, Tween::backOut));
    }

    private static int slideX(ListSettings cfg, int slide) {
        if (slide == 0) return 0;
        return switch (cfg.direction == null ? Direction.DOWN : cfg.direction) {
            case RIGHT -> -slide;
            case LEFT -> slide;
            default -> 0;
        };
    }

    private static int slideY(ListSettings cfg, int slide) {
        if (slide == 0) return 0;
        return switch (cfg.direction == null ? Direction.DOWN : cfg.direction) {
            case DOWN -> -slide;
            case UP -> slide;
            default -> 0;
        };
    }

    /** Drop animation state for effects that are gone, so the map cannot grow forever. */
    private void pruneAnimations() {
        if (entryAnims.isEmpty()) return;
        Set<String> live = new HashSet<>();
        for (EffectSnapshot snap : effects()) {
            live.add(snap.key());
        }
        entryAnims.keySet().removeIf(key -> !live.contains(key));
    }

    /** Briefly brighten the buffs. Driven by the host mod's combo streak event. */
    public void pulse() {
        comboPulseUntil = System.currentTimeMillis() + 600L;
    }

    /** Forget cached geometry and animation, after settings are replaced wholesale. */
    public void reset() {
        entryAnims.clear();
        lastFrame = null;
    }
}

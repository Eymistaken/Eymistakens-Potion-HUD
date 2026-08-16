package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.EffectSnapshot;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
import com.eymistaken.potionhud.PotionHudConfig.IconStyle;
import com.eymistaken.potionhud.PotionHudConfig.TimeFormat;
import com.eymistaken.potionhud.PotionHudConfig.VanillaBar;
import com.eymistaken.potionhud.util.DurationFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Minecraft's own top-right effect grid: where each icon lands, and everything this mod
 * draws onto or in place of one.
 *
 * <p>Two callers, one arithmetic. {@link VanillaEffectOverlay} uses it in game, where
 * Minecraft paints the icons and we only add to them; the HUD module uses it in the
 * editor, where Minecraft's own layer is switched off and the whole grid — frames,
 * sprites and extras — is drawn from here instead. Sharing this class is what keeps the
 * editor's preview from drifting away from what the game actually shows.
 *
 * <p>The grid is re-derived rather than read back from Minecraft, because the vanilla
 * element does not report its layout. Every number in it is a setting, so a future change
 * to Minecraft's spacing is a slider away.
 */
public final class VanillaIconGrid {

    /** Vanilla draws the 18x18 effect sprite inset inside its 24x24 frame. */
    public static final int FRAME_INSET = 3;

    private static final Identifier FRAME =
        Identifier.fromNamespaceAndPath("minecraft", "hud/effect_background");
    private static final Identifier FRAME_AMBIENT =
        Identifier.fromNamespaceAndPath("minecraft", "hud/effect_background_ambient");

    /** Built once, on first use, and only ever from the editor. See {@link #previewEffects}. */
    private static List<EffectSnapshot> samples;

    private VanillaIconGrid() {}

    /** One effect and the top-left corner of the frame it is drawn in. */
    public record Cell(EffectSnapshot snap, int x, int y) {}

    // --- Geometry ---------------------------------------------------------------

    public static int cellSize(DisplaySettings cfg) {
        return Math.max(8, cfg.gridIconSize);
    }

    /**
     * Where every icon goes: beneficial effects along the top row, everything else along
     * the second, both filling right to left from the screen's edge.
     */
    public static List<Cell> layout(DisplaySettings cfg, List<EffectSnapshot> effects, int screenWidth) {
        List<EffectSnapshot> ordered = vanillaOrder(effects);
        List<Cell> cells = new ArrayList<>(ordered.size());

        int pitch = Math.max(8, cfg.gridPitch);
        int beneficial = 0;
        int other = 0;

        for (EffectSnapshot snap : ordered) {
            int x;
            int y = cfg.gridOriginY;
            if (snap.beneficial()) {
                beneficial++;
                x = screenWidth + cfg.gridOriginX - pitch * beneficial;
            } else {
                other++;
                x = screenWidth + cfg.gridOriginX - pitch * other;
                y += cfg.gridRowGap;
            }
            cells.add(new Cell(snap, x, y));
        }
        return cells;
    }

    /**
     * The rectangle every cell sits inside.
     *
     * @return {@code {x, y, width, height}}, or null when there is nothing to enclose
     */
    public static int[] bounds(DisplaySettings cfg, List<Cell> cells) {
        if (cells.isEmpty()) return null;

        int cell = cellSize(cfg);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Cell c : cells) {
            minX = Math.min(minX, c.x());
            minY = Math.min(minY, c.y());
            maxX = Math.max(maxX, c.x() + cell);
            maxY = Math.max(maxY, c.y() + cell);
        }
        return new int[] { minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY) };
    }

    /**
     * Vanilla's own ordering: ambient effects first, then ascending duration, then
     * colour — all reversed. Reproduced so the extras land on the icon they belong to.
     */
    public static List<EffectSnapshot> vanillaOrder(List<EffectSnapshot> source) {
        List<EffectSnapshot> out = new ArrayList<>(source.size());
        for (EffectSnapshot snap : source) {
            if (snap.showIcon()) out.add(snap);
        }
        out.sort(Comparator.<EffectSnapshot>comparingInt(snap -> snap.ambient() ? 1 : 0)
            .thenComparingInt(EffectSnapshot::duration)
            .thenComparingInt(EffectSnapshot::color));
        Collections.reverse(out);
        return out;
    }

    // --- Drawing ------------------------------------------------------------------

    /**
     * The icons themselves, frame and all — Minecraft's job in game, ours in the editor,
     * where its own effect layer is switched off so the preview owns these pixels.
     */
    public static void drawIcons(GuiGraphicsExtractor context, TextSink sink,
                                 DisplaySettings cfg, List<Cell> cells) {
        int cell = cellSize(cfg);
        int inner = Math.max(1, cell - FRAME_INSET * 2);
        int tint = sink.col(0xFFFFFFFF);

        for (Cell c : cells) {
            Identifier frame = c.snap().ambient() ? FRAME_AMBIENT : FRAME;
            context.blitSprite(RenderPipelines.GUI_TEXTURED, frame, c.x(), c.y(), cell, cell, tint);
            PotionIcons.draw(context, c.snap(), c.x() + FRAME_INSET, c.y() + FRAME_INSET,
                inner, IconStyle.SPRITE, tint);
        }
    }

    /**
     * Everything {@link PotionHudConfig.DisplayMode#ENHANCED} adds on top of an icon: the
     * duration reading, and the timer and level for anyone who asked for them.
     *
     * <p>Leaves {@code sink}'s opacity exactly as it found it — the blink is per icon, and
     * the caller's own fade has to survive it.
     */
    public static void drawExtras(GuiGraphicsExtractor context, TextSink sink,
                                  DisplaySettings cfg, List<Cell> cells) {
        int cell = cellSize(cfg);
        int warnTicks = Math.max(1, cfg.warnThresholdSeconds) * 20;
        float base = sink.alpha();

        for (Cell c : cells) {
            EffectSnapshot snap = c.snap();
            boolean warning = cfg.warnLowTime && !snap.infinite() && snap.duration() <= warnTicks;
            float fade = base * (warning && cfg.warnBlink ? blink() : 1f);
            sink.setAlpha(fade);

            drawDuration(context, cfg, snap, c.x(), c.y(), cell, warning, fade);

            if (cfg.vanillaTimer) {
                String time = DurationFormat.time(snap.duration(), TimeFormat.CLOCK,
                    snap.infinite(), PotionHudConfig.instance.list.infiniteLabel);
                if (!time.isEmpty()) {
                    int color = warning ? opaque(cfg.warnColor) : opaque(cfg.vanillaTimerColor);
                    drawSmall(context, sink, time, c.x() + cell / 2f, c.y() + cell - 10f, color, true);
                }
            }

            if (cfg.vanillaLevel) {
                String level = DurationFormat.level(
                    snap.amplifier(), PotionHudConfig.instance.list.levelStyle, false);
                if (!level.isEmpty()) {
                    drawSmall(context, sink, level, c.x() + FRAME_INSET, c.y() + 1f, 0xFFFFFFFF, false);
                }
            }
        }

        sink.setAlpha(base);
    }

    /**
     * How much time is left, drawn into the icon rather than on top of it.
     *
     * <p>Both styles stay inside the 18x18 sprite and out of the frame's border, and both
     * spend their weight on the part that is <em>gone</em> rather than the part that is
     * left: a dark, low-alpha wash reads as the icon being used up, where an opaque black
     * track reads as a progress bar someone glued to a texture.
     */
    private static void drawDuration(GuiGraphicsExtractor context, DisplaySettings cfg, EffectSnapshot snap,
                                     int x, int y, int cell, boolean warning, float fade) {
        VanillaBar style = cfg.vanillaBar == null ? VanillaBar.UNDERLINE : cfg.vanillaBar;
        if (style == VanillaBar.NONE || snap.infinite()) return;

        int inner = cell - FRAME_INSET * 2;
        if (inner < 4) return;
        float progress = Math.max(0f, Math.min(1f, snap.progress()));

        int rgb = warning ? cfg.warnColor & 0x00FFFFFF
            : cfg.vanillaBarEffectColor ? snap.color() & 0x00FFFFFF
            : cfg.vanillaBarColor & 0x00FFFFFF;

        if (style == VanillaBar.DRAIN) {
            // Shade the spent portion from the top down, the way a cooldown reads. Nothing
            // is added to the icon's silhouette at all — it just darkens as it runs out.
            int spent = Math.round(inner * (1f - progress));
            if (spent > 0) {
                Bars.rect(context, x + FRAME_INSET, y + FRAME_INSET, inner, spent,
                    Bars.withAlpha(0x66000000, fade));
            }
            if (warning) {
                Bars.rect(context, x + FRAME_INSET, y + FRAME_INSET, inner, inner,
                    Bars.withAlpha(0x30000000 | rgb, fade));
            }
            return;
        }

        // UNDERLINE: a hairline on the sprite's bottom row. The remainder is a faint
        // shadow rather than a track, so at full duration there is nothing to notice.
        int thickness = Math.max(1, Math.min(3, cfg.vanillaBarThickness));
        int barY = y + cell - FRAME_INSET - thickness;
        int filled = Math.round(inner * progress);

        Bars.rect(context, x + FRAME_INSET, barY, inner, thickness,
            Bars.withAlpha(0x50000000, fade));
        if (filled > 0) {
            Bars.rect(context, x + FRAME_INSET, barY, filled, thickness,
                Bars.withAlpha(0xE0000000 | rgb, fade));
            // One row of the same colour at a fraction of the alpha, bleeding up into the
            // sprite. This is what stops the line looking pasted on.
            if (barY - 1 > y) {
                Bars.rect(context, x + FRAME_INSET, barY - 1, filled, 1,
                    Bars.withAlpha(0x40000000 | rgb, fade));
            }
        }
    }

    /** Half-size text, drawn through the same sink as everything else so styling matches. */
    private static void drawSmall(GuiGraphicsExtractor context, TextSink sink, String text,
                                  float centerX, float topY, int color, boolean centered) {
        final float scale = 0.5f;
        int width = sink.width(text);

        context.pose().pushMatrix();
        context.pose().translate(centered ? centerX - width * scale / 2f : centerX, topY);
        context.pose().scale(scale, scale);
        sink.text(context, text, 0, 0, color);
        context.pose().popMatrix();
    }

    /** A steady pulse rather than a hard on/off, so it reads as urgency, not a glitch. */
    private static float blink() {
        double phase = (System.currentTimeMillis() % 800L) / 800.0;
        return 0.45f + 0.55f * (float) Math.abs(Math.sin(phase * Math.PI));
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    // --- Editor preview -------------------------------------------------------------

    /**
     * What the editor's preview should draw.
     *
     * <p>The player's own effects whenever there are any. When there are none the grid
     * would be empty, and an empty grid is a module with no size — nothing to grab, and no
     * way back to another display mode. Two stand-ins fix that and double as a look at
     * what the duration bar and timer settings actually do.
     */
    public static List<EffectSnapshot> previewEffects(List<EffectSnapshot> live) {
        for (EffectSnapshot snap : live) {
            if (snap.showIcon()) return live;
        }
        return samples();
    }

    /** One of each row: something beneficial on top, something harmful under it. */
    private static List<EffectSnapshot> samples() {
        if (samples == null) {
            List<EffectSnapshot> built = new ArrayList<>(2);
            // Held part-spent on purpose, so the bar has something to show.
            sample(built, MobEffects.SPEED, 24 * 20, 0, 40 * 20);
            sample(built, MobEffects.POISON, 21 * 20, 1, 30 * 20);
            samples = List.copyOf(built);
        }
        return samples;
    }

    private static void sample(List<EffectSnapshot> out, Holder<MobEffect> effect,
                               int duration, int amplifier, int peak) {
        try {
            out.add(EffectSnapshot.of(new MobEffectInstance(effect, duration, amplifier), peak));
        } catch (Exception ignored) {
            // A stand-in is a convenience; failing to build one must not take the editor
            // down with it. The caller falls back to its own placeholder.
        }
    }
}

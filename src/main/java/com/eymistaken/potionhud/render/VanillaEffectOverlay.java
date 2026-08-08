package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.EffectSnapshot;
import com.eymistaken.potionhud.EffectTracker;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
import com.eymistaken.potionhud.PotionHudConfig.TimeFormat;
import com.eymistaken.potionhud.PotionHudConfig.VanillaBar;
import com.eymistaken.potionhud.util.DurationFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Wraps Minecraft's own top-right effect icons, and is the only thing standing between the
 * player and them: it decides per frame whether they are drawn plainly, drawn with a
 * duration readout woven in, or not drawn at all.
 *
 * <p>Registered once through {@code HudElementRegistry.replaceElement}, then reads
 * {@link DisplayMode} every frame — which is why switching modes takes effect on the spot
 * rather than on the next restart.
 *
 * <p>The extras have to re-derive where vanilla put each icon, because the vanilla element
 * does not report its layout. That arithmetic is one small block here and every number in
 * it is a setting, so a future change to Minecraft's spacing is a slider away.
 */
public final class VanillaEffectOverlay implements HudElement {

    /** Vanilla draws the 18x18 effect sprite inset inside its 24x24 frame. */
    private static final int FRAME_INSET = 3;

    private final HudElement original;
    private final TextSink sink = new TextSink();

    private VanillaEffectOverlay(HudElement original) {
        this.original = original;
    }

    /** Handed to {@code HudElementRegistry.replaceElement} as a method reference. */
    public static HudElement wrap(HudElement original) {
        return new VanillaEffectOverlay(original);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        DisplaySettings cfg = PotionHudConfig.instance.display;
        DisplayMode mode = cfg.mode == null ? DisplayMode.ENHANCED : cfg.mode;

        // Switched off entirely, the icons go back to behaving as if the mod were not
        // installed — which is the difference between that and HIDDEN.
        if (!cfg.enabled) {
            original.extractRenderState(graphics, deltaTracker);
            return;
        }

        // CUSTOM hides these too: the whole point of moving the display somewhere else is
        // not to have it in two places at once.
        if (mode == DisplayMode.HIDDEN || mode == DisplayMode.CUSTOM) return;

        original.extractRenderState(graphics, deltaTracker);

        if (mode == DisplayMode.ENHANCED) drawOverlay(graphics, cfg);
    }

    private void drawOverlay(GuiGraphicsExtractor context, DisplaySettings cfg) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        // Vanilla hands the effect display over to the screen when one is open; drawing
        // over an empty corner would leave the extras floating on their own.
        if (client.gui.screen() != null) return;

        List<EffectSnapshot> effects = vanillaOrder(EffectTracker.snapshots());
        if (effects.isEmpty()) return;

        int screenWidth = context.guiWidth();
        int pitch = Math.max(8, cfg.gridPitch);
        int cell = Math.max(8, cfg.gridIconSize);
        int warnTicks = Math.max(1, cfg.warnThresholdSeconds) * 20;

        int beneficial = 0;
        int other = 0;

        for (EffectSnapshot snap : effects) {
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

            boolean warning = cfg.warnLowTime && !snap.infinite() && snap.duration() <= warnTicks;
            float pulse = warning && cfg.warnBlink ? blink() : 1f;
            sink.setAlpha(pulse);

            drawDuration(context, cfg, snap, x, y, cell, warning, pulse);

            if (cfg.vanillaTimer) {
                String time = DurationFormat.time(snap.duration(), TimeFormat.CLOCK,
                    snap.infinite(), PotionHudConfig.instance.list.infiniteLabel);
                if (!time.isEmpty()) {
                    int color = warning ? opaque(cfg.warnColor) : opaque(cfg.vanillaTimerColor);
                    drawSmall(context, time, x + cell / 2f, y + cell - 10f, color, true);
                }
            }

            if (cfg.vanillaLevel) {
                String level = DurationFormat.level(
                    snap.amplifier(), PotionHudConfig.instance.list.levelStyle, false);
                if (!level.isEmpty()) {
                    drawSmall(context, level, x + FRAME_INSET, y + 1f, 0xFFFFFFFF, false);
                }
            }
        }

        sink.setAlpha(1f);
    }

    /**
     * How much time is left, drawn into the icon rather than on top of it.
     *
     * <p>Both styles stay inside the 18x18 sprite and out of the frame's border, and both
     * spend their weight on the part that is <em>gone</em> rather than the part that is
     * left: a dark, low-alpha wash reads as the icon being used up, where an opaque black
     * track reads as a progress bar someone glued to a texture.
     */
    private void drawDuration(GuiGraphicsExtractor context, DisplaySettings cfg, EffectSnapshot snap,
                              int x, int y, int cell, boolean warning, float pulse) {
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
                    Bars.withAlpha(0x66000000, pulse));
            }
            if (warning) {
                Bars.rect(context, x + FRAME_INSET, y + FRAME_INSET, inner, inner,
                    Bars.withAlpha(0x30000000 | rgb, pulse));
            }
            return;
        }

        // UNDERLINE: a hairline on the sprite's bottom row. The remainder is a faint
        // shadow rather than a track, so at full duration there is nothing to notice.
        int thickness = Math.max(1, Math.min(3, cfg.vanillaBarThickness));
        int barY = y + cell - FRAME_INSET - thickness;
        int filled = Math.round(inner * progress);

        Bars.rect(context, x + FRAME_INSET, barY, inner, thickness,
            Bars.withAlpha(0x50000000, pulse));
        if (filled > 0) {
            Bars.rect(context, x + FRAME_INSET, barY, filled, thickness,
                Bars.withAlpha(0xE0000000 | rgb, pulse));
            // One row of the same colour at a fraction of the alpha, bleeding up into the
            // sprite. This is what stops the line looking pasted on.
            if (barY - 1 > y) {
                Bars.rect(context, x + FRAME_INSET, barY - 1, filled, 1,
                    Bars.withAlpha(0x40000000 | rgb, pulse));
            }
        }
    }

    /**
     * Vanilla's own ordering: ambient effects first, then ascending duration, then
     * colour — all reversed. Reproduced so the extras land on the icon they belong to.
     */
    private static List<EffectSnapshot> vanillaOrder(List<EffectSnapshot> source) {
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

    /** A steady pulse rather than a hard on/off, so it reads as urgency, not a glitch. */
    private static float blink() {
        double phase = (System.currentTimeMillis() % 800L) / 800.0;
        return 0.45f + 0.55f * (float) Math.abs(Math.sin(phase * Math.PI));
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    /** Half-size text, drawn through the same sink as everything else so styling matches. */
    private void drawSmall(GuiGraphicsExtractor context, String text,
                           float centerX, float topY, int color, boolean centered) {
        final float scale = 0.5f;
        int width = sink.width(text);

        context.pose().pushMatrix();
        context.pose().translate(centered ? centerX - width * scale / 2f : centerX, topY);
        context.pose().scale(scale, scale);
        sink.text(context, text, 0, 0, color);
        context.pose().popMatrix();
    }
}

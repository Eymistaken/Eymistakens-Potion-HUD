package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.EffectTracker;
import com.eymistaken.potionhud.HudCompat;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
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
 * <p>Where each icon lands, and what gets drawn onto it, both live in
 * {@link VanillaIconGrid}; this class is only the gate.
 */
public final class VanillaEffectOverlay implements HudElement {

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
        // In the HUD editor the module previews whichever mode is selected, and it draws
        // the whole grid itself — frames, sprites and all — because the extras cannot be
        // put on icons that Minecraft may or may not paint behind a screen. Leaving this
        // element on as well would draw every icon twice.
        if (HudCompat.editorOpen()) return;

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

        if (mode == DisplayMode.ENHANCED) drawExtras(graphics, cfg);
    }

    private void drawExtras(GuiGraphicsExtractor context, DisplaySettings cfg) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        // Vanilla hands the effect display over to the screen when one is open; drawing
        // over an empty corner would leave the extras floating on their own.
        if (client.gui.screen() != null) return;

        List<VanillaIconGrid.Cell> cells =
            VanillaIconGrid.layout(cfg, EffectTracker.snapshots(), context.guiWidth());
        if (cells.isEmpty()) return;

        sink.setAlpha(1f);
        VanillaIconGrid.drawExtras(context, sink, cfg, cells);
    }
}

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
import net.minecraft.client.gui.screens.Screen;

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

        // VANILLA means exactly that: Minecraft's icons, untouched, nothing added.
        if (mode != DisplayMode.ENHANCED) {
            original.extractRenderState(graphics, deltaTracker);
            return;
        }

        drawGrid(graphics, cfg);
    }

    /**
     * The enhanced grid, icons and all.
     *
     * <p>Minecraft's own element is deliberately not called here. It lays its icons out
     * with numbers of its own that it never reports, so anything drawn onto them has to
     * guess where they are — and the moment the spacing settings say something different
     * from that guess, the readouts slide off the icons they belong to while the icons
     * themselves sit still. Painting both from one set of numbers is what makes those
     * settings mean something.
     */
    private void drawGrid(GuiGraphicsExtractor context, DisplaySettings cfg) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // Vanilla stands its own display down for a screen that shows the effects itself,
        // and only for such a screen — an inventory, not the chat box. Standing down for
        // every screen would make the icons vanish the moment anyone opened one.
        Screen screen = client.gui.screen();
        if (screen != null && screen.showsActiveEffects()) return;

        List<VanillaIconGrid.Cell> cells =
            VanillaIconGrid.layout(cfg, EffectTracker.snapshots(), context.guiWidth());
        if (cells.isEmpty()) return;

        sink.setAlpha(1f);
        VanillaIconGrid.drawIcons(context, sink, cfg, cells);
        VanillaIconGrid.drawExtras(context, sink, cfg, cells);
    }
}

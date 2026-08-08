package com.eymistaken.potionhud.standalone;

import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.render.TextSink;
import com.eymistaken.potionhud.util.Tween;
import com.eymistaken.potionhud.view.Anchors;
import com.eymistaken.potionhud.view.PotionListView;
import com.eymistaken.potionhud.view.PotionViews;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the custom effect list when Eymistaken's HUD is not installed.
 *
 * <p>Registered as a plain Fabric HUD element, and only when the HUD is absent — with it
 * present, this never runs and the modules do the drawing instead, so the two can never
 * both put a potion list on screen.
 *
 * <p>What is lost without the host: the drag-and-drop editor, presets and share codes.
 * What is kept: every display, every setting, and the same fade in and out — the anchor
 * and offsets come from the same config fields the editor would have written.
 */
public final class StandaloneHud implements HudElement {

    private static final float FADE_SECONDS = 0.18f;

    private final TextSink listSink = new TextSink();
    private final Tween listFade = new Tween(0f);

    public StandaloneHud() {
        listFade.snap(0f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        PotionHudConfig config = PotionHudConfig.instance;
        if (!config.general.standaloneRendering) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.gui.hud.isHidden()) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        drawList(graphics, config, screenWidth, screenHeight);
    }

    private void drawList(GuiGraphicsExtractor graphics, PotionHudConfig config, int screenW, int screenH) {
        // The one condition, same as the module's isEnabled(): this list exists only in
        // the mode that asked for it.
        boolean wanted = config.display.enabled
            && config.display.mode == DisplayMode.CUSTOM
            && PotionViews.LIST.hasContent();
        float alpha = listFade.update(wanted ? 1f : 0f, FADE_SECONDS, Tween::sineInOut);
        if (alpha <= 0.02f) return;

        listSink.setAlpha(alpha);
        PotionListView.Frame frame = PotionViews.LIST.frame(listSink);
        int width = Math.max(1, Math.round(frame.width * PotionViews.LIST.scale()));
        int height = Math.max(1, Math.round(frame.height * PotionViews.LIST.scale()));

        PotionViews.LIST.draw(graphics, listSink, frame,
            Anchors.x(config.list.anchor, screenW, width, config.list.xOffset),
            Anchors.y(config.list.anchor, screenH, height, config.list.yOffset));
    }
}


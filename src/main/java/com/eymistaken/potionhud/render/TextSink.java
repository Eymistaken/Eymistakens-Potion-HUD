package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.HudCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * How the views draw and measure text, and how they fade.
 *
 * <p>One implementation covers both worlds. With Eymistaken's HUD installed it renders in
 * the font and outline style the user chose for their whole HUD; without it, plain vanilla
 * text with an outline toggle of our own. The views never find out which, which is what
 * lets the exact same layout and drawing code back a HUD module and a standalone overlay.
 */
public final class TextSink {

    private float alpha = 1f;

    /**
     * Ambient opacity, in {@code [0,1]}. Driven by the HUD's fade animation in addon mode
     * and by the standalone layer's own fade otherwise.
     */
    public void setAlpha(float alpha) {
        this.alpha = alpha < 0f ? 0f : (alpha > 1f ? 1f : alpha);
    }

    public float alpha() {
        return alpha;
    }

    /** Apply the ambient opacity to an ARGB color. */
    public int col(int argb) {
        if (alpha >= 1f) return argb;
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public Font font() {
        return Minecraft.getInstance().font;
    }

    public int lineHeight() {
        return font().lineHeight;
    }

    public int width(String text) {
        return font().width(HudCompat.styled(text));
    }

    /** Draw {@code text}, applying the ambient opacity to {@code argb} on the way. */
    public void text(GuiGraphicsExtractor context, String text, int x, int y, int argb) {
        Font font = font();
        Component component = HudCompat.styled(text);
        int color = col(argb);

        if (HudCompat.textOutline()) {
            // A 1px border on all eight sides instead of the vanilla drop shadow: it
            // reads far better over a busy PvP background, and it is what the HUD does
            // for its own modules, so ours match when both are installed.
            int outline = color & 0xFF000000;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    context.text(font, component, x + dx, y + dy, outline, false);
                }
            }
            context.text(font, component, x, y, color, false);
        } else {
            context.text(font, component, x, y, color);
        }
    }
}

package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.EffectSnapshot;
import com.eymistaken.potionhud.PotionHudConfig.IconStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The one place this mod touches Minecraft's texture API.
 *
 * <p>Effect icons are resolved through {@link Hud#getMobEffectSprite}, the same helper
 * Minecraft's own effect display and inventory screen use, so a resource pack that
 * restyles potion icons restyles ours with it.
 *
 * <p>{@link IconStyle#DOT} is a deliberate second path that needs nothing but
 * {@code fill}: a badge in the effect's own colour, for anyone who wants the list to
 * read as colour rather than pictograms.
 */
public final class PotionIcons {

    /** Fallback colours when an effect reports no colour of its own. */
    private static final int BENEFICIAL = 0x55FF55;
    private static final int HARMFUL = 0xFF5555;
    private static final int NEUTRAL = 0x55AAFF;

    private PotionIcons() {}

    /**
     * Draw one effect icon with its top-left corner at {@code (x, y)}.
     *
     * @param tint ARGB tint, already faded by the caller's {@code col(...)}
     */
    public static void draw(GuiGraphicsExtractor context, EffectSnapshot snap,
                            int x, int y, int size, IconStyle style, int tint) {
        if (style == IconStyle.NONE || size <= 0) return;

        int rgb = effectColor(snap);
        if (style == IconStyle.DOT) {
            drawDot(context, rgb, x, y, size, tint);
            return;
        }

        Identifier sprite = Hud.getMobEffectSprite(snap.effect());
        if (sprite == null) {
            drawDot(context, rgb, x, y, size, tint);
            return;
        }
        // The tint argument is what carries the module's fade onto the icon; the
        // four-int overload would draw it at full opacity regardless.
        context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size, tint);
    }

    /**
     * A filled badge in the effect's own colour: the icon-free style, and the automatic
     * stand-in whenever the sprite is missing.
     */
    private static void drawDot(GuiGraphicsExtractor context, int rgb,
                                int x, int y, int size, int tint) {
        int alpha = (tint >>> 24) & 0xFF;
        rgb &= 0x00FFFFFF;
        int fill = (alpha << 24) | rgb;
        int border = (alpha << 24) | darken(rgb, 0.55f);

        int radius = Math.max(2, size / 3);
        Bars.roundedRect(context, x, y, size, size, radius, border);
        Bars.roundedRect(context, x + 1, y + 1, size - 2, size - 2, Math.max(1, radius - 1), fill);
    }

    /** The effect's particle colour, falling back to a colour for its category. */
    public static int effectColor(EffectSnapshot snap) {
        int color = snap.color() & 0x00FFFFFF;
        if (color != 0) return color;
        return categoryColor(snap.category());
    }

    public static int categoryColor(MobEffectCategory category) {
        if (category == MobEffectCategory.BENEFICIAL) return BENEFICIAL;
        if (category == MobEffectCategory.HARMFUL) return HARMFUL;
        return NEUTRAL;
    }

    private static int darken(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}

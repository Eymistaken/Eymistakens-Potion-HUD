package com.eymistaken.potionhud.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Boxes and bars, drawn entirely out of {@code fill} rectangles.
 *
 * <p>Same approach the HUD's own keystrokes module takes for its ripple: no textures, no
 * shaders, nothing that behaves differently between resource packs — just axis-aligned
 * spans, one scanline at a time.
 */
public final class Bars {

    private Bars() {}

    /** Plain filled box. {@code w}/{@code h} are sizes, not a second corner. */
    public static void rect(GuiGraphicsExtractor context, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        context.fill(x, y, x + w, y + h, argb);
    }

    /**
     * Filled box with rounded corners, built scanline by scanline so the curve is exact
     * at any radius and needs no texture.
     */
    public static void roundedRect(GuiGraphicsExtractor context, int x, int y, int w, int h, int radius, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;

        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            rect(context, x, y, w, h, argb);
            return;
        }

        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < r) {
                int dy = r - row - 1;
                inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
            } else if (row >= h - r) {
                int dy = row - (h - r);
                inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
            }
            int rowWidth = w - inset * 2;
            if (rowWidth > 0) {
                context.fill(x + inset, y + row, x + inset + rowWidth, y + row + 1, argb);
            }
        }
    }

    /**
     * Horizontal progress bar: a track, then a fill sized to {@code progress}.
     *
     * @param progress fraction in {@code [0,1]}; anything above 0 keeps at least one
     *                 visible pixel so an almost-empty bar never blinks out early
     */
    public static void progress(GuiGraphicsExtractor context, int x, int y, int w, int h,
                                float progress, int trackArgb, int fillArgb) {
        if (w <= 0 || h <= 0) return;
        rect(context, x, y, w, h, trackArgb);

        if (progress <= 0f) return;
        if (progress > 1f) progress = 1f;
        int filled = Math.max(1, Math.round(w * progress));
        rect(context, x, y, Math.min(filled, w), h, fillArgb);
    }

    /**
     * Scale an ARGB colour's alpha by {@code factor}, clamped to {@code [0,1]}, leaving
     * its RGB alone.
     */
    public static int withAlpha(int argb, float factor) {
        if (factor >= 1f) return argb;
        if (factor < 0f) factor = 0f;
        int a = Math.round(((argb >>> 24) & 0xFF) * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Pack a background colour and an 0..255 opacity into one ARGB int.
     *
     * @param color   RGB colour; any alpha byte on it is ignored
     * @param opacity 0..255
     */
    public static int packBg(int color, int opacity) {
        return ((opacity & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    /** Blend two RGB colours; alpha comes from neither, callers pack it themselves. */
    public static int lerpColor(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}

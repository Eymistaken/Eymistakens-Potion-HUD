package com.eymistaken.potionhud.view;

import com.eymistaken.potionhud.PotionHudConfig.Anchor;

/**
 * Turns an anchor plus an offset into screen coordinates.
 *
 * <p>Only used by the standalone renderer. With Eymistaken's HUD installed its own
 * placement resolver does this job — and does more with it, since it also handles snapping
 * and overlap — so the two must agree on the basics: the same five corners and the same
 * five-pixel margin, measured from the display's own bounding box.
 */
public final class Anchors {

    /** Margin from the screen edge, matching what the host HUD leaves. */
    private static final int GAP = 5;

    private Anchors() {}

    public static int x(Anchor anchor, int screenWidth, int width, int offset) {
        int base = switch (anchor == null ? Anchor.TOP_LEFT : anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - GAP;
            case CENTER -> (screenWidth - width) / 2;
            default -> GAP;
        };
        return base + offset;
    }

    public static int y(Anchor anchor, int screenHeight, int height, int offset) {
        int base = switch (anchor == null ? Anchor.TOP_LEFT : anchor) {
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - GAP;
            case CENTER -> (screenHeight - height) / 2;
            default -> GAP;
        };
        return base + offset;
    }
}

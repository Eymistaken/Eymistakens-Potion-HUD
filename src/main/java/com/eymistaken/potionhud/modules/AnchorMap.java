package com.eymistaken.potionhud.modules;

import com.eymistaken.potionhud.PotionHudConfig.Anchor;
import com.eymistaken.simplecps.SimpleCPSConfig.Position;

/**
 * Translates between our {@link Anchor} and the HUD's {@code Position}.
 *
 * <p>The two enums list the same five corners, but they cannot be the same type: ours has
 * to survive on an install with no HUD, and its has to be what the editor's placement API
 * receives. This class lives in the modules package, which only ever loads with the HUD
 * present.
 */
final class AnchorMap {

    private AnchorMap() {}

    static Position toHud(Anchor anchor) {
        if (anchor == null) return Position.TOP_LEFT;
        return switch (anchor) {
            case TOP_RIGHT -> Position.TOP_RIGHT;
            case BOTTOM_LEFT -> Position.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> Position.BOTTOM_RIGHT;
            case CENTER -> Position.CENTER;
            default -> Position.TOP_LEFT;
        };
    }

    static Anchor fromHud(Position position) {
        if (position == null) return Anchor.TOP_LEFT;
        return switch (position) {
            case TOP_RIGHT -> Anchor.TOP_RIGHT;
            case BOTTOM_LEFT -> Anchor.BOTTOM_LEFT;
            case BOTTOM_RIGHT -> Anchor.BOTTOM_RIGHT;
            case CENTER -> Anchor.CENTER;
            default -> Anchor.TOP_LEFT;
        };
    }
}

package com.eymistaken.potionhud.view;

/**
 * The effect list, as one instance shared by whichever path is drawing it.
 *
 * <p>It has to be shared rather than created per renderer: it accumulates per-effect
 * animation state, so if the HUD module and the standalone layer each held their own
 * copy, one would be fed and the other would be drawn.
 */
public final class PotionViews {

    public static final PotionListView LIST = new PotionListView();

    private PotionViews() {}
}

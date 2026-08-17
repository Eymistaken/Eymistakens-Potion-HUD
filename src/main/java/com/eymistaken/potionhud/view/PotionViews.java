package com.eymistaken.potionhud.view;

import com.eymistaken.potionhud.EffectTracker;

/**
 * The effect list, as one instance shared by whichever path is drawing it.
 *
 * <p>It has to be shared rather than created per renderer: it accumulates per-effect
 * animation state, so if the HUD module and the standalone layer each held their own
 * copy, one would be fed and the other would be drawn.
 *
 * <p>The one thing that does get its own instance is the settings screen's preview, which
 * draws stand-in effects alongside this one rather than instead of it — sharing animation
 * state with a view being drawn a second time in the same frame is exactly the thing this
 * class exists to make impossible.
 */
public final class PotionViews {

    public static final PotionListView LIST = new PotionListView(EffectTracker::snapshots, false);

    private PotionViews() {}
}

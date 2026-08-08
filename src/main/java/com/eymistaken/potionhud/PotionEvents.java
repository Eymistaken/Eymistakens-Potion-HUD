package com.eymistaken.potionhud;

/**
 * Where this mod publishes its potion events, and a one-way bridge onto Eymistaken's HUD's
 * bus when that mod is installed.
 *
 * <p>The indirection is the point. Publishing straight onto the HUD's bus would have been
 * shorter, but it would also mean {@link EffectTracker} cannot run without the HUD — and
 * the whole point of the optional dependency is that it can. So the tracker always
 * publishes here, and here decides whether there is a bus to forward to.
 *
 * <p>Nothing inside this mod subscribes; the events exist for other addons, which reach
 * them through the HUD's own bus on exactly the terms its API documents.
 */
public final class PotionEvents {

    private PotionEvents() {}

    /** Hand an event to the HUD's bus, if the HUD is there to have one. */
    public static void emit(Object event) {
        if (event == null) return;
        HudCompat.forwardEvent(event);
    }
}

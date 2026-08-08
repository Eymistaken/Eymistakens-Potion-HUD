package com.eymistaken.potionhud.event;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * Fired when the player gains an effect they did not have, or when an effect they
 * already had is re-applied at a higher level.
 *
 * <p>Published on {@link com.eymistaken.simplecps.api.EymistakenHudEventBus}, so any other
 * Eymistaken's HUD addon can subscribe to it the same way it subscribes to the HUD's own
 * events. Dispatch is by exact class, and handlers run inline on the client thread.
 *
 * @param effect    the effect holder
 * @param id        registry id, e.g. {@code minecraft:speed}
 * @param amplifier 0 for level I
 * @param duration  ticks the effect started with, as first seen
 * @param ambient   true for beacon/conduit effects
 */
public record PotionEffectGainedEvent(
    Holder<MobEffect> effect,
    Identifier id,
    int amplifier,
    int duration,
    boolean ambient
) {}

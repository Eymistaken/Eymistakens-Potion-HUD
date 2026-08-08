package com.eymistaken.potionhud.event;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * Fired when an effect the player had is gone: it ran out, was drunk away with milk, or
 * was replaced by a different level of itself.
 *
 * <p>{@code expired} tells those apart — true when the effect simply ran down to zero,
 * false when it disappeared with time still on it.
 *
 * @param effect    the effect holder
 * @param id        registry id
 * @param amplifier 0 for level I
 * @param expired   true when it ran out on its own
 */
public record PotionEffectLostEvent(
    Holder<MobEffect> effect,
    Identifier id,
    int amplifier,
    boolean expired
) {}

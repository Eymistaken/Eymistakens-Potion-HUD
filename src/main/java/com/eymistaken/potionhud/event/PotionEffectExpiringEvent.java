package com.eymistaken.potionhud.event;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * Fired once when an effect drops below the configured warning threshold — not every
 * tick after it. Re-arms if the effect is topped back up above the threshold, so
 * drinking another potion gets you a fresh warning later.
 *
 * <p>Never fired for infinite-duration effects.
 *
 * @param effect         the effect holder
 * @param id             registry id
 * @param amplifier      0 for level I
 * @param remaining      ticks left at the moment it crossed the threshold
 * @param thresholdTicks the threshold it crossed
 */
public record PotionEffectExpiringEvent(
    Holder<MobEffect> effect,
    Identifier id,
    int amplifier,
    int remaining,
    int thresholdTicks
) {}

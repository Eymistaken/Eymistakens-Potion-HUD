package com.eymistaken.potionhud;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * One active effect, frozen at the tick it was read.
 *
 * <p>Rendering runs many times per tick and must not re-query the player, so the modules
 * read this instead of {@code MobEffectInstance} directly. {@code peakDuration} is the
 * longest duration {@link EffectTracker} has seen for this effect since it was applied —
 * Minecraft does not expose an effect's original length, so the progress bar needs a
 * remembered maximum to divide by.
 *
 * @param effect       the effect holder, kept so consumers can reach the real effect
 * @param id           registry id, e.g. {@code minecraft:speed}
 * @param displayName  translated effect name, without a level
 * @param amplifier    0 for level I
 * @param duration     ticks remaining
 * @param peakDuration ticks; the divisor for the progress bar, never 0
 * @param ambient      true for beacon/conduit effects
 * @param showIcon     false when the effect asked to stay off the HUD
 * @param visible      false when the effect's particles are hidden
 * @param infinite     true for an infinite-duration effect
 * @param category     beneficial / neutral / harmful
 * @param color        the effect's own particle colour as RGB, no alpha
 */
public record EffectSnapshot(
    Holder<MobEffect> effect,
    Identifier id,
    Component displayName,
    int amplifier,
    int duration,
    int peakDuration,
    boolean ambient,
    boolean showIcon,
    boolean visible,
    boolean infinite,
    MobEffectCategory category,
    int color
) {

    public static EffectSnapshot of(MobEffectInstance inst, int peakDuration) {
        Holder<MobEffect> holder = inst.getEffect();
        MobEffect value = holder.value();
        Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(value);
        return new EffectSnapshot(
            holder,
            id != null ? id : Identifier.fromNamespaceAndPath("minecraft", "unknown"),
            value.getDisplayName(),
            inst.getAmplifier(),
            inst.getDuration(),
            Math.max(1, peakDuration),
            inst.isAmbient(),
            inst.showIcon(),
            inst.isVisible(),
            inst.isInfiniteDuration(),
            value.getCategory(),
            value.getColor() & 0x00FFFFFF
        );
    }

    /** Identity for the tracker's maps: a level change counts as a different effect. */
    public String key() {
        return id + "#" + amplifier;
    }

    public boolean beneficial() {
        return category == MobEffectCategory.BENEFICIAL;
    }

    public boolean harmful() {
        return category == MobEffectCategory.HARMFUL;
    }

    /** Remaining duration as a fraction of the longest it has been, in {@code [0,1]}. */
    public float progress() {
        if (infinite) return 1f;
        float p = duration / (float) peakDuration;
        return p < 0f ? 0f : (p > 1f ? 1f : p);
    }

    public int remainingSeconds() {
        return duration / 20;
    }
}

package com.eymistaken.potionhud.render;

import com.eymistaken.potionhud.EffectSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Effects to show when the real ones cannot be shown.
 *
 * <p>Two places ask for these. The HUD editor draws the vanilla grid itself, and an empty
 * grid is a module with no size — nothing to grab, and no way back to another display mode.
 * The settings screen's preview card has the same problem and a worse version of it: it
 * opens from the main menu too, where there is no player to have effects at all.
 *
 * <p>Live effects win whenever there are any, so both are looking at the real thing while
 * the game is running. The stand-ins only fill the gap, and they are chosen to fill it
 * usefully: one of each category, a level above one, a part-spent duration for the bar to
 * show, and one effect already inside the expiry warning's threshold. Between them every
 * color and timer setting has something to demonstrate itself on.
 */
public final class PreviewEffects {

    /** Built once, on first use, and only ever from the editor or the settings screen. */
    private static List<EffectSnapshot> samples;

    private PreviewEffects() {}

    /**
     * Stand-ins for the vanilla icon grid, which only ever draws effects that asked for an
     * icon — so an effect list made entirely of hidden ones counts as empty here.
     */
    public static List<EffectSnapshot> forGrid(List<EffectSnapshot> live) {
        for (EffectSnapshot snap : live) {
            if (snap.showIcon()) return live;
        }
        return samples();
    }

    /** Stand-ins for the custom list, which draws whatever survives its own filters. */
    public static List<EffectSnapshot> forList(List<EffectSnapshot> live) {
        return live.isEmpty() ? samples() : live;
    }

    private static List<EffectSnapshot> samples() {
        if (samples == null) {
            List<EffectSnapshot> built = new ArrayList<>(5);
            // Held part-spent on purpose, so the duration bars have something to show.
            sample(built, MobEffects.SPEED, 24 * 20, 0, 40 * 20);
            sample(built, MobEffects.STRENGTH, 214 * 20, 1, 300 * 20);
            sample(built, MobEffects.GLOWING, 45 * 20, 0, 60 * 20);
            sample(built, MobEffects.POISON, 21 * 20, 0, 30 * 20);
            // Inside the default eight-second warning threshold, so the warning color and
            // its blink are on show without anyone having to wait for a potion to run out.
            sample(built, MobEffects.WITHER, 5 * 20, 0, 30 * 20);
            samples = List.copyOf(built);
        }
        return samples;
    }

    private static void sample(List<EffectSnapshot> out, Holder<MobEffect> effect,
                               int duration, int amplifier, int peak) {
        try {
            out.add(EffectSnapshot.of(new MobEffectInstance(effect, duration, amplifier), peak));
        } catch (Exception ignored) {
            // A stand-in is a convenience; failing to build one must not take the editor or
            // the settings screen down with it. The caller falls back to its own placeholder.
        }
    }
}

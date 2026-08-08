package com.eymistaken.potionhud;

import com.eymistaken.potionhud.event.PotionEffectExpiringEvent;
import com.eymistaken.potionhud.event.PotionEffectGainedEvent;
import com.eymistaken.potionhud.event.PotionEffectLostEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The single place the player's active effects are read.
 *
 * <p>Driven once per client tick from {@link PotionHudClient}, never from a module's
 * {@code tick} — three modules ticking would otherwise read the player three times and
 * fire every event three times over.
 *
 * <p>It also remembers two things Minecraft does not tell you on its own:
 * <ul>
 *   <li>the longest duration each effect has had, so a progress bar has a denominator;</li>
 *   <li>which effects have already had their "about to run out" warning, so the warning
 *       fires once per potion rather than once per tick.</li>
 * </ul>
 */
public final class EffectTracker {

    /** What the modules render. Replaced wholesale each tick, never mutated in place. */
    private static List<EffectSnapshot> snapshots = List.of();

    private static final Map<String, Integer> peaks = new HashMap<>();
    private static final Map<String, EffectSnapshot> lastByKey = new HashMap<>();
    private static final Set<String> warned = new HashSet<>();

    private EffectTracker() {}

    /** Active effects as of the last tick, already snapshotted. Never null. */
    public static List<EffectSnapshot> snapshots() {
        return snapshots;
    }

    public static void reset() {
        snapshots = List.of();
        peaks.clear();
        lastByKey.clear();
        warned.clear();
    }

    public static void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            // Leaving a world should not fire a "lost" storm on the way out.
            if (!lastByKey.isEmpty() || !snapshots.isEmpty()) reset();
            return;
        }

        PotionHudConfig config = PotionHudConfig.instance;
        int warnTicks = Math.max(1, config.display.warnThresholdSeconds) * 20;

        Collection<MobEffectInstance> active = client.player.getActiveEffects();

        List<EffectSnapshot> fresh = new ArrayList<>(active.size());
        Map<String, EffectSnapshot> byKey = new HashMap<>(active.size() * 2);

        for (MobEffectInstance inst : active) {
            EffectSnapshot probe = EffectSnapshot.of(inst, 1);
            String key = probe.key();

            // Peak duration: a re-applied potion pushes the duration back up, which is
            // the only signal we get that the effect was refreshed.
            int peak = peaks.getOrDefault(key, 0);
            if (inst.getDuration() > peak) {
                peak = inst.getDuration();
                peaks.put(key, peak);
                warned.remove(key); // topped up, so it is allowed to warn again later
            }

            EffectSnapshot snap = EffectSnapshot.of(inst, peak);
            fresh.add(snap);
            byKey.put(key, snap);

            if (!lastByKey.containsKey(key)) {
                emit(new PotionEffectGainedEvent(
                    snap.effect(), snap.id(), snap.amplifier(), snap.duration(), snap.ambient()));
            }

            if (!snap.infinite() && snap.duration() <= warnTicks && warned.add(key)) {
                emit(new PotionEffectExpiringEvent(
                    snap.effect(), snap.id(), snap.amplifier(), snap.duration(), warnTicks));
            }
        }

        for (Map.Entry<String, EffectSnapshot> gone : lastByKey.entrySet()) {
            if (byKey.containsKey(gone.getKey())) continue;
            EffectSnapshot last = gone.getValue();
            // A couple of ticks of slack: the effect is removed on the tick it hits 0,
            // so "ran out" and "was cured" are told apart by what was left last frame.
            boolean expired = last.duration() <= 2;
            emit(new PotionEffectLostEvent(last.effect(), last.id(), last.amplifier(), expired));
            peaks.remove(gone.getKey());
            warned.remove(gone.getKey());
        }

        lastByKey.clear();
        lastByKey.putAll(byKey);
        snapshots = List.copyOf(fresh);
    }

    /**
     * Publish on our own dispatch, which forwards to Eymistaken's HUD's bus when that mod
     * is installed. Going through {@link PotionEvents} rather than straight to the HUD is
     * what lets the tracker run at all on an install without it.
     */
    private static void emit(Object event) {
        PotionEvents.emit(event);
    }
}

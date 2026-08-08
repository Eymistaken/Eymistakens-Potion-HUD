package com.eymistaken.potionhud;

import com.eymistaken.potionhud.modules.PotionHudModule;
import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.api.EymistakenHudEventBus;
import com.eymistaken.simplecps.api.EymistakenHudPlugin;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.event.ComboStreakEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code eymistaken_hud} entrypoint: hands the HUD a single module.
 *
 * <p>Only ever loaded when Eymistaken's HUD is installed — Fabric queries an entrypoint
 * key only on behalf of the mod that owns it, so on an install without the HUD nothing
 * here is touched and its classes are never resolved. That is the whole reason every
 * HUD-facing class lives behind this entrypoint or in the {@code modules} package.
 *
 * <p>This mod's own potion events are published from {@link PotionEvents}, which forwards
 * them to the HUD's bus for any other plugin that wants to react to them.
 */
public class PotionHudPlugin implements EymistakenHudPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("Eymistaken's Potion HUD");

    private PotionHudModule potionHud;

    @Override
    public void registerHudModules(HudModuleManager manager) {
        // Fabric does not order client entrypoints, so the HUD's plugin loop can run
        // before our own initializer. Load here too rather than trusting the order.
        PotionHudConfig.ensureLoaded();

        // One module, deliberately. The plugins list is a list of mods; the effect list
        // and the enhanced vanilla icons are two things this one mod does, and they belong
        // behind its one entry rather than beside it.
        potionHud = register(manager, new PotionHudModule());
    }

    /**
     * Register one module, refusing rather than renaming on a name clash.
     *
     * <p>The name is the key both the layout and the saved settings live under, so quietly
     * picking a different one would orphan everything the user has already configured.
     * Better to say so and leave the other mod's module alone.
     */
    private <T extends HudModule> T register(HudModuleManager manager, T module) {
        String name = module.getName();
        if (manager.isNameTaken(name)) {
            LOGGER.error(
                "Not registering \"{}\": another mod already uses that module name. "
                + "Its settings and placement would collide with ours.", name);
            return null;
        }
        manager.registerModule(module);
        return module;
    }

    @Override
    public void registerEvents(EymistakenHudEventBus bus) {
        // One of the host's own events: land a combo tier and your buffs flash with it.
        // Off by default — it is a flourish, not a feature anyone asked for.
        bus.subscribe(ComboStreakEvent.class, event -> {
            if (potionHud != null && PotionHudConfig.instance.list.pulseOnCombo) {
                potionHud.pulse();
            }
        });
    }
}

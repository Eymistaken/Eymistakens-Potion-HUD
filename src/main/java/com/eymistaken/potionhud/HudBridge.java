package com.eymistaken.potionhud;

import com.eymistaken.potionhud.modules.PotionHudModule;
import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.EymistakenHudEventBus;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.gui.settings.HudSettingsScreen;
import com.eymistaken.simplecps.gui.settings.SettingsSchema;
import com.eymistaken.simplecps.util.EymHudFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Every call into Eymistaken's HUD, in one class.
 *
 * <p>Loaded only from {@link HudCompat}, and only once it has confirmed the HUD is
 * installed. Nothing else may reference this class directly — the moment something does
 * so unguarded, the mod stops working without the HUD.
 */
final class HudBridge {

    private HudBridge() {}

    static void emit(Object event) {
        try {
            EymistakenHudEventBus.getInstance().emit(event);
        } catch (Throwable ignored) {
            // The bus already logs and skips a handler that throws. Anything reaching
            // here is the HUD itself misbehaving, and killing the tick loop over it
            // would be worse than losing one event.
        }
    }

    static void saveConfig() {
        SimpleCPSConfig.save();
    }

    static boolean editorOpen() {
        Minecraft client = Minecraft.getInstance();
        return client.gui != null && client.gui.screen() instanceof HudEditorScreen;
    }

    /**
     * The HUD's settings screen, opened on our module's page. Null when the module never
     * registered (a name clash), since the screen would then have no page of ours to show.
     */
    static Screen settingsScreen(Screen parent) {
        for (HudModule module : HudModuleManager.getInstance().getModules()) {
            if (module instanceof PotionHudModule) {
                return new HudSettingsScreen(parent, SettingsSchema.pageIdFor(module));
            }
        }
        return null;
    }

    static Component styled(String text) {
        return EymHudFonts.text(text);
    }

    static boolean textOutline() {
        return SimpleCPSConfig.instance != null && SimpleCPSConfig.instance.textOutline;
    }
}

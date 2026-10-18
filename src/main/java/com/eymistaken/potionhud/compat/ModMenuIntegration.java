package com.eymistaken.potionhud.compat;

import com.eymistaken.potionhud.HudCompat;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Adds the settings button to this mod's Mod Menu entry.
 *
 * <p>Loaded only when Mod Menu is installed, because Fabric queries an entrypoint key on
 * behalf of the mod that owns it. The button opens the first settings screen that can
 * actually be built:
 * <ol>
 *   <li>the Cloth Config screen, when Cloth is installed;</li>
 *   <li>otherwise Eymistaken's HUD's own settings screen, open on this mod's page. The HUD
 *       stopped depending on Cloth when its settings menu was redrawn, so an install with
 *       the HUD no longer brings Cloth along — and without this step the button simply
 *       vanished there, even though the HUD has a full page of these settings;</li>
 *   <li>otherwise nothing, and the button does not appear.</li>
 * </ol>
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ClothConfigScreen::create;
        }
        // Same as the interface default when the HUD is absent: no screen, no button.
        return HudCompat::settingsScreen;
    }
}

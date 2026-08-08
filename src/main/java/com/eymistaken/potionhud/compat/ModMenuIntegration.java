package com.eymistaken.potionhud.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Adds the settings button to this mod's Mod Menu entry.
 *
 * <p>Loaded only when Mod Menu is installed, because Fabric queries an entrypoint key on
 * behalf of the mod that owns it. Cloth Config is checked separately: the screen itself is
 * built out of Cloth widgets, so without it there is nothing to open and the default
 * "no screen" factory is returned instead.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            // Same as the interface default: the button simply does not appear.
            return screen -> null;
        }
        return ClothConfigScreen::create;
    }
}

package com.eymistaken.potionhud;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * The boundary between this mod and Eymistaken's HUD.
 *
 * <p>Eymistaken's HUD is an optional dependency: with it installed the potion displays
 * become real HUD modules, draggable in its editor and carried by its presets and share
 * codes; without it they render themselves and are configured from a settings screen of
 * their own.
 *
 * <p>Making that work is a classloading problem more than a logic one. A class that
 * mentions a missing type blows up the moment it is loaded, so <em>nothing</em> here
 * names a HUD class. Every call that needs one goes through {@link HudBridge}, which this
 * class only ever touches after {@link #isPresent()} has come back true — and a class is
 * not loaded until it is first used, so on an install without the HUD it never loads at
 * all.
 */
public final class HudCompat {

    private static final String HUD_MOD_ID = "simplecps";

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded(HUD_MOD_ID);

    private HudCompat() {}

    /** True when Eymistaken's HUD is installed and this mod should act as its addon. */
    public static boolean isPresent() {
        return PRESENT;
    }

    /** Republish one of our events on the HUD's bus, so its other addons can hear it. */
    static void forwardEvent(Object event) {
        if (!PRESENT) return;
        HudBridge.emit(event);
    }

    /** Ask the HUD to write its config, which also collects our modules' saved state. */
    public static void saveHostConfig() {
        if (!PRESENT) return;
        HudBridge.saveConfig();
    }

    /** True while the HUD's drag-and-drop editor is open. Always false without the HUD. */
    public static boolean editorOpen() {
        return PRESENT && HudBridge.editorOpen();
    }

    /**
     * Wrap text so it renders in whatever font should be in charge.
     *
     * <p>With the HUD installed that is the font the user picked for their whole HUD —
     * following the host rather than competing with it is the entire point of being an
     * addon. Without it, plain vanilla text.
     */
    public static Component styled(String text) {
        return PRESENT ? HudBridge.styled(text) : Component.literal(text);
    }

    /** Whether to outline text instead of shadowing it, from the HUD when it is present. */
    public static boolean textOutline() {
        return PRESENT ? HudBridge.textOutline() : PotionHudConfig.instance.general.textOutline;
    }
}

package com.eymistaken.potionhud;

import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.render.VanillaEffectOverlay;
import com.eymistaken.potionhud.standalone.StandaloneHud;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Everything that happens once, at client start, whether or not Eymistaken's HUD is
 * installed.
 *
 * <p>With the HUD present it registers one module from {@link PotionHudPlugin} and this
 * class only does the plumbing. Without it, the same display is drawn by
 * {@link StandaloneHud} instead — which is registered here, and only here, so the two
 * paths can never both be live.
 */
public class PotionHudClient implements ClientModInitializer {

    private static final Identifier STANDALONE_LAYER =
        Identifier.fromNamespaceAndPath("eympotionhud", "standalone");

    /** Parallel to {@link DisplayMode}'s constants; what the action bar says on a cycle. */
    private static final String[] MODE_LABELS = { "Vanilla icons", "Enhanced icons", "Custom display", "Hidden" };

    public static KeyMapping cycleKey;

    @Override
    public void onInitializeClient() {
        PotionHudConfig.ensureLoaded();

        // Wrapped rather than removed, so all three vanilla-icon modes are one
        // registration and switching between them needs no restart.
        HudElementRegistry.replaceElement(VanillaHudElements.MOB_EFFECTS, VanillaEffectOverlay::wrap);

        if (!HudCompat.isPresent()) {
            // No host to hand modules to, so draw them ourselves. Before the chat layer,
            // matching where the HUD puts its own.
            HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT, STANDALONE_LAYER, new StandaloneHud());
        }

        cycleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.eympotionhud.cycle",
            InputConstants.UNKNOWN.getValue(),
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("eympotionhud", "categories.eympotionhud"))
        ));

        // One tick handler for the whole mod, so the player is read once per tick however
        // many things end up drawing from it.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            EffectTracker.onTick(client);
            while (cycleKey.consumeClick()) cycleDisplayMode();
        });

        // Settings are batched in memory while the user drags sliders around; this is the
        // last chance to get them onto disk.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PotionHudConfig.flush());
    }

    /**
     * Step to the next display mode and say so in the action bar.
     *
     * <p>A cycle rather than an on/off toggle because the modes are the mod: with four of
     * them and no hidden "previous mode" to remember, one key gets you to any of them and
     * back. The message is the only feedback there is — three of the four look like
     * something changed in the corner of the screen, and the fourth looks like nothing.
     */
    private static void cycleDisplayMode() {
        DisplayMode[] modes = DisplayMode.values();
        DisplayMode current = PotionHudConfig.instance.display.mode;
        DisplayMode next = modes[(current == null ? 0 : current.ordinal() + 1) % modes.length];

        PotionHudConfig.instance.display.mode = next;
        PotionHudConfig.markDirty();
        PotionHudConfig.flush();
        HudCompat.saveHostConfig();

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(
                Component.literal("Potion HUD: " + MODE_LABELS[next.ordinal()]));
        }
    }
}

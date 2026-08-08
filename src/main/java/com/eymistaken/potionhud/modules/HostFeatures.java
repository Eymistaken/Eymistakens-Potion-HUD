package com.eymistaken.potionhud.modules;

import com.eymistaken.simplecps.api.HudModule;

/**
 * What the installed copy of Eymistaken's HUD can do.
 *
 * <p>The mod is compiled against the newest HUD but only <em>suggests</em> it, so it also
 * has to behave on the version someone already has. Asking the host class directly beats
 * parsing its version string: a capability either is there or it is not, and nothing has
 * to be updated here when the HUD's numbering moves on.
 *
 * <p>Lives in {@code modules} on purpose. Every class in this package already touches HUD
 * types, so it is only ever loaded when the HUD is present — see
 * {@link com.eymistaken.potionhud.HudCompat}, which is the gate that keeps it that way.
 */
final class HostFeatures {

    /**
     * Whether the host builds plugin settings pages out of declared tabs.
     *
     * <p>Added in HUD 1.1.1-8. Looked up on {@link HudModule} rather than on {@code this}:
     * our own modules override the method whatever the host supports, so only the
     * superclass answers the question.
     */
    static final boolean SETTINGS_TABS = hasMethod("getSettingsTabs");

    private HostFeatures() {
    }

    private static boolean hasMethod(String name) {
        try {
            HudModule.class.getMethod(name);
            return true;
        } catch (NoSuchMethodException | RuntimeException e) {
            return false;
        }
    }
}

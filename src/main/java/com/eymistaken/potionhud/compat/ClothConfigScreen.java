package com.eymistaken.potionhud.compat;

import com.eymistaken.potionhud.HudCompat;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.Anchor;
import com.eymistaken.potionhud.PotionHudConfig.BarStyle;
import com.eymistaken.potionhud.PotionHudConfig.ColorMode;
import com.eymistaken.potionhud.PotionHudConfig.Direction;
import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
import com.eymistaken.potionhud.PotionHudConfig.GeneralSettings;
import com.eymistaken.potionhud.PotionHudConfig.IconStyle;
import com.eymistaken.potionhud.PotionHudConfig.LevelStyle;
import com.eymistaken.potionhud.PotionHudConfig.ListSettings;
import com.eymistaken.potionhud.PotionHudConfig.SortOrder;
import com.eymistaken.potionhud.PotionHudConfig.Style;
import com.eymistaken.potionhud.PotionHudConfig.TimeFormat;
import com.eymistaken.potionhud.PotionHudConfig.VanillaBar;
import com.eymistaken.potionhud.view.PotionViews;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen for installs without Eymistaken's HUD.
 *
 * <p>With the HUD there, its own settings screen and drag-and-drop editor are the better
 * place to be and this is redundant — but harmless, because both edit the same config
 * object. Without it, this is the only way to reach any of these settings short of hand
 * editing {@code config/eympotionhud.json}.
 *
 * <p>Only loaded once {@link ModMenuIntegration} has confirmed Cloth Config is installed.
 */
public final class ClothConfigScreen {

    private ClothConfigScreen() {}

    public static Screen create(Screen parent) {
        PotionHudConfig.ensureLoaded();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Eymistaken's Potion HUD"))
            .setSavingRunnable(() -> {
                PotionHudConfig.save();
                // Keeps the host's copy of our state in step when both are installed.
                HudCompat.saveHostConfig();
                PotionViews.LIST.reset();
            });

        ConfigEntryBuilder entries = builder.entryBuilder();

        display(builder, entries);
        effectList(builder, entries);
        general(builder, entries);

        return builder.build();
    }

    // --- Categories ------------------------------------------------------------

    /**
     * The mode, and everything that follows from it. First category on purpose: it is the
     * only setting most people will ever need to touch.
     */
    private static void display(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Display"));
        DisplaySettings defaults = new DisplaySettings();

        bool(category, entries, "Enabled", displayCfg().enabled, defaults.enabled, v -> displayCfg().enabled = v);
        select(category, entries, "Display Mode", DisplayMode.class, displayCfg().mode, defaults.mode, v -> displayCfg().mode = v);
        category.addEntry(entries.startTextDescription(Component.literal(
            "VANILLA leaves Minecraft's icons alone. ENHANCED draws the remaining duration into them. "
            + "CUSTOM hides them and puts the Effect List on screen instead. HIDDEN shows nothing."
        )).build());

        bool(category, entries, "Low Time Warning", displayCfg().warnLowTime, defaults.warnLowTime, v -> displayCfg().warnLowTime = v);
        slider(category, entries, "Warning Threshold (s)", displayCfg().warnThresholdSeconds, defaults.warnThresholdSeconds, 1, 60, v -> displayCfg().warnThresholdSeconds = v);
        colour(category, entries, "Warning Colour", displayCfg().warnColor, defaults.warnColor, v -> displayCfg().warnColor = v);
        bool(category, entries, "Warning Blink", displayCfg().warnBlink, defaults.warnBlink, v -> displayCfg().warnBlink = v);
        bool(category, entries, "Warning Shake (custom only)", displayCfg().warnShake, defaults.warnShake, v -> displayCfg().warnShake = v);

        select(category, entries, "Enhanced: Duration Style", VanillaBar.class, displayCfg().vanillaBar, defaults.vanillaBar, v -> displayCfg().vanillaBar = v);
        slider(category, entries, "Enhanced: Underline Thickness", displayCfg().vanillaBarThickness, defaults.vanillaBarThickness, 1, 3, v -> displayCfg().vanillaBarThickness = v);
        bool(category, entries, "Enhanced: Use Effect Colour", displayCfg().vanillaBarEffectColor, defaults.vanillaBarEffectColor, v -> displayCfg().vanillaBarEffectColor = v);
        colour(category, entries, "Enhanced: Fixed Colour", displayCfg().vanillaBarColor, defaults.vanillaBarColor, v -> displayCfg().vanillaBarColor = v);
        bool(category, entries, "Enhanced: Show Timer", displayCfg().vanillaTimer, defaults.vanillaTimer, v -> displayCfg().vanillaTimer = v);
        colour(category, entries, "Enhanced: Timer Colour", displayCfg().vanillaTimerColor, defaults.vanillaTimerColor, v -> displayCfg().vanillaTimerColor = v);
        bool(category, entries, "Enhanced: Show Level", displayCfg().vanillaLevel, defaults.vanillaLevel, v -> displayCfg().vanillaLevel = v);

        slider(category, entries, "Enhanced: Icon Spacing", displayCfg().gridPitch, defaults.gridPitch, 16, 40, v -> displayCfg().gridPitch = v);
        slider(category, entries, "Enhanced: Row Gap", displayCfg().gridRowGap, defaults.gridRowGap, 16, 48, v -> displayCfg().gridRowGap = v);
        slider(category, entries, "Enhanced: Nudge X", displayCfg().gridOriginX, defaults.gridOriginX, -30, 30, v -> displayCfg().gridOriginX = v);
        slider(category, entries, "Enhanced: Nudge Y", displayCfg().gridOriginY, defaults.gridOriginY, -30, 30, v -> displayCfg().gridOriginY = v);
    }

    /** The custom display. Only ever on screen in {@link DisplayMode#CUSTOM}. */
    private static void effectList(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Effect List"));
        ListSettings defaults = new ListSettings();

        bool(category, entries, "Always Show", cfg().alwaysShow, defaults.alwaysShow, v -> cfg().alwaysShow = v);
        select(category, entries, "Anchor", Anchor.class, cfg().anchor, defaults.anchor, v -> cfg().anchor = v);
        slider(category, entries, "X Offset", cfg().xOffset, defaults.xOffset, -400, 400, v -> cfg().xOffset = v);
        slider(category, entries, "Y Offset", cfg().yOffset, defaults.yOffset, -400, 400, v -> cfg().yOffset = v);
        slider(category, entries, "Scale %", cfg().scale, defaults.scale, 50, 300, v -> cfg().scale = v);

        select(category, entries, "Style", Style.class, cfg().style, defaults.style, v -> cfg().style = v);
        select(category, entries, "Grow Direction", Direction.class, cfg().direction, defaults.direction, v -> cfg().direction = v);
        slider(category, entries, "Items Per Line", cfg().columns, defaults.columns, 1, 8, v -> cfg().columns = v);
        slider(category, entries, "Spacing", cfg().spacing, defaults.spacing, 0, 12, v -> cfg().spacing = v);
        bool(category, entries, "Split Positive / Negative", cfg().splitGroups, defaults.splitGroups, v -> cfg().splitGroups = v);

        select(category, entries, "Icon Style", IconStyle.class, cfg().iconStyle, defaults.iconStyle, v -> cfg().iconStyle = v);
        slider(category, entries, "Icon Size", cfg().iconSize, defaults.iconSize, 8, 24, v -> cfg().iconSize = v);

        bool(category, entries, "Show Effect Name", cfg().showName, defaults.showName, v -> cfg().showName = v);
        select(category, entries, "Level Style", LevelStyle.class, cfg().levelStyle, defaults.levelStyle, v -> cfg().levelStyle = v);
        bool(category, entries, "Show Level I", cfg().showLevelOne, defaults.showLevelOne, v -> cfg().showLevelOne = v);
        select(category, entries, "Duration Format", TimeFormat.class, cfg().timeFormat, defaults.timeFormat, v -> cfg().timeFormat = v);
        text(category, entries, "Infinite Label", cfg().infiniteLabel, defaults.infiniteLabel, v -> cfg().infiniteLabel = v);

        select(category, entries, "Colour Mode", ColorMode.class, cfg().colorMode, defaults.colorMode, v -> cfg().colorMode = v);
        colour(category, entries, "Text Colour", cfg().textColor, defaults.textColor, v -> cfg().textColor = v);
        colour(category, entries, "Positive Colour", cfg().positiveColor, defaults.positiveColor, v -> cfg().positiveColor = v);
        colour(category, entries, "Negative Colour", cfg().negativeColor, defaults.negativeColor, v -> cfg().negativeColor = v);
        colour(category, entries, "Neutral Colour", cfg().neutralColor, defaults.neutralColor, v -> cfg().neutralColor = v);
        colour(category, entries, "Timer Colour", cfg().timeColor, defaults.timeColor, v -> cfg().timeColor = v);

        bool(category, entries, "Show Background", cfg().showBackground, defaults.showBackground, v -> cfg().showBackground = v);
        colour(category, entries, "Background Colour", cfg().backgroundColor, defaults.backgroundColor, v -> cfg().backgroundColor = v);
        slider(category, entries, "Background Opacity", cfg().backgroundOpacity, defaults.backgroundOpacity, 0, 255, v -> cfg().backgroundOpacity = v);
        bool(category, entries, "Rounded Corners", cfg().roundedBackground, defaults.roundedBackground, v -> cfg().roundedBackground = v);

        bool(category, entries, "Show Duration Bar", cfg().showBar, defaults.showBar, v -> cfg().showBar = v);
        select(category, entries, "Bar Placement", BarStyle.class, cfg().barStyle, defaults.barStyle, v -> cfg().barStyle = v);
        slider(category, entries, "Bar Height", cfg().barHeight, defaults.barHeight, 1, 6, v -> cfg().barHeight = v);
        colour(category, entries, "Bar Colour", cfg().barColor, defaults.barColor, v -> cfg().barColor = v);
        slider(category, entries, "Bar Track Opacity", cfg().barTrackOpacity, defaults.barTrackOpacity, 0, 255, v -> cfg().barTrackOpacity = v);

        bool(category, entries, "Hide Ambient (Beacon)", cfg().hideAmbient, defaults.hideAmbient, v -> cfg().hideAmbient = v);
        bool(category, entries, "Hide Particle-less", cfg().hideParticleless, defaults.hideParticleless, v -> cfg().hideParticleless = v);
        bool(category, entries, "Hide Positive", cfg().hidePositive, defaults.hidePositive, v -> cfg().hidePositive = v);
        bool(category, entries, "Hide Negative", cfg().hideNegative, defaults.hideNegative, v -> cfg().hideNegative = v);
        bool(category, entries, "Hide Infinite", cfg().hideInfinite, defaults.hideInfinite, v -> cfg().hideInfinite = v);
        text(category, entries, "Hidden Effects", cfg().hiddenEffects, defaults.hiddenEffects, v -> cfg().hiddenEffects = v);

        select(category, entries, "Sort By", SortOrder.class, cfg().sortOrder, defaults.sortOrder, v -> cfg().sortOrder = v);
        bool(category, entries, "Harmful Last", cfg().harmfulLast, defaults.harmfulLast, v -> cfg().harmfulLast = v);

        bool(category, entries, "Animate Entries", cfg().animate, defaults.animate, v -> cfg().animate = v);
        slider(category, entries, "Animation Length (cs)", cfg().animationSpeed, defaults.animationSpeed, 4, 60, v -> cfg().animationSpeed = v);
        bool(category, entries, "Pulse On Combo Streak", cfg().pulseOnCombo, defaults.pulseOnCombo, v -> cfg().pulseOnCombo = v);
    }

    private static void general(ConfigBuilder builder, ConfigEntryBuilder entries) {
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("General"));
        GeneralSettings defaults = new GeneralSettings();

        bool(category, entries, "Text Outline", generalCfg().textOutline, defaults.textOutline, v -> generalCfg().textOutline = v);
        bool(category, entries, "Standalone Rendering", generalCfg().standaloneRendering, defaults.standaloneRendering, v -> generalCfg().standaloneRendering = v);

        category.addEntry(entries.startTextDescription(Component.literal(
            HudCompat.isPresent()
                ? "Eymistaken's HUD is installed, so these displays are its modules: drag them in its editor (Right Shift) and they travel with its presets and share codes. Text outline and standalone rendering are ignored while it is there."
                : "Eymistaken's HUD is not installed. The displays are drawn from their anchors and offsets above. Install it to drag them around instead."
        )).build());
    }

    // --- Entry helpers -----------------------------------------------------------

    private static ListSettings cfg() {
        return PotionHudConfig.instance.list;
    }

    private static DisplaySettings displayCfg() {
        return PotionHudConfig.instance.display;
    }

    private static GeneralSettings generalCfg() {
        return PotionHudConfig.instance.general;
    }

    private static void bool(ConfigCategory category, ConfigEntryBuilder entries, String label,
                             boolean current, boolean fallback, Consumer<Boolean> setter) {
        category.addEntry(entries.startBooleanToggle(Component.literal(label), current)
            .setDefaultValue(fallback)
            .setSaveConsumer(setter)
            .build());
    }

    private static void slider(ConfigCategory category, ConfigEntryBuilder entries, String label,
                               int current, int fallback, int min, int max, Consumer<Integer> setter) {
        category.addEntry(entries.startIntSlider(Component.literal(label), clamp(current, min, max), min, max)
            .setDefaultValue(fallback)
            .setSaveConsumer(setter)
            .build());
    }

    private static void colour(ConfigCategory category, ConfigEntryBuilder entries, String label,
                               int current, int fallback, Consumer<Integer> setter) {
        category.addEntry(entries.startColorField(Component.literal(label), current & 0x00FFFFFF)
            .setDefaultValue(fallback & 0x00FFFFFF)
            .setSaveConsumer(value -> setter.accept(value & 0x00FFFFFF))
            .build());
    }

    private static void text(ConfigCategory category, ConfigEntryBuilder entries, String label,
                             String current, String fallback, Consumer<String> setter) {
        category.addEntry(entries.startStrField(Component.literal(label), current == null ? "" : current)
            .setDefaultValue(fallback == null ? "" : fallback)
            .setSaveConsumer(setter)
            .build());
    }

    private static <T extends Enum<T>> void select(ConfigCategory category, ConfigEntryBuilder entries,
                                                   String label, Class<T> type, T current, T fallback,
                                                   Consumer<T> setter) {
        // A config naming a constant this build does not have parses to null; fall back
        // rather than handing Cloth a null selection.
        category.addEntry(entries.startEnumSelector(Component.literal(label), type, current == null ? fallback : current)
            .setDefaultValue(fallback)
            .setSaveConsumer(setter)
            .build());
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}

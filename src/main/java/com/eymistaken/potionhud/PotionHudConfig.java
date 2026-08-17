package com.eymistaken.potionhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every setting this mod owns, in one Gson-serializable tree.
 *
 * <p>Two things persist it, on purpose:
 *
 * <ol>
 *   <li>{@code config/eympotionhud.json} — our own file, written by {@link #save()}.
 *       Nothing is ever written into {@code SimpleCPSConfig}, per the HUD's API guide.</li>
 *   <li>The HUD's own state channel — each module's {@code saveState()} returns its
 *       section of this tree, so the settings ride along inside {@code simplecps.json},
 *       named presets, {@code EYMHUD1-} share codes and per-server configs for free.</li>
 * </ol>
 *
 * <p>The HUD's copy wins on load: it replays stored state into every module right after
 * registration, which is exactly what you want — applying a preset or a friend's share
 * code should move the potion HUD too.
 *
 * <p>Nothing in this class refers to Eymistaken's HUD. It has to stay that way: the HUD
 * is an optional dependency, and a single import of one of its classes here would make
 * the whole mod fail to load without it.
 */
public class PotionHudConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Eymistaken's Potion HUD");

    /** No pretty printing inside share codes is fine; this file is meant to be hand-editable. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_FILE =
        FabricLoader.getInstance().getConfigDir().resolve("eympotionhud.json").toFile();

    public static PotionHudConfig instance = new PotionHudConfig();

    private static boolean loaded = false;
    private static boolean dirty = false;

    // --- Sections -----------------------------------------------------------

    public DisplaySettings display = new DisplaySettings();
    public ListSettings list = new ListSettings();
    public GeneralSettings general = new GeneralSettings();

    // --- Enums --------------------------------------------------------------
    // Stored by name, so reordering them later cannot silently repoint a setting.

    /**
     * Screen corner a module is measured from.
     *
     * <p>Deliberately our own enum rather than the HUD's {@code Position}: the constants
     * match one-for-one and are mapped across in the module wrappers, which keeps this
     * config loadable when the HUD is not installed.
     */
    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }

    /** How one effect entry is laid out. */
    public enum Style { FULL, COMPACT, ICONS, TEXT, GRID }

    /** Which way the list grows from its anchor. */
    public enum Direction { DOWN, UP, RIGHT, LEFT }

    public enum IconStyle { SPRITE, DOT, NONE }

    public enum TimeFormat { CLOCK, SECONDS, TENTHS, HIDDEN }

    public enum LevelStyle { ROMAN, NUMBER, NONE }

    public enum SortOrder { TIME_LEFT, TIME_LEFT_DESC, NAME, CATEGORY, VANILLA }

    /** Where the duration bar is drawn relative to the entry. */
    public enum BarStyle { UNDER_TEXT, BEHIND_ROW, UNDER_ICON }

    /**
     * How effects are shown on screen. One setting, four answers — the whole mod hangs
     * off it.
     *
     * <p>Deliberately a single choice rather than a switch per display. Two independent
     * toggles let you turn both on and end up reading the same effect twice in two
     * different corners, which is not a configuration anybody wants.
     */
    public enum DisplayMode {
        /** Minecraft's own icons, untouched. The mod stays out of the way. */
        VANILLA,
        /** Minecraft's own icons, with a duration bar and an expiry warning woven in. */
        ENHANCED,
        /** Vanilla icons hidden; our own list draws instead, wherever it has been put. */
        CUSTOM,
        /** Nothing at all. */
        HIDDEN
    }

    /** How the remaining duration is shown on a vanilla icon in {@link DisplayMode#ENHANCED}. */
    public enum VanillaBar {
        NONE,
        /** A hairline along the icon's bottom edge. */
        UNDERLINE,
        /** The icon itself dims from the top down as the effect runs out. */
        DRAIN
    }

    public enum ColorMode { SINGLE, CATEGORY, RAINBOW }

    // --- What is shown, and the warning that applies to all of it ------------

    /**
     * The mod's one structural decision, plus everything that is true of an effect
     * display regardless of which one is on screen.
     *
     * <p>The expiry warning lives here rather than beside the custom list because it is
     * not a property of that list: an effect running out has to read the same whether you
     * are watching a vanilla icon or our own row.
     */
    public static class DisplaySettings {
        /**
         * Master switch for everything this mod draws. Off means Minecraft's icons behave
         * exactly as they would without the mod installed — which is a different answer
         * from {@link DisplayMode#HIDDEN}, where hiding them is the point.
         */
        public boolean enabled = true;

        public DisplayMode mode = DisplayMode.ENHANCED;

        // Low-time warning, shared by ENHANCED and CUSTOM.
        public boolean warnLowTime = true;
        public int warnThresholdSeconds = 8;
        public int warnColor = 0xFF5555;
        public boolean warnBlink = true;
        /** Custom display only; there is no room to shake a vanilla icon without hitting its neighbor. */
        public boolean warnShake = false;

        // ENHANCED: what gets drawn onto Minecraft's own icons.
        public VanillaBar vanillaBar = VanillaBar.UNDERLINE;
        public int vanillaBarThickness = 1;
        /** Tint the bar with the effect's own color, so it reads as part of the icon. */
        public boolean vanillaBarEffectColor = true;
        public int vanillaBarColor = 0x55FF55;
        public boolean vanillaTimer = false;
        public boolean vanillaLevel = false;
        public int vanillaTimerColor = 0xFFFFFF;

        // Vanilla's own grid, re-derived. Exposed so a future layout change in
        // Minecraft can be corrected from the settings screen instead of a patch.
        public int gridPitch = 25;
        public int gridRowGap = 26;
        public int gridOriginX = 0;
        public int gridOriginY = 1;
        public int gridIconSize = 24;
    }

    // --- Potion HUD module --------------------------------------------------

    /**
     * The custom effect list — everything about how it looks and where it sits.
     *
     * <p>There is no {@code enabled} here on purpose. Whether this list is on screen is
     * {@link DisplaySettings#mode}'s answer alone, so there is nothing to get out of sync.
     */
    public static class ListSettings {
        // Placement — owned by us, driven by the HUD editor.
        public Anchor anchor = Anchor.TOP_RIGHT;
        public int xOffset = 0;
        public int yOffset = 0;
        public int scale = 100;

        // Presentation
        public Style style = Style.FULL;
        public Direction direction = Direction.DOWN;
        public int columns = 1;
        public int spacing = 2;
        public boolean alwaysShow = false;

        // Icons
        public IconStyle iconStyle = IconStyle.SPRITE;
        public int iconSize = 16;

        // Text
        public boolean showName = true;
        public LevelStyle levelStyle = LevelStyle.ROMAN;
        public boolean showLevelOne = false;
        public TimeFormat timeFormat = TimeFormat.CLOCK;
        public String infiniteLabel = "**:**";

        // Color
        public ColorMode colorMode = ColorMode.SINGLE;
        public int textColor = 0xFFFFFF;
        public int positiveColor = 0x55FF55;
        public int negativeColor = 0xFF5555;
        public int neutralColor = 0x55AAFF;
        public int timeColor = 0xAAAAAA;

        // Background
        public boolean showBackground = false;
        public int backgroundColor = 0x000000;
        public int backgroundOpacity = 128;
        public boolean roundedBackground = true;

        // Duration bar
        public boolean showBar = true;
        public BarStyle barStyle = BarStyle.UNDER_TEXT;
        public int barHeight = 2;
        public int barColor = 0x55FF55;
        public int barTrackColor = 0x000000;
        public int barTrackOpacity = 140;

        // Filters
        public boolean hideAmbient = false;
        public boolean hideParticleless = false;
        public boolean hidePositive = false;
        public boolean hideNegative = false;
        public boolean hideInfinite = false;
        /** Comma-separated effect ids, e.g. {@code minecraft:mining_fatigue, minecraft:nausea}. */
        public String hiddenEffects = "";

        // Ordering
        public SortOrder sortOrder = SortOrder.TIME_LEFT;
        public boolean harmfulLast = false;

        // Split into two independently draggable groups
        public boolean splitGroups = false;
        public int positiveXOffset = 0;
        public int positiveYOffset = 0;
        public int negativeXOffset = 0;
        public int negativeYOffset = 0;

        // Motion
        public boolean animate = true;
        public int animationSpeed = 18; // hundredths of a second for one entry tween

        // Cross-mod
        public boolean pulseOnCombo = false;
    }

    // --- Standalone / global -------------------------------------------------

    /**
     * Settings that only matter when Eymistaken's HUD is not installed.
     *
     * <p>With the HUD present, the font and outline come from its own global settings and
     * these are ignored — the addon deliberately follows the host rather than competing
     * with it.
     */
    public static class GeneralSettings {
        /** Thin black outline instead of the vanilla drop shadow. */
        public boolean textOutline = false;
        /** Master switch for the standalone HUD layer. */
        public boolean standaloneRendering = true;
    }

    // --- IO -----------------------------------------------------------------

    /**
     * Read the config file once. Idempotent, and called from both entrypoints:
     * Fabric does not order {@code client} initializers, so the HUD's plugin loop can
     * run before ours and modules must never read an unloaded config.
     */
    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    private static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            // An empty or truncated file parses to null; keep the defaults rather
            // than handing every module a null section.
            PotionHudConfig parsed = GSON.fromJson(reader, PotionHudConfig.class);
            if (parsed != null) {
                instance = parsed;
                instance.repair();
            }
        } catch (Exception e) {
            LOGGER.error("Could not read {}; starting from defaults.", CONFIG_FILE.getName(), e);
        }
    }

    /**
     * Note a setting change without touching the disk.
     *
     * <p>Settings arrive one at a time from sliders and color pickers, so writing on
     * every change would mean a file write per dragged pixel. {@link #flush()} does the
     * actual write, and it is driven by the HUD's own save cycle.
     */
    public static void markDirty() {
        dirty = true;
    }

    /** Write the config out if anything changed since the last write. */
    public static void flush() {
        if (!dirty) return;
        dirty = false;
        save();
    }

    public static void save() {
        dirty = false;
        File temp = new File(CONFIG_FILE.getParentFile(), CONFIG_FILE.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temp)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            LOGGER.error("Could not write {}.", CONFIG_FILE.getName(), e);
            return;
        }
        // Write-then-rename, so a crash mid-save cannot leave a half-written config.
        if (!temp.renameTo(CONFIG_FILE)) {
            if (CONFIG_FILE.delete() && temp.renameTo(CONFIG_FILE)) return;
            LOGGER.error("Could not replace {}.", CONFIG_FILE.getName());
        }
    }

    /**
     * A hand-edited or older file can be missing whole sections; fill them back in.
     * Also run after {@code loadState}, since a preset written by an older build can
     * carry a section this one has since extended.
     */
    public void repair() {
        if (display == null) display = new DisplaySettings();
        if (list == null) list = new ListSettings();
        if (general == null) general = new GeneralSettings();
        if (list.anchor == null) list.anchor = Anchor.TOP_RIGHT;
        // A file naming a constant this build dropped parses to null, and every read of
        // these two would then have to guard itself.
        if (display.mode == null) display.mode = DisplayMode.ENHANCED;
        if (display.vanillaBar == null) display.vanillaBar = VanillaBar.UNDERLINE;
    }

    // --- Module state bridge -------------------------------------------------

    /**
     * Serialize one section for {@code HudModule.saveState()}.
     *
     * <p>Gson omits nulls, which is what the HUD's serializer requires: it strips JSON
     * nulls at every depth, so a null would not survive the round trip anyway.
     */
    public static JsonElement toTree(Object section) {
        return GSON.toJsonTree(section);
    }

    /**
     * Deserialize a section from {@code HudModule.loadState()}.
     *
     * @return the parsed section, or null when the stored blob is unusable — the caller
     *         then keeps its current settings rather than resetting them
     */
    public static <T> T fromTree(JsonElement state, Class<T> type) {
        if (state == null || !state.isJsonObject()) return null;
        try {
            return GSON.fromJson(state, type);
        } catch (Exception e) {
            LOGGER.error("Ignoring unreadable stored state for {}.", type.getSimpleName(), e);
            return null;
        }
    }
}

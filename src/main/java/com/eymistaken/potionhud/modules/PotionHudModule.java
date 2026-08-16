package com.eymistaken.potionhud.modules;

import com.eymistaken.potionhud.EffectTracker;
import com.eymistaken.potionhud.HudCompat;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.Anchor;
import com.eymistaken.potionhud.PotionHudConfig.BarStyle;
import com.eymistaken.potionhud.PotionHudConfig.ColorMode;
import com.eymistaken.potionhud.PotionHudConfig.Direction;
import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
import com.eymistaken.potionhud.PotionHudConfig.IconStyle;
import com.eymistaken.potionhud.PotionHudConfig.LevelStyle;
import com.eymistaken.potionhud.PotionHudConfig.ListSettings;
import com.eymistaken.potionhud.PotionHudConfig.SortOrder;
import com.eymistaken.potionhud.PotionHudConfig.Style;
import com.eymistaken.potionhud.PotionHudConfig.TimeFormat;
import com.eymistaken.potionhud.PotionHudConfig.VanillaBar;
import com.eymistaken.potionhud.render.TextSink;
import com.eymistaken.potionhud.render.VanillaIconGrid;
import com.eymistaken.potionhud.util.Enums;
import com.eymistaken.potionhud.view.PotionListView;
import com.eymistaken.potionhud.view.PotionViews;
import com.eymistaken.simplecps.HudPlacementResolver;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.SimpleCPSConfig.Position;
import com.eymistaken.simplecps.api.ActionSetting;
import com.eymistaken.simplecps.api.BooleanSetting;
import com.eymistaken.simplecps.api.ColorSetting;
import com.eymistaken.simplecps.api.CycleSetting;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.HudModuleSetting;
import com.eymistaken.simplecps.api.IHudElement;
import com.eymistaken.simplecps.api.SettingsTab;
import com.eymistaken.simplecps.api.SliderSetting;
import com.eymistaken.simplecps.api.TextSetting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The effect list, presented to Eymistaken's HUD as one of its modules.
 *
 * <p>Everything that decides pixels lives in {@link PotionListView}, which knows nothing
 * about the HUD; this class is the adapter that hands the HUD its placement accessors,
 * settings, sub-elements and saved state. That split is what lets the same display run
 * unchanged when the HUD is not installed.
 */
public class PotionHudModule extends HudModule {

    /**
     * Doubles as the settings page title and the editor's hover label, so it is written
     * for people rather than prefixed with a mod id. The plugin checks for a clash before
     * registering rather than renaming itself, because this string is the key both the
     * layout and the saved settings are stored under.
     */
    public static final String NAME = "Eymistaken's Potion HUD";

    private final TextSink sink = new TextSink();

    private final GroupElement positiveGroup = new GroupElement(true);
    private final GroupElement negativeGroup = new GroupElement(false);

    private static ListSettings cfg() {
        return PotionHudConfig.instance.list;
    }

    private static DisplaySettings display() {
        return PotionHudConfig.instance.display;
    }

    /** True when the custom effect list is the chosen display. */
    private static boolean listActive() {
        DisplaySettings display = display();
        return display.enabled && display.mode == DisplayMode.CUSTOM;
    }

    /**
     * Whether the editor needs a stand-in to grab.
     *
     * <p>With every display off there is nothing on screen, so the module would be a
     * 1x1 dot in the editor and the right-click menu that turns something back on would
     * be unreachable. The list's placeholder stands in for it.
     */
    private static boolean editorHandle() {
        return HudCompat.editorOpen() && !listActive();
    }

    /**
     * Whether the module is standing in for Minecraft's own effect icons right now.
     *
     * <p>True only inside the editor, and only in the modes that put icons in the corner:
     * both vanilla-icon modes, and the master switch being off — which also leaves the
     * icons on screen, untouched. The editor is where the mod has to answer "what does
     * this mode look like", and a box drawn in the shape of the custom list was the wrong
     * answer three times out of four.
     *
     * <p>Outside the editor this is false, so every accessor below falls back to exactly
     * what it did before: the list, or nothing.
     */
    private static boolean iconPreview() {
        if (!HudCompat.editorOpen()) return false;
        DisplaySettings display = display();
        if (!display.enabled) return true;
        DisplayMode mode = display.mode == null ? DisplayMode.ENHANCED : display.mode;
        return mode == DisplayMode.VANILLA || mode == DisplayMode.ENHANCED;
    }

    /** The extras are the enhanced mode's alone; the other two previews are plain icons. */
    private static boolean previewExtras() {
        DisplaySettings display = display();
        return display.enabled && display.mode == DisplayMode.ENHANCED;
    }

    private static List<VanillaIconGrid.Cell> previewCells(int screenWidth) {
        DisplaySettings display = display();
        return VanillaIconGrid.layout(display,
            VanillaIconGrid.previewEffects(EffectTracker.snapshots()), screenWidth);
    }

    /**
     * The vanilla grid's rectangle on screen as {@code {x, y, width, height}}, or null
     * when it could not be measured.
     *
     * <p>Measured off the window rather than a render context because the editor asks for
     * the module's box from its mouse handlers too, long after the frame is gone.
     */
    private int[] iconRect() {
        int screenWidth = client.getWindow().getGuiScaledWidth();
        return VanillaIconGrid.bounds(display(), previewCells(screenWidth));
    }

    private static PotionListView view() {
        return PotionViews.LIST;
    }

    // --- Identity and placement -------------------------------------------------

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supportsFade() {
        return true;
    }

    @Override
    public Position getPositionType() {
        // Vanilla's grid hangs off the top-right corner, which is what makes the offsets
        // below mean anything.
        return iconPreview() ? Position.TOP_RIGHT : AnchorMap.toHud(cfg().anchor);
    }

    @Override
    public void setPositionType(Position pos) {
        if (iconPreview()) return;
        cfg().anchor = AnchorMap.fromHud(pos);
        PotionHudConfig.markDirty();
    }

    /**
     * Where the preview sits, measured from the corner the host would anchor it to.
     *
     * <p>The editor reads our own {@link #getX()} for the box it draws, but the host's
     * placement pass works from the anchor and these offsets — so reporting the grid here
     * too is what stops it reserving a slice of the corner somewhere the icons are not.
     */
    @Override
    public int getXOffset() {
        int[] rect = iconPreview() ? iconRect() : null;
        if (rect == null) return cfg().xOffset;
        return rect[0] - (client.getWindow().getGuiScaledWidth() - rect[2] - HudPlacementResolver.EDGE_GAP);
    }

    @Override
    public int getYOffset() {
        int[] rect = iconPreview() ? iconRect() : null;
        if (rect == null) return cfg().yOffset;
        return rect[1] - HudPlacementResolver.EDGE_GAP;
    }

    // Minecraft decides where its own icons go, so while the preview is standing in for
    // them there is nothing for a drag, a nudge or a scroll to write to. Refusing the
    // write is what keeps the box on top of the icons it is describing; the list's real
    // placement is still editable from the settings screen, where the editor is closed
    // and every accessor here reads its stored value again.

    @Override
    public void setXOffset(int x) {
        if (iconPreview()) return;
        cfg().xOffset = x;
        PotionHudConfig.markDirty();
    }

    @Override
    public void setYOffset(int y) {
        if (iconPreview()) return;
        cfg().yOffset = y;
        PotionHudConfig.markDirty();
    }

    @Override
    public void setScale(int scale) {
        if (iconPreview()) return;
        cfg().scale = scale;
        PotionHudConfig.markDirty();
    }

    @Override
    public int getScale() {
        return cfg().scale;
    }

    // --- The editor's box ----------------------------------------------------------

    /**
     * The preview draws where Minecraft's icons actually are, not where the host placed
     * the module, so the box, the hover outline and the right-click hit test all have to
     * be told the same thing.
     */
    @Override
    public int getX() {
        int[] rect = iconPreview() ? iconRect() : null;
        return rect == null ? super.getX() : rect[0];
    }

    @Override
    public int getY() {
        int[] rect = iconPreview() ? iconRect() : null;
        return rect == null ? super.getY() : rect[1];
    }

    @Override
    public void onPositionUpdated() {
        PotionHudConfig.markDirty();
        // Writes simplecps.json, which collects our saveState() and flushes our file.
        SimpleCPSConfig.save();
    }

    /**
     * The master switch, and nothing else.
     *
     * <p>This is what the settings screen prints as ENABLED/DISABLED beside the module
     * name and what the sidebar shows as ON/OFF, so it has to answer "is this mod doing
     * anything" rather than "is one of its displays painting pixels right now". Reporting
     * the latter made the page say DISABLED while the mod was busy enhancing the vanilla
     * icons, which is a lie about the only thing that badge is for.
     *
     * <p>Costs nothing: what actually gets drawn is decided per display inside
     * {@link #extractRenderState}, and a mode with nothing of its own to draw draws
     * nothing.
     */
    @Override
    public boolean isEnabled() {
        return display().enabled;
    }

    // Zero in a mode that draws nothing here: the host then drops the module from the
    // placement pass altogether, which is what keeps an idle mod from taking a slice out
    // of the corner that other modules stack into.

    @Override
    public int getWidth() {
        int[] rect = iconPreview() ? iconRect() : null;
        if (rect != null) return rect[2];
        return listActive() || editorHandle() ? view().scaledWidth(sink) : 0;
    }

    @Override
    public int getHeight() {
        int[] rect = iconPreview() ? iconRect() : null;
        if (rect != null) return rect[3];
        return listActive() || editorHandle() ? view().scaledHeight(sink) : 0;
    }

    // --- Rendering ---------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        sink.setAlpha(renderAlpha);

        if (iconPreview() && drawIconPreview(context)) {
            refreshGroupBounds(null);
            return;
        }

        if (listActive() || editorHandle()) {
            PotionListView.Frame frame = view().frame(sink);
            refreshGroupBounds(frame);
            view().draw(context, sink, frame, this.x, this.y);
        } else {
            refreshGroupBounds(null);
        }
    }

    /**
     * The chosen mode as it will actually look, drawn where it will actually be.
     *
     * <p>Minecraft's own effect layer is switched off while the editor is open (see
     * {@code VanillaEffectOverlay}), so this draws the icons as well as anything the mode
     * adds to them. Nothing here reads {@code this.x/this.y}: the grid's position is
     * Minecraft's, and {@link #getX()} reports it back so the box lands on top.
     *
     * @return false when there was nothing to draw, so the caller can fall back to the
     *         list's placeholder rather than leaving the editor with nothing to grab
     */
    private boolean drawIconPreview(GuiGraphicsExtractor context) {
        DisplaySettings display = display();
        List<VanillaIconGrid.Cell> cells = previewCells(context.guiWidth());
        if (cells.isEmpty()) return false;

        VanillaIconGrid.drawIcons(context, sink, display, cells);
        if (previewExtras()) {
            VanillaIconGrid.drawExtras(context, sink, display, cells);
        }
        drawPinnedHint(context, VanillaIconGrid.bounds(display, cells));
        return true;
    }

    /**
     * One line under the grid saying why it will not move.
     *
     * <p>The box refuses to be dragged, and a control that ignores you without saying so
     * reads as broken. Half size and half opacity: it is a note in the editor, not part
     * of the HUD.
     */
    private void drawPinnedHint(GuiGraphicsExtractor context, int[] rect) {
        if (rect == null) return;
        final float scale = 0.5f;
        String hint = "Minecraft's own position";
        float width = sink.width(hint) * scale;

        context.pose().pushMatrix();
        context.pose().translate(rect[0] + rect[2] - width, rect[1] + rect[3] + 2f);
        context.pose().scale(scale, scale);
        sink.text(context, hint, 0, 0, 0x99FFFFFF);
        context.pose().popMatrix();
    }

    /** Keep the sub-elements' on-screen rectangles current for the editor's hit testing. */
    private void refreshGroupBounds(PotionListView.Frame frame) {
        float s = view().scale();
        if (frame == null || frame.positive == null || frame.negative == null) {
            positiveGroup.cache(this.x, this.y, 0, 0);
            negativeGroup.cache(this.x, this.y, 0, 0);
            return;
        }
        positiveGroup.cache(
            this.x + Math.round(frame.positiveX * s), this.y + Math.round(frame.positiveY * s),
            Math.round(frame.positive.width * s), Math.round(frame.positive.height * s));
        negativeGroup.cache(
            this.x + Math.round(frame.negativeX * s), this.y + Math.round(frame.negativeY * s),
            Math.round(frame.negative.width * s), Math.round(frame.negative.height * s));
    }

    /** Called from the plugin's {@code ComboStreakEvent} subscription. */
    public void pulse() {
        view().pulse();
    }

    // --- Sub-elements ---------------------------------------------------------------

    /**
     * The halves of a split list, once one has been pulled off on its own.
     *
     * <p>Empty otherwise, because the host drops a module from the editor's drag list the
     * moment it reports any sub-elements — so returning nothing is what keeps the module
     * itself grabbable in the ordinary case.
     */
    @Override
    public List<IHudElement> getSubElements() {
        if (!listActive() || !cfg().splitGroups || !anyGroupDetached()) return Collections.emptyList();
        return List.<IHudElement>of(positiveGroup, negativeGroup);
    }

    private static boolean anyGroupDetached() {
        ListSettings cfg = cfg();
        return cfg.positiveXOffset != 0 || cfg.positiveYOffset != 0
            || cfg.negativeXOffset != 0 || cfg.negativeYOffset != 0;
    }

    /** One half of a split list — beneficial and neutral effects, or harmful ones. */
    public final class GroupElement implements IHudElement {

        private final boolean positiveSide;
        private int cachedX, cachedY, cachedW, cachedH;

        GroupElement(boolean positiveSide) {
            this.positiveSide = positiveSide;
        }

        void cache(int x, int y, int w, int h) {
            this.cachedX = x;
            this.cachedY = y;
            this.cachedW = Math.max(1, w);
            this.cachedH = Math.max(1, h);
        }

        @Override
        public int getX() {
            return cachedX;
        }

        @Override
        public int getY() {
            return cachedY;
        }

        @Override
        public int getWidth() {
            return cachedW;
        }

        @Override
        public int getHeight() {
            return cachedH;
        }

        @Override
        public int getXOffset() {
            return positiveSide ? cfg().positiveXOffset : cfg().negativeXOffset;
        }

        @Override
        public int getYOffset() {
            return positiveSide ? cfg().positiveYOffset : cfg().negativeYOffset;
        }

        @Override
        public void setXOffset(int x) {
            if (positiveSide) cfg().positiveXOffset = x;
            else cfg().negativeXOffset = x;
            PotionHudConfig.markDirty();
        }

        @Override
        public void setYOffset(int y) {
            if (positiveSide) cfg().positiveYOffset = y;
            else cfg().negativeYOffset = y;
            PotionHudConfig.markDirty();
        }

        @Override
        public String getName() {
            return positiveSide ? "Positive Effects" : "Negative Effects";
        }

        @Override
        public void resetToDefaults() {
            setXOffset(0);
            setYOffset(0);
        }

        @Override
        public void onPositionUpdated() {
            PotionHudConfig.markDirty();
            SimpleCPSConfig.save();
        }

        @Override
        public List<HudModuleSetting> getContextMenuSettings() {
            List<HudModuleSetting> settings = new ArrayList<>();
            settings.add(new ActionSetting("Group", PotionHudModule.this::groupBack));
            return settings;
        }
    }

    /**
     * Split the two halves apart at their current positions, so the first drag does not
     * make them jump. Mirrors the HUD's own armour module Ungroup.
     */
    private void ungroup() {
        ListSettings cfg = cfg();
        cfg.splitGroups = true;
        float s = view().scale();
        PotionListView.Frame frame = view().frame(sink);
        // A one-pixel nudge guarantees a non-zero offset, which is the flag the editor
        // reads as "this group is on its own now".
        cfg.positiveXOffset = Math.max(1, Math.round(frame.positiveX * s));
        cfg.positiveYOffset = Math.round(frame.positiveY * s);
        cfg.negativeXOffset = Math.max(1, Math.round(frame.negativeX * s));
        cfg.negativeYOffset = Math.round(frame.negativeY * s);
        PotionHudConfig.markDirty();
        SimpleCPSConfig.save();
    }

    private void groupBack() {
        ListSettings cfg = cfg();
        cfg.positiveXOffset = 0;
        cfg.positiveYOffset = 0;
        cfg.negativeXOffset = 0;
        cfg.negativeYOffset = 0;
        PotionHudConfig.markDirty();
        SimpleCPSConfig.save();
    }

    // --- Resets ------------------------------------------------------------------------

    @Override
    public void resetToDefaults() {
        ListSettings cfg = cfg();
        cfg.anchor = Anchor.TOP_RIGHT;
        cfg.xOffset = 0;
        cfg.yOffset = 0;
        cfg.scale = 100;
        cfg.positiveXOffset = 0;
        cfg.positiveYOffset = 0;
        cfg.negativeXOffset = 0;
        cfg.negativeYOffset = 0;
        PotionHudConfig.markDirty();
    }

    @Override
    public void resetVisualDefaults() {
        // Everything visual goes back to stock; placement and the enabled flag are
        // explicitly out of scope for this reset, per the HUD's API contract.
        ListSettings current = cfg();
        ListSettings fresh = new ListSettings();
        fresh.anchor = current.anchor;
        fresh.xOffset = current.xOffset;
        fresh.yOffset = current.yOffset;
        fresh.scale = current.scale;
        fresh.splitGroups = current.splitGroups;
        fresh.positiveXOffset = current.positiveXOffset;
        fresh.positiveYOffset = current.positiveYOffset;
        fresh.negativeXOffset = current.negativeXOffset;
        fresh.negativeYOffset = current.negativeYOffset;
        PotionHudConfig.instance.list = fresh;
        // The display mode itself survives: it is what the player picked the mod for, not
        // a visual detail, and resetting it would move the display out from under them.
        DisplaySettings freshDisplay = new DisplaySettings();
        freshDisplay.mode = display().mode;
        PotionHudConfig.instance.display = freshDisplay;
        view().reset();
        PotionHudConfig.markDirty();
    }

    // --- Settings ---------------------------------------------------------------------

    /**
     * The master switch and the structural choice. Between them they answer "what is this
     * mod doing right now", which is the question a right-click in the editor is asking.
     */
    private static List<HudModuleSetting> masterSwitches() {
        List<HudModuleSetting> settings = new ArrayList<>(2);

        settings.add(new BooleanSetting("Enabled",
            () -> display().enabled,
            value -> { display().enabled = value; PotionHudConfig.markDirty(); }));

        settings.add(new CycleSetting("Display Mode",
            Enums.labels(DisplayMode.class, "Vanilla Icons", "Enhanced Icons", "Custom", "Hidden"),
            () -> Enums.ordinal(display().mode),
            index -> {
                display().mode = Enums.byOrdinal(DisplayMode.class, index, DisplayMode.ENHANCED);
                PotionHudConfig.markDirty();
            }));

        return settings;
    }

    @Override
    public List<HudModuleSetting> getContextMenuSettings() {
        List<HudModuleSetting> settings = new ArrayList<>(super.getContextMenuSettings());

        settings.addAll(masterSwitches());

        settings.add(new SliderSetting("Scale %", 50, 300, 100,
            () -> cfg().scale,
            value -> { cfg().scale = value; PotionHudConfig.markDirty(); }));

        settings.add(new CycleSetting("Style",
            Enums.labels(Style.class),
            () -> Enums.ordinal(cfg().style),
            index -> { cfg().style = Enums.byOrdinal(Style.class, index, Style.FULL); PotionHudConfig.markDirty(); }));

        settings.add(new ColorSetting("Text Color",
            () -> cfg().textColor,
            value -> { cfg().textColor = value; PotionHudConfig.markDirty(); }));

        settings.add(new BooleanSetting("Always Show",
            () -> cfg().alwaysShow,
            value -> { cfg().alwaysShow = value; PotionHudConfig.markDirty(); }));

        if (cfg().splitGroups && !anyGroupDetached()) {
            settings.add(new ActionSetting("Ungroup", this::ungroup));
        } else if (anyGroupDetached()) {
            settings.add(new ActionSetting("Group", this::groupBack));
        }

        return settings;
    }

    /**
     * Split into tabs rather than handed over as one list: fifty-odd rows in a single
     * scroll is the thing tabs exist to prevent. The context menu settings above lead the
     * first tab for free, so nothing here repeats them.
     *
     * <p>Six short names, because the host's tab strip does not scroll — tabs past the
     * panel's width are simply not drawn, and POSITION already takes a slot.
     */
    @Override
    public List<SettingsTab> getSettingsTabs() {
        return List.of(
            new SettingsTab("general", "General", generalSettings()),
            new SettingsTab("icons", "Icons", vanillaSettings()),
            new SettingsTab("list", "List", styleSettings()),
            new SettingsTab("timer", "Timer", timerSettings()),
            new SettingsTab("filter", "Filter", filterSettings()));
    }

    /**
     * The same settings flattened into one list, for a host older than the tab API.
     *
     * <p>A host that understands tabs would render this as a duplicate SETTINGS tab on the
     * end, so it gets nothing. One that does not never calls {@link #getSettingsTabs()},
     * and without this every setting below would be unreachable there. Grouping survives
     * as ordering rather than as headings; that is the whole cost of the fallback.
     */
    @Override
    public List<HudModuleSetting> getSettingsScreenSettings() {
        if (HostFeatures.SETTINGS_TABS) return List.of();

        List<HudModuleSetting> flat = new ArrayList<>();
        flat.addAll(generalSettings());
        flat.addAll(vanillaSettings());
        flat.addAll(styleSettings());
        flat.addAll(timerSettings());
        flat.addAll(filterSettings());
        return flat;
    }

    /** How the entries are arranged and how they move. */
    private List<HudModuleSetting> generalSettings() {
        List<HudModuleSetting> settings = new ArrayList<>();

        settings.add(new CycleSetting("Direction",
            Enums.labels(Direction.class),
            () -> Enums.ordinal(cfg().direction),
            index -> { cfg().direction = Enums.byOrdinal(Direction.class, index, Direction.DOWN); PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Items Per Line", 1, 8, 1,
            () -> cfg().columns,
            value -> { cfg().columns = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Spacing", 0, 12, 2,
            () -> cfg().spacing,
            value -> { cfg().spacing = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Split Groups",
            () -> cfg().splitGroups,
            value -> { cfg().splitGroups = value; if (!value) groupBack(); PotionHudConfig.markDirty(); }));

        settings.add(new BooleanSetting("Animate",
            () -> cfg().animate,
            value -> { cfg().animate = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Animation Length (cs)", 4, 60, 18,
            () -> cfg().animationSpeed,
            value -> { cfg().animationSpeed = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Pulse On Combo",
            () -> cfg().pulseOnCombo,
            value -> { cfg().pulseOnCombo = value; PotionHudConfig.markDirty(); }));

        return settings;
    }

    /** What each entry is made of: its icon, its name, its numbers. */
    private List<HudModuleSetting> styleSettings() {
        List<HudModuleSetting> settings = new ArrayList<>();

        settings.add(new CycleSetting("Icon Style",
            Enums.labels(IconStyle.class, "Effect Icon", "Colour Dot", "None"),
            () -> Enums.ordinal(cfg().iconStyle),
            index -> { cfg().iconStyle = Enums.byOrdinal(IconStyle.class, index, IconStyle.SPRITE); PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Icon Size", 8, 24, 16,
            () -> cfg().iconSize,
            value -> { cfg().iconSize = value; PotionHudConfig.markDirty(); }));

        settings.add(new BooleanSetting("Show Name",
            () -> cfg().showName,
            value -> { cfg().showName = value; PotionHudConfig.markDirty(); }));
        settings.add(new CycleSetting("Level Style",
            Enums.labels(LevelStyle.class, "Roman", "Number", "Hidden"),
            () -> Enums.ordinal(cfg().levelStyle),
            index -> { cfg().levelStyle = Enums.byOrdinal(LevelStyle.class, index, LevelStyle.ROMAN); PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Show Level I",
            () -> cfg().showLevelOne,
            value -> { cfg().showLevelOne = value; PotionHudConfig.markDirty(); }));
        settings.add(new CycleSetting("Duration Format",
            Enums.labels(TimeFormat.class, "M:SS", "Seconds", "Tenths", "Hidden"),
            () -> Enums.ordinal(cfg().timeFormat),
            index -> { cfg().timeFormat = Enums.byOrdinal(TimeFormat.class, index, TimeFormat.CLOCK); PotionHudConfig.markDirty(); }));
        settings.add(new TextSetting("Infinite Label",
            () -> cfg().infiniteLabel,
            value -> { cfg().infiniteLabel = value; PotionHudConfig.markDirty(); }));

        settings.addAll(colourSettings());
        return settings;
    }

    /** Every colour the list draws with, plus the panel behind it. */
    private List<HudModuleSetting> colourSettings() {
        List<HudModuleSetting> settings = new ArrayList<>();

        settings.add(new CycleSetting("Colour Mode",
            Enums.labels(ColorMode.class, "Single", "By Category", "Rainbow"),
            () -> Enums.ordinal(cfg().colorMode),
            index -> { cfg().colorMode = Enums.byOrdinal(ColorMode.class, index, ColorMode.SINGLE); PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Positive Effects",
            () -> cfg().positiveColor,
            value -> { cfg().positiveColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Negative Effects",
            () -> cfg().negativeColor,
            value -> { cfg().negativeColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Neutral Effects",
            () -> cfg().neutralColor,
            value -> { cfg().neutralColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Timer Text",
            () -> cfg().timeColor,
            value -> { cfg().timeColor = value; PotionHudConfig.markDirty(); }));

        settings.add(new BooleanSetting("Background",
            () -> cfg().showBackground,
            value -> { cfg().showBackground = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Background Colour",
            () -> cfg().backgroundColor,
            value -> { cfg().backgroundColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Background Opacity", 0, 255, 128,
            () -> cfg().backgroundOpacity,
            value -> { cfg().backgroundOpacity = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Rounded Corners",
            () -> cfg().roundedBackground,
            value -> { cfg().roundedBackground = value; PotionHudConfig.markDirty(); }));

        return settings;
    }

    /** Both readings of time left: the bar that drains and the warning that it is nearly gone. */
    private List<HudModuleSetting> timerSettings() {
        List<HudModuleSetting> settings = new ArrayList<>();

        settings.add(new BooleanSetting("Duration Bar",
            () -> cfg().showBar,
            value -> { cfg().showBar = value; PotionHudConfig.markDirty(); }));
        settings.add(new CycleSetting("Bar Placement",
            Enums.labels(BarStyle.class, "Under Text", "Behind Row", "Under Icon"),
            () -> Enums.ordinal(cfg().barStyle),
            index -> { cfg().barStyle = Enums.byOrdinal(BarStyle.class, index, BarStyle.UNDER_TEXT); PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Bar Height", 1, 6, 2,
            () -> cfg().barHeight,
            value -> { cfg().barHeight = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Bar Colour",
            () -> cfg().barColor,
            value -> { cfg().barColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Bar Track Opacity", 0, 255, 140,
            () -> cfg().barTrackOpacity,
            value -> { cfg().barTrackOpacity = value; PotionHudConfig.markDirty(); }));

        // These four are the expiry warning for the whole mod, not just this list — the
        // vanilla-icon overlay reads exactly the same fields, so an effect running out
        // looks the same wherever you are reading it.
        settings.add(new BooleanSetting("Expiry Warning",
            () -> display().warnLowTime,
            value -> { display().warnLowTime = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Warning Threshold (s)", 1, 60, 8,
            () -> display().warnThresholdSeconds,
            value -> { display().warnThresholdSeconds = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Warning Colour",
            () -> display().warnColor,
            value -> { display().warnColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Warning Blink",
            () -> display().warnBlink,
            value -> { display().warnBlink = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Warning Shake",
            () -> display().warnShake,
            value -> { display().warnShake = value; PotionHudConfig.markDirty(); }));

        return settings;
    }

    /** Which effects reach the list at all, and in what order. */
    private List<HudModuleSetting> filterSettings() {
        List<HudModuleSetting> settings = new ArrayList<>();

        settings.add(new BooleanSetting("Hide Ambient",
            () -> cfg().hideAmbient,
            value -> { cfg().hideAmbient = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Hide Particle-less",
            () -> cfg().hideParticleless,
            value -> { cfg().hideParticleless = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Hide Positive",
            () -> cfg().hidePositive,
            value -> { cfg().hidePositive = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Hide Negative",
            () -> cfg().hideNegative,
            value -> { cfg().hideNegative = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Hide Infinite",
            () -> cfg().hideInfinite,
            value -> { cfg().hideInfinite = value; PotionHudConfig.markDirty(); }));
        settings.add(new TextSetting("Hidden Effects",
            () -> cfg().hiddenEffects,
            value -> { cfg().hiddenEffects = value; PotionHudConfig.markDirty(); }));

        settings.add(new CycleSetting("Sort Order",
            Enums.labels(SortOrder.class, "Time Left", "Longest First", "Name", "Category", "Vanilla"),
            () -> Enums.ordinal(cfg().sortOrder),
            index -> { cfg().sortOrder = Enums.byOrdinal(SortOrder.class, index, SortOrder.TIME_LEFT); PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Harmful Last",
            () -> cfg().harmfulLast,
            value -> { cfg().harmfulLast = value; PotionHudConfig.markDirty(); }));

        return settings;
    }

    /**
     * What gets drawn onto Minecraft's own effect icons in
     * {@link DisplayMode#ENHANCED}. Nothing here does anything in any other mode.
     *
     * <p>Deliberately restrained by default: a hairline and nothing else. The icons are
     * artwork someone drew, and the point is to answer "how long left" without covering
     * them up — the timer and level readouts are here for people who want them, off until
     * they say so.
     */
    private List<HudModuleSetting> vanillaSettings() {
        List<HudModuleSetting> settings = new ArrayList<>();

        settings.add(new CycleSetting("Duration Style",
            Enums.labels(VanillaBar.class, "None", "Underline", "Drain"),
            () -> Enums.ordinal(display().vanillaBar),
            index -> {
                display().vanillaBar = Enums.byOrdinal(VanillaBar.class, index, VanillaBar.UNDERLINE);
                PotionHudConfig.markDirty();
            }));
        settings.add(new SliderSetting("Underline Thickness", 1, 3, 1,
            () -> display().vanillaBarThickness,
            value -> { display().vanillaBarThickness = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Use Effect Colour",
            () -> display().vanillaBarEffectColor,
            value -> { display().vanillaBarEffectColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Fixed Colour",
            () -> display().vanillaBarColor,
            value -> { display().vanillaBarColor = value; PotionHudConfig.markDirty(); }));

        settings.add(new BooleanSetting("Show Timer",
            () -> display().vanillaTimer,
            value -> { display().vanillaTimer = value; PotionHudConfig.markDirty(); }));
        settings.add(new ColorSetting("Timer Colour",
            () -> display().vanillaTimerColor,
            value -> { display().vanillaTimerColor = value; PotionHudConfig.markDirty(); }));
        settings.add(new BooleanSetting("Show Level",
            () -> display().vanillaLevel,
            value -> { display().vanillaLevel = value; PotionHudConfig.markDirty(); }));

        // The vanilla grid is re-derived rather than read from Minecraft, so these four
        // are the escape hatch if a future release nudges it.
        settings.add(new SliderSetting("Icon Spacing", 16, 40, 25,
            () -> display().gridPitch,
            value -> { display().gridPitch = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Row Gap", 16, 48, 26,
            () -> display().gridRowGap,
            value -> { display().gridRowGap = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Nudge X", -30, 30, 0,
            () -> display().gridOriginX,
            value -> { display().gridOriginX = value; PotionHudConfig.markDirty(); }));
        settings.add(new SliderSetting("Nudge Y", -30, 30, 1,
            () -> display().gridOriginY,
            value -> { display().gridOriginY = value; PotionHudConfig.markDirty(); }));

        return settings;
    }

    // --- State --------------------------------------------------------------------------

    @Override
    public JsonElement saveState() {
        // The HUD saves whenever the user changes something, which is exactly when our
        // own file should catch up too.
        PotionHudConfig.flush();

        JsonObject state = new JsonObject();
        state.add("list", PotionHudConfig.toTree(cfg()));
        state.add("display", PotionHudConfig.toTree(display()));
        return state;
    }

    @Override
    public void loadState(JsonElement state) {
        if (state == null || !state.isJsonObject()) return;
        JsonObject object = state.getAsJsonObject();

        // has() per key: a preset written before a section existed must leave that
        // section alone rather than resetting it.
        if (object.has("list")) {
            ListSettings restored = PotionHudConfig.fromTree(object.get("list"), ListSettings.class);
            if (restored != null) PotionHudConfig.instance.list = restored;
        }
        if (object.has("display")) {
            DisplaySettings restored = PotionHudConfig.fromTree(object.get("display"), DisplaySettings.class);
            if (restored != null) PotionHudConfig.instance.display = restored;
        }

        PotionHudConfig.instance.repair();
        view().reset();
        PotionHudConfig.save();
    }
}

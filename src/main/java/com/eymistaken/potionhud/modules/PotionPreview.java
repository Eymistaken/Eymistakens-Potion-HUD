package com.eymistaken.potionhud.modules;

import com.eymistaken.potionhud.EffectTracker;
import com.eymistaken.potionhud.PotionHudConfig;
import com.eymistaken.potionhud.PotionHudConfig.DisplayMode;
import com.eymistaken.potionhud.PotionHudConfig.DisplaySettings;
import com.eymistaken.potionhud.render.PreviewEffects;
import com.eymistaken.potionhud.render.TextSink;
import com.eymistaken.potionhud.render.VanillaIconGrid;
import com.eymistaken.potionhud.view.PotionListView;
import com.eymistaken.simplecps.api.HudPreview;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The chosen display, drawn into the settings screen's PREVIEW card.
 *
 * <p>Written by hand rather than handed to {@code HudPreview.ofModule}, which would draw
 * the module through its own render path. That path is built for the editor: what it
 * measures and draws is decided by {@code HudCompat.editorOpen()}, and the settings screen
 * is not the editor — the icon modes would report no size at all and the card would come
 * up empty. The grid's placement is the other half of it. Minecraft hangs those icons off
 * the right edge of the screen, and a preview card wants them at its own origin.
 *
 * <p>So the modes are resolved here instead, against the same rules the editor uses:
 * <ul>
 *   <li>both icon modes, and the master switch being off — which also leaves Minecraft's
 *       icons on screen — draw the vanilla grid, with the enhanced extras on top in the
 *       one mode that has them;</li>
 *   <li>the custom mode draws the list;</li>
 *   <li>hidden draws nothing and reports no size, which the host reads as "skip". The
 *       module returns null for that mode so no card is built at all, but the host
 *       resolves the preview when the page changes rather than every frame, so this is
 *       what the card falls back to if the mode is switched while it is open.</li>
 * </ul>
 *
 * <p>Everything drawing state is this class's own. The card is painted in the same frame
 * as the live HUD, so a shared animation would be stepped twice and run at double speed;
 * the list gets its own view and this gets its own sink.
 */
final class PotionPreview implements HudPreview {

    /**
     * The screen width the grid is laid out against.
     *
     * <p>Any value does: the grid is measured after the fact and shifted to the card's
     * origin, so this only has to be wide enough to lay out without the arithmetic
     * wrapping past zero.
     */
    private static final int GRID_REF_WIDTH = 320;

    /** Its own, so the ambient fade the live path sets cannot follow the card. */
    private final TextSink sink = new TextSink();

    /** Its own, so arrival tweens and the rainbow hue are not stepped twice a frame. */
    private final PotionListView view =
        new PotionListView(() -> PreviewEffects.forList(EffectTracker.snapshots()), true);

    private static DisplaySettings display() {
        return PotionHudConfig.instance.display;
    }

    private static DisplayMode mode() {
        DisplayMode mode = display().mode;
        return mode == null ? DisplayMode.ENHANCED : mode;
    }

    /** True when the card should show Minecraft's icon grid rather than the custom list. */
    private static boolean grid() {
        DisplaySettings display = display();
        if (!display.enabled) return true;
        DisplayMode mode = mode();
        return mode == DisplayMode.VANILLA || mode == DisplayMode.ENHANCED;
    }

    /** True when the custom list is the chosen display. */
    private static boolean list() {
        return display().enabled && mode() == DisplayMode.CUSTOM;
    }

    /** The extras are the enhanced mode's alone; the other two grids are plain icons. */
    private static boolean extras() {
        return display().enabled && mode() == DisplayMode.ENHANCED;
    }

    private static List<VanillaIconGrid.Cell> cells() {
        return VanillaIconGrid.layout(display(),
            PreviewEffects.forGrid(EffectTracker.snapshots()), GRID_REF_WIDTH);
    }

    /** The grid's own rectangle as {@code {x, y, width, height}}, or null when empty. */
    private static int[] gridRect() {
        return VanillaIconGrid.bounds(display(), cells());
    }

    // --- Size -------------------------------------------------------------------

    @Override
    public int width() {
        if (grid()) {
            int[] rect = gridRect();
            return rect == null ? 0 : rect[2];
        }
        return list() ? view.scaledWidth(sink) : 0;
    }

    @Override
    public int height() {
        if (grid()) {
            int[] rect = gridRect();
            return rect == null ? 0 : rect[3];
        }
        return list() ? view.scaledHeight(sink) : 0;
    }

    // --- Drawing ----------------------------------------------------------------

    @Override
    public void render(GuiGraphicsExtractor context, float tickDelta) {
        // The card owns the geometry: it has already sized, centered and clipped us, and it
        // draws us one-for-one with screen pixels. Nothing here scales itself.
        sink.setAlpha(1f);

        if (grid()) {
            drawGrid(context);
        } else if (list()) {
            PotionListView.Frame frame = view.frame(sink);
            view.draw(context, sink, frame, 0, 0);
        }
    }

    /**
     * The vanilla grid, moved to the card's origin.
     *
     * <p>The cells are laid out from the right edge of a screen, so their coordinates start
     * wherever that arithmetic put them. Translating by the measured top-left is how the
     * grid ends up drawn from {@code (0, 0)} as the card requires — the card's own
     * transform is untouched.
     */
    private void drawGrid(GuiGraphicsExtractor context) {
        DisplaySettings display = display();
        List<VanillaIconGrid.Cell> cells = cells();
        int[] rect = VanillaIconGrid.bounds(display, cells);
        if (rect == null) return;

        context.pose().pushMatrix();
        context.pose().translate((float) -rect[0], (float) -rect[1]);
        VanillaIconGrid.drawIcons(context, sink, display, cells);
        if (extras()) {
            VanillaIconGrid.drawExtras(context, sink, display, cells);
        }
        context.pose().popMatrix();
    }
}

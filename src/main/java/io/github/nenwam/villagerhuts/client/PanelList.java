package io.github.nenwam.villagerhuts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** A scrolling list inside the worker panel, with the creative inventory's scrollbar and no background of its own. */
abstract class PanelList<E extends ContainerObjectSelectionList.Entry<E>> extends ContainerObjectSelectionList<E> {
    static final int SCROLLBAR_WIDTH = 14;
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    PanelList(Minecraft minecraft, int defaultEntryHeight) {
        super(minecraft, 0, 0, 0, defaultEntryHeight);
    }

    @Override
    public int getRowLeft() {
        return this.getX();
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - SCROLLBAR_WIDTH - 3;
    }

    @Override
    public int scrollbarWidth() {
        return SCROLLBAR_WIDTH;
    }

    @Override
    protected int scrollBarX() {
        return this.getRight() - SCROLLBAR_WIDTH;
    }

    @Override
    protected int scrollerHeight() {
        return 15;
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = this.scrollBarX();
        NativeStyle.inset(graphics, x, this.getY(), SCROLLBAR_WIDTH, this.getHeight());
        int y = Math.min(this.scrollBarY(), this.getBottom() - 15);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.scrollable() ? SCROLLER : SCROLLER_DISABLED, x + 1, y, 12, 15);
    }

    // The panel draws its own background; skip the full-screen list styling.
    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }
}

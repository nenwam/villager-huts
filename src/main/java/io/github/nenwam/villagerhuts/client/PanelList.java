package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A scrolling list inside the worker panel, with the creative inventory's scrollbar and no background of its own.
 * Its rows can each be a different height (vanilla's lists give every row the same height). Each row lays out and
 * draws its own content and buttons; clicks go to the row under the mouse.
 */
abstract class PanelList<E extends PanelList.Entry> extends AbstractContainerWidget {
    static final int SCROLLBAR_WIDTH = 14;
    private static final int SCROLLER_HEIGHT = 15;
    private static final int SCROLL_STEP = 18; // per notch of the mouse wheel
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    protected final Minecraft minecraft;
    private final List<E> entries = new ArrayList<>();
    private final List<Integer> heights = new ArrayList<>();
    private int contentHeight;
    private double scroll;
    private boolean draggingScroller;

    PanelList(Minecraft minecraft) {
        super(0, 0, 0, 0, Component.empty());
        this.minecraft = minecraft;
    }

    /** Adds a row this many pixels tall (including its padding). */
    void addEntry(E entry, int height) {
        this.entries.add(entry);
        this.heights.add(height);
        this.contentHeight += height;
    }

    void clearEntries() {
        this.setFocused(null);
        this.entries.clear();
        this.heights.clear();
        this.contentHeight = 0;
    }

    double scrollAmount() {
        return this.scroll;
    }

    void setScrollAmount(double amount) {
        this.scroll = Mth.clamp(amount, 0.0, this.maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - this.getHeight());
    }

    private boolean scrollable() {
        return this.maxScroll() > 0;
    }

    /** The width rows get: the list's, less the scrollbar and a small gap before it. */
    int getRowWidth() {
        return this.getWidth() - SCROLLBAR_WIDTH - 3;
    }

    void updateSizeAndPosition(int width, int height, int x, int y) {
        this.setRectangle(width, height, x, y);
        this.setScrollAmount(this.scroll);
    }

    @Override
    public List<E> children() {
        return this.entries;
    }

    // Where each row is now, scrolled (rows out of sight too, so clicks always find the right one).
    private void layOut() {
        int top = this.getY() - (int) this.scroll;
        for (int i = 0; i < this.entries.size(); i++) {
            int height = this.heights.get(i);
            this.entries.get(i).setBounds(this.getX(), top, this.getRowWidth(), height);
            top += height;
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.setScrollAmount(this.scroll); // the rows may have changed since
        this.layOut();
        boolean mouseInside = this.isMouseOver(mouseX, mouseY);
        graphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());
        for (E entry : this.entries) {
            if (entry.bottom() > this.getY() && entry.top() < this.getBottom()) {
                entry.renderContent(graphics, mouseX, mouseY, mouseInside && entry.isMouseOver(mouseX, mouseY), partialTick);
            }
        }
        graphics.disableScissor();

        int x = this.getRight() - SCROLLBAR_WIDTH;
        NativeStyle.inset(graphics, x, this.getY(), SCROLLBAR_WIDTH, this.getHeight());
        graphics.blitSprite(this.scrollable() ? SCROLLER : SCROLLER_DISABLED, x + 1, this.scrollerY(), 12, SCROLLER_HEIGHT);
    }

    private int scrollerY() {
        int travel = this.getHeight() - SCROLLER_HEIGHT;
        return this.getY() + (this.scrollable() ? (int) (this.scroll * travel / this.maxScroll()) : 0);
    }

    // Puts the middle of the scroller under the mouse.
    private void scrollTo(double mouseY) {
        double fraction = (mouseY - this.getY() - SCROLLER_HEIGHT / 2.0) / Math.max(1, this.getHeight() - SCROLLER_HEIGHT);
        this.setScrollAmount(fraction * this.maxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (button == 0 && mouseX >= this.getRight() - SCROLLBAR_WIDTH) {
            if (this.scrollable()) {
                this.draggingScroller = true;
                this.scrollTo(mouseY);
            }
            return true;
        }
        this.layOut();
        for (E entry : this.entries) {
            if (entry.isMouseOver(mouseX, mouseY) && entry.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(entry);
                this.setDragging(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScroller) {
            this.scrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScroller = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.setScrollAmount(this.scroll - scrollY * SCROLL_STEP);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    /** A row of the list: lays out and draws its content, and passes clicks to its buttons (children()). */
    abstract static class Entry extends AbstractContainerEventHandler {
        static final int CONTENT_PADDING = 2;

        private int x;
        private int y;
        private int width;
        private int height;

        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        int top() {
            return this.y;
        }

        int bottom() {
            return this.y + this.height;
        }

        int getContentX() {
            return this.x + CONTENT_PADDING;
        }

        int getContentY() {
            return this.y + CONTENT_PADDING;
        }

        int getContentRight() {
            return this.x + this.width - CONTENT_PADDING;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        }

        /** Draws the row (its buttons included), at the position from setBounds. */
        abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick);
    }
}

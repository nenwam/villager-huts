package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.nenwam.villagerhuts.network.WorkerInfo;
import io.github.nenwam.villagerhuts.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The scrolling list of workers inside the worker panel (see WorkerPanel), drawn in the
 * inventory's style: each worker is a colored slot with its job tool and number, its name and
 * status, a book button that opens its details page, an eye toggle for its farm highlight,
 * and a row of item slots for what it collected.
 */
class WorkerList extends ContainerObjectSelectionList<WorkerList.Row> {
    // Row heights include 2px of list padding above and below, plus a little space between workers.
    private static final int ROW_GAP = 6;
    static final int ROW_HEIGHT = 18 + 4 + ROW_GAP;            // worker without collected items
    private static final int ROW_HEIGHT_ITEMS = 38 + 4 + ROW_GAP; // with a row of item slots
    private static final int SCROLLBAR_WIDTH = 14;
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    WorkerList(Minecraft minecraft) {
        super(minecraft, 0, 0, 0, ROW_HEIGHT);
    }

    void setWorkers(List<WorkerInfo> workers) {
        double scroll = this.scrollAmount();
        this.clearEntries();
        for (WorkerInfo info : workers) {
            Row row = new Row(info);
            this.addEntry(row, row.collected.isEmpty() ? ROW_HEIGHT : ROW_HEIGHT_ITEMS);
        }
        this.setScrollAmount(scroll);
    }

    @Override
    public int getRowLeft() {
        return this.getX();
    }

    @Override
    public int getRowWidth() {
        return this.getWidth() - SCROLLBAR_WIDTH - 3;
    }

    // ---------------------------------------------------------------- creative-inventory style scrollbar

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

    // ---------------------------------------------------------------- rows

    class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final WorkerInfo info;
        private final SlotToggle areaToggle;
        private final SlotButton infoButton;
        private final ItemStack jobIcon;
        private final List<Map.Entry<ItemStack, Integer>> collected = new ArrayList<>();

        Row(WorkerInfo info) {
            this.info = info;
            this.areaToggle = new SlotToggle(button -> ClientWorkers.toggleHighlight(info.id()),
                    () -> ClientWorkers.isShown(info.id()), () -> ClientWorkers.color(info.number()),
                    Component.translatable("gui.villagerhuts.panel.show"), Component.translatable("gui.villagerhuts.panel.hide"));
            this.areaToggle.active = info.workArea().isPresent();
            this.infoButton = new SlotButton(new ItemStack(Items.BOOK), Component.translatable("gui.villagerhuts.panel.details"),
                    button -> WorkerPanel.showDetails(info.id()));
            this.jobIcon = new ItemStack(info.job().equals("farmer") ? Items.IRON_HOE : ModItems.WORKER_HUT.get());
            List<Map.Entry<Identifier, Integer>> items = new ArrayList<>(info.collected().entrySet());
            items.sort(Map.Entry.<Identifier, Integer>comparingByValue().reversed());
            for (Map.Entry<Identifier, Integer> item : items) {
                this.collected.add(Map.entry(new ItemStack(BuiltInRegistries.ITEM.getValue(item.getKey())), item.getValue()));
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = WorkerList.this.minecraft.font;
            int x = this.getContentX();
            int y = this.getContentY();
            int right = this.getContentRight();

            // The worker: a slot in its color, holding its job tool, with its number like a stack count.
            NativeStyle.slot(graphics, x, y, ClientWorkers.color(this.info.number()));
            graphics.item(this.jobIcon, x + 1, y + 1);
            graphics.itemDecorations(font, this.jobIcon, x + 1, y + 1, String.valueOf(this.info.number()));

            this.areaToggle.setPosition(right - 18, y);
            this.infoButton.setPosition(right - 38, y);
            int textX = x + 21;
            int textWidth = this.infoButton.getX() - textX - 2;

            // Name, with days worked (small) at the end of the line; status underneath.
            FormattedCharSequence days = this.days().getVisualOrderText();
            int daysX = this.infoButton.getX() - 3 - NativeStyle.smallWidth(font, days);
            NativeStyle.smallText(graphics, font, days, daysX, y + 2, NativeStyle.LABEL);
            graphics.text(font, fit(font, this.title(), daysX - textX - 4), textX, y + 1, NativeStyle.LABEL, false);
            NativeStyle.smallText(graphics, font, fitSmall(font, this.info.status(), textWidth), textX, y + 11,
                    this.info.loaded() ? NativeStyle.LABEL : 0xFF707070);

            // What it has collected, as a row of item slots (most first). Nothing collected: no row.
            int itemsY = y + 20;
            int hoveredItem = -1;
            for (int i = 0; i < this.collected.size(); i++) {
                int slotX = x + i * 18;
                if (slotX + 18 > right) {
                    break;
                }
                Map.Entry<ItemStack, Integer> item = this.collected.get(i);
                NativeStyle.slot(graphics, slotX, itemsY);
                graphics.item(item.getKey(), slotX + 1, itemsY + 1);
                graphics.itemDecorations(font, item.getKey(), slotX + 1, itemsY + 1, shortCount(item.getValue()));
                if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= itemsY && mouseY < itemsY + 18) {
                    hoveredItem = i;
                }
            }

            this.infoButton.extractRenderState(graphics, mouseX, mouseY, a);
            this.areaToggle.extractRenderState(graphics, mouseX, mouseY, a);

            if (hoveredItem >= 0) {
                Map.Entry<ItemStack, Integer> item = this.collected.get(hoveredItem);
                graphics.setTooltipForNextFrame(List.of(item.getKey().getHoverName().getVisualOrderText(),
                        Component.translatable("gui.villagerhuts.panel.collected_count", item.getValue()).getVisualOrderText()), mouseX, mouseY);
            }
        }

        private Component title() {
            return WorkerDetails.title(this.info);
        }

        private Component days() {
            long days = this.info.employedTicks() / 24000L;
            return Component.translatable("gui.villagerhuts.panel.days_short", days);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.infoButton, this.areaToggle);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.infoButton, this.areaToggle);
        }
    }

    /** Fits stack-count style: 120, 1.2k, 12k. */
    static String shortCount(int count) {
        if (count < 1000) {
            return String.valueOf(count);
        }
        return count < 10000 ? String.format(Locale.ROOT, "%.1fk", count / 1000.0) : (count / 1000) + "k";
    }

    static FormattedCharSequence fit(Font font, Component text, int width) {
        return Language.getInstance().getVisualOrder(font.substrByWidth(text, Math.max(0, width)));
    }

    static FormattedCharSequence fitSmall(Font font, Component text, int width) {
        return fit(font, text, (int) (width / NativeStyle.SMALL));
    }
}

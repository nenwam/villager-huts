package io.github.nenwam.villagerhuts.client;

import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jspecify.annotations.Nullable;

/**
 * The worker management panel, attached to the side of the survival inventory (right side,
 * or left if there's no room). Opened and closed with the Workers button; stays open between
 * inventory openings. The list page shows your workers with live status, days worked and what
 * they've collected, plus buttons to highlight their farm areas in the world; each worker's book
 * button opens its details page (back arrow returns to the list).
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class WorkerPanel {
    private static final int MAX_WIDTH = 200;
    private static final int MIN_WIDTH = 140;
    private static final int GAP = 2;
    private static final int MARGIN = 2;
    private static final int HEADER = 24;
    private static final int PADDING = 7; // same inner margin as the inventory
    private static final int SHOW_ALL_COLOR = 0xFFE0B040;

    private static final WidgetSprites BACK_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/page_backward"), Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted"));

    private static boolean open;
    private static @Nullable UUID selected; // worker whose details page is showing, or null for the list
    private static @Nullable Widgets current;

    private record Widgets(InventoryScreen screen, WorkerList list, Button showAll,
                           WorkerDetails details, Button back, Button detailToggle, PanelArea area) {}

    /** Opens a worker's details page (from its book button). */
    public static void showDetails(@Nullable UUID worker) {
        selected = worker;
    }

    public static boolean isOpen() {
        return open;
    }

    public static void setOpen(boolean value) {
        open = value;
        if (open) {
            ClientWorkers.request();
        }
    }

    public static void toggle() {
        setOpen(!open);
    }

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        WorkerList list = new WorkerList(Minecraft.getInstance());
        Button showAll = new SlotToggle(button -> ClientWorkers.toggleShowAll(), ClientWorkers::isShowingAll, () -> SHOW_ALL_COLOR,
                Component.translatable("gui.villagerhuts.panel.show_all"), Component.translatable("gui.villagerhuts.panel.hide_all"));
        WorkerDetails details = new WorkerDetails();
        Button back = new ImageButton(12, 17, BACK_SPRITES, button -> showDetails(null), Component.translatable("gui.villagerhuts.panel.back"));
        back.setTooltip(Tooltip.create(Component.translatable("gui.villagerhuts.panel.back")));
        Button detailToggle = new SlotToggle(button -> { if (selected != null) ClientWorkers.toggleHighlight(selected); },
                () -> selected != null && ClientWorkers.isShown(selected), WorkerPanel::selectedColor,
                Component.translatable("gui.villagerhuts.panel.show"), Component.translatable("gui.villagerhuts.panel.hide"));
        PanelArea area = new PanelArea();
        event.addListener(list);
        event.addListener(showAll);
        event.addListener(details);
        event.addListener(back);
        event.addListener(detailToggle);
        event.addListener(area); // last: only gets clicks the list and buttons didn't use
        Widgets widgets = new Widgets(screen, list, showAll, details, back, detailToggle, area);
        current = widgets;
        layout(widgets);
        list.setWorkers(ClientWorkers.workers());
        ClientWorkers.setListener(() -> {
            Widgets now = current;
            if (now != null) {
                now.list().setWorkers(ClientWorkers.workers());
            }
        });
        if (open) {
            ClientWorkers.request();
        }
    }

    // Re-place every frame: follows the inventory when the recipe book opens or the window resizes.
    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Pre event) {
        Widgets widgets = current;
        if (widgets != null && event.getScreen() == widgets.screen()) {
            layout(widgets);
        }
    }

    @SubscribeEvent
    static void onRenderBackground(ScreenEvent.Render.Background event) {
        Widgets widgets = current;
        if (!open || widgets == null || event.getScreen() != widgets.screen()) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        ScreenRectangle panel = widgets.area().getRectangle();
        NativeStyle.panel(graphics, panel.left(), panel.top(), panel.width(), panel.height());
        WorkerInfo details = widgets.details().info();
        if (details != null) {
            int titleX = widgets.back().getRight() + 4;
            int titleWidth = widgets.detailToggle().getX() - titleX - 2;
            graphics.text(font, WorkerList.fit(font, WorkerDetails.title(details), titleWidth), titleX, panel.top() + 9, NativeStyle.LABEL, false);
            return;
        }
        graphics.text(font, Component.translatable("gui.villagerhuts.panel.title"), panel.left() + 8, panel.top() + 9, NativeStyle.LABEL, false);
        if (ClientWorkers.workers().isEmpty()) {
            int wrapWidth = (int) ((panel.width() - 2 * PADDING) / NativeStyle.SMALL);
            int y = panel.top() + HEADER + 2;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.villagerhuts.workers.empty"), wrapWidth)) {
                NativeStyle.smallText(graphics, font, line, panel.left() + PADDING, y, NativeStyle.LABEL_SOFT);
                y += 8;
            }
        }
    }

    // Holding an item and releasing the mouse outside the inventory normally drops the item.
    // Over this panel, hand the release to the panel instead.
    @SubscribeEvent
    static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Widgets widgets = current;
        if (!open || widgets == null || event.getScreen() != widgets.screen()) {
            return;
        }
        MouseButtonEvent mouse = event.getMouseButtonEvent();
        if (widgets.area().isMouseOver(mouse.x(), mouse.y())) {
            widgets.list().mouseReleased(mouse);
            widgets.showAll().mouseReleased(mouse);
            widgets.back().mouseReleased(mouse);
            widgets.detailToggle().mouseReleased(mouse);
            widgets.screen().setDragging(false);
            event.setCanceled(true);
        }
    }

    // Inventory screens don't pass the scroll wheel on to their widgets, so give it to the list here.
    @SubscribeEvent
    static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        Widgets widgets = current;
        if (!open || widgets == null || event.getScreen() != widgets.screen()
                || !widgets.area().isMouseOver(event.getMouseX(), event.getMouseY())) {
            return;
        }
        if (widgets.list().visible) {
            widgets.list().mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY());
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onClosing(ScreenEvent.Closing event) {
        Widgets widgets = current;
        if (widgets != null && event.getScreen() == widgets.screen()) {
            current = null;
            ClientWorkers.setListener(null);
        }
    }

    private static void layout(Widgets widgets) {
        InventoryScreen screen = widgets.screen();
        int left = screen.leftPos;
        int top = screen.topPos;
        int right = left + screen.imageWidth;
        int spaceRight = screen.width - right - GAP - MARGIN;
        int spaceLeft = left - GAP - MARGIN;

        int width;
        int x;
        if (spaceRight >= MIN_WIDTH || spaceRight >= spaceLeft) {
            width = Math.max(100, Math.min(MAX_WIDTH, spaceRight));
            x = right + GAP;
        } else {
            width = Math.min(MAX_WIDTH, spaceLeft);
            x = left - GAP - width;
        }
        int height = screen.imageHeight;

        if (!widgets.area().getRectangle().equals(new ScreenRectangle(x, top, width, height))) {
            widgets.area().setRectangle(width, height, x, top);
            widgets.list().updateSizeAndPosition(width - 2 * PADDING, height - HEADER - PADDING, x + PADDING, top + HEADER);
            widgets.details().setRectangle(width - 2 * PADDING, height - HEADER - PADDING, x + PADDING, top + HEADER);
        }
        widgets.showAll().setPosition(x + width - PADDING - 18, top + 4);
        widgets.detailToggle().setPosition(x + width - PADDING - 18, top + 4);
        widgets.back().setPosition(x + PADDING, top + 4);

        // Which page: a worker's details, or the list. (Back to the list if that worker is gone.)
        widgets.details().show(selected);
        if (selected != null && widgets.details().info() == null) {
            selected = null;
            widgets.details().show(null);
        }
        boolean detailsPage = open && selected != null;
        boolean listPage = open && selected == null;
        widgets.list().visible = listPage;
        widgets.showAll().visible = listPage;
        widgets.details().visible = detailsPage;
        widgets.back().visible = detailsPage;
        widgets.detailToggle().visible = detailsPage;
        widgets.detailToggle().active = detailsPage && widgets.details().info() != null && widgets.details().info().workArea().isPresent();
        widgets.area().visible = open;
    }

    private static int selectedColor() {
        WorkerInfo info = current != null ? current.details().info() : null;
        return info != null ? ClientWorkers.color(info.number()) : NativeStyle.SLOT_FILL;
    }

    /** The panel's whole area: takes clicks that land on the panel but not on a button or row. */
    private static final class PanelArea extends AbstractWidget {
        PanelArea() {
            super(0, 0, 0, 0, Component.empty());
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return this.visible && this.isMouseOver(event.x(), event.y());
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    private WorkerPanel() {}
}

package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.network.RenameWorkerPayload;
import io.github.nenwam.villagerhuts.network.WorkerCommandPayload;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jspecify.annotations.Nullable;

/**
 * The worker management panel, attached to the side of the survival inventory (right side,
 * or left if there's no room). Opened and closed with the Workers button; stays open between
 * inventory openings. The list page shows your workers with live status, days worked and what
 * they've collected, plus buttons to highlight their farms and chests in the world, and to give all your own
 * workers an order at once (work, follow you, stay); each worker's book button opens its details page (back arrow
 * returns to the list). Workers shared with you (or your party's) are listed too, after your own. On the details page:
 *  - the name tag button renames the worker: type a name, then Enter (or click the name tag again) saves
 *    and Escape cancels;
 *  - beside its slot, its orders (work, follow you, stay put; the one it's on is in its color), and on the
 *    right a button to take its tool back (owner only). These only work while the worker is near enough to be loaded:
 *    otherwise they're greyed out, and their tooltips say why;
 *  - the compass says where it is in chat (with a map marker, with Xaero's minimap), near or far;
 *  - the player-head button opens its sharing page (see WorkerSharing); the back arrow returns to the details.
 * When the server turns a button down (or reports what it did), a notice shows at the bottom of the panel for a few seconds.
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
    private static final int NOTICE_FILL = 0xF0303030;
    private static final int NOTICE_BORDER = 0xFF000000;
    private static final int NOTICE_TEXT = 0xFFFFE080;

    private static final WidgetSprites BACK_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/page_backward"), ResourceLocation.withDefaultNamespace("recipe_book/page_backward_highlighted"));

    private static boolean open;
    private static @Nullable UUID selected; // worker whose details page is showing, or null for the list
    private static boolean sharingPage;     // showing the selected worker's sharing page instead of its details
    private static @Nullable UUID renaming; // worker whose name is being typed, or null
    private static boolean focusNameBox;    // give the name box focus on the next frame (see toggleRename)
    private static @Nullable Boolean fighterButtons; // whether the order and tool buttons are set up for a fighter
    private static @Nullable String fieldButton;     // which way the field button is set up (the field it was set for)
    private static @Nullable Widgets current;

    private record Widgets(InventoryScreen screen, WorkerList list, Button showAll, List<Button> orderAll, WorkerDetails details, Button back,
                           Button rename, EditBox nameBox, Button detailToggle, List<Button> orders, Button field, Button locate, Button takeTool,
                           Button sharing, WorkerSharing sharingList, PanelArea area) {}

    /** Where the panel is on this screen, while it's showing (so EMI and JEI keep their item lists out of the way). */
    public static Optional<ScreenRectangle> panelArea(Screen screen) {
        Widgets widgets = current;
        return open && widgets != null && widgets.screen() == screen ? Optional.of(widgets.area().getRectangle()) : Optional.empty();
    }

    /** Opens a worker's details page (from its book button). */
    public static void showDetails(@Nullable UUID worker) {
        selected = worker;
        sharingPage = false;
    }

    // The back arrow: from the sharing page to the details, from the details to the list.
    private static void back() {
        if (sharingPage) {
            sharingPage = false;
        } else {
            showDetails(null);
        }
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
        List<Button> orderAll = List.of(
                orderAllButton(Items.WHEAT, "gui.villagerhuts.panel.all_work", WorkerCommandPayload.Command.WORK),
                orderAllButton(Items.LEAD, "gui.villagerhuts.panel.all_follow", WorkerCommandPayload.Command.FOLLOW),
                orderAllButton(Items.ARMOR_STAND, "gui.villagerhuts.panel.all_stay", WorkerCommandPayload.Command.STAY));
        WorkerDetails details = new WorkerDetails();
        Button back = new ImageButton(12, 17, BACK_SPRITES, button -> back(), Component.translatable("gui.villagerhuts.panel.back"));
        back.setTooltip(Tooltip.create(Component.translatable("gui.villagerhuts.panel.back")));
        Button detailToggle = new SlotToggle(button -> { if (selected != null) ClientWorkers.toggleHighlight(selected); },
                () -> selected != null && ClientWorkers.isShown(selected), WorkerPanel::selectedColor,
                Component.translatable("gui.villagerhuts.panel.show"), Component.translatable("gui.villagerhuts.panel.hide"));
        Button rename = new SlotButton(new ItemStack(Items.NAME_TAG), Component.translatable("gui.villagerhuts.panel.rename"),
                button -> toggleRename());
        EditBox nameBox = new EditBox(Minecraft.getInstance().font, 0, 14, Component.translatable("gui.villagerhuts.panel.rename"));
        nameBox.setMaxLength(RenameWorkerPayload.MAX_NAME_LENGTH);
        nameBox.setHint(Component.translatable("gui.villagerhuts.panel.rename_hint"));
        List<Button> orders = List.of(
                orderButton(Items.WHEAT, "gui.villagerhuts.panel.order_work", WorkerEntity.Order.WORK),
                orderButton(Items.LEAD, "gui.villagerhuts.panel.order_follow", WorkerEntity.Order.FOLLOW),
                orderButton(Items.ARMOR_STAND, "gui.villagerhuts.panel.order_stay", WorkerEntity.Order.STAY));
        // A farmer's field: clear it (everything planted, into the chest), or start farming it again once it's cleared.
        Button field = new SlotButton(new ItemStack(Items.CHEST), Component.translatable("gui.villagerhuts.panel.clear_field"), button -> {
            WorkerInfo info = current != null ? current.details().info() : null;
            if (info != null) {
                ClientWorkers.command(info.id(), info.field().equals("farming")
                        ? WorkerCommandPayload.Command.CLEAR_FIELD : WorkerCommandPayload.Command.START_FARMING);
            }
        });
        Button locate = new SlotButton(new ItemStack(Items.COMPASS),
                Component.translatable(ClientWorkers.hasXaero() ? "gui.villagerhuts.panel.locate_on_map" : "gui.villagerhuts.panel.locate"),
                button -> { if (selected != null) ClientWorkers.locate(selected); });
        Button takeTool = new SlotButton(new ItemStack(Items.IRON_HOE), Component.translatable("gui.villagerhuts.panel.take_tool"),
                button -> { if (selected != null) ClientWorkers.command(selected, WorkerCommandPayload.Command.TAKE_TOOL); });
        Button sharing = new SlotButton(new ItemStack(Items.PLAYER_HEAD), Component.translatable("gui.villagerhuts.panel.sharing"),
                button -> sharingPage = true);
        WorkerSharing sharingList = new WorkerSharing(Minecraft.getInstance());
        PanelArea area = new PanelArea();
        event.addListener(list);
        event.addListener(showAll);
        orderAll.forEach(event::addListener);
        event.addListener(details);
        event.addListener(back);
        event.addListener(rename);
        event.addListener(nameBox);
        event.addListener(detailToggle);
        orders.forEach(event::addListener);
        event.addListener(field);
        event.addListener(locate);
        event.addListener(takeTool);
        event.addListener(sharing);
        event.addListener(sharingList);
        event.addListener(area); // last: only gets clicks the list and buttons didn't use
        Widgets widgets = new Widgets(screen, list, showAll, orderAll, details, back, rename, nameBox, detailToggle, orders, field, locate,
                takeTool, sharing, sharingList, area);
        current = widgets;
        renaming = null;
        focusNameBox = false;
        fighterButtons = null;
        fieldButton = null;
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

    // Drawn between the inventory's background and its widgets (and the worker panel's), so they go on top.
    @SubscribeEvent
    static void onRenderBackground(ContainerScreenEvent.Render.Background event) {
        Widgets widgets = current;
        if (!open || widgets == null || event.getContainerScreen() != widgets.screen()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        ScreenRectangle panel = widgets.area().getRectangle();
        NativeStyle.panel(graphics, panel.left(), panel.top(), panel.width(), panel.height());
        WorkerInfo details = widgets.details().info();
        if (details != null && sharingPage) {
            int titleX = widgets.back().getRight() + 4;
            Component title = Component.translatable("gui.villagerhuts.sharing.title", WorkerDetails.name(details));
            graphics.drawString(font, WorkerList.fit(font, title, panel.right() - PADDING - titleX), titleX, panel.top() + 9, NativeStyle.LABEL, false);
            return;
        }
        if (details != null) {
            if (renaming == null) { // while renaming, the name box sits here instead
                int titleX = widgets.back().getRight() + 4;
                int titleWidth = widgets.rename().getX() - titleX - 2;
                graphics.drawString(font, WorkerList.fit(font, WorkerDetails.title(details), titleWidth), titleX, panel.top() + 9, NativeStyle.LABEL, false);
            }
            return;
        }
        int titleRight = widgets.orderAll().getFirst().getX() - 2;
        graphics.drawString(font, WorkerList.fit(font, Component.translatable("gui.villagerhuts.panel.title"), titleRight - panel.left() - 8),
                panel.left() + 8, panel.top() + 9, NativeStyle.LABEL, false);
        if (ClientWorkers.workers().isEmpty()) {
            int y = panel.top() + HEADER + 2;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.villagerhuts.workers.empty"), panel.width() - 2 * PADDING)) {
                NativeStyle.text(graphics, font, line, panel.left() + PADDING, y, NativeStyle.LABEL_SOFT);
                y += NativeStyle.LINE;
            }
        }
    }

    // The latest notice (why a button did nothing, or what it did), in a box at the bottom of the panel, over everything.
    @SubscribeEvent
    static void onRenderNotice(ScreenEvent.Render.Post event) {
        Widgets widgets = current;
        Component notice = ClientWorkers.notice();
        if (!open || widgets == null || notice == null || event.getScreen() != widgets.screen()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        ScreenRectangle panel = widgets.area().getRectangle();
        List<FormattedCharSequence> lines = font.split(notice, panel.width() - 2 * PADDING - 6);
        lines = lines.subList(0, Math.min(3, lines.size()));
        int boxTop = panel.bottom() - PADDING - lines.size() * NativeStyle.LINE - 6;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400); // in front of item icons, like a tooltip
        graphics.fill(panel.left() + PADDING - 1, boxTop - 1, panel.right() - PADDING + 1, panel.bottom() - PADDING + 1, NOTICE_BORDER);
        graphics.fill(panel.left() + PADDING, boxTop, panel.right() - PADDING, panel.bottom() - PADDING, NOTICE_FILL);
        int y = boxTop + 3;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, panel.left() + PADDING + 3, y, NOTICE_TEXT, false);
            y += NativeStyle.LINE;
        }
        graphics.pose().popPose();
    }

    // Holding an item and releasing the mouse outside the inventory normally drops the item.
    // Over this panel, hand the release to the panel instead.
    @SubscribeEvent
    static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        Widgets widgets = current;
        if (!open || widgets == null || event.getScreen() != widgets.screen()) {
            return;
        }
        double x = event.getMouseX();
        double y = event.getMouseY();
        int button = event.getButton();
        if (widgets.area().isMouseOver(x, y)) {
            widgets.list().mouseReleased(x, y, button);
            widgets.showAll().mouseReleased(x, y, button);
            widgets.back().mouseReleased(x, y, button);
            widgets.rename().mouseReleased(x, y, button);
            widgets.nameBox().mouseReleased(x, y, button);
            widgets.detailToggle().mouseReleased(x, y, button);
            widgets.orders().forEach(order -> order.mouseReleased(x, y, button));
            widgets.field().mouseReleased(x, y, button);
            widgets.takeTool().mouseReleased(x, y, button);
            widgets.sharing().mouseReleased(x, y, button);
            widgets.sharingList().mouseReleased(x, y, button);
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
        } else if (widgets.sharingList().visible) {
            widgets.sharingList().mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY());
        } else if (widgets.details().visible) {
            widgets.details().scrollBy(event.getScrollDeltaY());
        }
        event.setCanceled(true);
    }

    // While renaming, every key goes to the name box. Otherwise E (inventory) closes the inventory, Q drops
    // the hovered item, 1-9 swap it with the hotbar, and Escape closes the inventory instead of cancelling.
    // (Typed letters reach the name box by themselves: it has focus.)
    @SubscribeEvent
    static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Widgets widgets = current;
        if (renaming == null || widgets == null || event.getScreen() != widgets.screen()) {
            return;
        }
        int key = event.getKeyCode();
        if (key == InputConstants.KEY_ESCAPE) {
            finishRename(widgets, false);
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            finishRename(widgets, true);
        } else {
            widgets.nameBox().keyPressed(key, event.getScanCode(), event.getModifiers());
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onClosing(ScreenEvent.Closing event) {
        Widgets widgets = current;
        if (widgets != null && event.getScreen() == widgets.screen()) {
            current = null;
            renaming = null;
            ClientWorkers.setListener(null);
        }
    }

    private static void layout(Widgets widgets) {
        if (focusNameBox) {
            focusNameBox = false;
            if (renaming != null) {
                widgets.screen().setFocused(widgets.nameBox());
            }
        }
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
            widgets.sharingList().updateSizeAndPosition(width - 2 * PADDING, height - HEADER - PADDING, x + PADDING, top + HEADER);
        }
        widgets.showAll().setPosition(x + width - PADDING - 18, top + 4);
        for (int i = 0; i < widgets.orderAll().size(); i++) { // left of Show all
            widgets.orderAll().get(i).setPosition(x + width - PADDING - 18 - 20 * (widgets.orderAll().size() - i), top + 4);
        }
        widgets.detailToggle().setPosition(x + width - PADDING - 18, top + 4);
        widgets.rename().setPosition(x + width - PADDING - 38, top + 4);
        widgets.back().setPosition(x + PADDING, top + 4);
        for (int i = 0; i < widgets.orders().size(); i++) {
            widgets.orders().get(i).setPosition(x + PADDING + 21 + i * 20, top + HEADER);
        }
        int nameX = widgets.back().getRight() + 3;
        widgets.nameBox().setRectangle(Math.max(0, widgets.rename().getX() - 3 - nameX), 14, nameX, top + 6);

        // Which page: a worker's details, or the list. (Back to the list if that worker is gone.)
        widgets.details().show(selected);
        if (selected != null && widgets.details().info() == null) {
            showDetails(null); // gone (or no longer shared with you)
            widgets.details().show(null);
        }
        boolean detailsPage = open && selected != null && !sharingPage;
        boolean sharingShown = open && selected != null && sharingPage;
        boolean listPage = open && selected == null;
        // Clicking anywhere else, or leaving the page, saves the name being typed.
        if (renaming != null && (!renaming.equals(selected) || !detailsPage || !widgets.nameBox().isFocused())) {
            finishRename(widgets, true);
        }
        widgets.list().visible = listPage;
        widgets.showAll().visible = listPage;
        widgets.orderAll().forEach(button -> button.visible = listPage);
        widgets.details().visible = detailsPage;
        widgets.back().visible = detailsPage || sharingShown;
        widgets.rename().visible = detailsPage;
        widgets.nameBox().visible = detailsPage && renaming != null;
        widgets.detailToggle().visible = detailsPage;
        WorkerInfo shown = widgets.details().info();
        widgets.detailToggle().active = detailsPage && shown != null && (shown.farm().isPresent() || shown.storage().isPresent() || shown.bed().isPresent());
        Component tooFar = shown != null && !shown.loaded() ? Component.translatable("gui.villagerhuts.panel.too_far") : null;
        for (Button order : widgets.orders()) {
            order.visible = detailsPage;
            unavailable(order, tooFar);
        }
        if (shown != null) {
            setUpButtons(widgets, shown);
        }
        widgets.takeTool().visible = detailsPage && shown != null && shown.yours() && WorkerJob.byId(shown.job()).hasTool();
        unavailable(widgets.takeTool(), tooFar);
        widgets.field().visible = detailsPage && shown != null && shown.job().equals(WorkerJob.FARMER.id()) && shown.farm().isPresent();
        unavailable(widgets.field(), tooFar);
        widgets.locate().visible = detailsPage;
        widgets.sharing().visible = detailsPage;
        placeRightButtons(widgets, x, width, top);
        widgets.sharingList().visible = sharingShown;
        if (sharingShown) {
            widgets.sharingList().refresh(shown);
        }
        widgets.area().visible = open;
    }

    private static void unavailable(Button button, @Nullable Component reason) {
        if (button instanceof SlotButton slot) {
            slot.setUnavailable(reason);
        }
    }

    // The details page's buttons on the right (field, compass, sharing, tool): beside the orders if there's room, or on a
    // row of their own under them in a narrow panel. The facts start below whichever row is lower.
    private static void placeRightButtons(Widgets widgets, int x, int width, int top) {
        List<Button> right = new ArrayList<>();
        for (Button button : List.of(widgets.field(), widgets.locate(), widgets.sharing(), widgets.takeTool())) {
            if (button.visible) {
                right.add(button);
            }
        }
        int ordersRight = x + PADDING + 21 + widgets.orders().size() * 20;
        boolean fits = x + width - PADDING - right.size() * 20 >= ordersRight;
        int rowY = fits ? top + HEADER : top + HEADER + 20;
        int rightEdge = x + width - PADDING + 2;
        for (int i = 0; i < right.size(); i++) {
            right.get(right.size() - 1 - i).setPosition(rightEdge - 20 * (i + 1), rowY);
        }
        widgets.details().setFactsTop(fits || right.isEmpty() ? 23 : 43);
    }

    // Farmers and fighters word their Work order differently, and the tool button shows the worker's own tool.
    // A farmer's field button clears the field, or, once it's cleared (or being cleared), starts it farming again.
    private static void setUpButtons(Widgets widgets, WorkerInfo shown) {
        if (!shown.field().equals(fieldButton) && widgets.field() instanceof SlotButton field) {
            fieldButton = shown.field();
            boolean farming = shown.field().equals("farming");
            field.setIcon(new ItemStack(farming ? Items.CHEST : Items.WHEAT_SEEDS));
            setHint(field, Component.translatable(farming ? "gui.villagerhuts.panel.clear_field" : "gui.villagerhuts.panel.start_farming"));
        }
        boolean fighter = WorkerJob.byId(shown.job()).isFighter();
        if (widgets.takeTool() instanceof SlotButton takeTool) {
            takeTool.setIcon(shown.tool().isEmpty() ? new ItemStack(Items.IRON_HOE) : shown.tool());
        }
        if (Boolean.valueOf(fighter).equals(fighterButtons)) {
            return;
        }
        fighterButtons = fighter;
        if (widgets.orders().getFirst() instanceof SlotButton work) {
            work.setIcon(new ItemStack(fighter ? Items.SHIELD : Items.WHEAT));
            setHint(work, Component.translatable(fighter ? "gui.villagerhuts.panel.order_guard" : "gui.villagerhuts.panel.order_work"));
        }
        setHint(widgets.takeTool(), Component.translatable(fighter ? "gui.villagerhuts.panel.take_weapon" : "gui.villagerhuts.panel.take_tool"));
    }

    private static void setHint(Button button, Component hint) {
        button.setMessage(hint);
        if (!(button instanceof SlotButton)) { // a slot button makes its own tooltip from its hint
            button.setTooltip(Tooltip.create(hint));
        }
    }

    // One of the list page's buttons that give all your own workers the same order.
    private static Button orderAllButton(Item icon, String hint, WorkerCommandPayload.Command order) {
        return new SlotButton(new ItemStack(icon), Component.translatable(hint), button -> ClientWorkers.orderAll(order));
    }

    // One of the order buttons beside the worker's slot: shown in the worker's color while it's on that order.
    private static Button orderButton(Item icon, String hint, WorkerEntity.Order order) {
        return new SlotButton(new ItemStack(icon), Component.translatable(hint),
                button -> { if (selected != null) ClientWorkers.command(selected, WorkerCommandPayload.Command.valueOf(order.name())); }) {
            @Override
            protected int fillColor() {
                WorkerInfo info = current != null ? current.details().info() : null;
                return info != null && info.order().equals(order.id()) ? ClientWorkers.color(info.number()) : NativeStyle.SLOT_FILL;
            }
        };
    }

    // The name tag button: starts renaming the worker on the details page, or saves the name being typed.
    private static void toggleRename() {
        Widgets widgets = current;
        if (widgets == null) {
            return;
        }
        if (renaming != null) {
            finishRename(widgets, true);
            return;
        }
        WorkerInfo info = widgets.details().info();
        if (info == null) {
            return;
        }
        renaming = info.id();
        widgets.nameBox().setValue(info.customName().orElse(""));
        widgets.nameBox().visible = true;
        // The screen focuses the button that was clicked once this returns, so focus the name box on the next frame.
        focusNameBox = true;
        setRenameHint(widgets, Component.translatable("gui.villagerhuts.panel.rename_save"));
    }

    /** Stops renaming, sending the typed name to the server if it's a change (or throwing it away). */
    private static void finishRename(Widgets widgets, boolean save) {
        UUID worker = renaming;
        renaming = null;
        widgets.nameBox().visible = false;
        if (widgets.screen().getFocused() == widgets.nameBox()) {
            widgets.screen().setFocused(null);
        }
        setRenameHint(widgets, Component.translatable("gui.villagerhuts.panel.rename"));
        if (!save || worker == null) {
            return;
        }
        String name = widgets.nameBox().getValue().strip();
        String oldName = ClientWorkers.workers().stream().filter(info -> info.id().equals(worker))
                .findFirst().flatMap(WorkerInfo::customName).orElse("");
        if (!name.equals(oldName)) {
            ClientWorkers.rename(worker, name);
        }
    }

    private static void setRenameHint(Widgets widgets, Component hint) {
        widgets.rename().setMessage(hint);
        widgets.rename().setTooltip(Tooltip.create(hint));
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
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return this.visible && this.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    private WorkerPanel() {}
}

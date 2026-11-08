package io.github.nenwam.villagerhuts.client;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.registry.ModItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds a "Workers" button to the survival inventory that opens and closes the worker panel
 * (see WorkerPanel). It starts just right of the recipe book
 * button; if another mod's button is in the way, it moves to the next free spot. It re-checks
 * every frame, so it also follows the panel when the recipe book opens.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class InventoryButton {

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            WorkersButton button = new WorkersButton();
            event.addListener(button);
            place(screen, button);
        }
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof WorkersButton button) {
                    place(screen, button);
                }
            }
        }
    }

    // Spots to try, in order of preference (relative to the inventory panel).
    private static void place(InventoryScreen screen, WorkersButton button) {
        int left = screen.leftPos;
        int top = screen.topPos;
        int right = left + screen.imageWidth;
        int[][] spots = {
                {left + 126, top + 61},  // right of the recipe book button
                {left + 148, top + 61},
                {right + 2, top + 2},    // outside the panel's right edge, top down
                {right + 2, top + 22},
                {right + 2, top + 42},
                {right + 2, top + 62},
                {left - 22, top + 2},    // outside the left edge
                {left - 22, top + 22},
                {right - 20, top - 20},  // above the panel
                {right - 42, top - 20},
        };
        for (int[] spot : spots) {
            ScreenRectangle area = new ScreenRectangle(spot[0], spot[1], button.getWidth(), button.getHeight());
            if (isOnScreen(screen, area) && !overlapsAnotherWidget(screen, button, area)) {
                button.setPosition(spot[0], spot[1]);
                return;
            }
        }
        button.setPosition(spots[0][0], spots[0][1]); // everything taken: use the default spot
    }

    private static boolean isOnScreen(InventoryScreen screen, ScreenRectangle area) {
        return area.left() >= 0 && area.top() >= 0 && area.right() <= screen.width && area.bottom() <= screen.height;
    }

    private static boolean overlapsAnotherWidget(InventoryScreen screen, AbstractWidget self, ScreenRectangle area) {
        for (GuiEventListener child : screen.children()) {
            if (child != self && child instanceof AbstractWidget widget && widget.visible && widget.getRectangle().intersects(area)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Same size as the recipe book button, with a farmer icon. Light and raised while the panel is
     * closed; dark and pushed in while it's open.
     */
    static final class WorkersButton extends Button {
        private final ItemStack icon = new ItemStack(ModItems.FARMER.get());
        private Boolean shownState;

        WorkersButton() {
            super(0, 0, 20, 18, Component.translatable("gui.villagerhuts.workers.button"),
                    button -> WorkerPanel.toggle(), DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            boolean open = WorkerPanel.isOpen();
            if (!Boolean.valueOf(open).equals(this.shownState)) {
                this.shownState = open;
                this.setTooltip(Tooltip.create(Component.translatable(open ? "gui.villagerhuts.workers.button_hide" : "gui.villagerhuts.workers.button")));
            }
            int x = this.getX();
            int y = this.getY();
            if (open) {
                NativeStyle.pressed(graphics, x, y, this.width, this.height);
            } else {
                NativeStyle.raised(graphics, x, y, this.width, this.height);
            }
            if (this.isHoveredOrFocused()) {
                graphics.fill(x + 1, y + 1, x + this.width - 1, y + this.height - 1, 0x30FFFFFF);
            }
            int pushed = open ? 1 : 0; // the icon sinks a pixel when pressed in
            graphics.item(this.icon, x + 2 + pushed, y + 1 + pushed);
        }
    }

    private InventoryButton() {}
}

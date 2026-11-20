package io.github.nenwam.villagerhuts.client;

import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A button drawn as an inventory slot holding an item icon (e.g. the book that opens worker details). One that can't
 * be used right now is greyed out, with why in its tooltip (see setUnavailable).
 */
class SlotButton extends Button {
    private static final int GREYED = (NativeStyle.SLOT_FILL & 0xFFFFFF) | 0xB0000000; // the slot's color, over the icon

    private ItemStack icon;
    private @Nullable Component unavailable; // why it can't be used now, or null

    SlotButton(ItemStack icon, Component hint, OnPress onPress) {
        super(0, 0, 18, 18, hint, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.setTooltip(Tooltip.create(hint));
    }

    /** Changes the icon (e.g. to the worker's own tool). */
    void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    /** Greys the button out and says why in its tooltip (e.g. the worker is too far away); null makes it usable again. */
    void setUnavailable(@Nullable Component reason) {
        if (Objects.equals(reason, this.unavailable) && this.active == (reason == null)) {
            return;
        }
        this.unavailable = reason;
        this.active = reason == null;
        this.updateTooltip();
    }

    /** The hint (also its tooltip). */
    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.updateTooltip();
    }

    private void updateTooltip() {
        Component hint = this.getMessage();
        this.setTooltip(Tooltip.create(this.unavailable == null ? hint
                : Component.empty().append(hint).append("\n").append(this.unavailable.copy().withStyle(ChatFormatting.RED))));
    }

    /** The color inside the slot: plain, unless a subclass shows a state with it (like the order a worker is on). */
    protected int fillColor() {
        return NativeStyle.SLOT_FILL;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        NativeStyle.slot(graphics, this.getX(), this.getY(), this.fillColor());
        if (this.isHoveredOrFocused() && this.active) {
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, NativeStyle.SLOT_HOVER);
        }
        graphics.renderItem(this.icon, this.getX() + 1, this.getY() + 1);
        if (!this.active) { // drawn as an overlay: items are drawn in front of everything else in the slot
            graphics.fill(RenderType.guiOverlay(), this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, GREYED);
        }
    }
}

package io.github.nenwam.villagerhuts.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A button drawn as an inventory slot holding an item icon (e.g. the book that opens worker details). */
class SlotButton extends Button {
    private final ItemStack icon;

    SlotButton(ItemStack icon, Component hint, OnPress onPress) {
        super(0, 0, 18, 18, hint, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.setTooltip(Tooltip.create(hint));
    }

    /** The color inside the slot: plain, unless a subclass shows a state with it (like the order a worker is on). */
    protected int fillColor() {
        return NativeStyle.SLOT_FILL;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        NativeStyle.slot(graphics, this.getX(), this.getY(), this.fillColor());
        if (this.isHoveredOrFocused()) {
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, NativeStyle.SLOT_HOVER);
        }
        graphics.item(this.icon, this.getX() + 1, this.getY() + 1);
    }
}

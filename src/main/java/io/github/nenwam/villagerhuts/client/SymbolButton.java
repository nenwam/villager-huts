package io.github.nenwam.villagerhuts.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** A button drawn as an inventory slot with a symbol in it (+ to share, × to stop sharing). */
class SymbolButton extends Button {
    private final Component symbol;
    private final int color;

    SymbolButton(String symbol, int color, Component hint, OnPress onPress) {
        super(0, 0, 18, 18, hint, onPress, DEFAULT_NARRATION);
        this.symbol = Component.literal(symbol);
        this.color = color;
        this.setTooltip(Tooltip.create(hint));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        NativeStyle.slot(graphics, this.getX(), this.getY());
        if (this.isHoveredOrFocused()) {
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, NativeStyle.SLOT_HOVER);
        }
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, this.symbol, this.getX() + 9 - font.width(this.symbol) / 2, this.getY() + 5, this.color, true);
    }
}

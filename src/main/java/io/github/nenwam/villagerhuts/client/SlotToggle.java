package io.github.nenwam.villagerhuts.client;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An on/off switch drawn as an inventory slot: an eye of ender on a colored slot when on,
 * an ender pearl (a "closed eye") on a plain slot when off.
 */
final class SlotToggle extends Button {
    private static final ItemStack ON_ICON = new ItemStack(Items.ENDER_EYE);
    private static final ItemStack OFF_ICON = new ItemStack(Items.ENDER_PEARL);

    private final BooleanSupplier isOn;
    private final IntSupplier onColor;
    private final Component turnOnHint;
    private final Component turnOffHint;
    private Boolean shownState;

    SlotToggle(OnPress onPress, BooleanSupplier isOn, IntSupplier onColor, Component turnOnHint, Component turnOffHint) {
        super(0, 0, 18, 18, turnOnHint, onPress, DEFAULT_NARRATION);
        this.isOn = isOn;
        this.onColor = onColor;
        this.turnOnHint = turnOnHint;
        this.turnOffHint = turnOffHint;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean on = this.isOn.getAsBoolean();
        if (!Boolean.valueOf(on).equals(this.shownState)) { // keep the hint in step with the state
            this.shownState = on;
            Component hint = on ? this.turnOffHint : this.turnOnHint;
            this.setMessage(hint);
            this.setTooltip(Tooltip.create(hint));
        }
        NativeStyle.slot(graphics, this.getX(), this.getY(), on ? this.onColor.getAsInt() : NativeStyle.SLOT_FILL);
        if (this.isHoveredOrFocused()) {
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + 17, this.getY() + 17, NativeStyle.SLOT_HOVER);
        }
        graphics.item(on ? ON_ICON : OFF_ICON, this.getX() + 1, this.getY() + 1);
    }
}

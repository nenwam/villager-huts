package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.nenwam.villagerhuts.network.WorkerInfo;
import io.github.nenwam.villagerhuts.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The worker details page of the worker panel: the worker's slot (its buttons, for orders and its tool,
 * are separate widgets beside it: see WorkerPanel), what it's doing, where it works, and everything it
 * has collected. Reads the latest data every frame, so it updates live.
 */
final class WorkerDetails extends AbstractWidget {
    private static final int LINE = NativeStyle.LINE;

    private @Nullable UUID worker;

    WorkerDetails() {
        super(0, 0, 0, 0, Component.empty());
    }

    void show(@Nullable UUID worker) {
        this.worker = worker;
    }

    @Nullable WorkerInfo info() {
        if (this.worker == null) {
            return null;
        }
        for (WorkerInfo info : ClientWorkers.workers()) {
            if (info.id().equals(this.worker)) {
                return info;
            }
        }
        return null;
    }

    static Component title(WorkerInfo info) {
        Component job = Component.translatable("job.villagerhuts." + info.job());
        return info.customName()
                .<Component>map(name -> Component.translatable("gui.villagerhuts.workers.named", name, job))
                .orElse(job);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        WorkerInfo info = this.info();
        if (info == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();

        // Who: the worker's slot (color, tool, number). Its name and job are in the panel's title.
        ItemStack icon = new ItemStack(info.job().equals("farmer") ? Items.IRON_HOE : ModItems.FARMER.get());
        NativeStyle.slot(graphics, x, y, ClientWorkers.color(info.number()));
        graphics.item(icon, x + 1, y + 1);
        graphics.itemDecorations(font, icon, x + 1, y + 1, String.valueOf(info.number()));

        // What, where, how long.
        int lineY = y + 23;
        for (Component line : this.facts(info)) {
            for (FormattedCharSequence wrapped : font.split(line, width)) {
                NativeStyle.text(graphics, font, wrapped, x, lineY, NativeStyle.LABEL);
                lineY += LINE;
            }
        }

        // Everything it has collected, as a grid of item slots (no section if nothing yet).
        if (info.collected().isEmpty()) {
            return;
        }
        lineY += 3;
        NativeStyle.text(graphics, font, Component.translatable("gui.villagerhuts.details.collected").getVisualOrderText(), x, lineY, NativeStyle.LABEL);
        lineY += LINE;
        List<Map.Entry<Identifier, Integer>> items = new ArrayList<>(info.collected().entrySet());
        items.sort(Map.Entry.<Identifier, Integer>comparingByValue().reversed());
        int columns = Math.max(1, width / 18);
        for (int i = 0; i < items.size(); i++) {
            int slotX = x + (i % columns) * 18;
            int slotY = lineY + (i / columns) * 18;
            if (slotY + 18 > this.getBottom()) {
                break; // out of room
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(items.get(i).getKey()));
            int count = items.get(i).getValue();
            NativeStyle.slot(graphics, slotX, slotY);
            graphics.item(stack, slotX + 1, slotY + 1);
            graphics.itemDecorations(font, stack, slotX + 1, slotY + 1, WorkerList.shortCount(count));
            if (mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                graphics.setTooltipForNextFrame(List.of(stack.getHoverName().getVisualOrderText(),
                        Component.translatable("gui.villagerhuts.panel.collected_count", count).getVisualOrderText()), mouseX, mouseY);
            }
        }
    }

    // "Label: value" lines, with the label in the darker label color.
    private List<Component> facts(WorkerInfo info) {
        List<Component> lines = new ArrayList<>();
        lines.add(fact("status", info.status()));
        long days = info.employedTicks() / 24000L;
        lines.add(fact("worked", days == 0 ? Component.translatable("gui.villagerhuts.details.days_less")
                : days == 1 ? Component.translatable("gui.villagerhuts.details.days_one")
                : Component.translatable("gui.villagerhuts.details.days", days)));
        if (info.farm().isPresent()) {
            BoundingBox area = info.farm().get();
            lines.add(fact("farm", Component.translatable("gui.villagerhuts.details.farm_assigned",
                    area.getXSpan(), area.getZSpan(), area.getCenter().getX(), area.getCenter().getZ())));
        } else {
            lines.add(fact("farm", Component.translatable("gui.villagerhuts.details.farm_none")));
        }
        if (info.storage().isPresent()) {
            BlockPos chest = info.storage().get();
            lines.add(fact("chest", Component.translatable("gui.villagerhuts.details.position", chest.getX(), chest.getY(), chest.getZ())));
        } else {
            lines.add(fact("chest", Component.translatable("gui.villagerhuts.details.chest_none")));
        }
        lines.add(info.loaded()
                ? fact("carrying", Component.translatable("gui.villagerhuts.details.carrying_value", info.carried()))
                : fact("carrying", Component.translatable("gui.villagerhuts.details.unknown_far_away")));
        return lines;
    }

    private static Component fact(String label, Component value) {
        return Component.empty()
                .append(Component.translatable("gui.villagerhuts.details." + label).withColor(NativeStyle.LABEL & 0xFFFFFF))
                .append(" ")
                .append(value);
    }

    // Clicks on the page itself do nothing (its buttons are separate widgets).
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

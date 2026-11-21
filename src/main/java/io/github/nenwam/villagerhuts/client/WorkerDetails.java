package io.github.nenwam.villagerhuts.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The worker details page of the worker panel: the worker's slot (its buttons, for orders, sharing and its tool,
 * are separate widgets beside it: see WorkerPanel), what it's doing, its level and today's harvest (a fighter: its
 * health and damage, weapon and ammo), where it works and sleeps, who it has defeated and everything it has
 * collected. Reads the latest data every frame, so it updates live.
 * Scrolls (mouse wheel) when it doesn't all fit.
 */
final class WorkerDetails extends AbstractWidget {
    private static final int LINE = NativeStyle.LINE;
    private static final int SCROLL_STEP = 10;

    private @Nullable UUID worker;
    private int factsTop = 23; // below the worker's slot and its buttons (one row of them, or two), which don't scroll
    private double scroll;
    private int contentHeight;

    WorkerDetails() {
        super(0, 0, 0, 0, Component.empty());
    }

    void show(@Nullable UUID worker) {
        if (worker == null || !worker.equals(this.worker)) {
            this.scroll = 0;
        }
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

    /** Where the facts start, below the buttons (WorkerPanel puts them on two rows in a narrow panel). */
    void setFactsTop(int factsTop) {
        this.factsTop = factsTop;
    }

    void scrollBy(double notches) {
        this.scroll = Mth.clamp(this.scroll - notches * SCROLL_STEP, 0, this.maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - (this.getHeight() - this.factsTop));
    }

    /** What to show in its slot: its tool or weapon, or, with none, the item that hires its kind of worker. */
    static ItemStack icon(WorkerInfo info) {
        if (!info.tool().isEmpty()) {
            return info.tool();
        }
        return new ItemStack(WorkerJob.byId(info.job()).isFighter() ? ModItems.FIGHTER : ModItems.FARMER);
    }

    /** Its name, or its job if it has none. */
    static Component name(WorkerInfo info) {
        return info.customName().<Component>map(Component::literal).orElseGet(() -> Component.translatable("job.villagerhuts." + info.job()));
    }

    /** "Ramesh (Farmer)", or for a worker shared with you, "Ramesh (Steve's Farmer)". */
    static Component title(WorkerInfo info) {
        Component job = Component.translatable("job.villagerhuts." + info.job());
        if (!info.yours()) {
            job = Component.translatable("gui.villagerhuts.workers.owners_job", info.owner().name(), job);
        }
        Component jobPart = job;
        return info.customName()
                .<Component>map(name -> Component.translatable("gui.villagerhuts.workers.named", name, jobPart))
                .orElse(jobPart);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        WorkerInfo info = this.info();
        if (info == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int x = this.getX();
        int width = this.getWidth();
        int top = this.getY();
        int bottom = this.getBottom();
        // Who: the worker's slot (color, tool, number). Its name and job are in the panel's title.
        ItemStack icon = icon(info);
        NativeStyle.slot(graphics, x, top, ClientWorkers.color(info.number()));
        graphics.item(icon, x + 1, top + 1);
        graphics.itemDecorations(font, icon, x + 1, top + 1, String.valueOf(info.number()));

        // Everything under it scrolls.
        int factsTop = top + this.factsTop;
        boolean mouseInside = mouseX >= x && mouseX < this.getRight() && mouseY >= factsTop && mouseY < bottom;
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
        graphics.enableScissor(x, factsTop, this.getRight(), bottom);

        // What, where, how long.
        int lineY = factsTop - (int) this.scroll;
        for (Component line : this.facts(info)) {
            for (FormattedCharSequence wrapped : font.split(line, width)) {
                NativeStyle.text(graphics, font, wrapped, x, lineY, NativeStyle.LABEL);
                lineY += LINE;
            }
        }

        // Who it has defeated (spawn eggs), then everything it has collected, as grids of slots (no section if none yet).
        List<Slot> defeated = new ArrayList<>();
        sorted(info.defeated()).forEach(entry -> BuiltInRegistries.ENTITY_TYPE.getOptional(entry.getKey()).ifPresent(type ->
                defeated.add(new Slot(SpawnEggItem.byId(type).map(egg -> new ItemStack(egg)).orElseGet(() -> new ItemStack(Items.SKELETON_SKULL)),
                        type.getDescription(), entry.getValue(), "gui.villagerhuts.panel.defeated_count"))));
        lineY = this.grid(graphics, font, "gui.villagerhuts.details.defeated", defeated, x, lineY, width, mouseInside, mouseX, mouseY);
        List<Slot> collected = new ArrayList<>();
        sorted(info.collected()).forEach(entry -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(entry.getKey()));
            collected.add(new Slot(stack, stack.getHoverName(), entry.getValue(), "gui.villagerhuts.panel.collected_count"));
        });
        lineY = this.grid(graphics, font, "gui.villagerhuts.details.collected", collected, x, lineY, width, mouseInside, mouseX, mouseY);
        graphics.disableScissor();
        this.contentHeight = lineY + (int) this.scroll - factsTop;
    }

    /** One slot of a grid: an icon, what it is, and how many. */
    private record Slot(ItemStack icon, Component name, int count, String countKey) {}

    private static List<Map.Entry<Identifier, Integer>> sorted(Map<Identifier, Integer> counts) {
        List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<Identifier, Integer>comparingByValue().reversed());
        return entries;
    }

    // A heading and a grid of slots with counts (nothing at all if there are none). Returns the y below it.
    private int grid(GuiGraphicsExtractor graphics, Font font, String label, List<Slot> slots, int x, int lineY, int width,
                     boolean mouseInside, int mouseX, int mouseY) {
        if (slots.isEmpty()) {
            return lineY;
        }
        lineY += 3;
        NativeStyle.text(graphics, font, Component.translatable(label).getVisualOrderText(), x, lineY, NativeStyle.LABEL);
        lineY += LINE;
        int columns = Math.max(1, width / 18);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int slotX = x + (i % columns) * 18;
            int slotY = lineY + (i / columns) * 18;
            NativeStyle.slot(graphics, slotX, slotY);
            graphics.item(slot.icon(), slotX + 1, slotY + 1);
            graphics.itemDecorations(font, slot.icon(), slotX + 1, slotY + 1, WorkerList.shortCount(slot.count()));
            if (mouseInside && mouseX >= slotX && mouseX < slotX + 18 && mouseY >= slotY && mouseY < slotY + 18) {
                graphics.setTooltipForNextFrame(List.of(slot.name().getVisualOrderText(),
                        Component.translatable(slot.countKey(), slot.count()).getVisualOrderText()), mouseX, mouseY);
            }
        }
        return lineY + ((slots.size() + columns - 1) / columns) * 18;
    }

    // "Label: value" lines, with the label in the darker label color.
    private List<Component> facts(WorkerInfo info) {
        WorkerJob job = WorkerJob.byId(info.job());
        List<Component> lines = new ArrayList<>();
        lines.add(fact("status", info.status()));
        if (job.isFighter()) {
            // What it fights with first: these change the most, and the page may need scrolling for the rest.
            lines.add(fact("weapon", info.tool().isEmpty() ? Component.translatable("gui.villagerhuts.details.weapon_none")
                    : info.tool().getHoverName()));
            if (info.ammo() >= 0) {
                lines.add(fact("ammo", Component.translatable(info.ammo() > 0 ? "gui.villagerhuts.details.ammo_value"
                        : "gui.villagerhuts.details.ammo_none", info.ammo())));
            }
            lines.add(fact("health", info.health() >= 0
                    ? Component.translatable("gui.villagerhuts.details.health_value", info.health(), info.maxHealth())
                    : Component.translatable("gui.villagerhuts.details.unknown_far_away")));
            lines.add(fact("level", Component.translatable("gui.villagerhuts.details.level_fighter", WorkerEntity.rankName(info.rank()),
                    WorkerEntity.fighterHealth(info.rank()), WorkerEntity.fighterDamageBonus(info.rank()))));
        } else {
            lines.add(fact("level", Component.translatable("gui.villagerhuts.details.level_value", WorkerEntity.rankName(info.rank()),
                    info.harvestedToday(), info.dailyLimit())));
        }
        WorkerEntity.Upgrade upgrade = WorkerEntity.upgradeFrom(info.rank());
        if (upgrade != null) {
            lines.add(fact("upgrade", Component.translatable("gui.villagerhuts.details.upgrade_value",
                    WorkerEntity.rankName(info.rank() + 1), upgrade.describe())));
        }
        String place = job.isFighter() ? "post" : "farm";
        if (info.farm().isPresent()) {
            BoundingBox area = info.farm().get();
            lines.add(fact(place, Component.translatable("gui.villagerhuts.details." + place + "_assigned",
                    area.getXSpan(), area.getZSpan(), area.getCenter().getX(), area.getCenter().getZ())));
        } else {
            lines.add(fact(place, Component.translatable("gui.villagerhuts.details." + place + "_none")));
        }
        lines.add(fact("chest", info.storage().map(WorkerDetails::position)
                .orElseGet(() -> Component.translatable("gui.villagerhuts.details.chest_none"))));
        lines.add(fact("bed", info.bed().map(WorkerDetails::position)
                .orElseGet(() -> Component.translatable("gui.villagerhuts.details.bed_none"))));
        if (job.isFighter()) {
            int defeated = info.defeated().values().stream().mapToInt(Integer::intValue).sum();
            lines.add(fact("defeated_total", Component.translatable("gui.villagerhuts.details.defeated_value", defeated)));
        }
        lines.add(info.loaded()
                ? fact("carrying", Component.translatable("gui.villagerhuts.details.carrying_value", info.carried()))
                : fact("carrying", Component.translatable("gui.villagerhuts.details.unknown_far_away")));
        long days = info.employedTicks() / 24000L;
        lines.add(fact("worked", days == 0 ? Component.translatable("gui.villagerhuts.details.days_less")
                : days == 1 ? Component.translatable("gui.villagerhuts.details.days_one")
                : Component.translatable("gui.villagerhuts.details.days", days)));
        if (!info.yours()) {
            lines.add(fact("owner", Component.literal(info.owner().name())));
        }
        return lines;
    }

    private static Component position(BlockPos pos) {
        return Component.translatable("gui.villagerhuts.details.position", pos.getX(), pos.getY(), pos.getZ());
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

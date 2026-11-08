package io.github.nenwam.villagerhuts.client;

import java.util.List;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerAssigning;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.network.AssignModePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * While you're giving a worker its farm or chest (you right-clicked it; see entity/WorkerAssigning):
 *  - a hint above the hotbar says which worker, and how to cancel;
 *  - looking at a chest or barrel outlines it in yellow (red if it's too far from the worker's farm);
 *  - looking at anything else shows the farm area the worker would get there: a yellow box around it
 *    with the farmland it covers outlined in green (red if there's no farmland, or it's too far from
 *    the worker).
 * While you're making it a new farm (you shift + right-clicked it), looking at the ground shows the farm it
 * would make there (see FarmPlan) in different colors: a cyan box, the land it would till outlined in orange,
 * where it would put water in blue, and farmland that's already there in green.
 * Both use the same checks and limits as the server, so the preview matches.
 * Right-clicking a block meanwhile doesn't do its normal thing (no chest opening): the server assigns it.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class AssignPreview {
    private static final int COLOR_AREA = ARGB.color(220, 255, 220, 50);
    private static final int COLOR_FARMLAND = ARGB.color(200, 90, 255, 90);
    private static final int COLOR_PROBLEM = ARGB.color(220, 255, 70, 70);
    private static final int COLOR_NEW_AREA = ARGB.color(220, 80, 220, 255);
    private static final int COLOR_TILL = ARGB.color(220, 255, 150, 40);
    private static final int COLOR_WATER = ARGB.color(230, 50, 110, 255);
    private static final int HINT_COLOR = 0xFFFFFFFF;
    private static final int HINT_SOFT_COLOR = 0xFFC0C0C0;
    private static final int REFRESH_TICKS = 10;
    private static final Identifier HINT_LAYER = Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "assign_hint");

    private static AssignModePayload.@Nullable Selected selected;
    private static int ticksLeft;
    private static @Nullable Preview cached;

    /** An outline of some blocks, in a color. */
    private record Cells(VoxelShape shape, int color) {}

    private record Preview(BlockPos aimedAt, AssignModePayload.Selected selected, long gameTime, BlockPos origin,
                           VoxelShape area, int areaColor, List<Cells> cells) {}

    /** The blocks a chest or barrel takes up: both halves of a double chest. */
    static BoundingBox storageBox(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return BoundingBox.fromCorners(pos, pos.relative(ChestBlock.getConnectedDirection(state)));
        }
        return new BoundingBox(pos);
    }

    // ------------------------------------------------------------------ selection

    @SubscribeEvent
    static void registerPayloadHandler(RegisterClientPayloadHandlersEvent event) {
        event.register(AssignModePayload.TYPE, (payload, context) -> {
            selected = payload.selected().orElse(null);
            ticksLeft = selected == null ? 0 : selected.ticks();
            cached = null;
        });
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (selected != null && (--ticksLeft <= 0 || Minecraft.getInstance().player == null)) {
            selected = null; // ran out (the server times it out too)
            cached = null;
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        selected = null;
        cached = null;
    }

    // The click still goes to the server, which assigns the block; here it just mustn't open the chest.
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (selected != null && event.getLevel().isClientSide()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ hint

    @SubscribeEvent
    static void registerHint(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, HINT_LAYER, AssignPreview::drawHint);
    }

    private static void drawHint(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        AssignModePayload.Selected now = selected;
        Minecraft minecraft = Minecraft.getInstance();
        if (now == null || minecraft.gui.hud.isHidden()) { // F1 hides it, like the rest of the HUD
            return;
        }
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 92; // above the action bar messages (which say whether it worked)
        String hint = now.newFarm() ? "gui.villagerhuts.assign.new_farm_hint" : "gui.villagerhuts.assign.hint";
        graphics.centeredText(minecraft.font, Component.translatable(hint, now.name()), x, y, HINT_COLOR);
        String cancel = now.newFarm() ? "gui.villagerhuts.assign.new_farm_cancel" : "gui.villagerhuts.assign.cancel";
        graphics.centeredText(minecraft.font, Component.translatable(cancel, now.name()), x, y + 11, HINT_SOFT_COLOR);
    }

    // ------------------------------------------------------------------ preview

    @SubscribeEvent
    static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        AssignModePayload.Selected now = selected;
        if (now == null) {
            return;
        }
        BlockPos aimedAt = event.getBlockPos();
        long time = event.getLevel().getGameTime();
        Preview preview = cached;
        if (preview == null || !preview.aimedAt().equals(aimedAt) || preview.selected() != now || time - preview.gameTime() >= REFRESH_TICKS) {
            preview = build(event.getLevel(), aimedAt, now, time);
            cached = preview;
        }

        Preview shown = preview;
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(shown.origin().getX() - camera.x, shown.origin().getY() - camera.y, shown.origin().getZ() - camera.z);
            collector.submitShapeOutline(poseStack, shown.area(), RenderTypes.linesTranslucent(), shown.areaColor(), lineWidth, false);
            for (Cells cells : shown.cells()) {
                collector.submitShapeOutline(poseStack, cells.shape(), RenderTypes.linesTranslucent(), cells.color(), lineWidth, false);
            }
            poseStack.popPose();
            return false;
        });
    }

    private static Preview build(ClientLevel level, BlockPos aimedAt, AssignModePayload.Selected now, long time) {
        // Distances are measured from the worker; if it isn't loaded here, it's much too far anyway.
        Entity worker = level.getEntity(now.worker());
        BlockPos workerPos = worker != null ? worker.blockPosition() : null;

        if (now.newFarm()) {
            FarmPlan plan = FarmPlan.find(level, aimedAt, now.newFarmSize());
            BoundingBox box = plan.box();
            if (box == null) {
                return nothingHere(aimedAt, now, time, plan.window());
            }
            BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
            boolean ok = workerPos != null && WorkerAssigning.isWithin(workerPos, box.getCenter(), now.maxDistance());
            return new Preview(aimedAt, now, time, origin, outline(box), ok ? COLOR_NEW_AREA : COLOR_PROBLEM, List.of(
                    new Cells(mergedCells(plan.till(), origin), COLOR_TILL),
                    new Cells(mergedCells(plan.water(), origin), COLOR_WATER),
                    new Cells(mergedCells(plan.farmland(), origin), COLOR_FARMLAND)));
        }

        if (WorkerAssigning.isStorage(level, aimedAt)) {
            BoundingBox box = storageBox(level, aimedAt);
            boolean ok = workerPos != null
                    && WorkerAssigning.isWithin(WorkerAssigning.chestAnchor(now.farm().orElse(null), workerPos), aimedAt, now.maxDistance());
            return new Preview(aimedAt, now, time, new BlockPos(box.minX(), box.minY(), box.minZ()), outline(box),
                    ok ? COLOR_AREA : COLOR_PROBLEM, List.of());
        }

        FarmArea area = FarmArea.find(level, aimedAt, now.maxFarmSize());
        BoundingBox box = area.box();
        if (box == null) {
            return nothingHere(aimedAt, now, time, area.window());
        }
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        boolean ok = workerPos != null && area.isNear(workerPos, now.maxDistance());
        return new Preview(aimedAt, now, time, origin, outline(box), ok ? COLOR_AREA : COLOR_PROBLEM,
                List.of(new Cells(mergedCells(area.farmland(), origin), COLOR_FARMLAND)));
    }

    // Nothing to farm: show the whole searched square, flat at ground level, in red.
    private static Preview nothingHere(BlockPos aimedAt, AssignModePayload.Selected now, long time, BoundingBox window) {
        int groundY = window.minY() + FarmArea.SEARCH_HEIGHT;
        BlockPos origin = new BlockPos(window.minX(), groundY, window.minZ());
        return new Preview(aimedAt, now, time, origin, Shapes.box(0, 0, 0, window.getXSpan(), 1, window.getZSpan()), COLOR_PROBLEM, List.of());
    }

    private static VoxelShape outline(BoundingBox box) {
        return Shapes.box(0, 0, 0, box.getXSpan(), box.getYSpan(), box.getZSpan());
    }

    // One shape for all the farmland blocks, so only the field's outer edges are drawn.
    private static VoxelShape mergedCells(List<BlockPos> cells, BlockPos origin) {
        VoxelShape shape = Shapes.empty();
        for (BlockPos cell : cells) {
            int x = cell.getX() - origin.getX();
            int y = cell.getY() - origin.getY();
            int z = cell.getZ() - origin.getZ();
            shape = Shapes.joinUnoptimized(shape, Shapes.box(x, y, z, x + 1, y + 1, z + 1), BooleanOp.OR);
        }
        return shape.optimize();
    }

    private AssignPreview() {}
}

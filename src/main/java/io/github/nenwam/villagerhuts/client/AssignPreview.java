package io.github.nenwam.villagerhuts.client;

import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerAssigning;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.network.AssignModePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
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
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * While you're giving a worker its farm, chest or bed (you right-clicked it; see entity/WorkerAssigning):
 *  - a hint above the hotbar says which worker, and how to cancel (drawHint, registered in VillagerHutsClient);
 *  - looking at a chest, barrel or bed outlines it in yellow (red if it's too far from the worker's farm);
 *  - looking at anything else shows the farm area the worker would get there: a yellow box around it
 *    with the farmland it covers outlined in green (red if there's no farmland, or it's too far from
 *    the worker).
 * While you're making it a new farm (you shift + right-clicked it), looking at the ground shows the farm it
 * would make there (see FarmPlan) in different colors: a cyan box, the land it would till outlined in orange,
 * where it would put water in blue, and farmland that's already there in green.
 * For a fighter, looking at anything that isn't a chest or a bed shows the area it would guard from a post there:
 * a red-orange box, with the spot it would stand on outlined (red if it's too far from the fighter).
 * All of these use the same checks and limits as the server, so the preview matches.
 * Right-clicking a block meanwhile doesn't do its normal thing (no chest opening): the server assigns it.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class AssignPreview {
    private static final int COLOR_AREA = FastColor.ARGB32.color(220, 255, 220, 50);
    private static final int COLOR_FARMLAND = FastColor.ARGB32.color(200, 90, 255, 90);
    private static final int COLOR_PROBLEM = FastColor.ARGB32.color(220, 255, 70, 70);
    private static final int COLOR_NEW_AREA = FastColor.ARGB32.color(220, 80, 220, 255);
    private static final int COLOR_TILL = FastColor.ARGB32.color(220, 255, 150, 40);
    private static final int COLOR_WATER = FastColor.ARGB32.color(230, 50, 110, 255);
    private static final int COLOR_GUARD = FastColor.ARGB32.color(220, 255, 110, 50);
    private static final int HINT_COLOR = 0xFFFFFFFF;
    private static final int HINT_SOFT_COLOR = 0xFFC0C0C0;
    private static final int REFRESH_TICKS = 10;
    public static final ResourceLocation HINT_LAYER = ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "assign_hint");

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

    /** The blocks a bed takes up: both halves (just the one block if it's been broken). */
    static BoundingBox bedBox(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) {
            return BoundingBox.fromCorners(pos, pos.relative(BedBlock.getConnectedDirection(state)));
        }
        return new BoundingBox(pos);
    }

    /**
     * Outlines a shape's edges in a color (ARGB), offset by (x, y, z) from the pose. Like vanilla's block outline, for
     * a lines vertex consumer.
     */
    static void drawShape(PoseStack poseStack, VertexConsumer lines, VoxelShape shape, double x, double y, double z, int color) {
        float alpha = FastColor.ARGB32.alpha(color) / 255.0F;
        float red = FastColor.ARGB32.red(color) / 255.0F;
        float green = FastColor.ARGB32.green(color) / 255.0F;
        float blue = FastColor.ARGB32.blue(color) / 255.0F;
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length;
            dy /= length;
            dz /= length;
            lines.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z)).setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz);
            lines.addVertex(pose, (float) (x2 + x), (float) (y2 + y), (float) (z2 + z)).setColor(red, green, blue, alpha).setNormal(pose, dx, dy, dz);
        });
    }

    /** The entity with this id, if it's loaded on this side (1.21.1's client level has no lookup by id for it). */
    static @Nullable Entity findEntity(ClientLevel level, UUID id) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ selection

    /** The server says which worker you're giving something to now, or that you've finished (see ModNetwork). */
    public static void receive(AssignModePayload payload) {
        selected = payload.selected().orElse(null);
        ticksLeft = selected == null ? 0 : selected.ticks();
        cached = null;
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

    /** The hint above the hotbar (a GUI layer above the action bar, registered in VillagerHutsClient). */
    public static void drawHint(GuiGraphics graphics, DeltaTracker deltaTracker) {
        AssignModePayload.Selected now = selected;
        Minecraft minecraft = Minecraft.getInstance();
        if (now == null || minecraft.options.hideGui) { // F1 hides it, like the rest of the HUD
            return;
        }
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 92; // above the action bar messages (which say whether it worked)
        String hint = now.newFarm() ? "gui.villagerhuts.assign.new_farm_hint"
                : now.fighter() ? "gui.villagerhuts.assign.fighter_hint" : "gui.villagerhuts.assign.hint";
        graphics.drawCenteredString(minecraft.font, Component.translatable(hint, now.name()), x, y, HINT_COLOR);
        String cancel = now.newFarm() ? "gui.villagerhuts.assign.new_farm_cancel" : "gui.villagerhuts.assign.cancel";
        graphics.drawCenteredString(minecraft.font, Component.translatable(cancel, now.name()), x, y + 11, HINT_SOFT_COLOR);
    }

    // ------------------------------------------------------------------ preview

    // Drawn along with the block outline (vanilla still draws its own outline too).
    @SubscribeEvent
    static void onBlockHighlight(RenderHighlightEvent.Block event) {
        AssignModePayload.Selected now = selected;
        ClientLevel level = Minecraft.getInstance().level;
        if (now == null || level == null) {
            return;
        }
        BlockPos aimedAt = event.getTarget().getBlockPos();
        long time = level.getGameTime();
        Preview preview = cached;
        if (preview == null || !preview.aimedAt().equals(aimedAt) || preview.selected() != now || time - preview.gameTime() >= REFRESH_TICKS) {
            preview = build(level, aimedAt, now, time);
            cached = preview;
        }

        Vec3 camera = event.getCamera().getPosition();
        double x = preview.origin().getX() - camera.x;
        double y = preview.origin().getY() - camera.y;
        double z = preview.origin().getZ() - camera.z;
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        drawShape(event.getPoseStack(), lines, preview.area(), x, y, z, preview.areaColor());
        for (Cells cells : preview.cells()) {
            drawShape(event.getPoseStack(), lines, cells.shape(), x, y, z, cells.color());
        }
    }

    private static Preview build(ClientLevel level, BlockPos aimedAt, AssignModePayload.Selected now, long time) {
        // Distances are measured from the worker; if it isn't loaded here, it's much too far anyway.
        Entity worker = findEntity(level, now.worker());
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

        if (WorkerAssigning.isBed(level, aimedAt)) {
            BoundingBox box = bedBox(level, aimedAt);
            boolean ok = workerPos != null && WorkerAssigning.isWithin(WorkerAssigning.chestAnchor(now.farm().orElse(null), workerPos),
                    WorkerAssigning.bedHead(level, aimedAt), now.maxDistance());
            return new Preview(aimedAt, now, time, new BlockPos(box.minX(), box.minY(), box.minZ()), outline(box),
                    ok ? COLOR_AREA : COLOR_PROBLEM, List.of());
        }

        if (WorkerAssigning.isStorage(level, aimedAt)) {
            BoundingBox box = storageBox(level, aimedAt);
            boolean ok = workerPos != null
                    && WorkerAssigning.isWithin(WorkerAssigning.chestAnchor(now.farm().orElse(null), workerPos), aimedAt, now.maxDistance());
            return new Preview(aimedAt, now, time, new BlockPos(box.minX(), box.minY(), box.minZ()), outline(box),
                    ok ? COLOR_AREA : COLOR_PROBLEM, List.of());
        }

        if (now.fighter()) {
            BlockPos post = WorkerAssigning.postAt(level, aimedAt);
            int r = now.guardRadius();
            BoundingBox box = new BoundingBox(post.getX() - r, post.getY() - 2, post.getZ() - r, post.getX() + r, post.getY() + 4, post.getZ() + r);
            BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
            boolean ok = workerPos != null && WorkerAssigning.isWithin(workerPos, post, now.maxDistance());
            return new Preview(aimedAt, now, time, origin, outline(box), ok ? COLOR_GUARD : COLOR_PROBLEM,
                    List.of(new Cells(mergedCells(List.of(post), origin), ok ? COLOR_AREA : COLOR_PROBLEM)));
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

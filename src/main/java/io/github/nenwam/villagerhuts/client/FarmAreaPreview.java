package io.github.nenwam.villagerhuts.client;

import java.util.List;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.item.WorkerLink;
import io.github.nenwam.villagerhuts.registry.ModDataComponents;
import io.github.nenwam.villagerhuts.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.jspecify.annotations.Nullable;

/**
 * While you hold a linked Work Area Marker and look at a block, shows the farm area the worker
 * would get there: a yellow box around it with the farmland it covers outlined in green.
 * Red if there's no farmland, or it's too far from the worker's hut. Uses the same FarmArea
 * logic and limits as the server.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class FarmAreaPreview {
    private static final int COLOR_AREA = ARGB.color(220, 255, 220, 50);
    private static final int COLOR_FARMLAND = ARGB.color(200, 90, 255, 90);
    private static final int COLOR_PROBLEM = ARGB.color(220, 255, 70, 70);
    private static final int REFRESH_TICKS = 10;

    private static @Nullable Preview cached;

    private record Preview(BlockPos aimedAt, WorkerLink link, long gameTime, BlockPos origin,
                           VoxelShape area, @Nullable VoxelShape farmland, boolean ok) {}

    @SubscribeEvent
    static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        WorkerLink link = linkIn(player.getMainHandItem());
        if (link == null) {
            link = linkIn(player.getOffhandItem());
        }
        if (link == null) {
            return;
        }

        BlockPos aimedAt = event.getBlockPos();
        long now = event.getLevel().getGameTime();
        Preview preview = cached;
        if (preview == null || !preview.aimedAt().equals(aimedAt) || !preview.link().equals(link) || now - preview.gameTime() >= REFRESH_TICKS) {
            preview = build(event, aimedAt, link, now);
            cached = preview;
        }

        Preview shown = preview;
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(shown.origin().getX() - camera.x, shown.origin().getY() - camera.y, shown.origin().getZ() - camera.z);
            collector.submitShapeOutline(poseStack, shown.area(), RenderTypes.linesTranslucent(), shown.ok() ? COLOR_AREA : COLOR_PROBLEM, lineWidth, false);
            if (shown.farmland() != null) {
                collector.submitShapeOutline(poseStack, shown.farmland(), RenderTypes.linesTranslucent(), COLOR_FARMLAND, lineWidth, false);
            }
            poseStack.popPose();
            return false;
        });
    }

    private static Preview build(ExtractBlockOutlineRenderStateEvent event, BlockPos aimedAt, WorkerLink link, long now) {
        FarmArea area = FarmArea.find(event.getLevel(), aimedAt, link.maxFarmSize());
        BoundingBox box = area.box();
        if (box == null) {
            // No farmland: show the whole searched square, flat at ground level, in red.
            BoundingBox window = area.window();
            int groundY = window.minY() + FarmArea.SEARCH_HEIGHT;
            BlockPos origin = new BlockPos(window.minX(), groundY, window.minZ());
            return new Preview(aimedAt, link, now, origin,
                    Shapes.box(0, 0, 0, window.getXSpan(), 1, window.getZSpan()), null, false);
        }
        BlockPos origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        VoxelShape outline = Shapes.box(0, 0, 0, box.getXSpan(), box.getYSpan(), box.getZSpan());
        return new Preview(aimedAt, link, now, origin, outline,
                mergedCells(area.farmland(), origin), area.isNear(link.hutCenter(), link.maxFarmDistance()));
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

    private static @Nullable WorkerLink linkIn(ItemStack stack) {
        return stack.is(ModItems.WORK_AREA_MARKER.get()) ? stack.get(ModDataComponents.WORKER_LINK.get()) : null;
    }

    private FarmAreaPreview() {}
}

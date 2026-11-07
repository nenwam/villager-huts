package io.github.nenwam.villagerhuts.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.hut.HutPlacement;
import io.github.nenwam.villagerhuts.item.HutItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.jspecify.annotations.Nullable;

/**
 * While you hold a hut item and look at a block, draws a wireframe of the hut where it would be
 * built: yellow if it fits, red if something is in the way. Uses the same placement math as the
 * server (HutPlacement), so the preview matches what gets built.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID, value = Dist.CLIENT)
public final class HutPreview {
    private static final int COLOR_FITS = ARGB.color(220, 255, 220, 50);
    private static final int COLOR_BLOCKED = ARGB.color(220, 255, 70, 70);
    private static final Set<String> EMPTY_BLOCKS = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
            "minecraft:structure_void", "minecraft:structure_block");

    private static final Map<Identifier, Optional<HutShape>> SHAPES = new HashMap<>();

    @SubscribeEvent
    static void onExtractBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HutItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof HutItem hutItem)) {
            return;
        }
        HutShape shape = SHAPES.computeIfAbsent(hutItem.structureId(), id -> Optional.ofNullable(HutShape.load(id))).orElse(null);
        if (shape == null) {
            return;
        }

        // Same rules as HutItem.useOn: tall grass/flowers mean "build on the ground below".
        BlockPos clicked = event.getBlockPos();
        BlockPos ground = event.getLevel().getBlockState(clicked).canBeReplaced() ? clicked.below() : clicked;
        HutPlacement placement = HutPlacement.compute(shape.size(), ground, player.getDirection(), HutItem.getRotation(stack));
        BoundingBox bounds = placement.bounds();
        VoxelShape outline = shape.outline(placement.rotation());
        boolean fits = player.mayBuild() && HutPlacement.hasRoom(event.getLevel(), bounds)
                && !HutPlacement.isPlayerInTheWay(event.getLevel(), bounds);
        int color = fits ? COLOR_FITS : COLOR_BLOCKED;
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();

        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(bounds.minX() - camera.x, bounds.minY() - camera.y, bounds.minZ() - camera.z);
            collector.submitShapeOutline(poseStack, outline, RenderTypes.linesTranslucent(), color, lineWidth, false);
            poseStack.popPose();
            return false; // keep the normal outline on the targeted block too
        });
    }

    /** The solid blocks of a hut design, and its outline for each rotation (built on first use). */
    private record HutShape(Vec3i size, List<BlockPos> solids, Map<Rotation, VoxelShape> outlines) {

        VoxelShape outline(Rotation rotation) {
            return this.outlines.computeIfAbsent(rotation, this::buildOutline);
        }

        // Merges the solid blocks into one shape, so only its outer edges get drawn.
        private VoxelShape buildOutline(Rotation rotation) {
            BoundingBox local = HutPlacement.localBounds(this.size, rotation);
            boolean[][][] filled = new boolean[local.getXSpan()][local.getYSpan()][local.getZSpan()];
            for (BlockPos pos : this.solids) {
                BlockPos p = StructureTemplate.transform(pos, Mirror.NONE, rotation, BlockPos.ZERO);
                filled[p.getX() - local.minX()][p.getY() - local.minY()][p.getZ() - local.minZ()] = true;
            }
            VoxelShape shape = Shapes.empty();
            for (int y = 0; y < filled[0].length; y++) {
                for (int z = 0; z < filled[0][0].length; z++) {
                    int x = 0;
                    while (x < filled.length) { // one box per run of blocks along X keeps this quick
                        if (!filled[x][y][z]) {
                            x++;
                            continue;
                        }
                        int start = x;
                        while (x < filled.length && filled[x][y][z]) {
                            x++;
                        }
                        shape = Shapes.or(shape, Shapes.box(start, y, z, x, y + 1, z + 1));
                    }
                }
            }
            return shape;
        }

        // Reads the design straight from the mod's files (the client doesn't have the server's copy).
        static @Nullable HutShape load(Identifier id) {
            String path = "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
            var modFile = ModList.get().getModFileById(VillagerHuts.MODID);
            if (modFile == null) {
                return null;
            }
            try (InputStream in = modFile.getFile().getContents().openFile(path)) {
                if (in == null) {
                    VillagerHuts.LOGGER.warn("No hut design at {} for the placement preview", path);
                    return null;
                }
                CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                ListTag sizeTag = tag.getListOrEmpty("size");
                Vec3i size = new Vec3i(sizeTag.getIntOr(0, 0), sizeTag.getIntOr(1, 0), sizeTag.getIntOr(2, 0));

                ListTag palette = tag.getListOrEmpty("palette");
                boolean[] solid = new boolean[palette.size()];
                for (int i = 0; i < palette.size(); i++) {
                    CompoundTag entry = palette.getCompoundOrEmpty(i);
                    String block = entry.getStringOr("id", entry.getStringOr("Name", "minecraft:air"));
                    solid[i] = !EMPTY_BLOCKS.contains(block);
                }

                List<BlockPos> solids = new ArrayList<>();
                ListTag blocks = tag.getListOrEmpty("blocks");
                for (int i = 0; i < blocks.size(); i++) {
                    CompoundTag block = blocks.getCompoundOrEmpty(i);
                    int state = block.getIntOr("state", -1);
                    ListTag pos = block.getListOrEmpty("pos");
                    if (state >= 0 && state < solid.length && solid[state]) {
                        solids.add(new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0)));
                    }
                }
                return new HutShape(size, solids, new EnumMap<>(Rotation.class));
            } catch (IOException e) {
                VillagerHuts.LOGGER.warn("Couldn't read hut design {} for the placement preview", path, e);
                return null;
            }
        }
    }

    private HutPreview() {}
}

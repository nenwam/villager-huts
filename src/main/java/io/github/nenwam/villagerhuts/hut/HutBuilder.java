package io.github.nenwam.villagerhuts.hut;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Places a hut structure in the world and spawns its worker.
 *
 * Hut designs are normal structure files (build one in-game, save it with a Structure Block).
 * Design rules:
 *  - The front of the design is its north edge (low Z): that side faces the player.
 *  - Layer 0 (the bottom layer) replaces the ground. Everything above must be clear to build.
 *  - Put a Structure Block in "Data" mode with the text "worker" indoors where the worker should
 *    spawn. It's removed when the hut is built, and the worker shelters there at night.
 */
public final class HutBuilder {
    public static final String WORKER_MARKER = "worker";

    public enum Result { BUILT, NO_SPACE, PLAYER_IN_THE_WAY, NOT_ALLOWED, MISSING_STRUCTURE }

    public static Result build(ServerLevel level, Identifier structureId, BlockPos ground, Direction facing, int quarterTurns, @Nullable Player player) {
        StructureTemplate template = level.getStructureTemplateManager().get(structureId).orElse(null);
        if (template == null || template.getSize().equals(Vec3i.ZERO)) {
            VillagerHuts.LOGGER.error("Hut structure {} not found. Expected data/{}/structure/{}.nbt",
                    structureId, structureId.getNamespace(), structureId.getPath());
            return Result.MISSING_STRUCTURE;
        }

        HutPlacement placement = HutPlacement.compute(template.getSize(), ground, facing, quarterTurns);
        BoundingBox box = placement.bounds();
        if (player != null && !canBuildAt(level, player, box)) {
            return Result.NOT_ALLOWED;
        }
        if (!HutPlacement.hasRoom(level, box)) {
            return Result.NO_SPACE;
        }
        if (HutPlacement.isPlayerInTheWay(level, box)) {
            return Result.PLAYER_IN_THE_WAY;
        }

        StructurePlaceSettings settings = placement.settings();
        template.placeInWorld(level, placement.origin(), placement.origin(), settings, level.getRandom(), Block.UPDATE_ALL);

        BlockPos workerSpot = null;
        for (StructureTemplate.StructureBlockInfo marker : template.filterBlocks(placement.origin(), settings, Blocks.STRUCTURE_BLOCK)) {
            level.setBlock(marker.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            String name = marker.nbt() == null ? "" : marker.nbt().getStringOr("metadata", "");
            if (WORKER_MARKER.equals(name)) {
                workerSpot = marker.pos();
            }
        }

        BoundingBox bounds = placement.bounds();
        BlockPos spawnPos = workerSpot;
        if (spawnPos == null) {
            VillagerHuts.LOGGER.warn("Hut structure {} has no \"{}\" marker; spawning worker in the middle", structureId, WORKER_MARKER);
            spawnPos = new BlockPos(bounds.getCenter().getX(), bounds.minY() + 1, bounds.getCenter().getZ());
        }

        spawnWorker(level, spawnPos, bounds, workerSpot, facing, player);
        level.playSound(null, ground, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        return Result.BUILT;
    }

    // Respects adventure mode, spawn protection and the world border, at every corner of the hut.
    private static boolean canBuildAt(ServerLevel level, Player player, BoundingBox box) {
        if (!player.mayBuild()) {
            return false;
        }
        for (BlockPos corner : new BlockPos[] {
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.minY(), box.minZ()),
                new BlockPos(box.minX(), box.minY(), box.maxZ()), new BlockPos(box.maxX(), box.minY(), box.maxZ()),
                box.getCenter()}) {
            if (!level.mayInteract(player, corner)) {
                return false;
            }
        }
        return true;
    }

    private static void spawnWorker(ServerLevel level, BlockPos pos, BoundingBox hutBounds, @Nullable BlockPos shelter, Direction facing, @Nullable Player player) {
        WorkerEntity worker = ModEntities.WORKER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (worker == null) {
            return;
        }
        // Face back toward the player who built the hut.
        worker.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing.getOpposite().toYRot(), 0.0F);
        worker.assignHut(hutBounds, player, shelter);
        level.addFreshEntity(worker);
    }

    private HutBuilder() {}
}

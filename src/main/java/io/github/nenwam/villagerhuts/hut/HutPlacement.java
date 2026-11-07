package io.github.nenwam.villagerhuts.hut;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * Where a hut goes. Shared by the server (building it) and the client (drawing the preview),
 * so the preview always matches what gets built.
 *
 * The footprint starts at the clicked block and extends away from the player, centered
 * side to side. Unrotated, the design's front (north edge) faces the player; each extra
 * quarter turn rotates the design 90 degrees clockwise within that spot.
 */
public record HutPlacement(BlockPos origin, Rotation rotation, BoundingBox bounds) {

    public static HutPlacement compute(Vec3i size, BlockPos ground, Direction facing, int quarterTurns) {
        Rotation rotation = rotationFor(facing).getRotated(Rotation.values()[quarterTurns & 3]);
        BoundingBox local = localBounds(size, rotation);
        int xSpan = local.getXSpan();
        int zSpan = local.getZSpan();

        int minX;
        int minZ;
        switch (facing) {
            case NORTH -> {
                minX = ground.getX() - xSpan / 2;
                minZ = ground.getZ() - zSpan + 1;
            }
            case EAST -> {
                minX = ground.getX();
                minZ = ground.getZ() - zSpan / 2;
            }
            case WEST -> {
                minX = ground.getX() - xSpan + 1;
                minZ = ground.getZ() - zSpan / 2;
            }
            default -> { // SOUTH
                minX = ground.getX() - xSpan / 2;
                minZ = ground.getZ();
            }
        }
        BlockPos origin = new BlockPos(minX - local.minX(), ground.getY() - local.minY(), minZ - local.minZ());
        return new HutPlacement(origin, rotation, local.moved(origin.getX(), origin.getY(), origin.getZ()));
    }

    /** Where the design's blocks end up relative to (0,0,0) once rotated. */
    public static BoundingBox localBounds(Vec3i size, Rotation rotation) {
        BlockPos far = new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        return BoundingBox.fromCorners(
                StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, BlockPos.ZERO),
                StructureTemplate.transform(far, Mirror.NONE, rotation, BlockPos.ZERO));
    }

    public StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setRotation(this.rotation)
                .setRotationPivot(BlockPos.ZERO)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK); // markers are read, not placed
    }

    /**
     * The bottom layer replaces the ground (but never containers or unbreakable blocks);
     * everything above it must be air or something replaceable like grass or flowers.
     */
    public static boolean hasRoom(BlockGetter level, BoundingBox box) {
        if (level.isOutsideBuildHeight(box.minY()) || level.isOutsideBuildHeight(box.maxY())) {
            return false; // would be cut off at the top or bottom of the world
        }
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (pos.getY() == box.minY()) {
                if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
                    return false;
                }
            } else if (!state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** True if a player is standing where the hut's walls would go (they'd be built into them). */
    public static boolean isPlayerInTheWay(Level level, BoundingBox box) {
        AABB aboveGround = new AABB(box.minX(), box.minY() + 1, box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        return !level.getEntitiesOfClass(Player.class, aboveGround, player -> !player.isSpectator()).isEmpty();
    }

    // Designs face south (+Z) when unrotated; turn them to match the player's facing.
    private static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}

package io.github.nenwam.villagerhuts.item;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.registry.ModEntities;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

/**
 * Hires a farmer: use it on the ground and a worker appears there, holding an iron hoe (the one from
 * the recipe; you get it back if you take it from them), with a look and a name of its own. It belongs
 * to you. Give it a farm, a chest and a bed by right-clicking it, then the farm, chest or bed (see
 * entity/WorkerAssigning).
 */
public class FarmerItem extends Item {
    public FarmerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return hireAt(context, FarmerItem::spawnFarmer);
    }

    /** A new farmer with an iron hoe, standing at pos and facing the player who placed it, named differently from their other workers. */
    public static @Nullable WorkerEntity spawnFarmer(ServerLevel level, BlockPos pos, @Nullable Player owner) {
        return spawnWorker(level, pos, owner, WorkerJob.FARMER, new ItemStack(Items.IRON_HOE));
    }

    /**
     * A new worker with this job (and tool, if any), standing at pos and facing the player who placed it. It belongs to
     * them, follows them until it's given work, and gets a look and a name, different from their other workers' names.
     */
    public static @Nullable WorkerEntity spawnWorker(ServerLevel level, BlockPos pos, @Nullable Player owner, WorkerJob job, ItemStack tool) {
        WorkerEntity worker = ModEntities.WORKER.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (worker == null) {
            return null;
        }
        float facing = owner == null ? 0.0F : owner.getYRot() + 180.0F;
        worker.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing, 0.0F);
        worker.setYHeadRot(facing);
        if (!tool.isEmpty()) {
            worker.setItemSlot(EquipmentSlot.MAINHAND, tool);
            worker.setGuaranteedDrop(EquipmentSlot.MAINHAND); // you get the tool back if the worker dies
        }
        worker.setJob(job);
        worker.setHealth(worker.getMaxHealth());
        worker.hire(owner); // follows them until it has work
        Set<String> taken = new HashSet<>();
        if (owner != null) {
            WorkerRoster.get(level.getServer()).forPlayer(owner.getUUID())
                    .forEach(row -> row.getValue().customName().ifPresent(taken::add));
        }
        worker.giveIdentity(taken);
        level.addFreshEntity(worker);
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return worker;
    }

    /** Spawns a worker where the item's used, like a spawn egg (shared with the Fighter item). */
    static InteractionResult hireAt(UseOnContext context, SpawnWorker spawn) {
        // Spawning happens on the server; the client just plays the arm swing.
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        // Like a spawn egg: on top of the clicked face, or in place of tall grass and flowers.
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());
        if (player != null && (!player.mayBuild() || !level.mayInteract(player, pos))) {
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.not_allowed"));
            return InteractionResult.FAIL;
        }
        if (spawn.spawn(level, pos, player) == null) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    interface SpawnWorker {
        @Nullable WorkerEntity spawn(ServerLevel level, BlockPos pos, @Nullable Player owner);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.villagerhuts.farmer.tooltip").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.villagerhuts.farmer.tooltip.assign").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.villagerhuts.farmer.tooltip.upgrade").withStyle(ChatFormatting.GRAY));
    }
}

package io.github.nenwam.villagerhuts.item;

import java.util.function.Consumer;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.registry.ModEntities;
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
 * the recipe; you get it back if you take it from them). It belongs to you. Give it a farm and a chest
 * by right-clicking it, then the farm or chest (see entity/WorkerAssigning).
 */
public class FarmerItem extends Item {
    public FarmerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
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
        if (spawnFarmer(level, pos, player) == null) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** A new farmer with an iron hoe, standing at pos and facing the player who placed it. */
    public static @Nullable WorkerEntity spawnFarmer(ServerLevel level, BlockPos pos, @Nullable Player owner) {
        WorkerEntity worker = ModEntities.WORKER.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (worker == null) {
            return null;
        }
        float facing = owner == null ? 0.0F : owner.getYRot() + 180.0F;
        worker.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, facing, 0.0F);
        worker.setYHeadRot(facing);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        worker.setGuaranteedDrop(EquipmentSlot.MAINHAND); // you get the hoe back if the farmer dies
        worker.setJob(WorkerJob.FARMER);
        worker.hire(owner); // follows them until it has a farm or chest
        level.addFreshEntity(worker);
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return worker;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.villagerhuts.farmer.tooltip").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.villagerhuts.farmer.tooltip.assign").withStyle(ChatFormatting.GRAY));
    }
}

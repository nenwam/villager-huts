package io.github.nenwam.villagerhuts.item;

import java.util.List;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

/**
 * Hires a fighter: use it on the ground and a Sikh warrior appears there, unarmed, belonging to you and following
 * you. Hand them a melee weapon to make them a warrior, or a ranged one to make them a ranger (see job/Weapons).
 * Give them a post to guard, a chest and a bed by right-clicking them, then the block, chest or bed
 * (see entity/WorkerAssigning).
 */
public class FighterItem extends Item {
    public FighterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return FarmerItem.hireAt(context, FighterItem::spawnFighter);
    }

    /** A new unarmed fighter, standing at pos and facing the player who placed it, named differently from their other workers. */
    public static @Nullable WorkerEntity spawnFighter(ServerLevel level, BlockPos pos, @Nullable Player owner) {
        return FarmerItem.spawnWorker(level, pos, owner, WorkerJob.FIGHTER, ItemStack.EMPTY);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.villagerhuts.fighter.tooltip").withStyle(ChatFormatting.GRAY));
        builder.add(Component.translatable("item.villagerhuts.fighter.tooltip.arm").withStyle(ChatFormatting.GRAY));
        builder.add(Component.translatable("item.villagerhuts.fighter.tooltip.assign").withStyle(ChatFormatting.GRAY));
        builder.add(Component.translatable("item.villagerhuts.fighter.tooltip.upgrade").withStyle(ChatFormatting.GRAY));
    }
}

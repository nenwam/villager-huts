package io.github.nenwam.villagerhuts.item;

import java.util.List;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Raises a worker's level: right-click the worker holding enough of them (see WorkerEntity.upgradeFrom).
 * Diamond carrots make them Apprentice and Journeyman, flawless diamond carrots Expert and Master.
 * The tooltip lists the levels this one is for and how many each takes.
 */
public class UpgradeItem extends Item {
    public UpgradeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.villagerhuts.upgrade.tooltip").withStyle(ChatFormatting.GRAY));
        for (int rank = 1; rank < WorkerEntity.MAX_RANK; rank++) {
            WorkerEntity.Upgrade upgrade = WorkerEntity.upgradeFrom(rank);
            if (upgrade != null && stack.is(upgrade.item().asItem())) {
                builder.add(Component.translatable("item.villagerhuts.upgrade.tooltip.level",
                        WorkerEntity.rankName(rank + 1), upgrade.describe()).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}

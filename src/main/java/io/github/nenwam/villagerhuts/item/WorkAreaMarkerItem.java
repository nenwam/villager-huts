package io.github.nenwam.villagerhuts.item;

import java.util.function.Consumer;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.farm.FarmArea;
import io.github.nenwam.villagerhuts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Assigns a farm to a worker.
 *  1. Right-click one of your workers: links the marker to them.
 *  2. Look at a farm: the client previews the area (see client/FarmAreaPreview).
 *  3. Right-click the farm: the worker farms that area instead of the farm at its hut.
 *  Sneak + right-click the worker: clears its assigned farm.
 */
public class WorkAreaMarkerItem extends Item {
    public WorkAreaMarkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof WorkerEntity worker)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!worker.isOwnedBy(player)) {
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.not_owner"));
            return InteractionResult.FAIL;
        }
        BoundingBox hut = worker.getHutBounds();
        if (hut == null) {
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.marker_no_hut"));
            return InteractionResult.FAIL;
        }

        if (player.isSecondaryUseActive()) {
            worker.clearFarm();
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.farm_cleared"));
        } else {
            // Write to the item actually in hand: in creative mode, vanilla passes this method a copy.
            player.getItemInHand(hand).set(ModDataComponents.WORKER_LINK.get(), new WorkerLink(worker.getUUID(), hut.getCenter(),
                    Config.FARMER_MAX_FARM_SIZE.getAsInt(), Config.FARMER_MAX_FARM_DISTANCE.getAsInt()));
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.marker_linked"));
        }
        player.level().playSound(null, worker.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        WorkerLink link = context.getItemInHand().get(ModDataComponents.WORKER_LINK.get());
        if (link == null) {
            if (player != null && !context.getLevel().isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.villagerhuts.marker_unlinked"));
            }
            return InteractionResult.FAIL;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }

        FarmArea area = FarmArea.find(level, context.getClickedPos(), link.maxFarmSize());
        Component problem = null;
        WorkerEntity worker = level.getEntity(link.workerId()) instanceof WorkerEntity found ? found : null;
        if (area.box() == null) {
            problem = Component.translatable("message.villagerhuts.marker_no_farmland");
        } else if (!area.isNear(link.hutCenter(), link.maxFarmDistance())) {
            problem = Component.translatable("message.villagerhuts.marker_too_far", link.maxFarmDistance());
        } else if (worker == null) {
            problem = Component.translatable("message.villagerhuts.marker_worker_missing");
        } else if (player != null && !worker.isOwnedBy(player)) {
            problem = Component.translatable("message.villagerhuts.not_owner");
        }
        if (problem != null) {
            if (player != null) {
                player.sendOverlayMessage(problem);
            }
            return InteractionResult.FAIL;
        }

        BoundingBox box = area.box();
        worker.assignFarm(box);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.farm_assigned",
                    box.getXSpan(), box.getZSpan(), area.farmland().size()));
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.VILLAGER_WORK_FARMER, SoundSource.NEUTRAL, 1.0F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    // Glows once linked to a worker.
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.WORKER_LINK.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        WorkerLink link = stack.get(ModDataComponents.WORKER_LINK.get());
        if (link == null) {
            builder.accept(Component.translatable("item.villagerhuts.work_area_marker.tooltip.unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos hut = link.hutCenter();
            builder.accept(Component.translatable("item.villagerhuts.work_area_marker.tooltip.linked", hut.getX(), hut.getZ()).withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("item.villagerhuts.work_area_marker.tooltip.use").withStyle(ChatFormatting.GRAY));
        }
    }
}

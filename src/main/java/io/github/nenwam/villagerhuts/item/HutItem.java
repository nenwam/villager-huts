package io.github.nenwam.villagerhuts.item;

import java.util.function.Consumer;

import io.github.nenwam.villagerhuts.hut.HutBuilder;
import io.github.nenwam.villagerhuts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * An item that builds a hut (a saved structure) where you use it, with a worker inside.
 * Each hut type (farmer, and later others) is one of these pointing at a different structure.
 *
 * Right-click a block: build. Sneak + right-click (anywhere): rotate the design 90 degrees.
 * While held, the client draws a wireframe preview (see client/HutPreview).
 */
public class HutItem extends Item {
    private final Identifier structureId;

    public HutItem(Identifier structureId, Properties properties) {
        super(properties);
        this.structureId = structureId;
    }

    public Identifier structureId() {
        return this.structureId;
    }

    /** Extra clockwise quarter turns (0-3) set by sneak + right-clicking. */
    public static int getRotation(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.HUT_ROTATION.get(), 0) & 3;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return this.rotate(level, player, player.getItemInHand(hand));
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            return this.rotate(level, player, context.getItemInHand());
        }
        // Building happens on the server; the client just plays the arm swing.
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        // Clicked tall grass or flowers: build on the ground underneath instead.
        BlockPos ground = level.getBlockState(clicked).canBeReplaced() ? clicked.below() : clicked;
        ItemStack stack = context.getItemInHand();

        HutBuilder.Result result = HutBuilder.build(serverLevel, this.structureId, ground,
                context.getHorizontalDirection(), getRotation(stack), player);
        switch (result) {
            case BUILT -> {
                stack.consume(1, player);
                return InteractionResult.SUCCESS_SERVER;
            }
            case NO_SPACE, PLAYER_IN_THE_WAY, NOT_ALLOWED -> {
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable(switch (result) {
                        case PLAYER_IN_THE_WAY -> "message.villagerhuts.player_in_the_way";
                        case NOT_ALLOWED -> "message.villagerhuts.not_allowed";
                        default -> "message.villagerhuts.no_space";
                    }));
                }
                return InteractionResult.FAIL;
            }
            default -> {
                if (player != null) {
                    player.sendOverlayMessage(Component.translatable("message.villagerhuts.missing_structure", this.structureId.toString()));
                }
                return InteractionResult.FAIL;
            }
        }
    }

    // Only the server changes the rotation; it syncs to the client, which redraws the preview.
    private InteractionResult rotate(Level level, @Nullable Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int turns = (getRotation(stack) + 1) & 3;
        if (turns == 0) {
            stack.remove(ModDataComponents.HUT_ROTATION.get()); // back to default, so it stacks with fresh items
        } else {
            stack.set(ModDataComponents.HUT_ROTATION.get(), turns);
        }
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.rotated", turns * 90));
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3F, 1.2F);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.villagerhuts.hut.tooltip").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.villagerhuts.hut.tooltip.rotate", getRotation(stack) * 90).withStyle(ChatFormatting.GRAY));
    }
}

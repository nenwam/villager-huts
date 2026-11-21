package io.github.nenwam.villagerhuts.mixin;

import io.github.nenwam.villagerhuts.entity.WorkerCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every drop goes through here: a fighter's foe's drops go into the fighter's pockets first (WorkerCombat.pocketDrop). */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void villagerhuts$pocketDrop(ServerLevel level, ItemStack stack, Vec3 offset, CallbackInfoReturnable<ItemEntity> result) {
        ItemStack rest = WorkerCombat.pocketDrop((Entity) (Object) this, stack);
        if (rest.isEmpty()) {
            result.setReturnValue(null); // all in the fighter's pockets
        } else if (rest.getCount() != stack.getCount()) {
            stack.setCount(rest.getCount()); // the rest drops
        }
    }
}

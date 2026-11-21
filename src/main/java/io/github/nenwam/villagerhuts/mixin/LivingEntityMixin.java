package io.github.nenwam.villagerhuts.mixin;

import io.github.nenwam.villagerhuts.entity.WorkerCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells WorkerCombat when a mob starts and stops dropping its loot, so a fighter that killed it can take the drops. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void villagerhuts$startDrops(ServerLevel level, DamageSource source, CallbackInfo info) {
        WorkerCombat.startDrops((LivingEntity) (Object) this, source);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void villagerhuts$endDrops(ServerLevel level, DamageSource source, CallbackInfo info) {
        WorkerCombat.endDrops((LivingEntity) (Object) this);
    }
}

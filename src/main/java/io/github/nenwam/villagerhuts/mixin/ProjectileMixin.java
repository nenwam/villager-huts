package io.github.nenwam.villagerhuts.mixin;

import io.github.nenwam.villagerhuts.entity.WorkerCombat;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A fighter's arrow (or trident) flies on through anything that isn't a foe (WorkerCombat.passesThrough). */
@Mixin(Projectile.class)
abstract class ProjectileMixin {
    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void villagerhuts$passThroughFriends(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> result) {
        if (WorkerCombat.passesThrough((Projectile) (Object) this, hit)) {
            result.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}

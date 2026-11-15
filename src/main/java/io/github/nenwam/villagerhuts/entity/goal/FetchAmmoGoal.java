package io.github.nenwam.villagerhuts.entity.goal;

import java.util.function.Predicate;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.Weapons;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Ranger: running low on ammo (arrows, or fireworks for a crossbow; tipped and spectral arrows count)? Walk to
 * its chest and take some, up to a stack. Like a farmer's seeds: put arrows in a ranger's chest to keep it shooting.
 */
public class FetchAmmoGoal extends StorageTripGoal {
    private static final int LOW = 16;
    private static final int MAX_AMMO = 64;
    private static final int CHECK_TICKS = 100;

    public FetchAmmoGoal(WorkerEntity worker, double speed) {
        super(worker, speed);
    }

    @Override
    public Component describeActivity() {
        return Component.translatable("activity.villagerhuts.fetching_ammo");
    }

    @Override
    protected boolean needsTrip() {
        return this.worker.isRanger() && Weapons.usesAmmo(this.worker.getMainHandItem())
                && (this.worker.getTarget() == null || !this.worker.hasAmmo())
                && this.worker.getAmmoCount() < LOW && this.worker.hasFreeSlot();
    }

    @Override
    protected boolean isUseful(Container container) {
        Predicate<ItemStack> ammo = Weapons.ammoFor(this.worker.getMainHandItem());
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (ammo.test(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected int useStorage(Container container) {
        int wanted = MAX_AMMO - this.worker.getAmmoCount();
        return this.take(container, Weapons.ammoFor(this.worker.getMainHandItem()), wanted) > 0 ? 20 : CHECK_TICKS;
    }

    @Override
    protected int retryTicks() {
        return CHECK_TICKS;
    }
}

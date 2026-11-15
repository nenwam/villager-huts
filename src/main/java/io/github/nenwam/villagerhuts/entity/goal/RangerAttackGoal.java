package io.github.nenwam.villagerhuts.entity.goal;

import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.job.Weapons;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ranger: shoot its target from a distance. It closes in until it's within range and can see it, backs off if the
 * target gets too close, and shoots as often as its weapon and level allow (WorkerEntity.rangedInterval):
 *  - a bow (or any other ranged weapon that isn't a crossbow or a trident) draws and shoots an arrow from its pockets;
 *  - a crossbow is loaded first (taking as long as it does for you; Quick Charge helps), then fired, so
 *    Multishot and Piercing work, and it can fire fireworks;
 *  - a trident is thrown (a copy of it: the ranger keeps its own), so it needs no ammo.
 * Out of ammo, it stops (FetchAmmoGoal gets more from its chest).
 */
public class RangerAttackGoal extends Goal implements DescribesActivity {
    private static final double TOO_CLOSE = 4.0;
    private static final int AIM_TICKS = 10;       // sees the target this long before the first shot
    private static final int CROSSBOW_DELAY = 10;  // loaded: a moment to aim before firing

    private final WorkerEntity worker;
    private final double speed;
    private int seeTime;
    private int cooldown;
    private int loadedDelay;
    private boolean loading;

    public RangerAttackGoal(WorkerEntity worker, double speed) {
        this.worker = worker;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.worker.getTarget();
        return this.worker.getJob() == WorkerJob.RANGER && target != null && target.isAlive() && this.worker.hasAmmo();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.seeTime = 0;
        this.cooldown = AIM_TICKS;
        this.loading = false;
        this.worker.setAggressive(true);
    }

    @Override
    public void stop() {
        this.worker.setAggressive(false);
        this.worker.getNavigation().stop();
        if (this.worker.isUsingItem()) {
            this.worker.stopUsingItem();
        }
        this.loading = false;
    }

    @Override
    public Component describeActivity() {
        LivingEntity target = this.worker.getTarget();
        if (this.loading) {
            return Component.translatable("activity.villagerhuts.loading_crossbow");
        }
        return target == null
                ? Component.translatable("activity.villagerhuts.fighting_something")
                : Component.translatable("activity.villagerhuts.shooting", target.getName());
    }

    @Override
    public void tick() {
        LivingEntity target = this.worker.getTarget();
        if (target == null) {
            return;
        }
        double range = this.worker.rangedRange();
        double distanceSqr = this.worker.distanceToSqr(target);
        boolean canSee = this.worker.getSensing().hasLineOfSight(target);
        this.seeTime = canSee ? this.seeTime + 1 : 0;
        boolean inRange = canSee && distanceSqr <= range * range;

        // Get within range with a clear shot; step back if it comes too close; otherwise stand and shoot.
        if (!inRange) {
            this.worker.getNavigation().moveTo(target, this.speed);
        } else if (distanceSqr < TOO_CLOSE * TOO_CLOSE) {
            if (this.worker.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(this.worker, 8, 4, target.position());
                if (away != null) {
                    this.worker.getNavigation().moveTo(away.x, away.y, away.z, this.speed * 1.2);
                }
            }
        } else {
            this.worker.getNavigation().stop();
        }
        this.worker.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack weapon = this.worker.getMainHandItem();
        if (Weapons.isCrossbow(weapon)) {
            this.crossbow(target, weapon, inRange);
        } else if (--this.cooldown <= 0 && inRange && this.seeTime >= AIM_TICKS / 2) {
            this.worker.performRangedAttack(target, 1.0F);
            this.cooldown = this.worker.rangedInterval();
        }
    }

    // Load (hold "use" for the charge time: the crossbow takes its ammo when it's fully drawn), wait a moment, fire.
    private void crossbow(LivingEntity target, ItemStack crossbow, boolean inRange) {
        if (!CrossbowItem.isCharged(crossbow)) {
            if (!this.worker.isUsingItem()) {
                this.worker.startUsingItem(InteractionHand.MAIN_HAND);
                this.loading = true;
            } else if (this.worker.getTicksUsingItem() >= CrossbowItem.getChargeDuration(crossbow, this.worker)) {
                this.worker.releaseUsingItem();
                this.loading = false;
                this.loadedDelay = CROSSBOW_DELAY;
            }
            return;
        }
        if (--this.loadedDelay <= 0 && --this.cooldown <= 0 && inRange) {
            this.worker.performRangedAttack(target, 1.6F);
            this.cooldown = this.worker.rangedInterval() / 2; // most of a crossbow's wait is the loading
        }
    }
}

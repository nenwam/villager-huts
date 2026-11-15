package io.github.nenwam.villagerhuts.entity.goal;

import java.util.Comparator;
import java.util.EnumSet;

import io.github.nenwam.villagerhuts.entity.WorkerCombat;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jspecify.annotations.Nullable;

/**
 * Warriors and rangers: who to fight (see WorkerCombat for friend and foe). Every half second it looks for:
 *  1. whatever just hurt it, if it's a foe;
 *  2. the nearest foe it can see in its guard zone: around its post (working), around the player it follows
 *     (a bodyguard), or around the spot it was told to stay at (WorkerEntity.isInGuardZone).
 * It chases a foe a little way past its zone, then gives up and goes back (so it can't be lured away).
 */
public class FighterTargetGoal extends TargetGoal {
    private static final int SCAN_TICKS = 10;
    private static final int REVENGE_TICKS = 100;   // how long after being hurt it still goes for the attacker
    public static final double CHASE_MARGIN = 8.0;  // blocks past its zone it keeps chasing

    private final WorkerEntity worker;
    private final TargetingConditions conditions = TargetingConditions.forCombat().range(64.0);
    private @Nullable LivingEntity candidate;
    private int nextScanTick;

    public FighterTargetGoal(WorkerEntity worker) {
        super(worker, true);
        this.worker = worker;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.worker.canFight() || this.worker.tickCount < this.nextScanTick) {
            return false;
        }
        this.nextScanTick = this.worker.tickCount + SCAN_TICKS;
        this.candidate = this.findTarget();
        return this.candidate != null;
    }

    @Override
    public void start() {
        this.worker.setTarget(this.candidate);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.worker.getTarget();
        return target != null && WorkerCombat.willFight(this.worker, target)
                && this.worker.isInGuardZone(target, CHASE_MARGIN) && super.canContinueToUse();
    }

    @Override
    public void stop() {
        super.stop();
        this.candidate = null;
    }

    private @Nullable LivingEntity findTarget() {
        LivingEntity attacker = this.worker.getLastHurtByMob();
        if (attacker != null && this.worker.tickCount - this.worker.getLastHurtByMobTimestamp() < REVENGE_TICKS
                && this.isFightable(attacker, CHASE_MARGIN)) {
            return attacker;
        }
        return this.worker.level().getEntitiesOfClass(Mob.class, this.worker.guardZone(), mob -> this.isFightable(mob, 0.0)).stream()
                .min(Comparator.comparingDouble(this.worker::distanceToSqr))
                .orElse(null);
    }

    private boolean isFightable(LivingEntity entity, double margin) {
        return WorkerCombat.willFight(this.worker, entity) && this.worker.isInGuardZone(entity, margin)
                && this.canAttack(entity, this.conditions);
    }
}

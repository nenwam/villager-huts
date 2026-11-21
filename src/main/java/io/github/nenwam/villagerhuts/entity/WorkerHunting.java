package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.List;

import io.github.nenwam.villagerhuts.entity.goal.HuntWorkersGoal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * Any mob that hunts villagers also hunts workers, with the same priority and rules.
 * In vanilla that's zombies (including husks, zombie villagers, drowned) and raiders
 * (pillagers, vindicators, evokers, illusioners, ravagers). Modded villager-hunters are
 * picked up automatically.
 */
public final class WorkerHunting {

    /** Hooks hunting into the game (from VillagerHuts.onInitialize): each mob that's loaded gets set up once. */
    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob) {
                huntWorkersToo(mob);
            }
        });
    }

    private static void huntWorkersToo(Mob mob) {
        List<WrappedGoal> villagerHunting = new ArrayList<>();
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof HuntWorkersGoal) {
                return; // already set up
            }
            if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> goal
                    && AbstractVillager.class.isAssignableFrom(goal.targetType)) {
                villagerHunting.add(wrapped);
            }
        }
        for (WrappedGoal wrapped : villagerHunting) {
            mob.targetSelector.addGoal(wrapped.getPriority(),
                    new HuntWorkersGoal(mob, (NearestAttackableTargetGoal<?>) wrapped.getGoal()));
        }
    }

    private WorkerHunting() {}
}

package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.List;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.goal.HuntWorkersGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Any mob that hunts villagers also hunts workers, with the same priority and rules.
 * In vanilla that's zombies (including husks, zombie villagers, drowned) and raiders
 * (pillagers, vindicators, evokers, illusioners, ravagers). Modded villager-hunters are
 * picked up automatically.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class WorkerHunting {

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
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

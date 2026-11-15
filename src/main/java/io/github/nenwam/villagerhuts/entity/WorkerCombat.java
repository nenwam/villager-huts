package io.github.nenwam.villagerhuts.entity;

import java.util.Iterator;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Who fighters (warriors and rangers) fight, and what happens around their fights:
 *  - friends are never fought, and a fighter's blows, arrows and fireworks never hurt them (arrows fly on through);
 *  - foes are monsters (but not neutral ones that aren't angry, like endermen, and never a warden), and anything
 *    attacking a friend: wolves, bees or golems gone wild included;
 *  - what a fighter's foes drop goes into its pockets (what fits), and it counts who it has defeated.
 */
@EventBusSubscriber(modid = VillagerHuts.MODID)
public final class WorkerCombat {

    /** Never fought or hurt by a fighter: players, workers, villagers, golems, and anyone's pets. */
    public static boolean isFriend(WorkerEntity fighter, Entity entity) {
        if (entity == fighter || entity instanceof Player || entity instanceof WorkerEntity || entity instanceof AbstractVillager
                || entity instanceof AbstractGolem) {
            return true;
        }
        if (entity instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
            return true;
        }
        return fighter.isAlliedTo(entity);
    }

    /** Monsters and anything attacking a friend (see the class comment). */
    public static boolean isFoe(WorkerEntity fighter, LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !mob.isAlive() || mob instanceof Warden || isFriend(fighter, mob)) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && isFriend(fighter, target)) {
            return true;
        }
        return mob instanceof Enemy && !(mob instanceof NeutralMob neutral && !neutral.isAngry());
    }

    /**
     * A foe this fighter will take on: warriors leave creepers to rangers (blowing up in their face helps nobody), and a
     * ranger out of ammo takes on nothing (it fetches more instead).
     */
    public static boolean willFight(WorkerEntity fighter, LivingEntity entity) {
        return fighter.canFight() && isFoe(fighter, entity) && !(entity instanceof Creeper && !fighter.isRanger());
    }

    /** The fighter behind this damage (its blow, or its arrow or trident), if a fighter did it. */
    public static @Nullable WorkerEntity fighterOf(DamageSource source) {
        return source.getEntity() instanceof WorkerEntity worker && worker.getJob().isFighter() ? worker : null;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        WorkerEntity fighter = fighterOf(event.getSource());
        if (fighter != null && isFriend(fighter, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // An arrow (or trident) from a fighter only stops in a foe: anything else, it flies on through.
    @SubscribeEvent
    static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && event.getProjectile().getOwner() instanceof WorkerEntity fighter
                && fighter.getJob().isFighter() && !(hit.getEntity() instanceof LivingEntity living && isFoe(fighter, living))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        WorkerEntity fighter = fighterOf(event.getSource());
        if (fighter != null && !event.getEntity().level().isClientSide()) {
            fighter.recordDefeated(event.getEntity());
        }
    }

    // A foe's drops go straight into the fighter's pockets, as much as fits; the rest stays on the ground.
    @SubscribeEvent
    static void onDrops(LivingDropsEvent event) {
        WorkerEntity fighter = fighterOf(event.getSource());
        if (fighter == null || fighter.level().isClientSide() || !fighter.isAlive()) {
            return;
        }
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemEntity drop = drops.next();
            ItemStack stack = drop.getItem();
            ItemStack rest = fighter.getInventory().addItem(stack.copy());
            int taken = stack.getCount() - rest.getCount();
            if (taken > 0) {
                fighter.recordCollected(stack.copyWithCount(taken));
            }
            if (rest.isEmpty()) {
                drops.remove();
            } else {
                drop.setItem(rest);
            }
        }
        fighter.markWorked();
    }

    private WorkerCombat() {}
}

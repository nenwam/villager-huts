package io.github.nenwam.villagerhuts.entity;


import io.github.nenwam.villagerhuts.registry.ModTags;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Who fighters (warriors and rangers) fight, and what happens around their fights:
 *  - friends are never fought, and a fighter's blows, arrows and fireworks never hurt them (arrows fly on through);
 *  - foes are monsters (but not neutral ones that aren't angry, like endermen, and never a warden), and anything
 *    attacking a friend: wolves, bees or golems gone wild included;
 *  - what a fighter's foes drop goes into its pockets (what fits), and it counts who it has defeated.
 */
public final class WorkerCombat {

    /**
     * Never fought or hurt by a fighter: players, workers, villagers, golems, anyone's pets, and mobs in the friends tag
     * (Guard Villagers' guards).
     */
    public static boolean isFriend(WorkerEntity fighter, Entity entity) {
        if (entity == fighter || entity instanceof Player || entity instanceof WorkerEntity || entity instanceof AbstractVillager
                || entity instanceof AbstractGolem || entity.getType().builtInRegistryHolder().is(ModTags.FRIENDS)) {
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

    /** Hooks the fighters into the game's events (from VillagerHuts.onInitialize). */
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            WorkerEntity fighter = fighterOf(source);
            return fighter == null || !isFriend(fighter, entity);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            WorkerEntity fighter = fighterOf(source);
            if (fighter != null) {
                fighter.recordDefeated(entity);
            }
        });
    }

    // A foe's drops (its loot and what it carried) go straight into the fighter's pockets, as much as fits; the rest drops
    // on the ground. LivingEntityMixin says when a mob a fighter killed is dropping its loot; EntityMixin hands each drop here.
    private static @Nullable LivingEntity dropping;
    private static @Nullable WorkerEntity pocketing;

    /** A mob starts dropping its loot (LivingEntityMixin): if a fighter killed it, the drops go to the fighter. */
    public static void startDrops(LivingEntity dying, DamageSource source) {
        WorkerEntity fighter = fighterOf(source);
        if (fighter != null && fighter.isAlive()) {
            dropping = dying;
            pocketing = fighter;
        }
    }

    public static void endDrops(LivingEntity dying) {
        if (dropping == dying) {
            dropping = null;
            pocketing = null;
        }
    }

    /** Something an entity drops (EntityMixin). Returns what's left for the ground (empty if the fighter took it all). */
    public static ItemStack pocketDrop(Entity dropper, ItemStack stack) {
        WorkerEntity fighter = pocketing;
        if (fighter == null || dropper != dropping || stack.isEmpty()) {
            return stack;
        }
        ItemStack rest = fighter.getInventory().addItem(stack.copy());
        int taken = stack.getCount() - rest.getCount();
        if (taken > 0) {
            fighter.recordCollected(stack.copyWithCount(taken));
            fighter.markWorked();
        }
        return rest;
    }

    /**
     * An arrow (or trident) from a fighter only stops in a foe: anything else, it flies on through (ProjectileMixin,
     * when the projectile hits something).
     */
    public static boolean passesThrough(Projectile projectile, HitResult hit) {
        return hit instanceof EntityHitResult entityHit && projectile.getOwner() instanceof WorkerEntity fighter && fighter.getJob().isFighter()
                && !(entityHit.getEntity() instanceof LivingEntity living && isFoe(fighter, living));
    }

    private WorkerCombat() {}
}

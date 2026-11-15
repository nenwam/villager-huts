package io.github.nenwam.villagerhuts.job;

import java.util.function.Predicate;

import io.github.nenwam.villagerhuts.registry.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.Tags;

/**
 * Which items are weapons, and of which kind, for fighters: any melee weapon makes a warrior, any ranged one a ranger.
 * "Any" covers vanilla's weapons, modded ones, and ones added in future versions:
 *  - ranged: anything that shoots projectiles (bows, crossbows and their modded relatives all extend
 *    ProjectileWeaponItem), or is in the villagerhuts:ranger_weapons tag (bows, crossbows, tridents, and
 *    everything in c:tools/ranged_weapon, the tag mods put their ranged weapons in);
 *  - melee: anything else in the villagerhuts:warrior_weapons tag (swords, axes, maces, and everything in
 *    c:tools/melee_weapon), any sword (modded ones included), or any other item that adds attack damage and isn't a
 *    digging or farming tool.
 * Tridents are both; fighters throw them, so they're ranged here.
 */
public final class Weapons {

    public static boolean isRanged(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ProjectileWeaponItem || stack.is(ModTags.RANGER_WEAPONS));
    }

    public static boolean isMelee(ItemStack stack) {
        if (stack.isEmpty() || isRanged(stack)) {
            return false;
        }
        if (stack.is(ModTags.WARRIOR_WEAPONS)) {
            return true;
        }
        return stack.getItem() instanceof SwordItem || (addsAttackDamage(stack) && !isDiggingTool(stack));
    }

    private static boolean addsAttackDamage(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream()
                .anyMatch(entry -> entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().amount() > 0);
    }

    /** Pickaxes, shovels and hoes hit things too (they add attack damage), but they aren't weapons. */
    private static boolean isDiggingTool(ItemStack stack) {
        return (stack.getItem() instanceof DiggerItem && !stack.is(ItemTags.AXES)) || stack.is(Tags.Items.MINING_TOOL_TOOLS)
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES) || stack.is(ModTags.FARMER_TOOLS);
    }

    /** Thrown itself rather than shooting something (tridents). Needs no ammo. */
    public static boolean isThrown(ItemStack weapon) {
        return weapon.getItem() instanceof TridentItem;
    }

    public static boolean isCrossbow(ItemStack weapon) {
        return weapon.getItem() instanceof CrossbowItem;
    }

    /**
     * What a ranged weapon shoots (arrows, and fireworks for crossbows), or nothing for one that's thrown.
     * Ranged weapons that aren't projectile weapons (and aren't thrown) shoot arrows.
     */
    public static Predicate<ItemStack> ammoFor(ItemStack weapon) {
        if (isThrown(weapon)) {
            return stack -> false;
        }
        if (weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon) {
            return projectileWeapon.getAllSupportedProjectiles(weapon);
        }
        return ProjectileWeaponItem.ARROW_ONLY;
    }

    /** Whether the weapon needs ammo at all (tridents don't). */
    public static boolean usesAmmo(ItemStack weapon) {
        return isRanged(weapon) && !isThrown(weapon);
    }

    /**
     * Food a fighter will eat to heal: anything edible that does nothing else when eaten. That leaves out rotten flesh,
     * spider eyes, raw chicken and pufferfish (they make you ill), and golden apples and stews with effects (worth more to you).
     */
    public static boolean isPlainFood(ItemStack stack) {
        FoodProperties food = stack.isEmpty() ? null : stack.get(DataComponents.FOOD);
        return food != null && food.effects().isEmpty();
    }

    private Weapons() {}
}

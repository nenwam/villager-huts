package io.github.nenwam.villagerhuts;

import net.neoforged.neoforge.common.ModConfigSpec;

// Settings players and modpack makers can change, from the config file or the in-game screen.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue FARMER_WORK_DELAY = BUILDER
            .comment("Ticks a farmer waits between harvesting or planting each crop (20 ticks = 1 second)")
            .defineInRange("farmerWorkDelay", 20, 0, 200);

    public static final ModConfigSpec.IntValue FARMER_MAX_FARM_SIZE = BUILDER
            .comment("Largest farm one farmer can be assigned, in blocks per side (16 = one chunk)")
            .defineInRange("farmerMaxFarmSize", 16, 4, 32);

    public static final ModConfigSpec.IntValue FARMER_NEW_FARM_SIZE = BUILDER
            .comment("Size of a new farm a farmer makes (shift + right-click the farmer, then the ground), in blocks per side."
                    + " 9 is a classic farm: one water block in the middle keeps it all wet")
            .defineInRange("farmerNewFarmSize", 9, 3, 32);

    public static final ModConfigSpec.IntValue FARMER_MAX_FARM_DISTANCE = BUILDER
            .comment("How far a farm can be from the farmer when you assign it, and how far its chest can be from the farm, in blocks")
            .defineInRange("farmerMaxFarmDistance", 32, 8, 64);

    public static final ModConfigSpec.IntValue FARMER_DAILY_HARVEST = BUILDER
            .comment("Items a Novice farmer can harvest in one day before they're worn out and rest until the next morning."
                    + " Each level they're upgraded (with diamond carrots) adds this much again, up to 5x for a Master")
            .defineInRange("farmerDailyHarvest", 64, 1, 4096);

    public static final ModConfigSpec.IntValue FIGHTER_GUARD_RADIUS = BUILDER
            .comment("How far a fighter guards around its post, in blocks: it fights monsters that come this close to the post."
                    + " Following you, it guards you over the same distance")
            .defineInRange("fighterGuardRadius", 12, 4, 32);

    public static final ModConfigSpec.BooleanValue PROTECT_WORKERS = BUILDER
            .comment("Only a worker's owner can hurt it (other players' hits and arrows do nothing). Turn off for PvP servers")
            .define("protectWorkers", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

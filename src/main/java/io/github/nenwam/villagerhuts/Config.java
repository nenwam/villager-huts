package io.github.nenwam.villagerhuts;

import net.neoforged.neoforge.common.ModConfigSpec;

// Settings players and modpack makers can change, from the config file or the in-game screen.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue FARMER_WORK_DELAY = BUILDER
            .comment("Ticks a farmer waits between harvesting or planting each crop (20 ticks = 1 second)")
            .defineInRange("farmerWorkDelay", 20, 0, 200);

    public static final ModConfigSpec.IntValue FARMER_MAX_FARM_SIZE = BUILDER
            .comment("Largest farm one farmer can be assigned with a Work Area Marker, in blocks per side (16 = one chunk)")
            .defineInRange("farmerMaxFarmSize", 16, 4, 32);

    public static final ModConfigSpec.IntValue FARMER_MAX_FARM_DISTANCE = BUILDER
            .comment("How far an assigned farm can be from the farmer's hut, in blocks (center to center)")
            .defineInRange("farmerMaxFarmDistance", 32, 8, 64);

    static final ModConfigSpec SPEC = BUILDER.build();
}

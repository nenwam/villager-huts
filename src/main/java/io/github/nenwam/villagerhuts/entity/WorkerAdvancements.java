package io.github.nenwam.villagerhuts.entity;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The workers' advancements (their own tab): hiring a farmer and a fighter, a first upgrade, and a Master. Each has
 * one criterion, "done", that only this awards; modpacks can use them as quest tasks (FTB Quests' advancement tasks).
 */
public final class WorkerAdvancements {
    public static final String HIRE_FARMER = "hire_farmer";
    public static final String HIRE_FIGHTER = "hire_fighter";
    public static final String UPGRADE = "upgrade";
    public static final String MASTER = "master";

    /** Gives the player this advancement (workers/<name>), if they're a real player on the server. */
    public static void award(@Nullable Player player, String name) {
        if (player instanceof ServerPlayer serverPlayer) {
            AdvancementHolder advancement = serverPlayer.level().getServer().getAdvancements()
                    .get(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "workers/" + name));
            if (advancement != null) {
                serverPlayer.getAdvancements().award(advancement, "done");
            }
        }
    }

    private WorkerAdvancements() {}
}

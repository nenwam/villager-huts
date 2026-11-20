package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.compat.Teams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Tells players about their workers in chat (the notifyOwners setting):
 *  - when one needs them (its chest is full, it's out of seeds or arrows, it can't reach its farm or chest...):
 *    once the need has lasted a few seconds, and not again until it's been sorted out for a minute (and never
 *    more often than every couple of minutes per worker). Being in danger (no air, too hot or too cold: Ad Astra) is
 *    told straight away;
 *  - when one dies: how, and where.
 * A need goes to its owner and the players it's shared with; a death to its owner's party too.
 */
public final class WorkerNotices {
    // Statuses (WorkerEntity.currentStatus) that need a player to do something.
    private static final Set<String> NEEDS = Set.of(
            "activity.villagerhuts.pockets_full", "activity.villagerhuts.out_of_seeds", "activity.villagerhuts.out_of_season",
            "activity.villagerhuts.out_of_ammo", "activity.villagerhuts.no_storage", "activity.villagerhuts.storage_unreachable",
            "activity.villagerhuts.farm_unreachable", "activity.villagerhuts.no_oxygen", "activity.villagerhuts.too_hot",
            "activity.villagerhuts.too_cold");
    // Needs that hurt it: told after a second, whatever else it's said lately.
    private static final Set<String> URGENT = Set.of("activity.villagerhuts.no_oxygen", "activity.villagerhuts.too_hot",
            "activity.villagerhuts.too_cold");
    private static final int LASTS_SECONDS = 5;   // a need lasts this long before anyone's told
    private static final int SORTED_SECONDS = 60; // gone this long, the same need is worth telling again
    private static final long GAP_TICKS = 2400;   // at most one need per worker every 2 minutes

    private static @Nullable List<Sent> sentForTests; // see recordForTests

    /** A message sent to a player (for the game tests). */
    public record Sent(UUID player, Component message) {}

    /** One worker's needs, checked once a second (WorkerEntity.tick). */
    static final class Tracker {
        private @Nullable String told; // the need the players were last told about
        private @Nullable String need; // the need it has now, if any
        private int needSeconds;
        private int sortedSeconds;
        private long quietUntil;

        void check(WorkerEntity worker, ServerLevel level) {
            if (!Config.NOTIFY_OWNERS.getAsBoolean() || worker.getOwnerId() == null) {
                return;
            }
            Component status = worker.currentStatus();
            String now = status.getContents() instanceof TranslatableContents translatable && NEEDS.contains(translatable.getKey())
                    ? translatable.getKey() : null;
            if (now == null) {
                this.need = null;
                if (++this.sortedSeconds >= SORTED_SECONDS) {
                    this.told = null;
                }
                return;
            }
            this.sortedSeconds = 0;
            if (!now.equals(this.need)) {
                this.need = now;
                this.needSeconds = 0;
            }
            boolean urgent = URGENT.contains(now);
            if (++this.needSeconds >= (urgent ? 1 : LASTS_SECONDS) && !now.equals(this.told) && (urgent || level.getGameTime() >= this.quietUntil)) {
                this.told = now;
                this.quietUntil = level.getGameTime() + GAP_TICKS;
                BlockPos pos = worker.blockPosition();
                tell(recipients(worker, level, false), Component.translatable("message.villagerhuts.needs_you",
                        worker.getDisplayName(), pos.getX(), pos.getY(), pos.getZ(), status));
            }
        }
    }

    /** Tells its owner (its sharers and party too) that it died. `deathMessage`: how, worked out before it died. */
    static void died(WorkerEntity worker, ServerLevel level, Component deathMessage) {
        if (!Config.NOTIFY_OWNERS.getAsBoolean() || worker.getOwnerId() == null) {
            return;
        }
        BlockPos pos = worker.blockPosition();
        tell(recipients(worker, level, true), Component.translatable("message.villagerhuts.worker_died",
                deathMessage, pos.getX(), pos.getY(), pos.getZ()));
    }

    /** For the game tests: from now on, keeps a list of every message sent (the list returned). */
    public static List<Sent> recordForTests() {
        if (sentForTests == null) {
            sentForTests = new ArrayList<>();
        }
        return sentForTests;
    }

    // The players online who use this worker: its owner, the players it's shared with, and (if `party`) its owner's party.
    private static List<ServerPlayer> recipients(WorkerEntity worker, ServerLevel level, boolean party) {
        UUID owner = worker.getOwnerId();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (id.equals(owner) || worker.getSharedWith().contains(id) || (party && owner != null && Teams.sameParty(owner, id))) {
                players.add(player);
            }
        }
        return players;
    }

    private static void tell(List<ServerPlayer> players, Component message) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(message);
            if (sentForTests != null) {
                sentForTests.add(new Sent(player.getUUID(), message));
            }
        }
    }

    private WorkerNotices() {}
}

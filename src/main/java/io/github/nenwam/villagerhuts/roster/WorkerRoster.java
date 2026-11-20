package io.github.nenwam.villagerhuts.roster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.compat.Teams;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every player's workers, saved with the world. Lets the worker management screen list workers
 * that are far away (in unloaded chunks), and gives each worker a stable number per owner.
 * Loaded workers refresh their entry every second; dying removes it.
 *
 * Who owns a worker and who it's shared with is decided here (setOwnership), so it works for workers that
 * are far away too: a worker takes its owner and sharing from its entry at its next refresh. Names given in
 * the worker panel wait here the same way, until the worker's next refresh.
 */
public final class WorkerRoster extends SavedData {

    /**
     * How far a worker has got: its rank, today's harvest, what it has collected and defeated, and its tool.
     * Saved alongside the rest of its entry (not nested), so older saves load as they are.
     * @param tool  the item it works or fights with, if any (for its icon)
     * @param field a farmer's field: farming, clearing or cleared (WorkerEntity.Field)
     */
    public record Progress(int rank, int harvestedToday, Map<Identifier, Integer> collected, Map<Identifier, Integer> defeated,
                           Optional<Identifier> tool, String field) {
        static final MapCodec<Progress> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("rank", 1).forGetter(Progress::rank),
                Codec.INT.optionalFieldOf("harvested_today", 0).forGetter(Progress::harvestedToday),
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("collected").forGetter(Progress::collected),
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("defeated", Map.of()).forGetter(Progress::defeated),
                Identifier.CODEC.optionalFieldOf("tool").forGetter(Progress::tool),
                Codec.STRING.optionalFieldOf("field", WorkerEntity.Field.FARMING.id()).forGetter(Progress::field)
        ).apply(i, Progress::new));
    }

    /**
     * The last known state of one worker.
     * @param shared      the players its owner shares it with
     * @param pendingName a name given in the worker panel, for the worker to take at its next refresh
     *                    ("" = remove its name)
     * @param farm        its farm (a fighter: the area it guards round its post)
     * @param bed         the head end of its bed
     * @param position    where it was at its last refresh (where it still is, if it's far away: it only moves while loaded)
     */
    public record Entry(UUID owner, List<UUID> shared, int number, ResourceKey<Level> dimension, Optional<String> customName,
                        Optional<String> pendingName, String job, String order, long employedTicks, Optional<BoundingBox> farm,
                        Optional<BlockPos> storage, Optional<BlockPos> bed, Optional<BlockPos> position, Progress progress) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                UUIDUtil.CODEC.listOf().optionalFieldOf("shared", List.of()).forGetter(Entry::shared),
                Codec.INT.fieldOf("number").forGetter(Entry::number),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.STRING.optionalFieldOf("custom_name").forGetter(Entry::customName),
                Codec.STRING.optionalFieldOf("pending_name").forGetter(Entry::pendingName),
                Codec.STRING.fieldOf("job").forGetter(Entry::job),
                Codec.STRING.optionalFieldOf("order", WorkerEntity.Order.WORK.id()).forGetter(Entry::order),
                Codec.LONG.fieldOf("employed_ticks").forGetter(Entry::employedTicks),
                BoundingBox.CODEC.optionalFieldOf("farm").forGetter(Entry::farm),
                BlockPos.CODEC.optionalFieldOf("storage").forGetter(Entry::storage),
                BlockPos.CODEC.optionalFieldOf("bed").forGetter(Entry::bed),
                BlockPos.CODEC.optionalFieldOf("position").forGetter(Entry::position),
                Progress.MAP_CODEC.forGetter(Entry::progress)
        ).apply(i, Entry::new));

        public int rank() {
            return this.progress.rank();
        }

        public int harvestedToday() {
            return this.progress.harvestedToday();
        }

        public Map<Identifier, Integer> collected() {
            return this.progress.collected();
        }

        public Map<Identifier, Integer> defeated() {
            return this.progress.defeated();
        }

        public Optional<Identifier> tool() {
            return this.progress.tool();
        }

        /** Whether this player can give the worker work and orders: its owner, someone it's shared with, or its owner's party. */
        public boolean canCommand(UUID player) {
            return this.owner.equals(player) || this.shared.contains(player) || Teams.sameParty(this.owner, player);
        }

        Entry withOwnership(UUID newOwner, List<UUID> newShared, int newNumber) {
            return new Entry(newOwner, newShared, newNumber, this.dimension, this.customName, this.pendingName, this.job, this.order,
                    this.employedTicks, this.farm, this.storage, this.bed, this.position, this.progress);
        }

        Entry withName(Optional<String> name) {
            return new Entry(this.owner, this.shared, this.number, this.dimension, name, Optional.of(name.orElse("")), this.job, this.order,
                    this.employedTicks, this.farm, this.storage, this.bed, this.position, this.progress);
        }
    }

    private static final Codec<WorkerRoster> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC)
            .fieldOf("workers").codec()
            .xmap(WorkerRoster::new, roster -> roster.entries);

    public static final SavedDataType<WorkerRoster> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "workers"), WorkerRoster::new, CODEC);

    private final Map<UUID, Entry> entries;

    private WorkerRoster() {
        this(Map.of());
    }

    private WorkerRoster(Map<UUID, Entry> entries) {
        this.entries = new HashMap<>(entries);
    }

    public static WorkerRoster get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Adds the worker (giving it the lowest free number for its owner) or refreshes its entry. A worker that's
     * already listed takes its owner, sharing and any new name from its entry first.
     */
    public void update(WorkerEntity worker) {
        Entry old = this.entries.get(worker.getUUID());
        if (old != null) {
            if (!old.owner().equals(worker.getOwnerId()) || !Set.copyOf(old.shared()).equals(worker.getSharedWith())) {
                worker.setOwnership(old.owner(), old.shared()); // shared or given away from the worker panel
            }
            if (old.pendingName().isPresent()) { // renamed in the worker panel
                String pending = old.pendingName().get();
                worker.setCustomName(pending.isEmpty() ? null : Component.literal(pending));
            }
        }
        UUID owner = worker.getOwnerId();
        if (owner == null) {
            return;
        }
        int number = old != null && old.owner().equals(owner) ? old.number() : this.nextNumber(owner);
        Optional<String> name = worker.hasCustomName() ? Optional.of(worker.getCustomName().getString()) : Optional.empty();
        this.entries.put(worker.getUUID(), new Entry(owner, List.copyOf(worker.getSharedWith()), number, worker.level().dimension(), name,
                Optional.empty(), worker.getJob().id(), worker.getOrder().id(), worker.getEmployedTicks(),
                Optional.ofNullable(worker.getWorkArea()), Optional.ofNullable(worker.getStoragePos()), Optional.ofNullable(worker.getBedPos()),
                Optional.of(worker.blockPosition()),
                new Progress(worker.getRank(), worker.getHarvestedToday(), Map.copyOf(worker.getCollected()), Map.copyOf(worker.getDefeated()),
                        worker.getMainHandItem().isEmpty() ? Optional.empty()
                                : Optional.of(BuiltInRegistries.ITEM.getKey(worker.getMainHandItem().getItem())),
                        worker.getField().id())));
        this.setDirty();
    }

    /**
     * Renames a worker (an empty name removes its name). Its entry shows the new name right away; the worker
     * takes it at its next refresh, so one that's far away (not loaded) gets it when it's next loaded.
     */
    public void rename(UUID worker, Optional<String> name) {
        Entry old = this.entries.get(worker);
        if (old != null) {
            this.entries.put(worker, old.withName(name));
            this.setDirty();
        }
    }

    /**
     * Changes who owns a worker and who it's shared with. If it's loaded it takes the change now (pass it in),
     * otherwise when it's next loaded. A new owner gives it the next free number among their workers.
     */
    public void setOwnership(UUID worker, UUID owner, List<UUID> shared, @Nullable WorkerEntity loaded) {
        Entry old = this.entries.get(worker);
        if (old == null) {
            return;
        }
        List<UUID> cleaned = new ArrayList<>(new LinkedHashSet<>(shared));
        cleaned.remove(owner);
        int number = old.owner().equals(owner) ? old.number() : this.nextNumber(owner);
        this.entries.put(worker, old.withOwnership(owner, List.copyOf(cleaned), number));
        this.setDirty();
        if (loaded != null) {
            loaded.setOwnership(owner, cleaned);
        }
    }

    public void remove(UUID worker) {
        if (this.entries.remove(worker) != null) {
            this.setDirty();
        }
    }

    public Optional<Entry> entry(UUID worker) {
        return Optional.ofNullable(this.entries.get(worker));
    }

    /** The worker, if it's loaded right now (near enough to a player). */
    public static @Nullable WorkerEntity loaded(MinecraftServer server, UUID worker, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        return level != null && level.getEntity(worker) instanceof WorkerEntity found && found.isAlive() ? found : null;
    }

    /** The workers this player can use: their own (by number), then the ones shared with them or their party (by owner, then number). */
    public List<Map.Entry<UUID, Entry>> forPlayer(UUID player) {
        List<Map.Entry<UUID, Entry>> result = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
            if (entry.getValue().canCommand(player)) {
                result.add(entry);
            }
        }
        result.sort(Comparator.<Map.Entry<UUID, Entry>, Boolean>comparing(e -> !e.getValue().owner().equals(player))
                .thenComparing(e -> e.getValue().owner())
                .thenComparingInt(e -> e.getValue().number()));
        return result;
    }

    /** The worker (other than `except`) whose bed this is, if any. */
    public Optional<UUID> bedOwner(ResourceKey<Level> dimension, BlockPos bed, UUID except) {
        for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
            Entry value = entry.getValue();
            if (!entry.getKey().equals(except) && value.dimension().equals(dimension) && value.bed().filter(bed::equals).isPresent()) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** A player's name: from the server if they're online, otherwise from the server's list of players it has seen. */
    public static Component playerName(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        return server.services().nameToIdCache().get(player).map(NameAndId::name).<Component>map(Component::literal)
                .orElseGet(() -> Component.translatable("gui.villagerhuts.someone"));
    }

    private int nextNumber(UUID owner) {
        Set<Integer> used = new HashSet<>();
        for (Entry entry : this.entries.values()) {
            if (entry.owner().equals(owner)) {
                used.add(entry.number());
            }
        }
        int number = 1;
        while (used.contains(number)) {
            number++;
        }
        return number;
    }
}

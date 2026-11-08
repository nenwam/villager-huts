package io.github.nenwam.villagerhuts.roster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every player's workers, saved with the world. Lets the worker management screen list workers
 * that are far away (in unloaded chunks), and gives each worker a stable number per player.
 * Loaded workers refresh their entry every second; dying removes it.
 * Names given in the worker panel wait here until the worker's next refresh (when it's next loaded, if it's far away).
 */
public final class WorkerRoster extends SavedData {

    /**
     * The last known state of one worker.
     * @param pendingName a name given in the worker panel, for the worker to take at its next refresh
     *                    ("" = remove its name)
     */
    public record Entry(UUID owner, int number, ResourceKey<Level> dimension, Optional<String> customName,
                        Optional<String> pendingName, String job, String order, long employedTicks, Optional<BoundingBox> farm,
                        Optional<BlockPos> storage, Map<Identifier, Integer> collected) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Codec.INT.fieldOf("number").forGetter(Entry::number),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.STRING.optionalFieldOf("custom_name").forGetter(Entry::customName),
                Codec.STRING.optionalFieldOf("pending_name").forGetter(Entry::pendingName),
                Codec.STRING.fieldOf("job").forGetter(Entry::job),
                Codec.STRING.optionalFieldOf("order", WorkerEntity.Order.WORK.id()).forGetter(Entry::order),
                Codec.LONG.fieldOf("employed_ticks").forGetter(Entry::employedTicks),
                BoundingBox.CODEC.optionalFieldOf("farm").forGetter(Entry::farm),
                BlockPos.CODEC.optionalFieldOf("storage").forGetter(Entry::storage),
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("collected").forGetter(Entry::collected)
        ).apply(i, Entry::new));
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

    /** Adds the worker (giving it the lowest free number for its owner) or refreshes its entry. */
    public void update(WorkerEntity worker) {
        UUID owner = worker.getOwnerId();
        if (owner == null) {
            return;
        }
        Entry old = this.entries.get(worker.getUUID());
        if (old != null && old.pendingName().isPresent()) { // renamed in the worker panel
            String pending = old.pendingName().get();
            worker.setCustomName(pending.isEmpty() ? null : Component.literal(pending));
        }
        int number = old != null && old.owner().equals(owner) ? old.number() : this.nextNumber(owner);
        Optional<String> name = worker.hasCustomName() ? Optional.of(worker.getCustomName().getString()) : Optional.empty();
        this.entries.put(worker.getUUID(), new Entry(owner, number, worker.level().dimension(), name, Optional.empty(), worker.getJob().id(),
                worker.getOrder().id(), worker.getEmployedTicks(), Optional.ofNullable(worker.getFarmArea()), Optional.ofNullable(worker.getStoragePos()),
                Map.copyOf(worker.getCollected())));
        this.setDirty();
    }

    /**
     * Renames a worker (an empty name removes its name). Its entry shows the new name right away; the worker
     * takes it at its next refresh, so one that's far away (not loaded) gets it when it's next loaded.
     */
    public void rename(UUID worker, Optional<String> name) {
        Entry old = this.entries.get(worker);
        if (old == null) {
            return;
        }
        this.entries.put(worker, new Entry(old.owner(), old.number(), old.dimension(), name, Optional.of(name.orElse("")),
                old.job(), old.order(), old.employedTicks(), old.farm(), old.storage(), old.collected()));
        this.setDirty();
    }

    public void remove(UUID worker) {
        if (this.entries.remove(worker) != null) {
            this.setDirty();
        }
    }

    public Optional<Entry> entry(UUID worker) {
        return Optional.ofNullable(this.entries.get(worker));
    }

    public List<Map.Entry<UUID, Entry>> forOwner(UUID owner) {
        List<Map.Entry<UUID, Entry>> result = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
            if (entry.getValue().owner().equals(owner)) {
                result.add(entry);
            }
        }
        result.sort((a, b) -> Integer.compare(a.getValue().number(), b.getValue().number()));
        return result;
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

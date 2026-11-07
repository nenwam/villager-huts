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
 */
public final class WorkerRoster extends SavedData {

    /** The last known state of one worker. */
    public record Entry(UUID owner, int number, ResourceKey<Level> dimension, Optional<String> customName, String job,
                        long employedTicks, BlockPos hutCenter, Optional<BoundingBox> workArea, boolean farmAssigned,
                        Map<Identifier, Integer> collected) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Codec.INT.fieldOf("number").forGetter(Entry::number),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.STRING.optionalFieldOf("custom_name").forGetter(Entry::customName),
                Codec.STRING.fieldOf("job").forGetter(Entry::job),
                Codec.LONG.fieldOf("employed_ticks").forGetter(Entry::employedTicks),
                BlockPos.CODEC.fieldOf("hut_center").forGetter(Entry::hutCenter),
                BoundingBox.CODEC.optionalFieldOf("work_area").forGetter(Entry::workArea),
                Codec.BOOL.fieldOf("farm_assigned").forGetter(Entry::farmAssigned),
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
        BoundingBox hut = worker.getHutBounds();
        if (owner == null || hut == null) {
            return;
        }
        Entry old = this.entries.get(worker.getUUID());
        int number = old != null && old.owner().equals(owner) ? old.number() : this.nextNumber(owner);
        Optional<String> name = worker.hasCustomName() ? Optional.of(worker.getCustomName().getString()) : Optional.empty();
        this.entries.put(worker.getUUID(), new Entry(owner, number, worker.level().dimension(), name, worker.getJob().id(),
                worker.getEmployedTicks(), hut.getCenter(), Optional.ofNullable(worker.getWorkArea()),
                worker.getFarmArea() != null, Map.copyOf(worker.getCollected())));
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

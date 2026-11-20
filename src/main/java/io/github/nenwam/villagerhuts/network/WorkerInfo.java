package io.github.nenwam.villagerhuts.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One row of the worker management screen, sent from the server.
 *
 * @param order          work, follow or stay (WorkerEntity.Order id)
 * @param loaded         false if the worker is far away (its chunk isn't loaded); the rest is its last known state
 * @param employedTicks  time spent working (24000 ticks = one Minecraft day)
 * @param farm           its assigned farm, if it has one
 * @param storage        its assigned chest (or barrel), if it has one
 * @param bed            its assigned bed (the head end), if it has one
 * @param collected      item id -> how many it has harvested in total
 * @param carried        items in its pockets right now
 * @param owner          the player who owns it
 * @param yours          whether the player this was sent to owns it (otherwise it's shared with them)
 * @param shared         the players its owner shares it with
 * @param rank           1 (Novice) to 5 (Master)
 * @param harvestedToday items it has harvested today
 * @param dailyLimit     items it can harvest in a day
 * @param tool           what it works or fights with (empty if nothing; just the item if it's far away)
 * @param health         its health now (-1 if it's far away)
 * @param maxHealth      its full health
 * @param ammo           a ranger's ammo in its pockets (-1 if it isn't a ranger with a weapon that needs ammo, or is far away)
 * @param defeated       entity type id -> how many of them a fighter has defeated
 * @param field          a farmer's field: farming, clearing or cleared (WorkerEntity.Field id)
 * @param party          the name of its owner's party (FTB Teams), whose players can all use it; empty if none
 */
public record WorkerInfo(UUID id, int number, Optional<String> customName, String job, String order, Component status, boolean loaded,
                         long employedTicks, ResourceKey<Level> dimension, Optional<BoundingBox> farm, Optional<BlockPos> storage,
                         Optional<BlockPos> bed, Map<ResourceLocation, Integer> collected, int carried, PlayerRef owner, boolean yours,
                         List<PlayerRef> shared, int rank, int harvestedToday, int dailyLimit, ItemStack tool, int health,
                         int maxHealth, int ammo, Map<ResourceLocation, Integer> defeated, String field, Optional<String> party) {

    /** A player, by id and name (the name as the server last knew it). */
    public record PlayerRef(UUID id, String name) {
        static final StreamCodec<RegistryFriendlyByteBuf, PlayerRef> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerRef::id,
                ByteBufCodecs.STRING_UTF8, PlayerRef::name,
                PlayerRef::new);
    }

    private static final StreamCodec<ByteBuf, Optional<String>> NAME = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<? super RegistryFriendlyByteBuf, ResourceKey<Level>> DIMENSION = ResourceKey.streamCodec(Registries.DIMENSION);
    private static final StreamCodec<ByteBuf, Optional<BoundingBox>> AREA = ByteBufCodecs.optional(ByteBufCodecs.fromCodec(BoundingBox.CODEC));
    private static final StreamCodec<ByteBuf, Optional<BlockPos>> POS = ByteBufCodecs.optional(BlockPos.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Integer>> COLLECTED =
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PlayerRef>> PLAYERS = PlayerRef.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerInfo> STREAM_CODEC = StreamCodec.of(WorkerInfo::write, WorkerInfo::read);

    private static void write(RegistryFriendlyByteBuf buf, WorkerInfo info) {
        UUIDUtil.STREAM_CODEC.encode(buf, info.id);
        buf.writeVarInt(info.number);
        NAME.encode(buf, info.customName);
        ByteBufCodecs.STRING_UTF8.encode(buf, info.job);
        ByteBufCodecs.STRING_UTF8.encode(buf, info.order);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, info.status);
        buf.writeBoolean(info.loaded);
        buf.writeVarLong(info.employedTicks);
        DIMENSION.encode(buf, info.dimension);
        AREA.encode(buf, info.farm);
        POS.encode(buf, info.storage);
        POS.encode(buf, info.bed);
        COLLECTED.encode(buf, info.collected);
        buf.writeVarInt(info.carried);
        PlayerRef.STREAM_CODEC.encode(buf, info.owner);
        buf.writeBoolean(info.yours);
        PLAYERS.encode(buf, info.shared);
        buf.writeVarInt(info.rank);
        buf.writeVarInt(info.harvestedToday);
        buf.writeVarInt(info.dailyLimit);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, info.tool);
        buf.writeVarInt(info.health);
        buf.writeVarInt(info.maxHealth);
        buf.writeVarInt(info.ammo);
        COLLECTED.encode(buf, info.defeated);
        ByteBufCodecs.STRING_UTF8.encode(buf, info.field);
        NAME.encode(buf, info.party);
    }

    private static WorkerInfo read(RegistryFriendlyByteBuf buf) {
        return new WorkerInfo(
                UUIDUtil.STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                NAME.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readBoolean(),
                buf.readVarLong(),
                DIMENSION.decode(buf),
                AREA.decode(buf),
                POS.decode(buf),
                POS.decode(buf),
                COLLECTED.decode(buf),
                buf.readVarInt(),
                PlayerRef.STREAM_CODEC.decode(buf),
                buf.readBoolean(),
                PLAYERS.decode(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                COLLECTED.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                NAME.decode(buf));
    }
}

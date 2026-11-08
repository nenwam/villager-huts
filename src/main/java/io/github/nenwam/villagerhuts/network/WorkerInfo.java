package io.github.nenwam.villagerhuts.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One row of the worker management screen, sent from the server.
 *
 * @param order         work, follow or stay (WorkerEntity.Order id)
 * @param loaded        false if the worker is far away (its chunk isn't loaded); the rest is its last known state
 * @param employedTicks time spent working (24000 ticks = one Minecraft day)
 * @param farm          its assigned farm, if it has one
 * @param storage       its assigned chest (or barrel), if it has one
 * @param collected     item id -> how many it has harvested in total
 * @param carried       items in its pockets right now
 */
public record WorkerInfo(UUID id, int number, Optional<String> customName, String job, String order, Component status, boolean loaded,
                         long employedTicks, ResourceKey<Level> dimension, Optional<BoundingBox> farm, Optional<BlockPos> storage,
                         Map<Identifier, Integer> collected, int carried) {

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<String>> NAME = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<? super RegistryFriendlyByteBuf, ResourceKey<Level>> DIMENSION = ResourceKey.streamCodec(Registries.DIMENSION);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<BoundingBox>> AREA = ByteBufCodecs.optional(ByteBufCodecs.fromCodec(BoundingBox.CODEC));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<BlockPos>> POS = ByteBufCodecs.optional(BlockPos.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<Identifier, Integer>> COLLECTED =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT);

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
        COLLECTED.encode(buf, info.collected);
        buf.writeVarInt(info.carried);
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
                COLLECTED.decode(buf),
                buf.readVarInt());
    }
}

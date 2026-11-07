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
 * @param loaded        false if the worker is far away (its chunk isn't loaded); the rest is its last known state
 * @param employedTicks time spent working (24000 ticks = one Minecraft day)
 * @param workArea      where it farms: its assigned farm, or its hut's grounds
 * @param collected     item id -> how many it has harvested in total
 * @param carried       items in its pockets right now
 */
public record WorkerInfo(UUID id, int number, Optional<String> customName, String job, Component status, boolean loaded,
                         long employedTicks, BlockPos hutCenter, ResourceKey<Level> dimension, Optional<BoundingBox> workArea,
                         boolean farmAssigned, Map<Identifier, Integer> collected, int carried) {

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<String>> NAME = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<? super RegistryFriendlyByteBuf, ResourceKey<Level>> DIMENSION = ResourceKey.streamCodec(Registries.DIMENSION);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<BoundingBox>> AREA = ByteBufCodecs.optional(ByteBufCodecs.fromCodec(BoundingBox.CODEC));
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<Identifier, Integer>> COLLECTED =
            ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerInfo> STREAM_CODEC = StreamCodec.of(WorkerInfo::write, WorkerInfo::read);

    private static void write(RegistryFriendlyByteBuf buf, WorkerInfo info) {
        UUIDUtil.STREAM_CODEC.encode(buf, info.id);
        buf.writeVarInt(info.number);
        NAME.encode(buf, info.customName);
        ByteBufCodecs.STRING_UTF8.encode(buf, info.job);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, info.status);
        buf.writeBoolean(info.loaded);
        buf.writeVarLong(info.employedTicks);
        BlockPos.STREAM_CODEC.encode(buf, info.hutCenter);
        DIMENSION.encode(buf, info.dimension);
        AREA.encode(buf, info.workArea);
        buf.writeBoolean(info.farmAssigned);
        COLLECTED.encode(buf, info.collected);
        buf.writeVarInt(info.carried);
    }

    private static WorkerInfo read(RegistryFriendlyByteBuf buf) {
        return new WorkerInfo(
                UUIDUtil.STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                NAME.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readBoolean(),
                buf.readVarLong(),
                BlockPos.STREAM_CODEC.decode(buf),
                DIMENSION.decode(buf),
                AREA.decode(buf),
                buf.readBoolean(),
                COLLECTED.decode(buf),
                buf.readVarInt());
    }
}

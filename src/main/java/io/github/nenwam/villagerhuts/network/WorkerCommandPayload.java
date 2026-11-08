package io.github.nenwam.villagerhuts.network;

import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: a button on a worker's page in the worker panel. Only works while the worker is loaded. */
public record WorkerCommandPayload(UUID worker, Command command) implements CustomPacketPayload {
    public enum Command { WORK, FOLLOW, STAY, TAKE_TOOL }

    public static final Type<WorkerCommandPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "worker_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerCommandPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WorkerCommandPayload::worker,
            ByteBufCodecs.idMapper(i -> Command.values()[i], Command::ordinal), WorkerCommandPayload::command,
            WorkerCommandPayload::new);

    @Override
    public Type<WorkerCommandPayload> type() {
        return TYPE;
    }
}

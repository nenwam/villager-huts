package io.github.nenwam.villagerhuts.network;

import java.util.List;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: the player's workers, sorted by number. */
public record WorkerListPayload(List<WorkerInfo> workers) implements CustomPacketPayload {
    public static final Type<WorkerListPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "worker_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkerListPayload> STREAM_CODEC =
            WorkerInfo.STREAM_CODEC.apply(ByteBufCodecs.list()).map(WorkerListPayload::new, WorkerListPayload::workers);

    @Override
    public Type<WorkerListPayload> type() {
        return TYPE;
    }
}

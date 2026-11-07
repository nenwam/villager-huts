package io.github.nenwam.villagerhuts.network;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "send me my workers". Sent every second while the worker screen is open. */
public record RequestWorkersPayload() implements CustomPacketPayload {
    public static final RequestWorkersPayload INSTANCE = new RequestWorkersPayload();
    public static final Type<RequestWorkersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "request_workers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestWorkersPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<RequestWorkersPayload> type() {
        return TYPE;
    }
}

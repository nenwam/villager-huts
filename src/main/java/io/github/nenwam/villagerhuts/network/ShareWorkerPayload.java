package io.github.nenwam.villagerhuts.network;

import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: a button on a worker's sharing page in the worker panel (see ModNetwork.shareWorker).
 * @param player who to share it with, stop sharing it with, or give it to (yourself, to stop using a worker shared with you)
 */
public record ShareWorkerPayload(UUID worker, UUID player, Action action) implements CustomPacketPayload {
    public enum Action { SHARE, UNSHARE, GIVE }

    public static final Type<ShareWorkerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "share_worker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShareWorkerPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ShareWorkerPayload::worker,
            UUIDUtil.STREAM_CODEC, ShareWorkerPayload::player,
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), ShareWorkerPayload::action,
            ShareWorkerPayload::new);

    @Override
    public Type<ShareWorkerPayload> type() {
        return TYPE;
    }
}

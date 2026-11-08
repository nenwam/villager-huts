package io.github.nenwam.villagerhuts.network;

import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AnvilMenu;

/** Client -> server: "rename my worker", from the worker panel. An empty name removes the worker's name. */
public record RenameWorkerPayload(UUID worker, String name) implements CustomPacketPayload {
    /** Same limit as renaming in an anvil. */
    public static final int MAX_NAME_LENGTH = AnvilMenu.MAX_NAME_LENGTH;
    public static final Type<RenameWorkerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "rename_worker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RenameWorkerPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RenameWorkerPayload::worker,
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), RenameWorkerPayload::name,
            RenameWorkerPayload::new);

    @Override
    public Type<RenameWorkerPayload> type() {
        return TYPE;
    }
}

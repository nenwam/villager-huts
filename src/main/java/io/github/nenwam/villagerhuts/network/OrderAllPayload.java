package io.github.nenwam.villagerhuts.network;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: an order for all of the player's own workers at once (the list page's WORK, FOLLOW or STAY button). */
public record OrderAllPayload(WorkerCommandPayload.Command order) implements CustomPacketPayload {
    public static final Type<OrderAllPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "order_all"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderAllPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> WorkerCommandPayload.Command.values()[i], WorkerCommandPayload.Command::ordinal), OrderAllPayload::order,
            OrderAllPayload::new);

    @Override
    public Type<OrderAllPayload> type() {
        return TYPE;
    }
}

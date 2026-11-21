package io.github.nenwam.villagerhuts.network;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: a line for the worker panel, shown for a few seconds: why a button did nothing, or what it did. */
public record PanelNoticePayload(Component message) implements CustomPacketPayload {
    public static final Type<PanelNoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "panel_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, PanelNoticePayload::message,
            PanelNoticePayload::new);

    @Override
    public Type<PanelNoticePayload> type() {
        return TYPE;
    }
}

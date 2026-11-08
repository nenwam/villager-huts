package io.github.nenwam.villagerhuts.network;

import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Server -> client: the worker you're giving a farm or chest to, or empty when that's over (see WorkerAssigning). */
public record AssignModePayload(Optional<Selected> selected) implements CustomPacketPayload {
    public static final AssignModePayload NONE = new AssignModePayload(Optional.empty());

    /**
     * @param name    what to call the worker on screen
     * @param newFarm making a new farm (shift + right-click), rather than giving it an existing farm or a chest
     * @param farm    its farm, if it has one (a chest's distance is measured from it)
     * @param ticks   how long until the selection runs out by itself
     */
    public record Selected(UUID worker, Component name, boolean newFarm, Optional<BoundingBox> farm,
                           int maxFarmSize, int newFarmSize, int maxDistance, int ticks) {
        static final StreamCodec<RegistryFriendlyByteBuf, Selected> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Selected::worker,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Selected::name,
                ByteBufCodecs.BOOL, Selected::newFarm,
                ByteBufCodecs.optional(ByteBufCodecs.fromCodec(BoundingBox.CODEC)), Selected::farm,
                ByteBufCodecs.VAR_INT, Selected::maxFarmSize,
                ByteBufCodecs.VAR_INT, Selected::newFarmSize,
                ByteBufCodecs.VAR_INT, Selected::maxDistance,
                ByteBufCodecs.VAR_INT, Selected::ticks,
                Selected::new);
    }

    public static final Type<AssignModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(VillagerHuts.MODID, "assign_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssignModePayload> STREAM_CODEC =
            ByteBufCodecs.optional(Selected.STREAM_CODEC).map(AssignModePayload::new, AssignModePayload::selected);

    @Override
    public Type<AssignModePayload> type() {
        return TYPE;
    }
}

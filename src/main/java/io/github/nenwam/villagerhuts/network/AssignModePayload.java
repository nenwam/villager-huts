package io.github.nenwam.villagerhuts.network;

import java.util.Optional;
import java.util.UUID;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Server -> client: the worker you're giving a farm (or post), chest or bed to, or empty when that's over (see WorkerAssigning). */
public record AssignModePayload(Optional<Selected> selected) implements CustomPacketPayload {
    public static final AssignModePayload NONE = new AssignModePayload(Optional.empty());

    /**
     * @param name    what to call the worker on screen
     * @param newFarm making a new farm (shift + right-click), rather than giving it an existing farm or a chest
     * @param farm    its farm (or a fighter's guard area), if it has one (a chest's distance is measured from it)
     * @param ticks   how long until the selection runs out by itself
     * @param fighter it's a fighter: clicking a block gives it a post there (guarding guardRadius blocks round it)
     */
    public record Selected(UUID worker, Component name, boolean newFarm, Optional<BoundingBox> farm,
                           int maxFarmSize, int newFarmSize, int maxDistance, int ticks, boolean fighter, int guardRadius) {
        private static final StreamCodec<ByteBuf, Optional<BoundingBox>> AREA = ByteBufCodecs.optional(ByteBufCodecs.fromCodec(BoundingBox.CODEC));
        static final StreamCodec<RegistryFriendlyByteBuf, Selected> STREAM_CODEC = StreamCodec.of(Selected::write, Selected::read);

        private static void write(RegistryFriendlyByteBuf buf, Selected selected) {
            UUIDUtil.STREAM_CODEC.encode(buf, selected.worker);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, selected.name);
            buf.writeBoolean(selected.newFarm);
            AREA.encode(buf, selected.farm);
            buf.writeVarInt(selected.maxFarmSize);
            buf.writeVarInt(selected.newFarmSize);
            buf.writeVarInt(selected.maxDistance);
            buf.writeVarInt(selected.ticks);
            buf.writeBoolean(selected.fighter);
            buf.writeVarInt(selected.guardRadius);
        }

        private static Selected read(RegistryFriendlyByteBuf buf) {
            return new Selected(UUIDUtil.STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(),
                    AREA.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
        }
    }

    public static final Type<AssignModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "assign_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssignModePayload> STREAM_CODEC =
            ByteBufCodecs.optional(Selected.STREAM_CODEC).map(AssignModePayload::new, AssignModePayload::selected);

    @Override
    public Type<AssignModePayload> type() {
        return TYPE;
    }
}

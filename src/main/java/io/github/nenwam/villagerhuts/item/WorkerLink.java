package io.github.nenwam.villagerhuts.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Stored on a Work Area Marker once you link it to a worker. Also carries the server's farm
 * limits so the preview on your screen uses the same numbers the server will check.
 */
public record WorkerLink(UUID workerId, BlockPos hutCenter, int maxFarmSize, int maxFarmDistance) {
    public static final Codec<WorkerLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("worker").forGetter(WorkerLink::workerId),
            BlockPos.CODEC.fieldOf("hut_center").forGetter(WorkerLink::hutCenter),
            Codec.INT.fieldOf("max_farm_size").forGetter(WorkerLink::maxFarmSize),
            Codec.INT.fieldOf("max_farm_distance").forGetter(WorkerLink::maxFarmDistance)
    ).apply(i, WorkerLink::new));

    public static final StreamCodec<ByteBuf, WorkerLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WorkerLink::workerId,
            BlockPos.STREAM_CODEC, WorkerLink::hutCenter,
            ByteBufCodecs.VAR_INT, WorkerLink::maxFarmSize,
            ByteBufCodecs.VAR_INT, WorkerLink::maxFarmDistance,
            WorkerLink::new);
}

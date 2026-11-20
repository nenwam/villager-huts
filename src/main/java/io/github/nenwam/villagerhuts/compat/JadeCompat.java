package io.github.nenwam.villagerhuts.compat;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (when it's installed; Jade finds this itself): looking at a worker shows its job and level, its owner, and what
 * it's doing, under its name. The server sends the owner and status (only it knows them).
 */
@WailaPlugin
public class JadeCompat implements IWailaPlugin {
    static final ResourceLocation WORKER = ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "worker");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(WorkerData.INSTANCE, WorkerEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(WorkerLines.INSTANCE, WorkerEntity.class);
    }

    // Server side: its level, owner and status, for the lines below.
    private enum WorkerData implements IServerDataProvider<EntityAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof WorkerEntity worker) || !(worker.level() instanceof ServerLevel level)) {
                return;
            }
            data.putInt("rank", worker.getRank());
            if (worker.getOwnerId() != null) {
                data.putString("owner", WorkerRoster.playerName(level.getServer(), worker.getOwnerId()).getString());
            }
            data.putString("status", Component.Serializer.toJson(worker.currentStatus(), level.registryAccess()));
        }

        @Override
        public ResourceLocation getUid() {
            return WORKER;
        }
    }

    // Client side: "Farmer, Journeyman", "Owner: Connor", and what it's doing.
    private enum WorkerLines implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof WorkerEntity worker)) {
                return;
            }
            CompoundTag data = accessor.getServerData();
            Component job = worker.getJob().displayName();
            tooltip.add(data.contains("rank") ? Component.translatable("jade.villagerhuts.job_level", job, WorkerEntity.rankName(data.getInt("rank"))) : job);
            if (data.contains("owner")) {
                tooltip.add(Component.translatable("jade.villagerhuts.owner", data.getString("owner")).withStyle(ChatFormatting.GRAY));
            }
            if (data.contains("status")) {
                Component status = Component.Serializer.fromJson(data.getString("status"), accessor.getLevel().registryAccess());
                if (status != null) {
                    tooltip.add(status.copy().withStyle(ChatFormatting.GRAY));
                }
            }
        }

        @Override
        public ResourceLocation getUid() {
            return WORKER;
        }
    }
}

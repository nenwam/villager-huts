package io.github.nenwam.villagerhuts.entity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import io.github.nenwam.villagerhuts.entity.goal.DepositItemsGoal;
import io.github.nenwam.villagerhuts.entity.goal.DescribesActivity;
import io.github.nenwam.villagerhuts.entity.goal.FarmerWorkGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchSeedsGoal;
import io.github.nenwam.villagerhuts.entity.goal.FleeHostilesGoal;
import io.github.nenwam.villagerhuts.entity.goal.ShelterAtNightGoal;
import io.github.nenwam.villagerhuts.entity.goal.WorkReach;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.registry.ModTags;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A villager-like worker that belongs to a hut. It works during the day, shelters inside the hut
 * at night, and runs from hostile mobs.
 *
 * Interactions (only the player who built the hut can do these):
 *  - Right-click with a job tool (e.g. a hoe): the worker takes it and starts that job.
 *  - Shift-right-click with an empty hand: take the tool back; the worker becomes unemployed.
 *  - Right-click with an empty hand: shows what the worker is doing.
 */
public class WorkerEntity extends PathfinderMob implements InventoryCarrier {
    private static final EntityDataAccessor<String> DATA_JOB =
            SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.STRING);
    /** Ticks without any work before a worker counts as idle (and goes to put its items away). */
    private static final int IDLE_TICKS = 100;
    /** How far workers can plan a walk. Raised automatically for farms assigned far from the hut. */
    private static final double BASE_FOLLOW_RANGE = 48.0;
    private static final Codec<Map<Identifier, Integer>> COLLECTED_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);

    private final SimpleContainer inventory = new SimpleContainer(9);
    private @Nullable UUID ownerId;
    private @Nullable BoundingBox hutBounds;
    private @Nullable BoundingBox farmArea;
    private @Nullable BlockPos shelterPos;
    private boolean shelterSearched;
    private boolean sheltered;
    private int lastWorkTick = -IDLE_TICKS;
    private final Map<Identifier, Integer> collected = new LinkedHashMap<>();
    private long employedTicks;
    private int farmlandInArea = -1; // -1 = not checked yet
    private int emptyFarmland = -1;
    private int ripeCrops = -1;
    private StorageState storageState = StorageState.OK;
    private boolean farmUnreachable;

    /** What the last trip to the hut's storage found. */
    public enum StorageState { OK, NONE, UNREACHABLE }

    public WorkerEntity(EntityType<? extends WorkerEntity> type, Level level) {
        super(type, level);
        this.getNavigation().setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // The job is synced to players' games so the right outfit is drawn.
        builder.define(DATA_JOB, WorkerJob.NONE.id());
    }

    @Override
    protected void registerGoals() {
        // Lower number = higher priority. A goal can't start while a higher-priority one is using the legs.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.85));         // got hurt: run
        this.goalSelector.addGoal(2, new FleeHostilesGoal(this));        // monster nearby: run
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new ShelterAtNightGoal(this, 0.6)); // dark: go inside and stay
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.6));
        this.goalSelector.addGoal(6, new DepositItemsGoal(this, 0.6));
        this.goalSelector.addGoal(7, new FetchSeedsGoal(this, 0.6));     // out of seeds: get some from the chest
        this.goalSelector.addGoal(8, new FarmerWorkGoal(this, 0.6));
        this.goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ hut

    /**
     * Ties this worker to the hut that was just built for it.
     * @param shelter where to stay at night (the hut's "worker" marker), or null to find a spot indoors
     */
    public void assignHut(BoundingBox bounds, @Nullable Player owner, @Nullable BlockPos shelter) {
        this.hutBounds = bounds;
        this.ownerId = owner == null ? null : owner.getUUID();
        this.shelterPos = shelter;
        this.shelterSearched = shelter != null;
        this.updateHomeArea();
    }

    /** The hut's area (including its own farm, for a Farmer Hut). Its storage chest is in here. */
    public @Nullable BoundingBox getHutBounds() {
        return this.hutBounds;
    }

    public @Nullable UUID getOwnerId() {
        return this.ownerId;
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerId == null || this.ownerId.equals(player.getUUID());
    }

    // ------------------------------------------------------------------ farm

    /** Where the worker works: the farm assigned with a Work Area Marker, or else its hut's own grounds. */
    public @Nullable BoundingBox getWorkArea() {
        return this.farmArea != null ? this.farmArea : this.hutBounds;
    }

    public @Nullable BoundingBox getFarmArea() {
        return this.farmArea;
    }

    public void assignFarm(BoundingBox area) {
        this.farmArea = area;
        this.updateHomeArea();
    }

    public void clearFarm() {
        this.farmArea = null;
        this.updateHomeArea();
    }

    // Wander only within the hut, its grounds, and its farm; and be able to plan walks across all of it.
    private void updateHomeArea() {
        if (this.hutBounds == null) {
            return;
        }
        BoundingBox area = this.hutBounds;
        if (this.farmArea != null) {
            area = new BoundingBox(
                    Math.min(area.minX(), this.farmArea.minX()), Math.min(area.minY(), this.farmArea.minY()), Math.min(area.minZ(), this.farmArea.minZ()),
                    Math.max(area.maxX(), this.farmArea.maxX()), Math.max(area.maxY(), this.farmArea.maxY()), Math.max(area.maxZ(), this.farmArea.maxZ()));
        }
        int radius = Math.max(area.getXSpan(), area.getZSpan()) / 2 + 2;
        this.setHomeTo(area.getCenter(), radius);
        AttributeInstance followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(Math.max(BASE_FOLLOW_RANGE, Math.max(area.getXSpan(), area.getZSpan()) + 8));
        }
    }

    // ------------------------------------------------------------------ shelter

    /** The indoor spot this worker stays at during the night. */
    public @Nullable BlockPos getShelterPos() {
        if (this.shelterPos == null && !this.shelterSearched && this.hutBounds != null && !this.level().isClientSide()) {
            this.shelterSearched = true; // workers from older huts: find a spot once
            this.shelterPos = this.findIndoorSpot(this.hutBounds);
        }
        return this.shelterPos;
    }

    public boolean isSheltered() {
        return this.sheltered;
    }

    public void setSheltered(boolean sheltered) {
        this.sheltered = sheltered;
    }

    // A spot in the hut with a roof overhead, walls on all four sides, and room to stand.
    private @Nullable BlockPos findIndoorSpot(BoundingBox box) {
        Level level = this.level();
        BlockPos center = box.getCenter();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (WorkReach.isStandable(level, pos) && !level.canSeeSky(pos) && isEnclosed(level, pos)) {
                double distance = pos.distSqr(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private static boolean isEnclosed(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            boolean wall = false;
            for (int i = 1; i <= 6 && !wall; i++) {
                BlockPos side = pos.relative(direction, i);
                wall = !level.getBlockState(side).getCollisionShape(level, side).isEmpty();
            }
            if (!wall) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ jobs

    public WorkerJob getJob() {
        return WorkerJob.byId(this.entityData.get(DATA_JOB));
    }

    public void setJob(WorkerJob job) {
        this.entityData.set(DATA_JOB, job.id());
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        WorkerJob offeredJob = WorkerJob.forTool(held);
        if (!held.isEmpty() && offeredJob == null) {
            return super.mobInteract(player, hand); // name tags, leads, etc. work as normal
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (this.ownerId != null && !this.ownerId.equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.not_owner"));
            return InteractionResult.CONSUME;
        }

        if (offeredJob != null && this.getJob() == WorkerJob.NONE) {
            this.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
            this.setGuaranteedDrop(EquipmentSlot.MAINHAND); // you get the tool back if the worker dies
            held.consume(1, player);
            this.setJob(offeredJob);
            this.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getEyeY() + 0.3, this.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.job_assigned", offeredJob.displayName()));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (held.isEmpty() && player.isSecondaryUseActive() && this.getJob() != WorkerJob.NONE) {
            ItemStack tool = this.getMainHandItem().copy();
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (!player.getInventory().add(tool)) {
                this.spawnAtLocation(level, tool);
            }
            this.setJob(WorkerJob.NONE);
            player.sendOverlayMessage(Component.translatable("message.villagerhuts.job_removed"));
            return InteractionResult.SUCCESS_SERVER;
        }

        player.sendOverlayMessage(this.statusMessage());
        return InteractionResult.SUCCESS_SERVER;
    }

    private Component statusMessage() {
        WorkerJob job = this.getJob();
        Component farm = this.farmArea == null
                ? Component.translatable("message.villagerhuts.status_farm_hut")
                : Component.translatable("message.villagerhuts.status_farm_assigned",
                        this.farmArea.getXSpan(), this.farmArea.getZSpan(), this.farmArea.getCenter().getX(), this.farmArea.getCenter().getZ());
        if (job == WorkerJob.NONE) {
            return Component.translatable("message.villagerhuts.status_unemployed", farm);
        }
        int carried = 0;
        for (ItemStack stack : this.inventory.getItems()) {
            carried += stack.getCount();
        }
        return Component.translatable("message.villagerhuts.status_working", job.displayName(), carried, farm);
    }

    // ------------------------------------------------------------------ inventory & work tracking

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    /** Puts items in the worker's pockets; anything that doesn't fit is dropped on the ground. */
    public void storeOrDrop(ServerLevel level, ItemStack stack) {
        ItemStack rest = this.inventory.addItem(stack);
        if (!rest.isEmpty()) {
            this.spawnAtLocation(level, rest);
        }
    }

    public boolean hasFreeSlot() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Job goals call this whenever they get something done. */
    public void markWorked() {
        this.lastWorkTick = this.tickCount;
    }

    /** True when the worker hasn't had anything to do for a few seconds. */
    public boolean isIdle() {
        return this.tickCount - this.lastWorkTick > IDLE_TICKS;
    }

    // ------------------------------------------------------------------ stats & status (worker management screen)

    /** Counts something the worker gathered (e.g. harvested crops). */
    public void recordCollected(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.collected.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        }
    }

    public Map<Identifier, Integer> getCollected() {
        return this.collected;
    }

    /** Ticks spent with a job while loaded (24000 ticks = one Minecraft day). */
    public long getEmployedTicks() {
        return this.employedTicks;
    }

    public int getCarriedCount() {
        int carried = 0;
        for (ItemStack stack : this.inventory.getItems()) {
            carried += stack.getCount();
        }
        return carried;
    }

    /** Set by the farming goal after each look over its work area. */
    public void setFarmCounts(int farmland, int empty, int ripe) {
        this.farmlandInArea = farmland;
        this.emptyFarmland = empty;
        this.ripeCrops = ripe;
    }

    /** Farmland spots with nothing planted (as of the last look). */
    public int getEmptyFarmland() {
        return this.emptyFarmland;
    }

    /** Crops ready to harvest (as of the last look). */
    public int getRipeCrops() {
        return this.ripeCrops;
    }

    /**
     * Seeds a farmer can plant: in the farmer_seeds tag, and growing into a crop it can harvest later.
     * (Vanilla's villager seed tag includes pitcher pods, whose plant isn't a normal crop: planted, it
     * would sit on the farmland forever.)
     */
    public static boolean isPlantableSeed(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModTags.FARMER_SEEDS)
                && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CropBlock;
    }

    public void setStorageState(StorageState state) {
        this.storageState = state;
    }

    /** Set by the farming goal when there's work to do but no way to walk to it (e.g. fenced off). */
    public void setFarmUnreachable(boolean unreachable) {
        this.farmUnreachable = unreachable;
    }

    public boolean hasSeeds() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (isPlantableSeed(stack)) {
                return true;
            }
        }
        return false;
    }

    /** What the worker is doing right now, in words. */
    public Component currentStatus() {
        WrappedGoal running = this.goalSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .filter(goal -> goal.getGoal() instanceof DescribesActivity || goal.getGoal() instanceof PanicGoal)
                .min(Comparator.comparingInt(WrappedGoal::getPriority))
                .orElse(null);
        if (running != null) {
            return running.getGoal() instanceof DescribesActivity activity
                    ? activity.describeActivity()
                    : Component.translatable("activity.villagerhuts.panicking");
        }
        if (this.getJob() == WorkerJob.NONE) {
            return Component.translatable("activity.villagerhuts.unemployed");
        }
        if (this.farmlandInArea == 0) {
            return Component.translatable("activity.villagerhuts.no_farmland");
        }
        boolean outOfSeeds = this.getJob() == WorkerJob.FARMER && !this.hasSeeds() && this.emptyFarmland > 0 && this.ripeCrops == 0;
        boolean needsStorage = !this.hasFreeSlot() || outOfSeeds;
        if (needsStorage && this.storageState == StorageState.NONE) {
            return Component.translatable("activity.villagerhuts.no_storage");
        }
        if (needsStorage && this.storageState == StorageState.UNREACHABLE) {
            return Component.translatable("activity.villagerhuts.storage_unreachable");
        }
        if (!this.hasFreeSlot()) {
            return Component.translatable("activity.villagerhuts.pockets_full");
        }
        if (outOfSeeds) {
            return Component.translatable("activity.villagerhuts.out_of_seeds");
        }
        if (this.farmUnreachable && (this.ripeCrops > 0 || (this.hasSeeds() && this.emptyFarmland > 0))) {
            return Component.translatable("activity.villagerhuts.farm_unreachable");
        }
        return Component.translatable("activity.villagerhuts.waiting");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive()) {
            if (this.getJob() != WorkerJob.NONE) {
                this.employedTicks++;
            }
            if (this.tickCount % 20 == 0) {
                WorkerRoster.get(level.getServer()).update(this);
            }
        }
    }

    // Dying (or being removed for good) takes the worker off its owner's list right away.
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (this.dead && this.level() instanceof ServerLevel level) {
            WorkerRoster.get(level.getServer()).remove(this.getUUID());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
            WorkerRoster.get(level.getServer()).remove(this.getUUID());
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ vanilla behavior tweaks

    // Workers never despawn, even when no player is nearby.
    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    // Drop carried items on death, as well as the tool.
    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        this.inventory.removeAllItems().forEach(stack -> this.spawnAtLocation(level, stack));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("job", this.getJob().id());
        output.storeNullable("owner", UUIDUtil.CODEC, this.ownerId);
        output.storeNullable("hut_bounds", BoundingBox.CODEC, this.hutBounds);
        output.storeNullable("farm_area", BoundingBox.CODEC, this.farmArea);
        output.storeNullable("shelter_pos", BlockPos.CODEC, this.shelterPos);
        output.store("collected", COLLECTED_CODEC, this.collected);
        output.putLong("employed_ticks", this.employedTicks);
        this.writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setJob(WorkerJob.byId(input.getStringOr("job", WorkerJob.NONE.id())));
        this.ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.hutBounds = input.read("hut_bounds", BoundingBox.CODEC).orElse(null);
        this.farmArea = input.read("farm_area", BoundingBox.CODEC).orElse(null);
        this.shelterPos = input.read("shelter_pos", BlockPos.CODEC).orElse(null);
        this.shelterSearched = this.shelterPos != null;
        this.collected.clear();
        this.collected.putAll(input.read("collected", COLLECTED_CODEC).orElse(Map.of()));
        this.employedTicks = input.getLongOr("employed_ticks", 0L);
        this.readInventoryFromTag(input);
        this.updateHomeArea(); // also upgrades workers saved before follow range was raised
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }
}

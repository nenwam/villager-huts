package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

import io.github.nenwam.villagerhuts.entity.goal.DepositItemsGoal;
import io.github.nenwam.villagerhuts.entity.goal.DescribesActivity;
import io.github.nenwam.villagerhuts.entity.goal.FarmerWorkGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchBoneMealGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchSeedsGoal;
import io.github.nenwam.villagerhuts.entity.goal.FleeHostilesGoal;
import io.github.nenwam.villagerhuts.entity.goal.FollowOrStayGoal;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
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
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A villager-like worker, hired with a Farmer item. It works the farm it's assigned, takes seeds from
 * and stores its harvest in the chest it's assigned, shelters indoors near that chest at night (if
 * there's an inside near it), and runs from hostile mobs.
 *
 * Interactions (only the player who hired it can do these):
 *  - Right-click with an empty hand: select it, then right-click a farm or a chest to assign it
 *    (see WorkerAssigning). Right-click it again to cancel.
 *  - Shift + right-click with an empty hand: select it to make a new farm, then right-click the ground.
 *  - Right-click with a job tool (e.g. a hoe) while unemployed: the worker takes it and starts that job.
 * The worker panel can also give it orders (work, follow you, stay put) and take its tool back.
 * A new worker follows the player who hired it until it's given a farm or chest.
 */
public class WorkerEntity extends PathfinderMob implements InventoryCarrier {
    private static final EntityDataAccessor<String> DATA_JOB =
            SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.STRING);
    /** Ticks without any work before a worker counts as idle (and goes to put its items away). */
    private static final int IDLE_TICKS = 100;
    /** How far workers can plan a walk. Raised automatically for farms assigned far from the hut. */
    private static final double BASE_FOLLOW_RANGE = 48.0;
    /** How far from its chest a worker looks for somewhere indoors to spend the night. */
    private static final int SHELTER_SEARCH_RADIUS = 6;
    private static final int SHELTER_RETRY_TICKS = 1200;
    private static final Codec<Map<Identifier, Integer>> COLLECTED_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);

    private final SimpleContainer inventory = new SimpleContainer(9);
    private @Nullable UUID ownerId;
    private Order order = Order.WORK;
    private @Nullable BlockPos stayPos;
    private @Nullable BoundingBox farmArea;
    private boolean tillsLand; // a farm it made itself (see FarmPlan): it tills the land and puts the water in
    private final List<BlockPos> plannedWater = new ArrayList<>();
    private @Nullable BlockPos storagePos;
    private @Nullable BlockPos shelterPos; // found near the chest when first needed, not saved
    private int nextShelterSearchTick;
    private boolean sheltered;
    private int lastWorkTick = -IDLE_TICKS;
    private final Map<Identifier, Integer> collected = new LinkedHashMap<>();
    private long employedTicks;
    private int farmlandInArea = -1; // -1 = not checked yet
    private int emptyFarmland = -1;
    private int ripeCrops = -1;
    private int growingCrops = -1;
    private boolean farmWorkDone;
    private boolean farmWorkStuck;
    private StorageState storageState = StorageState.OK;
    private boolean farmUnreachable;

    /** What the last trip to its chest found. */
    public enum StorageState { OK, NONE, UNREACHABLE }

    /** What it's been told to do, from the worker panel. */
    public enum Order {
        WORK("work"), FOLLOW("follow"), STAY("stay");

        private final String id;

        Order(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Order byId(String id) {
            for (Order order : values()) {
                if (order.id.equals(id)) {
                    return order;
                }
            }
            return WORK;
        }
    }

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
        this.goalSelector.addGoal(4, new FollowOrStayGoal(this, 0.7));    // told to follow or stay: nothing else moves it
        this.goalSelector.addGoal(5, new ShelterAtNightGoal(this, 0.6)); // dark: go inside and stay
        this.goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.6));
        this.goalSelector.addGoal(7, new DepositItemsGoal(this, 0.6));
        this.goalSelector.addGoal(8, new FetchSeedsGoal(this, 0.6));     // out of seeds: get some from the chest
        this.goalSelector.addGoal(9, new FetchBoneMealGoal(this, 0.6));  // crops growing and bone meal in the chest
        this.goalSelector.addGoal(10, new FarmerWorkGoal(this, 0.6));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(13, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ owner

    /** Ties a newly hired worker to the player who placed it. It follows them until it's given a farm or chest. */
    public void hire(@Nullable Player owner) {
        this.ownerId = owner == null ? null : owner.getUUID();
        this.setOrder(Order.FOLLOW);
    }

    public @Nullable UUID getOwnerId() {
        return this.ownerId;
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerId == null || this.ownerId.equals(player.getUUID());
    }

    /** The player who hired it, if they're in this world (dimension) right now. */
    public @Nullable Player getOwner() {
        return this.ownerId == null ? null : this.level().getPlayerByUUID(this.ownerId);
    }

    // ------------------------------------------------------------------ orders

    public Order getOrder() {
        return this.order;
    }

    /** Work (its farm and chest), follow its owner, or stay where it is now. */
    public void setOrder(Order order) {
        this.order = order;
        this.stayPos = order == Order.STAY ? this.blockPosition() : null;
        this.getNavigation().stop();
        this.updateHomeArea();
    }

    /** Where it was told to stay (it walks back there if it gets pushed or chased away). */
    public @Nullable BlockPos getStayPos() {
        return this.stayPos;
    }

    /** Gives the job tool back to the player (or drops it at their feet); the worker becomes unemployed. */
    public void returnTool(Player player) {
        ItemStack tool = this.getMainHandItem().copy();
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (!tool.isEmpty() && !player.getInventory().add(tool) && player.level() instanceof ServerLevel level) {
            player.spawnAtLocation(level, tool); // no room: at their feet
        }
        this.setJob(WorkerJob.NONE);
    }

    // ------------------------------------------------------------------ farm & chest

    /** The farm the worker works (see WorkerAssigning). Null until it's given one. */
    public @Nullable BoundingBox getFarmArea() {
        return this.farmArea;
    }

    /** An existing farm to work. Giving a worker work also sets it working, if it was following or staying. */
    public void assignFarm(BoundingBox area) {
        this.farmArea = area;
        this.tillsLand = false;
        this.plannedWater.clear();
        this.setOrder(Order.WORK);
    }

    /** A new farm to make (see FarmPlan): it tills the land in the area and puts water where it's planned. */
    public void createFarm(BoundingBox area, List<BlockPos> water) {
        this.farmArea = area;
        this.tillsLand = true;
        this.plannedWater.clear();
        this.plannedWater.addAll(water);
        this.setOrder(Order.WORK);
    }

    /** Whether it made this farm itself, so it tills (and keeps tilling) the land in it. */
    public boolean tillsLand() {
        return this.tillsLand;
    }

    /** Water still to be put in for the farm it's making. */
    public List<BlockPos> getPlannedWater() {
        return List.copyOf(this.plannedWater);
    }

    public boolean isPlannedWater(BlockPos pos) {
        return this.plannedWater.contains(pos);
    }

    public void finishPlannedWater(BlockPos pos) {
        this.plannedWater.remove(pos);
    }

    /** The chest (or barrel) it takes seeds from and stores its harvest in. Null until it's given one. */
    public @Nullable BlockPos getStoragePos() {
        return this.storagePos;
    }

    public void assignStorage(BlockPos pos) {
        this.storagePos = pos.immutable();
        this.storageState = StorageState.OK;
        this.shelterPos = null; // its night shelter is near the chest: look again
        this.nextShelterSearchTick = 0;
        this.setOrder(Order.WORK);
    }

    /** Whether its chest is there and has something in it that matches. */
    public boolean chestHas(Predicate<ItemStack> wanted) {
        BlockPos pos = this.storagePos;
        if (pos == null || !this.level().isLoaded(pos) || !this.level().getBlockState(pos).is(ModTags.WORKER_STORAGE)) {
            return false;
        }
        Container chest = HopperBlockEntity.getContainerAt(this.level(), pos);
        if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (wanted.test(chest.getItem(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    // While working: wander only around its farm and chest, and be able to plan walks across all of it.
    // Following or staying: no limits (the order decides where it goes).
    private void updateHomeArea() {
        if (this.order != Order.WORK) {
            this.clearHome();
            return;
        }
        BoundingBox area = this.farmArea;
        if (this.storagePos != null) {
            BlockPos chest = this.storagePos;
            area = area == null ? new BoundingBox(chest) : new BoundingBox(
                    Math.min(area.minX(), chest.getX()), Math.min(area.minY(), chest.getY()), Math.min(area.minZ(), chest.getZ()),
                    Math.max(area.maxX(), chest.getX()), Math.max(area.maxY(), chest.getY()), Math.max(area.maxZ(), chest.getZ()));
        }
        if (area == null) {
            this.clearHome();
            return;
        }
        int radius = Math.max(area.getXSpan(), area.getZSpan()) / 2 + 2;
        this.setHomeTo(area.getCenter(), radius);
        AttributeInstance followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(Math.max(BASE_FOLLOW_RANGE, Math.max(area.getXSpan(), area.getZSpan()) + 8));
        }
    }

    // ------------------------------------------------------------------ shelter

    /**
     * The indoor spot this worker stays at during the night: the nearest one to its chest. Null if there's
     * nowhere indoors near the chest (it looks again every minute, in case you build around it).
     */
    public @Nullable BlockPos getShelterPos() {
        if (this.shelterPos == null && this.storagePos != null && this.tickCount >= this.nextShelterSearchTick
                && !this.level().isClientSide()) {
            this.nextShelterSearchTick = this.tickCount + SHELTER_RETRY_TICKS;
            BlockPos chest = this.storagePos;
            int r = SHELTER_SEARCH_RADIUS;
            this.shelterPos = this.findIndoorSpot(new BoundingBox(chest.getX() - r, chest.getY() - 2, chest.getZ() - r,
                    chest.getX() + r, chest.getY() + 2, chest.getZ() + r));
        }
        return this.shelterPos;
    }

    public boolean isSheltered() {
        return this.sheltered;
    }

    public void setSheltered(boolean sheltered) {
        this.sheltered = sheltered;
    }

    // A spot in the box with a roof overhead, walls on all four sides, and room to stand.
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

        if (player instanceof ServerPlayer serverPlayer) {
            // Next: right-click a farm or chest; or, sneaking, the ground for a new farm.
            WorkerAssigning.toggle(serverPlayer, this, held.isEmpty() && player.isSecondaryUseActive());
        }
        return InteractionResult.SUCCESS_SERVER;
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

    /**
     * Set by the farming goal after each look over its farm (land still to be tilled counts as empty farmland).
     * Spots it's skipping for now (e.g. it can't get to them) are left out of the counts.
     * @param done  nothing left it can do there right now, and nothing in its chest (seeds, bone meal) that would change that
     * @param stuck there's work it's skipping for now
     */
    public void setFarmCounts(int farmland, int empty, int ripe, int growing, boolean done, boolean stuck) {
        this.farmlandInArea = farmland;
        this.emptyFarmland = empty;
        this.ripeCrops = ripe;
        this.growingCrops = growing;
        this.farmWorkDone = done;
        this.farmWorkStuck = stuck;
    }

    /** Crops still growing (as of the last look). */
    public int getGrowingCrops() {
        return this.growingCrops;
    }

    /**
     * All its work is done for now: everything it can harvest, plant, till or bone-meal is done, and it's had
     * nothing to do for a few seconds. Then it puts everything it carries away in its chest.
     */
    public boolean isWorkDone() {
        boolean nothingToDo = this.getJob() == WorkerJob.NONE || this.farmArea == null || this.farmWorkDone;
        return nothingToDo && this.isIdle();
    }

    public static boolean isBoneMeal(ItemStack stack) {
        return stack.is(Items.BONE_MEAL);
    }

    public boolean hasBoneMeal() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (isBoneMeal(stack)) {
                return true;
            }
        }
        return false;
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
        if (this.order == Order.FOLLOW) {
            return Component.translatable(this.getOwner() != null ? "activity.villagerhuts.following" : "activity.villagerhuts.following_waiting");
        }
        if (this.order == Order.STAY) {
            return Component.translatable("activity.villagerhuts.staying");
        }
        if (this.getJob() == WorkerJob.NONE) {
            return Component.translatable("activity.villagerhuts.unemployed");
        }
        if (this.farmArea == null) {
            return Component.translatable("activity.villagerhuts.no_farm");
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
        if (this.farmUnreachable && this.farmWorkStuck) {
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
        output.putString("order", this.order.id());
        output.storeNullable("stay_pos", BlockPos.CODEC, this.stayPos);
        output.storeNullable("farm_area", BoundingBox.CODEC, this.farmArea);
        output.putBoolean("tills_land", this.tillsLand);
        output.store("planned_water", BlockPos.CODEC.listOf(), List.copyOf(this.plannedWater));
        output.storeNullable("storage_pos", BlockPos.CODEC, this.storagePos);
        output.store("collected", COLLECTED_CODEC, this.collected);
        output.putLong("employed_ticks", this.employedTicks);
        this.writeInventoryToTag(output);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setJob(WorkerJob.byId(input.getStringOr("job", WorkerJob.NONE.id())));
        this.ownerId = input.read("owner", UUIDUtil.CODEC).orElse(null);
        this.order = Order.byId(input.getStringOr("order", Order.WORK.id()));
        this.stayPos = input.read("stay_pos", BlockPos.CODEC).orElse(null);
        this.farmArea = input.read("farm_area", BoundingBox.CODEC).orElse(null);
        this.tillsLand = input.getBooleanOr("tills_land", false);
        this.plannedWater.clear();
        this.plannedWater.addAll(input.read("planned_water", BlockPos.CODEC.listOf()).orElse(List.of()));
        this.storagePos = input.read("storage_pos", BlockPos.CODEC).orElse(null);
        this.collected.clear();
        this.collected.putAll(input.read("collected", COLLECTED_CODEC).orElse(Map.of()));
        this.employedTicks = input.getLongOr("employed_ticks", 0L);
        this.readInventoryFromTag(input);
        this.updateHomeArea();
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

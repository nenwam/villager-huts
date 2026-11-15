package io.github.nenwam.villagerhuts.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;

import io.github.nenwam.villagerhuts.Config;
import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.goal.AvoidCreepersGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchAmmoGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchFoodGoal;
import io.github.nenwam.villagerhuts.entity.goal.FighterRestGoal;
import io.github.nenwam.villagerhuts.entity.goal.FighterTargetGoal;
import io.github.nenwam.villagerhuts.entity.goal.GuardPostGoal;
import io.github.nenwam.villagerhuts.entity.goal.RangerAttackGoal;
import io.github.nenwam.villagerhuts.entity.goal.WarriorAttackGoal;
import io.github.nenwam.villagerhuts.job.Weapons;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import io.github.nenwam.villagerhuts.entity.goal.DepositItemsGoal;
import io.github.nenwam.villagerhuts.entity.goal.DescribesActivity;
import io.github.nenwam.villagerhuts.entity.goal.FarmerWorkGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchBoneMealGoal;
import io.github.nenwam.villagerhuts.entity.goal.FetchSeedsGoal;
import io.github.nenwam.villagerhuts.entity.goal.FleeHostilesGoal;
import io.github.nenwam.villagerhuts.entity.goal.FollowOrStayGoal;
import io.github.nenwam.villagerhuts.entity.goal.RestGoal;
import io.github.nenwam.villagerhuts.entity.goal.ShelterAtNightGoal;
import io.github.nenwam.villagerhuts.entity.goal.WorkReach;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.registry.ModItems;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A villager-like worker, hired with a Farmer or a Fighter item. Each new worker gets a look and a name that
 * suits it (see WorkerLook): fighters are Sikh warriors.
 *
 * A farmer works the farm it's assigned, takes seeds from and stores its harvest in the chest it's assigned,
 * sleeps in the bed it's assigned (or shelters indoors near its chest), and runs from hostile mobs. It harvests
 * up to its daily limit (Config farmerDailyHarvest, times its rank), then puts everything away and rests by its
 * bed until the next morning.
 *
 * A fighter is unarmed until it's handed a weapon: a melee weapon makes it a warrior, a ranged one a ranger
 * (see job/Weapons). Armed, it guards: its post (working), the player it follows, or the spot it was told to
 * stay at, fighting the foes that come near (see WorkerCombat and FighterTargetGoal). Its foes' drops go in its
 * pockets and then its chest; rangers take ammo from the chest, and all fighters take food to heal. It keeps
 * watch at night and sleeps in the morning, and goes to bed to recover when badly hurt (FighterRestGoal).
 * Its weapon never wears out in its hands, like a farmer's hoe.
 *
 * Diamond carrots raise a worker's rank, Novice to Master: a farmer harvests more each day; a fighter gets more
 * health, hits harder, and shoots faster and straighter.
 *
 * Who can do what (multiplayer): the player who hired it owns it, and can share it with other players
 * from the worker panel. Its owner and the players it's shared with can give it work and orders, rename it
 * and upgrade it; only its owner can take its tool, share it, or give it away. Nobody else can use it at
 * all (not even a lead or a name tag), or hurt it (Config protectWorkers).
 *
 * Interactions:
 *  - Right-click with an empty hand: select it, then right-click a farm (a fighter: a block to guard), a chest
 *    or a bed to assign it (see WorkerAssigning). Right-click it again to cancel.
 *  - Shift + right-click a farmer with an empty hand: select it to make a new farm, then right-click the ground.
 *  - Right-click with diamond carrots or flawless ones: upgrade it to the next rank (see upgradeFrom).
 *  - Right-click with a job tool while it has none (a hoe for a farmer, a weapon for a fighter): it takes it and
 *    starts that job. Its owner can hand an armed fighter a different weapon: they swap, and get the old one back.
 *  - Right-click a fighter with food, or a ranger with ammo: it takes it.
 * The worker panel can also give it orders (work, follow you, stay put) and take its tool back.
 * A new worker follows the player who hired it until it's given a farm (or post), chest or bed.
 */
public class WorkerEntity extends PathfinderMob implements InventoryCarrier, CrossbowAttackMob {
    private static final EntityDataAccessor<String> DATA_JOB =
            SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LOOK =
            SynchedEntityData.defineId(WorkerEntity.class, EntityDataSerializers.INT);
    /** Ticks without any work before a worker counts as idle (and goes to put its items away). */
    private static final int IDLE_TICKS = 100;
    /** How far workers can plan a walk. Raised automatically for farms assigned far from the hut. */
    private static final double BASE_FOLLOW_RANGE = 48.0;
    /** How far from its chest a worker looks for somewhere indoors to spend the night. */
    private static final int SHELTER_SEARCH_RADIUS = 6;
    private static final int SHELTER_RETRY_TICKS = 1200;
    private static final long DAY_TICKS = 24000L;
    private static final Codec<Map<ResourceLocation, Integer>> COLLECTED_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT);

    /** Novice (1) to Master (5), like villagers. Each rank adds a day's harvest (Config farmerDailyHarvest). */
    public static final int MAX_RANK = 5;
    /** Every worker's health (a farmer's at any rank, a fighter's at Novice), and how much each rank adds to a fighter's. */
    public static final int BASE_HEALTH = 20;
    public static final int FIGHTER_HEALTH_PER_RANK = 5;
    private static final ResourceLocation RANK_DAMAGE = ResourceLocation.fromNamespaceAndPath(VillagerHuts.MODID, "rank_damage");
    private static final int EAT_TICKS = 40;           // between bites of food
    private static final int REGEN_TICKS = 200;        // fighters heal 1 health this often when not fighting
    private static final int BED_REGEN_TICKS = 20;     // and this often in bed
    private static final long MORNING_END = 6000L;     // noon: fighters sleep from sunrise until then
    /** What it takes to go up from Novice, Apprentice, Journeyman and Expert. */
    private static final List<Upgrade> UPGRADES = List.of(
            new Upgrade(ModItems.DIAMOND_CARROT, 1),
            new Upgrade(ModItems.DIAMOND_CARROT, 2),
            new Upgrade(ModItems.FLAWLESS_DIAMOND_CARROT, 1),
            new Upgrade(ModItems.FLAWLESS_DIAMOND_CARROT, 2));

    private final SimpleContainer inventory = new SimpleContainer(9);
    private @Nullable UUID ownerId;
    private final Set<UUID> sharedWith = new LinkedHashSet<>();
    private Order order = Order.WORK;
    private @Nullable UUID followId; // who it follows when told to: whoever gave the order
    private @Nullable BlockPos stayPos;
    private @Nullable BoundingBox farmArea;
    private boolean tillsLand; // a farm it made itself (see FarmPlan): it tills the land and puts the water in
    private final List<BlockPos> plannedWater = new ArrayList<>();
    private @Nullable BlockPos storagePos;
    private @Nullable BlockPos bedPos; // the head of its bed
    private @Nullable BlockPos shelterPos; // found near the chest when first needed, not saved
    private int nextShelterSearchTick;
    private boolean sheltered;
    private int rank = 1;
    private long harvestDay = Long.MIN_VALUE; // the day harvestedToday counts
    private int harvestedToday;
    private boolean doneForToday;
    private int lastWorkTick = -IDLE_TICKS;
    private final Map<ResourceLocation, Integer> collected = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> defeated = new LinkedHashMap<>(); // entity type -> how many (fighters)
    private @Nullable BlockPos guardPost; // fighters: where they stand guard
    private boolean recovering;           // fighters: badly hurt, resting in bed until healed
    private int nextEatTick;
    private int nextRegenTick;
    private long employedTicks;
    private int farmlandInArea = -1; // -1 = not checked yet
    private int emptyFarmland = -1;
    private int ripeCrops = -1;
    private int growingCrops = -1;
    private boolean farmWorkDone;
    private boolean farmWorkStuck;
    private StorageState storageState = StorageState.OK;
    private boolean farmUnreachable;
    private Field field = Field.FARMING;
    private boolean cropsGone; // clearing: nothing planted left on the farm (as of the last look)

    /** What the last trip to its chest found. */
    public enum StorageState { OK, NONE, UNREACHABLE }

    /**
     * A farmer's field, from the worker panel: farmed as usual, being cleared (everything planted is pulled up and put
     * in the chest), or cleared (it waits, planting nothing, until it's told to start farming again).
     */
    public enum Field {
        FARMING("farming"), CLEARING("clearing"), CLEARED("cleared");

        private final String id;

        Field(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static Field byId(String id) {
            for (Field field : values()) {
                if (field.id.equals(id)) {
                    return field;
                }
            }
            return FARMING;
        }
    }

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
        ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0) // a fist, like yours; weapons add to it
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // Synced to players' games so the worker is drawn with the right look and tool.
        builder.define(DATA_JOB, WorkerJob.NONE.id());
        builder.define(DATA_LOOK, 0);
    }

    @Override
    protected void registerGoals() {
        // Lower number = higher priority. A goal can't start while a higher-priority one is using the legs.
        // Each job's goals check the job themselves (farmers farm, fighters fight; the unarmed run like farmers).
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 0.85) {         // got hurt: run (not fighters who can fight)
            @Override
            public boolean canUse() {
                return !WorkerEntity.this.canFight() && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new FleeHostilesGoal(this));        // monster nearby: run
        this.goalSelector.addGoal(2, new AvoidCreepersGoal(this));       // fighters: keep clear of creepers
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new WarriorAttackGoal(this, 0.8));  // a foe: fight it (FighterTargetGoal picks it)
        this.goalSelector.addGoal(4, new RangerAttackGoal(this, 0.7));
        this.goalSelector.addGoal(5, new FollowOrStayGoal(this, 0.7));    // told to follow or stay: nothing else moves it
        this.goalSelector.addGoal(6, new ShelterAtNightGoal(this, 0.6)); // dark: go to bed (or at least inside)
        this.goalSelector.addGoal(6, new FighterRestGoal(this, 0.6));    // fighters: sleep in the morning, or recover
        this.goalSelector.addGoal(7, new MoveTowardsRestrictionGoal(this, 0.6));
        this.goalSelector.addGoal(8, new DepositItemsGoal(this, 0.6));
        this.goalSelector.addGoal(9, new RestGoal(this, 0.5));           // done for the day: stay by its bed
        this.goalSelector.addGoal(10, new FetchSeedsGoal(this, 0.6));    // out of seeds: get some from the chest
        this.goalSelector.addGoal(10, new FetchAmmoGoal(this, 0.6));     // low on arrows: get some from the chest
        this.goalSelector.addGoal(11, new FetchBoneMealGoal(this, 0.6)); // crops growing and bone meal in the chest
        this.goalSelector.addGoal(11, new FetchFoodGoal(this, 0.6));     // hurt and no food: get some from the chest
        this.goalSelector.addGoal(12, new FarmerWorkGoal(this, 0.6));
        this.goalSelector.addGoal(12, new GuardPostGoal(this, 0.6));
        this.goalSelector.addGoal(13, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(14, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(15, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new FighterTargetGoal(this));
    }

    // ------------------------------------------------------------------ look & name

    public WorkerLook getLook() {
        return WorkerLook.byOrdinal(this.entityData.get(DATA_LOOK));
    }

    public void setLook(WorkerLook look) {
        this.entityData.set(DATA_LOOK, look.ordinal());
    }

    /** A random look for its kind (farmer or fighter), and a name that suits it (not one in takenNames, while there are others). */
    public void giveIdentity(Set<String> takenNames) {
        WorkerLook look = WorkerLook.random(this.getJob().isFighter() ? WorkerLook.Kind.FIGHTER : WorkerLook.Kind.FARMER, this.random);
        this.setLook(look);
        this.setCustomName(Component.literal(look.pickName(takenNames, this.random)));
    }

    // Summoned with a command or a spawn egg: give it a look and name too. (The Farmer item does this itself,
    // so it can avoid the names of its owner's other workers.)
    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                                  @Nullable SpawnGroupData groupData) {
        if (!this.hasCustomName()) {
            this.giveIdentity(Set.of());
        }
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    // ------------------------------------------------------------------ owner & sharing

    /** Ties a newly hired worker to the player who placed it. It follows them until it's given work. */
    public void hire(@Nullable Player owner) {
        this.ownerId = owner == null ? null : owner.getUUID();
        this.sharedWith.clear();
        this.setOrder(Order.FOLLOW, this.ownerId);
    }

    public @Nullable UUID getOwnerId() {
        return this.ownerId;
    }

    public boolean isOwner(Player player) {
        return this.ownerId != null && this.ownerId.equals(player.getUUID());
    }

    /** Whether this player can give it work and orders: its owner, someone it's shared with, or anyone if it has no owner. */
    public boolean canCommand(Player player) {
        return this.canCommand(player.getUUID());
    }

    public boolean canCommand(UUID player) {
        return this.ownerId == null || this.ownerId.equals(player) || this.sharedWith.contains(player);
    }

    /** The players its owner has shared it with. */
    public Set<UUID> getSharedWith() {
        return Set.copyOf(this.sharedWith);
    }

    /**
     * Who owns it and who it's shared with. Only the worker roster calls this (WorkerRoster.setOwnership), so
     * sharing a worker that's far away still works: it takes the change when it's next loaded.
     */
    public void setOwnership(UUID owner, Collection<UUID> shared) {
        this.ownerId = owner;
        this.sharedWith.clear();
        for (UUID player : shared) {
            if (!player.equals(owner)) {
                this.sharedWith.add(player);
            }
        }
        if (this.followId != null && !this.canCommand(this.followId)) {
            this.followId = owner; // the player it was following can't use it any more
        }
    }

    /**
     * Nobody but its owner and the players it's shared with can use it: not even a lead or a name tag. Returns true
     * (and tells the player whose it is) if this player can't (see WorkerProtection, which stops the interaction).
     */
    public boolean turnsAway(Player player, InteractionHand hand) {
        if (!this.isAlive() || this.canCommand(player)) {
            return false;
        }
        if (this.level() instanceof ServerLevel level && hand == InteractionHand.MAIN_HAND && this.ownerId != null) {
            player.displayClientMessage(Component.translatable("message.villagerhuts.not_owner",
                    this.getDisplayName(), WorkerRoster.playerName(level.getServer(), this.ownerId)), true);
        }
        return true;
    }

    // Other players' hits and arrows don't hurt it (its owner's do).
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
                || (Config.PROTECT_WORKERS.getAsBoolean() && this.ownerId != null
                && source.getEntity() instanceof Player attacker && !this.isOwner(attacker));
    }

    // ------------------------------------------------------------------ orders

    public Order getOrder() {
        return this.order;
    }

    /** Work (its farm, chest and bed), follow its owner, or stay where it is now. */
    public void setOrder(Order order) {
        this.setOrder(order, null);
    }

    /** @param by who gave the order: on Follow, it follows them (null: its owner) */
    public void setOrder(Order order, @Nullable UUID by) {
        this.order = order;
        this.stayPos = order == Order.STAY ? this.blockPosition() : null;
        if (order == Order.FOLLOW) {
            this.followId = by != null ? by : this.ownerId;
        }
        this.getNavigation().stop();
        this.updateHomeArea();
    }

    /** Where it was told to stay (it walks back there if it gets pushed or chased away). */
    public @Nullable BlockPos getStayPos() {
        return this.stayPos;
    }

    /** Who it follows when told to: whoever gave the order (its owner at first). */
    public @Nullable UUID getFollowTargetId() {
        return this.followId != null ? this.followId : this.ownerId;
    }

    /** The player it follows, if they're in this world (dimension) right now. */
    public @Nullable Player getFollowTarget() {
        UUID id = this.getFollowTargetId();
        return id == null ? null : this.level().getPlayerByUUID(id);
    }

    /** Gives the job tool back to the player (or drops it at their feet); the worker becomes unemployed (a fighter, unarmed). */
    public void returnTool(Player player) {
        if (this.isUsingItem()) {
            this.stopUsingItem(); // a crossbow half loaded
        }
        giveTo(player, this.getMainHandItem().copy());
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.setJob(this.getJob().withoutTool());
    }

    private static void giveTo(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false); // no room: at their feet
        }
    }

    // ------------------------------------------------------------------ farm, chest & bed

    /** The farm the worker works (see WorkerAssigning). Null until it's given one. */
    public @Nullable BoundingBox getFarmArea() {
        return this.farmArea;
    }

    /** An existing farm to work. Giving a worker work also sets it working, if it was following or staying. */
    public void assignFarm(BoundingBox area) {
        this.farmArea = area;
        this.field = Field.FARMING;
        this.tillsLand = false;
        this.plannedWater.clear();
        this.setOrder(Order.WORK);
    }

    /** A new farm to make (see FarmPlan): it tills the land in the area and puts water where it's planned. */
    public void createFarm(BoundingBox area, List<BlockPos> water) {
        this.farmArea = area;
        this.field = Field.FARMING;
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

    /** The bed it sleeps in at night and rests by once it's done for the day (its head end). Null until it's given one. */
    public @Nullable BlockPos getBedPos() {
        return this.bedPos;
    }

    public void assignBed(BlockPos head) {
        if (this.isSleeping()) {
            this.stopSleeping();
        }
        this.bedPos = head.immutable();
        this.setOrder(Order.WORK);
    }

    /** Whether its bed is still there (not broken since it was assigned). */
    public boolean hasBed() {
        BlockPos bed = this.bedPos;
        return bed != null && this.level().isLoaded(bed) && this.level().getBlockState(bed).is(BlockTags.BEDS);
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

    // ------------------------------------------------------------------ guard post & guard zone (fighters)

    /** Where a fighter stands guard (a spot to stand on). Null until it's given one. */
    public @Nullable BlockPos getGuardPost() {
        return this.guardPost;
    }

    /** A post to guard. Giving a fighter a post also sets it working, if it was following or staying. */
    public void assignGuardPost(BlockPos post) {
        this.guardPost = post.immutable();
        this.setOrder(Order.WORK);
    }

    /** The area a post's guard covers: Config fighterGuardRadius all round it, a few blocks up and down. */
    public static BoundingBox guardArea(BlockPos post) {
        int radius = Config.FIGHTER_GUARD_RADIUS.getAsInt();
        return new BoundingBox(post.getX() - radius, post.getY() - 2, post.getZ() - radius,
                post.getX() + radius, post.getY() + 4, post.getZ() + radius);
    }

    /** Its farm (farmers) or the area around its post (fighters): what the worker panel and highlights show. */
    public @Nullable BoundingBox getWorkArea() {
        if (this.getJob().isFighter()) {
            return this.guardPost == null ? null : guardArea(this.guardPost);
        }
        return this.farmArea;
    }

    /**
     * The middle of the zone a fighter guards: the player it follows (a bodyguard), the spot it was told to stay at,
     * or its post. A working fighter with no post guards wherever it is.
     */
    public Vec3 guardCenter() {
        if (this.order == Order.FOLLOW) {
            Player target = this.getFollowTarget();
            return target != null ? target.position() : this.position();
        }
        BlockPos spot = this.order == Order.STAY ? this.stayPos : this.guardPost;
        return spot != null ? Vec3.atBottomCenterOf(spot) : this.position();
    }

    /** The box a fighter looks for foes in: Config fighterGuardRadius around its guard center. */
    public AABB guardZone() {
        int radius = Config.FIGHTER_GUARD_RADIUS.getAsInt();
        return AABB.ofSize(this.guardCenter(), radius * 2.0, 12.0, radius * 2.0);
    }

    /** Within its guard zone, or no more than `margin` blocks past it (measured flat; up and down within 8 blocks). */
    public boolean isInGuardZone(Entity entity, double margin) {
        Vec3 center = this.guardCenter();
        double reach = Config.FIGHTER_GUARD_RADIUS.getAsInt() + margin;
        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        return dx * dx + dz * dz <= reach * reach && Math.abs(entity.getY() - center.y) <= 8.0 + margin;
    }

    // While working: wander only around its farm (or post), chest and bed, and be able to plan walks across all of it.
    // Following or staying: no limits (the order decides where it goes).
    private void updateHomeArea() {
        if (this.order != Order.WORK) {
            this.clearRestriction();
            return;
        }
        BoundingBox area = include(include(this.getWorkArea(), this.storagePos), this.bedPos);
        if (area == null) {
            this.clearRestriction();
            return;
        }
        int radius = Math.max(area.getXSpan(), area.getZSpan()) / 2 + 2;
        this.restrictTo(area.getCenter(), radius);
        AttributeInstance followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(Math.max(BASE_FOLLOW_RANGE, Math.max(area.getXSpan(), area.getZSpan()) + 8));
        }
    }

    private static @Nullable BoundingBox include(@Nullable BoundingBox area, @Nullable BlockPos pos) {
        if (pos == null) {
            return area;
        }
        if (area == null) {
            return new BoundingBox(pos);
        }
        return new BoundingBox(
                Math.min(area.minX(), pos.getX()), Math.min(area.minY(), pos.getY()), Math.min(area.minZ(), pos.getZ()),
                Math.max(area.maxX(), pos.getX()), Math.max(area.maxY(), pos.getY()), Math.max(area.maxZ(), pos.getZ()));
    }

    // ------------------------------------------------------------------ shelter (no bed)

    /**
     * Without a bed, the indoor spot this worker stays at during the night: the nearest one to its chest. Null if
     * there's nowhere indoors near the chest (it looks again every minute, in case you build around it).
     */
    public @Nullable BlockPos getShelterPos() {
        if (this.shelterPos == null && this.storagePos != null && this.tickCount >= this.nextShelterSearchTick
                && !this.level().isClientSide) {
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

    // ------------------------------------------------------------------ rank & the daily limit

    /** 1 (Novice) to MAX_RANK (Master). */
    public int getRank() {
        return this.rank;
    }

    /** Items it can harvest in a day before it's worn out (Config farmerDailyHarvest, times its rank). */
    public int getDailyLimit() {
        return Config.FARMER_DAILY_HARVEST.getAsInt() * this.rank;
    }

    /** A fighter's health at this rank: 20 at Novice, 5 more each rank, 40 for a Master. */
    public static int fighterHealth(int rank) {
        return BASE_HEALTH + FIGHTER_HEALTH_PER_RANK * (Math.max(1, rank) - 1);
    }

    /** Damage a fighter adds to every blow or arrow at this rank: none at Novice, 1 more each rank. */
    public static int fighterDamageBonus(int rank) {
        return Math.max(1, rank) - 1;
    }

    // Fighters: health and damage go up with rank. Farmers stay at 20 health.
    private void applyRankStats() {
        boolean fighter = this.getJob().isFighter();
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double max = fighter ? fighterHealth(this.rank) : BASE_HEALTH;
            if (health.getBaseValue() != max) {
                health.setBaseValue(max);
                this.setHealth(Math.min(this.getHealth(), this.getMaxHealth()));
            }
        }
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(RANK_DAMAGE);
            if (fighter && fighterDamageBonus(this.rank) > 0) {
                damage.addTransientModifier(new AttributeModifier(RANK_DAMAGE, fighterDamageBonus(this.rank), AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /** What it takes to go up from this rank, or null at the top. */
    public static @Nullable Upgrade upgradeFrom(int rank) {
        return rank >= MAX_RANK ? null : UPGRADES.get(Math.max(1, rank) - 1);
    }

    /** Whether the stack is something that upgrades workers (at some rank). */
    public static boolean isUpgradeItem(ItemStack stack) {
        return UPGRADES.stream().anyMatch(upgrade -> stack.is(upgrade.item().asItem()));
    }

    /** An upgrade's price: this many of this item. */
    public record Upgrade(ItemLike item, int count) {
        /** "1 Diamond Carrot", "2 Diamond Carrots". */
        public Component describe() {
            String name = this.item.asItem().getDescriptionId();
            return Component.translatable("gui.villagerhuts.item_count", this.count,
                    Component.translatable(this.count == 1 ? name : name + ".plural"));
        }
    }

    /** "Novice", "Apprentice", ... (vanilla's villager level names). */
    public static Component rankName(int rank) {
        return Component.translatable("merchant.level." + rank);
    }

    /** Items harvested so far today. */
    public int getHarvestedToday() {
        this.startNewDayIfDue();
        return this.harvestedToday;
    }

    /** It's harvested its daily limit (or as near as it can get): it rests until the next morning. */
    public boolean isDoneForToday() {
        this.startNewDayIfDue();
        return this.doneForToday;
    }

    /**
     * Counts a harvest toward today's limit. If it would take it over the limit, it doesn't count and the worker
     * is done for the day instead (that crop is left for tomorrow), so it never harvests more than its limit.
     */
    public boolean takeHarvest(int items) {
        this.startNewDayIfDue();
        if (this.doneForToday || this.harvestedToday + items > this.getDailyLimit()) {
            this.doneForToday = true;
            return false;
        }
        this.harvestedToday += items;
        if (this.harvestedToday >= this.getDailyLimit()) {
            this.doneForToday = true;
        }
        return true;
    }

    // A new day starts each morning (when the sun comes up).
    private void startNewDayIfDue() {
        long day = Math.floorDiv(this.level().getDayTime(), DAY_TICKS);
        if (day != this.harvestDay) {
            this.harvestDay = day;
            this.harvestedToday = 0;
            this.doneForToday = false;
        }
    }

    // Diamond carrots: the next rank, if they hold the right ones, and enough of them.
    private InteractionResult upgrade(ServerLevel level, Player player, ItemStack carrots) {
        Upgrade upgrade = upgradeFrom(this.rank);
        if (upgrade == null) {
            player.displayClientMessage(Component.translatable("message.villagerhuts.max_rank", this.getDisplayName()), true);
            return InteractionResult.CONSUME;
        }
        if (!carrots.is(upgrade.item().asItem())) {
            player.displayClientMessage(Component.translatable("message.villagerhuts.upgrade_needs_other", upgrade.describe(),
                    rankName(this.rank + 1)), true);
            return InteractionResult.CONSUME;
        }
        if (carrots.getCount() < upgrade.count()) {
            player.displayClientMessage(Component.translatable("message.villagerhuts.upgrade_needs", upgrade.describe(),
                    rankName(this.rank + 1), carrots.getCount()), true);
            return InteractionResult.CONSUME;
        }
        carrots.consume(upgrade.count(), player);
        this.rank++;
        this.applyRankStats();
        this.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getEyeY() + 0.3, this.getZ(), 15, 0.5, 0.5, 0.5, 0.0);
        if (this.getJob().isFighter()) {
            this.heal(FIGHTER_HEALTH_PER_RANK); // the new health comes filled
            player.displayClientMessage(Component.translatable("message.villagerhuts.upgraded_fighter", this.getDisplayName(),
                    rankName(this.rank), fighterHealth(this.rank), fighterDamageBonus(this.rank)), true);
        } else {
            player.displayClientMessage(Component.translatable("message.villagerhuts.upgraded", this.getDisplayName(),
                    rankName(this.rank), this.getDailyLimit()), true);
        }
        WorkerRoster.get(level.getServer()).update(this);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ jobs

    public WorkerJob getJob() {
        return WorkerJob.byId(this.entityData.get(DATA_JOB));
    }

    public void setJob(WorkerJob job) {
        this.entityData.set(DATA_JOB, job.id());
        if (!this.level().isClientSide) {
            if (!job.isArmed()) {
                this.setTarget(null);
            }
            this.applyRankStats();
        }
    }

    public boolean isRanger() {
        return this.getJob() == WorkerJob.RANGER;
    }

    /** Armed and ready: a warrior, or a ranger with something to shoot. Otherwise it runs from monsters like a farmer. */
    public boolean canFight() {
        return this.getJob().isArmed() && (!this.isRanger() || this.hasAmmo());
    }

    // Only players who can use it get here (see turnsAway).
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        WorkerJob job = this.getJob();
        WorkerJob offeredJob = job.withTool(held);
        boolean upgrade = isUpgradeItem(held);
        boolean supplies = this.wantsSupplies(held);
        if (!held.isEmpty() && offeredJob == null && !upgrade && !supplies) {
            return super.mobInteract(player, hand); // name tags, leads, etc. work as normal
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (upgrade) {
            return this.upgrade(level, player, held);
        }
        if (supplies) {
            return this.takeSupplies(player, held);
        }

        if (offeredJob != null && !job.hasTool()) {
            this.equip(level, player, held, offeredJob);
            player.displayClientMessage(Component.translatable("message.villagerhuts.job_assigned", offeredJob.displayName()), true);
            return InteractionResult.SUCCESS;
        }
        if (offeredJob != null && job.isFighter()) {
            // An armed fighter handed another weapon: its owner swaps them (and gets the old one back).
            if (!this.isOwner(player) && this.ownerId != null) {
                player.displayClientMessage(Component.translatable("message.villagerhuts.owner_swaps_weapons", this.getDisplayName()), true);
                return InteractionResult.CONSUME;
            }
            if (this.isUsingItem()) {
                this.stopUsingItem();
            }
            ItemStack old = this.getMainHandItem().copy();
            this.equip(level, player, held, offeredJob);
            giveTo(player, old);
            player.displayClientMessage(Component.translatable("message.villagerhuts.weapon_swapped", this.getDisplayName(),
                    offeredJob.displayName(), this.getMainHandItem().getHoverName()), true);
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            // Next: right-click a farm (or post), chest or bed; or, sneaking, the ground for a new farm (farmers).
            WorkerAssigning.toggle(serverPlayer, this, held.isEmpty() && player.isSecondaryUseActive());
        }
        return InteractionResult.SUCCESS;
    }

    // Takes one of the held item as its tool, and the job that goes with it.
    private void equip(ServerLevel level, Player player, ItemStack held, WorkerJob job) {
        this.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
        this.setGuaranteedDrop(EquipmentSlot.MAINHAND); // you get the tool back if the worker dies
        held.consume(1, player);
        this.setJob(job);
        this.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getEyeY() + 0.3, this.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
        WorkerRoster.get(level.getServer()).update(this);
    }

    // Fighters take food, and rangers ammo for their weapon, handed to them.
    private boolean wantsSupplies(ItemStack held) {
        if (!this.getJob().isFighter() || held.isEmpty()) {
            return false;
        }
        ItemStack weapon = this.getMainHandItem();
        return Weapons.isPlainFood(held) || (this.isRanger() && Weapons.usesAmmo(weapon) && Weapons.ammoFor(weapon).test(held));
    }

    private InteractionResult takeSupplies(Player player, ItemStack held) {
        Component what = held.getHoverName();
        ItemStack rest = this.inventory.addItem(held.copy());
        int taken = held.getCount() - rest.getCount();
        if (taken == 0) {
            player.displayClientMessage(Component.translatable("message.villagerhuts.pockets_full", this.getDisplayName()), true);
            return InteractionResult.CONSUME;
        }
        held.consume(taken, player);
        this.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
        player.displayClientMessage(Component.translatable("message.villagerhuts.supplies_taken", this.getDisplayName(), taken, what), true);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ fighting

    /** Ticks between a warrior's blows: 20 (a second) at Novice, 2 fewer each rank. */
    public int meleeInterval() {
        return 20 - 2 * (this.rank - 1);
    }

    /** Ticks between a ranger's shots: 40 at Novice, 5 fewer each rank. */
    public int rangedInterval() {
        return 40 - 5 * (this.rank - 1);
    }

    /** How far a ranger shoots from: a bow's range (15), a crossbow's 12, a trident's 10. */
    public double rangedRange() {
        ItemStack weapon = this.getMainHandItem();
        if (Weapons.isThrown(weapon)) {
            return 10.0;
        }
        return Weapons.isCrossbow(weapon) ? 12.0 : 15.0;
    }

    // Shots spread less as it levels up: from a skeleton's on Normal (6) at Novice to dead straight at Master.
    private float spread() {
        return 6.0F - 1.5F * (this.rank - 1);
    }

    /** Ammo for its weapon in its pockets (arrows, or fireworks for a crossbow). */
    public int getAmmoCount() {
        ItemStack weapon = this.getMainHandItem();
        if (!Weapons.usesAmmo(weapon)) {
            return 0;
        }
        Predicate<ItemStack> ammo = Weapons.ammoFor(weapon);
        int count = 0;
        for (ItemStack stack : this.inventory.getItems()) {
            if (ammo.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Whether a ranger can shoot: it has ammo (or a loaded crossbow), or its weapon needs none (a trident). */
    public boolean hasAmmo() {
        ItemStack weapon = this.getMainHandItem();
        return !Weapons.usesAmmo(weapon) || (Weapons.isCrossbow(weapon) && CrossbowItem.isCharged(weapon)) || this.getAmmoCount() > 0;
    }

    // What its weapon shoots next: from its pockets (vanilla takes from the stack it's given, so this uses it up).
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        Predicate<ItemStack> ammo = Weapons.ammoFor(weapon);
        for (ItemStack stack : this.inventory.getItems()) {
            if (ammo.test(stack)) {
                return CommonHooks.getProjectile(this, weapon, stack);
            }
        }
        return CommonHooks.getProjectile(this, weapon, ItemStack.EMPTY);
    }

    // A warrior's blow: the weapon's damage and enchantments count, but the weapon doesn't wear out.
    @Override
    public boolean doHurtTarget(Entity target) {
        ItemStack weapon = this.getMainHandItem();
        int damage = weapon.getDamageValue();
        ItemStack backup = weapon.copy();
        boolean hit = super.doHurtTarget(target);
        this.keepWeapon(weapon, damage, backup);
        if (hit) {
            this.markWorked();
        }
        return hit;
    }

    // Undo any wear from a blow or a shot (or the weapon breaking).
    private void keepWeapon(ItemStack weapon, int damage, ItemStack backup) {
        if (this.getMainHandItem() == weapon && !weapon.isEmpty()) {
            weapon.setDamageValue(damage);
        } else if (this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, backup);
        }
    }

    /** A ranger's shot (RangerAttackGoal): a crossbow fires what it's loaded with, a trident is thrown, anything else shoots an arrow. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack weapon = this.getMainHandItem();
        if (Weapons.isCrossbow(weapon)) {
            int damage = weapon.getDamageValue();
            ItemStack backup = weapon.copy();
            this.performCrossbowAttack(this, power);
            this.keepWeapon(weapon, damage, backup);
        } else if (Weapons.isThrown(weapon)) {
            this.throwTrident(level, target, weapon);
        } else {
            this.shootArrow(level, target, weapon, power);
        }
        this.markWorked();
    }

    // Like a skeleton's shot, with an arrow from its pockets (Infinity: one arrow lasts forever, as for you).
    private void shootArrow(ServerLevel level, LivingEntity target, ItemStack weapon, float power) {
        ItemStack ammo = this.getProjectile(weapon);
        if (ammo.isEmpty()) {
            return;
        }
        ItemStack shot = this.useAmmo(level, weapon, ammo);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, shot, power, weapon);
        if (weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon) {
            arrow = projectileWeapon.customArrow(arrow, shot, weapon);
        }
        arrow.setBaseDamageFromMob(power + 0.25F * fighterDamageBonus(this.rank)); // half a point more per rank
        aimAndShoot(level, arrow, shot, target, 1.6F);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    // Takes one of the ammo, unless the weapon makes it free (Infinity on a bow, for plain arrows).
    private ItemStack useAmmo(ServerLevel level, ItemStack weapon, ItemStack ammo) {
        boolean free = ammo.getItem() instanceof ArrowItem arrowItem && arrowItem.isInfinite(ammo, weapon, this);
        int used = free ? 0 : EnchantmentHelper.processAmmoUse(level, weapon, ammo, 1);
        if (used == 0) {
            ItemStack copy = ammo.copyWithCount(1);
            copy.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE); // can't be picked up (it wasn't used up)
            return copy;
        }
        return ammo.split(used);
    }

    // Like a drowned: throws a copy of its trident (it keeps its own), doing a trident's damage. Loyalty would bring
    // it back to nobody, so not that.
    private void throwTrident(ServerLevel level, LivingEntity target, ItemStack weapon) {
        ItemStack copy = weapon.copyWithCount(1);
        EnchantmentHelper.updateEnchantments(copy, enchantments -> enchantments.removeIf(enchantment -> enchantment.is(Enchantments.LOYALTY)));
        ThrownTrident trident = new ThrownTrident(level, this, copy);
        aimAndShoot(level, trident, copy, target, 1.6F);
        this.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    private void aimAndShoot(ServerLevel level, AbstractArrow projectile, ItemStack item, LivingEntity target, float velocity) {
        double dx = target.getX() - this.getX();
        double dy = target.getY(1.0 / 3.0) - projectile.getY();
        double dz = target.getZ() - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        projectile.shoot(dx, dy + flat * 0.2, dz, velocity, this.spread());
        level.addFreshEntity(projectile);
    }

    // A crossbow fires what it's loaded with (Multishot, Piercing and fireworks included), with the worker's spread.
    @Override
    public void performCrossbowAttack(LivingEntity user, float velocity) {
        ItemStack crossbow = this.getMainHandItem();
        if (crossbow.getItem() instanceof CrossbowItem crossbowItem) {
            crossbowItem.performShooting(this.level(), this, InteractionHand.MAIN_HAND, crossbow, velocity, this.spread(), this.getTarget());
        }
        this.onCrossbowAttackPerformed();
    }

    @Override
    public void setChargingCrossbow(boolean charging) {
        // Workers fold their arms whatever they hold, so there's no pose to change.
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    // ------------------------------------------------------------------ healing (fighters)

    public boolean hasFood() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (Weapons.isPlainFood(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Badly hurt: it rests in bed (if it has one) until it's nearly healed (see FighterRestGoal). */
    public boolean isRecovering() {
        return this.recovering;
    }

    /** Sunrise to noon, after a night's watch: fighters with a bed sleep then. */
    public boolean isMorningAfterWatch() {
        return !this.level().isNight() && Math.floorMod(this.level().getDayTime(), DAY_TICKS) < MORNING_END;
    }

    // Eat when hurt (between fights), heal slowly anyway, and quickly in bed.
    private void fighterUpkeep() {
        float max = this.getMaxHealth();
        if (this.getHealth() < max * 0.5F) {
            this.recovering = true;
        } else if (this.getHealth() >= max * 0.9F) {
            this.recovering = false;
        }
        if (this.getTarget() != null || this.getHealth() >= max) {
            return;
        }
        if (this.getHealth() <= max - 2.0F && this.tickCount >= this.nextEatTick) {
            this.eat();
        }
        if (this.tickCount >= this.nextRegenTick) {
            this.heal(1.0F);
            this.nextRegenTick = this.tickCount + (this.isSleeping() ? BED_REGEN_TICKS : REGEN_TICKS);
        }
    }

    // One bite: heals as much as the food fills you up (bread 5, steak 8).
    private void eat() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (Weapons.isPlainFood(stack)) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                this.heal(food == null ? 1.0F : Math.max(1, food.nutrition()));
                if (this.level() instanceof ServerLevel level) {
                    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)),
                            this.getX(), this.getEyeY() - 0.2, this.getZ(), 6, 0.15, 0.1, 0.15, 0.05);
                }
                this.playSound(SoundEvents.GENERIC_EAT, 0.6F, 1.0F);
                stack.shrink(1);
                this.nextEatTick = this.tickCount + EAT_TICKS;
                return;
            }
        }
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
            this.spawnAtLocation(rest);
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

    public Map<ResourceLocation, Integer> getCollected() {
        return this.collected;
    }

    /** Counts a foe a fighter defeated, by kind (zombie, skeleton...). */
    public void recordDefeated(LivingEntity foe) {
        this.defeated.merge(BuiltInRegistries.ENTITY_TYPE.getKey(foe.getType()), 1, Integer::sum);
        this.markWorked();
    }

    /** Entity type -> how many of them it has defeated. */
    public Map<ResourceLocation, Integer> getDefeated() {
        return this.defeated;
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
     * All its work is done for now: everything it can harvest, plant, till or bone-meal is done (or it's done for
     * the day), and it's had nothing to do for a few seconds. Then it puts everything it carries away in its chest.
     */
    public boolean isWorkDone() {
        if (this.field == Field.CLEARING) {
            return this.cropsGone && this.isIdle(); // all pulled up: put it all away
        }
        if (this.getJob().isFighter()) {
            return this.getTarget() == null && this.isIdle(); // the fight's over: put the loot away
        }
        boolean nothingToDo = this.getJob() == WorkerJob.NONE || this.farmArea == null || this.farmWorkDone || this.isDoneForToday();
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

    public Field getField() {
        return this.field;
    }

    /** Clearing or cleared: it doesn't plant (or fetch seeds or bone meal) until it's told to start farming again. */
    public boolean isFieldPaused() {
        return this.field != Field.FARMING;
    }

    /**
     * From the worker panel: pull up everything planted on the farm (ripe or not) and put it all in the chest, then
     * wait. It works on it now, if it was following or staying. Returns false if it has no farm.
     */
    public boolean clearField() {
        if (this.getJob() != WorkerJob.FARMER || this.farmArea == null) {
            return false;
        }
        this.field = Field.CLEARING;
        this.cropsGone = false;
        this.setOrder(Order.WORK);
        return true;
    }

    /** From the worker panel: back to farming as usual, with the seeds in the chest. */
    public boolean startFarming() {
        if (this.getJob() != WorkerJob.FARMER || this.field == Field.FARMING) {
            return false;
        }
        this.field = Field.FARMING;
        this.setOrder(Order.WORK);
        return true;
    }

    /** Set by the farming goal while clearing: whether anything planted is left on the farm. */
    public void setCropsGone(boolean gone) {
        this.cropsGone = gone;
    }

    // Clearing: once nothing planted is left and its pockets are empty (all in the chest), the field is cleared.
    private void finishClearingIfDone() {
        if (this.field == Field.CLEARING && this.cropsGone && this.inventory.isEmpty()) {
            this.field = Field.CLEARED;
        }
    }

    public boolean hasSeeds() {
        for (ItemStack stack : this.inventory.getItems()) {
            if (isPlantableSeed(stack)) {
                return true;
            }
        }
        return false;
    }

    /** What the worker is doing right now, in words. Server side only (it names players). */
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
        boolean armed = this.getJob().isArmed();
        if (armed && this.isRanger() && !this.hasAmmo()) {
            return Component.translatable("activity.villagerhuts.out_of_ammo");
        }
        if (this.order == Order.FOLLOW) {
            Player target = this.getFollowTarget();
            UUID targetId = this.getFollowTargetId();
            if (target != null) {
                return Component.translatable(armed ? "activity.villagerhuts.guarding_player" : "activity.villagerhuts.following", target.getName());
            }
            Component name = targetId != null && this.level() instanceof ServerLevel level
                    ? WorkerRoster.playerName(level.getServer(), targetId) : Component.translatable("gui.villagerhuts.someone");
            return Component.translatable("activity.villagerhuts.following_waiting", name);
        }
        if (this.order == Order.STAY) {
            return Component.translatable(armed ? "activity.villagerhuts.holding" : "activity.villagerhuts.staying");
        }
        if (this.getJob() == WorkerJob.NONE) {
            return Component.translatable("activity.villagerhuts.unemployed");
        }
        if (this.getJob() == WorkerJob.FIGHTER) {
            return Component.translatable("activity.villagerhuts.unarmed");
        }
        if (this.field == Field.CLEARED) {
            return Component.translatable("activity.villagerhuts.field_cleared");
        }
        if (this.field == Field.CLEARING) {
            if (this.cropsGone && !this.inventory.isEmpty() && this.storageState == StorageState.NONE) {
                return Component.translatable("activity.villagerhuts.no_storage");
            }
            if (this.cropsGone && !this.inventory.isEmpty() && this.storageState == StorageState.UNREACHABLE) {
                return Component.translatable("activity.villagerhuts.storage_unreachable");
            }
            return Component.translatable("activity.villagerhuts.clearing_field");
        }
        if (armed) {
            return Component.translatable(this.guardPost == null ? "activity.villagerhuts.no_post" : "activity.villagerhuts.guarding");
        }
        if (this.isDoneForToday()) {
            return Component.translatable("activity.villagerhuts.done_for_today", this.getHarvestedToday(), this.getDailyLimit());
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
            if (this.getJob().hasTool()) {
                this.employedTicks++;
            }
            if (this.getJob().isFighter()) {
                this.fighterUpkeep();
            }
            this.finishClearingIfDone();
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
            if (this.isSleeping()) {
                this.stopSleeping(); // frees up the bed
            }
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
    protected void dropEquipment() {
        super.dropEquipment();
        this.inventory.removeAllItems().forEach(this::spawnAtLocation);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag output) {
        super.addAdditionalSaveData(output);
        output.putString("job", this.getJob().id());
        output.putString("look", this.getLook().id());
        store(output, "owner", UUIDUtil.CODEC, this.ownerId);
        store(output, "shared_with", UUIDUtil.CODEC.listOf(), List.copyOf(this.sharedWith));
        output.putString("order", this.order.id());
        store(output, "follow", UUIDUtil.CODEC, this.followId);
        store(output, "stay_pos", BlockPos.CODEC, this.stayPos);
        store(output, "farm_area", BoundingBox.CODEC, this.farmArea);
        output.putBoolean("tills_land", this.tillsLand);
        store(output, "planned_water", BlockPos.CODEC.listOf(), List.copyOf(this.plannedWater));
        store(output, "storage_pos", BlockPos.CODEC, this.storagePos);
        store(output, "bed_pos", BlockPos.CODEC, this.bedPos);
        output.putInt("rank", this.rank);
        output.putLong("harvest_day", this.harvestDay);
        output.putInt("harvested_today", this.harvestedToday);
        output.putBoolean("done_for_today", this.doneForToday);
        store(output, "collected", COLLECTED_CODEC, this.collected);
        store(output, "defeated", COLLECTED_CODEC, this.defeated);
        store(output, "guard_post", BlockPos.CODEC, this.guardPost);
        output.putString("field", this.field.id());
        output.putBoolean("crops_gone", this.cropsGone);
        output.putLong("employed_ticks", this.employedTicks);
        this.writeInventoryToTag(output, this.registryAccess());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag input) {
        super.readAdditionalSaveData(input);
        this.setJob(WorkerJob.byId(getString(input, "job", WorkerJob.NONE.id())));
        this.setLook(Optional.of(input.getString("look")).filter(look -> !look.isEmpty()).map(WorkerLook::byId).orElseGet(() -> WorkerLook.forOldWorker(this.getUUID())));
        this.ownerId = read(input, "owner", UUIDUtil.CODEC).orElse(null);
        this.sharedWith.clear();
        this.sharedWith.addAll(read(input, "shared_with", UUIDUtil.CODEC.listOf()).orElse(List.of()));
        this.order = Order.byId(getString(input, "order", Order.WORK.id()));
        this.followId = read(input, "follow", UUIDUtil.CODEC).orElse(null);
        this.stayPos = read(input, "stay_pos", BlockPos.CODEC).orElse(null);
        this.farmArea = read(input, "farm_area", BoundingBox.CODEC).orElse(null);
        this.tillsLand = getBoolean(input, "tills_land", false);
        this.plannedWater.clear();
        this.plannedWater.addAll(read(input, "planned_water", BlockPos.CODEC.listOf()).orElse(List.of()));
        this.storagePos = read(input, "storage_pos", BlockPos.CODEC).orElse(null);
        this.bedPos = read(input, "bed_pos", BlockPos.CODEC).orElse(null);
        this.rank = Math.clamp(getInt(input, "rank", 1), 1, MAX_RANK);
        this.harvestDay = getLong(input, "harvest_day", Long.MIN_VALUE);
        this.harvestedToday = getInt(input, "harvested_today", 0);
        this.doneForToday = getBoolean(input, "done_for_today", false);
        this.collected.clear();
        this.collected.putAll(read(input, "collected", COLLECTED_CODEC).orElse(Map.of()));
        this.defeated.clear();
        this.defeated.putAll(read(input, "defeated", COLLECTED_CODEC).orElse(Map.of()));
        this.guardPost = read(input, "guard_post", BlockPos.CODEC).orElse(null);
        this.field = Field.byId(getString(input, "field", Field.FARMING.id()));
        this.cropsGone = getBoolean(input, "crops_gone", false);
        this.employedTicks = getLong(input, "employed_ticks", 0L);
        this.readInventoryFromTag(input, this.registryAccess());
        this.applyRankStats();
        this.updateHomeArea();
    }

    // Saving with codecs into the entity's tag (and reading back, with a default when the key isn't there).
    private static <T> void store(CompoundTag tag, String key, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            codec.encodeStart(NbtOps.INSTANCE, value).result().ifPresent(encoded -> tag.put(key, encoded));
        }
    }

    private static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec) {
        Tag value = tag.get(key);
        return value == null ? Optional.empty() : codec.parse(NbtOps.INSTANCE, value).result();
    }

    private static String getString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    private static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    private static long getLong(CompoundTag tag, String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    private static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return this.isSleeping() ? null : SoundEvents.VILLAGER_AMBIENT;
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

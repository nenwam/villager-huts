package io.github.nenwam.villagerhuts.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerAssigning;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.entity.WorkerNames;
import io.github.nenwam.villagerhuts.entity.WorkerLook;
import io.github.nenwam.villagerhuts.item.FighterItem;
import io.github.nenwam.villagerhuts.job.Weapons;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ProjectileWeaponItem;
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.item.FarmerItem;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.network.ModNetwork;
import io.github.nenwam.villagerhuts.network.ShareWorkerPayload;
import io.github.nenwam.villagerhuts.network.WorkerCommandPayload;
import io.github.nenwam.villagerhuts.network.WorkerInfo;
import io.github.nenwam.villagerhuts.network.WorkerListPayload;
import io.github.nenwam.villagerhuts.registry.ModItems;
import io.netty.buffer.Unpooled;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Automated in-game tests ("chaos tests") for workers. Each test builds a fenced farm and a chest on a
 * grass platform and hires a farmer for them, then breaks things on purpose and checks the mod copes.
 *
 * Run them all with: ./gradlew runGameTestServer
 * Only registered in development and test runs, never in a normal game.
 */
public final class VillagerHutsTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, VillagerHuts.MODID);
    private static final Identifier PLATFORM = id("test/platform");

    // Positions on the test platform (relative). buildFarm makes a 7x7 farm (48 farmland around a water
    // block) with a fence around it, a gap in the fence on the south side, and a chest south of the gap.
    private static final int FARM_MIN_X = 4, FARM_MAX_X = 10, FARM_MIN_Z = 3, FARM_MAX_Z = 9;
    private static final BlockPos FARM_WATER = new BlockPos(7, 0, 6);
    private static final BlockPos FENCE_GAP = new BlockPos(7, 1, 10);
    private static final BlockPos FARM_CORNER = new BlockPos(4, 1, 3);       // crop level, inside the fence
    private static final BlockPos CHEST = new BlockPos(7, 1, 13);
    private static final BlockPos SPARE_CHEST_SPOT = new BlockPos(12, 1, 17);
    private static final BlockPos WORKER_SPOT = new BlockPos(9, 1, 12);       // where the farmer is hired
    private static final BlockPos FAR_AWAY = new BlockPos(60, 0, 0);          // past the 32-block limit
    private static final BlockPos NEW_FARM_CENTER = new BlockPos(8, 0, 16);   // bare grass: a 9x9 new farm fits around it
    private static final BlockPos BED_FOOT = new BlockPos(11, 1, 14);           // a bed near the chest, head to the south
    private static final BlockPos BED_HEAD = new BlockPos(11, 1, 15);

    private static final BlockPos POST_GROUND = new BlockPos(8, 0, 12);       // a fighter's post: it stands on this
    private static final BlockPos FIGHTER_SPOT = new BlockPos(8, 1, 10);      // where fighters are hired
    private static final BlockPos FOE_SPOT = new BlockPos(8, 1, 20);          // inside the post's guard zone

    /** When a test runs: in the normal batch, or in a batch of its own at night or in the morning (see registerTests). */
    private enum When { ANY, NIGHT, MORNING }

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> body, When when) {
        Test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            this(name, maxTicks, body, When.ANY);
        }
    }

    private static final List<Test> TESTS = List.of(
            new Test("farmer_item_hires_a_farmer", 40, VillagerHutsTests::farmerItemHiresAFarmer),
            new Test("farm_and_chest_assignment", 40, VillagerHutsTests::farmAndChestAssignment),
            new Test("farmer_fetches_seeds_and_plants", 2400, VillagerHutsTests::farmerFetchesSeedsAndPlants),
            new Test("farmer_harvests_replants_and_stores", 2400, VillagerHutsTests::farmerHarvestsReplantsAndStores),
            new Test("pitcher_pods_are_ignored", 400, VillagerHutsTests::pitcherPodsAreIgnored),
            new Test("chest_broken_then_replaced", 1600, VillagerHutsTests::chestBrokenThenReplaced),
            new Test("chest_broken_mid_trip", 800, VillagerHutsTests::chestBrokenMidTrip),
            new Test("fenced_off_farm_is_left_alone", 800, VillagerHutsTests::fencedOffFarmIsLeftAlone),
            new Test("walled_in_chest_is_reported", 500, VillagerHutsTests::walledInChestIsReported),
            new Test("death_removes_worker_from_roster", 100, VillagerHutsTests::deathRemovesWorkerFromRoster),
            new Test("workers_renamed_from_panel", 40, VillagerHutsTests::workersRenamedFromPanel),
            new Test("zombies_hunt_workers", 200, VillagerHutsTests::zombiesHuntWorkers),
            new Test("new_farm_plan", 40, VillagerHutsTests::newFarmPlan),
            new Test("new_farm_is_tilled_and_watered", 4000, VillagerHutsTests::newFarmIsTilledAndWatered),
            new Test("bone_meal_from_the_chest_is_used", 2000, VillagerHutsTests::boneMealFromTheChestIsUsed),
            new Test("leftovers_go_back_when_work_is_done", 2400, VillagerHutsTests::leftoversGoBackWhenWorkIsDone),
            new Test("staying_put_stops_work", 1200, VillagerHutsTests::stayingPutStopsWork),
            new Test("new_farmer_follows_its_owner", 600, VillagerHutsTests::newFarmerFollowsItsOwner),
            new Test("tool_taken_back_from_the_panel", 40, VillagerHutsTests::toolTakenBackFromThePanel),
            new Test("farmers_get_a_look_and_a_name", 40, VillagerHutsTests::farmersGetALookAndAName),
            new Test("sharing_a_worker", 40, VillagerHutsTests::sharingAWorker),
            new Test("strangers_cant_use_or_hurt_workers", 40, VillagerHutsTests::strangersCantUseOrHurtWorkers),
            new Test("upgrades_raise_the_daily_limit", 40, VillagerHutsTests::upgradesRaiseTheDailyLimit),
            new Test("recipes_for_farmers_and_upgrades", 40, VillagerHutsTests::recipesForFarmersAndUpgrades),
            new Test("farmer_stops_at_its_daily_limit", 3600, VillagerHutsTests::farmerStopsAtItsDailyLimit),
            new Test("done_for_the_day_rests_by_its_bed", 1200, VillagerHutsTests::doneForTheDayRestsByItsBed),
            new Test("worker_panel_messages_survive_the_network", 40, VillagerHutsTests::workerPanelMessagesSurviveTheNetwork),
            new Test("farmer_sleeps_in_its_bed_at_night", 800, VillagerHutsTests::farmerSleepsInItsBedAtNight, When.NIGHT),
            new Test("fighter_item_hires_an_unarmed_sikh_fighter", 40, VillagerHutsTests::fighterItemHiresAnUnarmedSikhFighter),
            new Test("weapons_make_warriors_and_rangers", 40, VillagerHutsTests::weaponsMakeWarriorsAndRangers),
            new Test("warrior_defends_its_post", 600, VillagerHutsTests::warriorDefendsItsPost),
            new Test("ranger_shoots_from_a_distance", 1200, VillagerHutsTests::rangerShootsFromADistance),
            new Test("ranger_fires_a_crossbow", 1200, VillagerHutsTests::rangerFiresACrossbow),
            new Test("ranger_throws_a_trident", 1200, VillagerHutsTests::rangerThrowsATrident),
            new Test("ranger_fetches_arrows_from_its_chest", 600, VillagerHutsTests::rangerFetchesArrowsFromItsChest),
            new Test("fighters_eat_to_heal", 1200, VillagerHutsTests::fightersEatToHeal),
            new Test("fighters_leave_friends_alone", 300, VillagerHutsTests::fightersLeaveFriendsAlone),
            new Test("warriors_leave_creepers_to_rangers", 200, VillagerHutsTests::warriorsLeaveCreepersToRangers),
            new Test("bodyguard_fights_near_its_player", 600, VillagerHutsTests::bodyguardFightsNearItsPlayer),
            new Test("fighter_upgrades_add_health_and_damage", 40, VillagerHutsTests::fighterUpgradesAddHealthAndDamage),
            new Test("fighters_never_hurt_friends", 40, VillagerHutsTests::fightersNeverHurtFriends),
            new Test("fighters_keep_watch_at_night", 400, VillagerHutsTests::fightersKeepWatchAtNight, When.NIGHT),
            new Test("fighters_sleep_in_the_morning", 800, VillagerHutsTests::fightersSleepInTheMorning, When.MORNING));

    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        for (Test test : TESTS) {
            FUNCTIONS.register(test.name(), () -> test.body());
        }
        FUNCTIONS.register(modBus);
        modBus.addListener(VillagerHutsTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> day =
                event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        // Tests run in batches by environment, so the night-time tests run on their own: the clock is set to midnight
        // for them (data/villagerhuts/function/test/night), then back to morning (test/day). The morning tests too.
        // (Each of these tests sets the time again when it starts: it can start a while after its batch does.)
        Holder<TestEnvironmentDefinition<?>> night = event.registerEnvironment(id("night"),
                new TestEnvironmentDefinition.Functions(Optional.of(id("test/night")), Optional.of(id("test/day"))));
        Holder<TestEnvironmentDefinition<?>> morning = event.registerEnvironment(id("morning"),
                new TestEnvironmentDefinition.Functions(Optional.of(id("test/day")), Optional.empty()));
        for (Test test : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(test.name()));
            Holder<TestEnvironmentDefinition<?>> environment = switch (test.when()) {
                case ANY -> day;
                case NIGHT -> night;
                case MORNING -> morning;
            };
            event.registerTest(id(test.name()), data -> new FunctionGameTestInstance(function, data),
                    new TestData<>(environment, PLATFORM, test.maxTicks(), 0, true));
        }
    }

    // ================================================================ tests

    /** The Farmer item hires a farmer with an iron hoe, owned by the player, following them, with no farm, chest or seeds yet. */
    private static void farmerItemHiresAFarmer(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        helper.assertTrue(worker != null, "no farmer spawned");
        helper.assertTrue(worker.getJob() == WorkerJob.FARMER, "not a farmer: " + worker.getJob());
        helper.assertTrue(worker.getMainHandItem().is(Items.IRON_HOE), "not holding an iron hoe");
        helper.assertTrue(worker.isOwner(owner) && owner.getUUID().equals(worker.getOwnerId()), "not owned by the player");
        helper.assertTrue(worker.getFarmArea() == null && worker.getStoragePos() == null, "started with a farm or chest");
        helper.assertTrue(!worker.hasSeeds(), "started with seeds");
        helper.assertTrue(worker.getOrder() == WorkerEntity.Order.FOLLOW, "not following its owner: " + worker.getOrder());
        assertStatus(helper, worker, "activity.villagerhuts.following_waiting"); // the mock owner isn't in the world
        helper.succeed();
    }

    /**
     * Right-clicking a farm or chest while the worker is selected (WorkerAssigning.assign): the farm is the
     * farmland's bounds, the chest is the clicked block, and spots with no farmland, or too far away, are refused.
     */
    private static void farmAndChestAssignment(GameTestHelper helper) {
        buildFarm(helper);
        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), null);

        BlockPos home = worker.blockPosition();
        BlockPos far = helper.absolutePos(FAR_AWAY);
        worker.snapTo(far.getX() + 0.5, far.getY() + 1, far.getZ() + 0.5, 0, 0); // walked off: everything is too far
        assertOutcome(helper, assign(helper, worker, CHEST), false, "message.villagerhuts.chest_too_far");
        assertOutcome(helper, assign(helper, worker, FARM_WATER), false, "message.villagerhuts.farm_too_far");
        worker.snapTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
        assertOutcome(helper, assign(helper, worker, new BlockPos(14, 0, 21)), false, "message.villagerhuts.no_farmland");
        helper.assertTrue(worker.getFarmArea() == null && worker.getStoragePos() == null, "a refused click still assigned something");

        assertOutcome(helper, assign(helper, worker, FARM_WATER), true, "message.villagerhuts.farm_assigned");
        BoundingBox expected = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(FARM_MIN_X, 0, FARM_MIN_Z)),
                helper.absolutePos(new BlockPos(FARM_MAX_X, 1, FARM_MAX_Z)));
        helper.assertTrue(expected.equals(worker.getFarmArea()), "farm is " + worker.getFarmArea() + ", expected " + expected);
        assertOutcome(helper, assign(helper, worker, CHEST), true, "message.villagerhuts.chest_assigned");
        helper.assertTrue(helper.absolutePos(CHEST).equals(worker.getStoragePos()), "chest is " + worker.getStoragePos());
        helper.assertTrue(worker.getOrder() == WorkerEntity.Order.WORK, "not set working by being given work: " + worker.getOrder());
        helper.succeed();
    }

    /** Seeds put in the chest get fetched and planted: exactly as many crops as seeds, none left over. */
    private static void farmerFetchesSeedsAndPlants(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        Container chest = container(helper, CHEST);
        chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 12));
        helper.succeedWhen(() -> {
            helper.assertTrue(countCrops(helper) == 12, "planted " + countCrops(helper) + " of 12 seeds");
            helper.assertTrue(count(chest, Items.WHEAT_SEEDS) == 0, "seeds left in the chest");
            helper.assertTrue(!worker.hasSeeds(), "worker still holding seeds");
        });
    }

    /** Ripe crops get harvested, replanted from their own seeds, and the harvest ends up in the chest. */
    private static void farmerHarvestsReplantsAndStores(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        BlockPos[] ripe = {new BlockPos(4, 1, 3), new BlockPos(5, 1, 3), new BlockPos(6, 1, 3), new BlockPos(4, 1, 4), new BlockPos(5, 1, 4)};
        for (BlockPos pos : ripe) {
            helper.setBlock(pos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        Container chest = container(helper, CHEST);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.WHEAT) >= ripe.length, "chest has " + count(chest, Items.WHEAT) + " wheat");
            for (BlockPos pos : ripe) {
                helper.assertTrue(helper.getBlockState(pos).is(Blocks.WHEAT), "not replanted at " + pos);
            }
            helper.assertTrue(worker.getCollected().values().stream().mapToInt(Integer::intValue).sum() > 0, "nothing counted as collected");
        });
    }

    /** Pitcher pods are in vanilla's villager seed list but never harvestable: leave them in the chest. */
    private static void pitcherPodsAreIgnored(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        Container chest = container(helper, CHEST);
        chest.setItem(0, new ItemStack(Items.PITCHER_POD, 8));
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(count(chest, Items.PITCHER_POD) == 8, "pitcher pods were taken");
                    helper.assertTrue(countBlocks(helper, Blocks.PITCHER_CROP) == 0, "a pitcher crop was planted");
                    assertStatus(helper, worker, "activity.villagerhuts.out_of_seeds");
                })
                .thenSucceed();
    }

    /** Chest broken while the worker's pockets are full: nothing lost or duplicated; a new chest, once assigned, gets used. */
    private static void chestBrokenThenReplaced(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        fillPockets(worker, Items.WHEAT);
        helper.setBlock(CHEST, Blocks.AIR);
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(count(worker.getInventory(), Items.WHEAT) == 9 * 64, "worker's wheat changed with no chest");
                    assertStatus(helper, worker, "activity.villagerhuts.no_storage");
                    helper.setBlock(SPARE_CHEST_SPOT, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
                    assertOutcome(helper, assign(helper, worker, SPARE_CHEST_SPOT), true, "message.villagerhuts.chest_assigned");
                })
                .thenWaitUntil(() -> {
                    Container replacement = container(helper, SPARE_CHEST_SPOT);
                    helper.assertTrue(count(replacement, Items.WHEAT) == 9 * 64, "new chest has " + count(replacement, Items.WHEAT) + " wheat");
                    helper.assertTrue(count(worker.getInventory(), Items.WHEAT) == 0, "worker still holding wheat");
                })
                .thenSucceed();
    }

    /** Chest broken while the farmer walks over to get seeds: no seeds appear, nothing gets planted. */
    private static void chestBrokenMidTrip(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        BlockPos start = helper.absolutePos(FARM_CORNER); // far from the chest, so the trip takes a while
        worker.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        container(helper, CHEST).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 20));
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> helper.setBlock(CHEST, Blocks.AIR))
                .thenIdle(400)
                .thenExecute(() -> {
                    helper.assertTrue(worker.isAlive(), "worker died");
                    helper.assertTrue(!worker.hasSeeds(), "worker has seeds from a chest that was broken");
                    helper.assertTrue(countCrops(helper) == 0, "crops planted without seeds");
                    assertStatus(helper, worker, "activity.villagerhuts.no_storage");
                })
                .thenSucceed();
    }

    /** Fence gap closed: the farm can't be reached, so nothing is planted (not even through the fence). */
    private static void fencedOffFarmIsLeftAlone(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        worker.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        helper.setBlock(FENCE_GAP, Blocks.OAK_FENCE);
        helper.startSequence()
                .thenIdle(600)
                .thenExecute(() -> {
                    helper.assertTrue(countCrops(helper) == 0, countCrops(helper) + " crops planted on a fenced-off farm");
                    helper.assertTrue(count(worker.getInventory(), Items.WHEAT_SEEDS) == 16, "seeds were used");
                    assertStatus(helper, worker, "activity.villagerhuts.farm_unreachable");
                })
                .thenSucceed();
    }

    /** Chest blocked in so nobody can stand next to it: reported, and nothing stored through the blocks. */
    private static void walledInChestIsReported(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        fillPockets(worker, Items.WHEAT);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CHEST.relative(side), Blocks.STONE);
        }
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    assertStatus(helper, worker, "activity.villagerhuts.storage_unreachable");
                    helper.assertTrue(count(container(helper, CHEST), Items.WHEAT) == 0, "stored into a chest it can't reach");
                    helper.assertTrue(count(worker.getInventory(), Items.WHEAT) == 9 * 64, "worker's wheat changed");
                })
                .thenSucceed();
    }

    /** The roster (worker panel list) adds an owned worker, and drops it as soon as it dies. */
    private static void deathRemovesWorkerFromRoster(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = hireFarmer(helper, owner);
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        helper.startSequence()
                .thenIdle(25) // the roster refreshes every 20 ticks
                .thenExecute(() -> helper.assertTrue(roster.entry(worker.getUUID()).isPresent(), "worker not added to the roster"))
                .thenExecute(() -> worker.kill(helper.getLevel()))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(roster.entry(worker.getUUID()).isEmpty(), "dead worker still on the roster"))
                .thenSucceed();
    }

    /**
     * Renaming from the worker panel: only the owner can, the name is cleaned up like an anvil rename,
     * an empty name removes it, and a worker renamed while far away takes the name when it's next loaded.
     */
    private static void workersRenamedFromPanel(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = hireFarmer(helper, owner);
        MinecraftServer server = helper.getLevel().getServer();
        WorkerRoster roster = WorkerRoster.get(server);
        roster.update(worker); // normally happens within a second

        helper.assertTrue(ModNetwork.renameWorker(server, owner.getUUID(), worker.getUUID(), "  Bob\u00a7\n "), "owner couldn't rename");
        assertName(helper, worker, roster, "Bob");
        helper.assertTrue(!ModNetwork.renameWorker(server, UUID.randomUUID(), worker.getUUID(), "Thief"), "someone else renamed it");
        helper.assertTrue(!ModNetwork.renameWorker(server, owner.getUUID(), worker.getUUID(), "x".repeat(51)), "51-letter name accepted");
        assertName(helper, worker, roster, "Bob");

        helper.assertTrue(ModNetwork.renameWorker(server, owner.getUUID(), worker.getUUID(), "   "), "owner couldn't clear the name");
        assertName(helper, worker, roster, null);

        roster.rename(worker.getUUID(), Optional.of("Far Away Fred")); // what the panel does for a worker that isn't loaded
        helper.assertTrue(roster.entry(worker.getUUID()).orElseThrow().customName().equals(Optional.of("Far Away Fred")),
                "panel doesn't show the name given while far away");
        roster.update(worker); // what happens when it's loaded again
        assertName(helper, worker, roster, "Far Away Fred");
        helper.assertTrue(roster.entry(worker.getUUID()).orElseThrow().pendingName().isEmpty(), "name still waiting after it was used");
        helper.succeed();
    }

    /**
     * Zombies go after workers like they go after villagers (they need to see them first, like villagers).
     * The helmet stops the zombie burning in the daylight the tests run in.
     */
    private static void zombiesHuntWorkers(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(14, 1, 14)); // open grass, in plain view
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.succeedWhen(() -> helper.assertTrue(zombie.getTarget() == worker, "zombie's target is " + zombie.getTarget()));
    }

    /**
     * Planning a new farm (shift + right-click, then the ground): every bit of grass or dirt with room on top is
     * tilled (a flower spot isn't, short grass is), and water goes in so all of it stays wet. Air: nothing to farm.
     */
    private static void newFarmPlan(GameTestHelper helper) {
        helper.setBlock(new BlockPos(6, 1, 18), Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(10, 1, 18), Blocks.POPPY);
        FarmPlan plan = FarmPlan.find(helper.getLevel(), helper.absolutePos(new BlockPos(8, 0, 15)), 16);
        helper.assertTrue(plan.box() != null, "no farm planned on grass");
        helper.assertTrue(plan.till().contains(helper.absolutePos(new BlockPos(6, 0, 18))), "grass under short grass not tilled");
        helper.assertTrue(!plan.till().contains(helper.absolutePos(new BlockPos(10, 0, 18))), "grass under a flower tilled");
        helper.assertTrue(!plan.water().isEmpty() && plan.till().size() + plan.water().size() == 255,
                plan.till().size() + " to till and " + plan.water().size() + " water, expected 255 in all (256 minus the flower)");
        for (BlockPos spot : plan.till()) {
            helper.assertTrue(plan.water().stream().anyMatch(water -> FarmPlan.wets(water, spot)), "nothing keeps " + spot + " wet");
        }
        BlockPos sky = helper.absolutePos(new BlockPos(8, 8, 15));
        helper.assertTrue(FarmPlan.find(helper.getLevel(), sky, 9).box() == null, "a farm planned in the air");

        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), null);
        assertOutcome(helper, WorkerAssigning.createFarm(helper.getLevel(), worker, sky), false, "message.villagerhuts.nothing_to_farm");
        BlockPos far = helper.absolutePos(FAR_AWAY);
        worker.snapTo(far.getX() + 0.5, far.getY() + 1, far.getZ() + 0.5, 0, 0);
        assertOutcome(helper, WorkerAssigning.createFarm(helper.getLevel(), worker, helper.absolutePos(NEW_FARM_CENTER)), false,
                "message.villagerhuts.farm_too_far");
        helper.succeed();
    }

    /** A new 9x9 farm on bare grass: the farmer puts water in the middle and tills the other 80 blocks, with no seeds needed. */
    private static void newFarmIsTilledAndWatered(GameTestHelper helper) {
        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), null);
        assertOutcome(helper, WorkerAssigning.createFarm(helper.getLevel(), worker, helper.absolutePos(NEW_FARM_CENTER)), true,
                "message.villagerhuts.farm_created");
        helper.assertTrue(worker.getPlannedWater().equals(List.of(helper.absolutePos(NEW_FARM_CENTER))),
                "water planned at " + worker.getPlannedWater() + ", expected just the middle");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(NEW_FARM_CENTER).is(Blocks.WATER), "no water in the middle");
            int farmland = 0;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (helper.getBlockState(NEW_FARM_CENTER.offset(x, 0, z)).getBlock() instanceof FarmlandBlock) {
                        farmland++;
                    }
                }
            }
            helper.assertTrue(farmland == 80, farmland + " of 80 blocks tilled");
        });
    }

    /** Bone meal put in the chest gets fetched and used: young crops grow up fast and get harvested. */
    private static void boneMealFromTheChestIsUsed(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        BlockPos[] young = {new BlockPos(4, 1, 3), new BlockPos(5, 1, 3), new BlockPos(6, 1, 3)};
        for (BlockPos pos : young) {
            helper.setBlock(pos, Blocks.WHEAT.defaultBlockState());
        }
        Container chest = container(helper, CHEST);
        chest.setItem(0, new ItemStack(Items.BONE_MEAL, 32));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.BONE_MEAL) + count(worker.getInventory(), Items.BONE_MEAL) < 32, "no bone meal used");
            helper.assertTrue(worker.getCollected().getOrDefault(Identifier.withDefaultNamespace("wheat"), 0) >= young.length,
                    "harvested " + worker.getCollected());
        });
    }

    /** Leftover seeds go back in the chest once everything is planted (not kept in its pockets). */
    private static void leftoversGoBackWhenWorkIsDone(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        worker.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 60));
        Container chest = container(helper, CHEST);
        helper.succeedWhen(() -> {
            helper.assertTrue(countCrops(helper) == 48, "planted " + countCrops(helper) + " of 48");
            helper.assertTrue(count(chest, Items.WHEAT_SEEDS) == 12, "chest has " + count(chest, Items.WHEAT_SEEDS) + " of the 12 seeds left over");
            helper.assertTrue(worker.getInventory().isEmpty(), "still carrying something");
        });
    }

    /** Told to stay put, a farmer with seeds and a farm doesn't work (or wander); told to work again, it does. */
    private static void stayingPutStopsWork(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        worker.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        worker.setOrder(WorkerEntity.Order.STAY);
        BlockPos spot = worker.blockPosition();
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(countCrops(helper) == 0, countCrops(helper) + " crops planted while staying put");
                    helper.assertTrue(worker.blockPosition().closerThan(spot, 2), "wandered off to " + worker.blockPosition());
                    assertStatus(helper, worker, "activity.villagerhuts.staying");
                    worker.setOrder(WorkerEntity.Order.WORK);
                })
                .thenWaitUntil(() -> helper.assertTrue(countCrops(helper) > 0, "not back at work"))
                .thenSucceed();
    }

    /** A newly hired farmer follows the player who hired it. */
    @SuppressWarnings("removal") // the mock player that's really in the world, which following needs
    private static void newFarmerFollowsItsOwner(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(new BlockPos(14, 1, 21));
        owner.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), owner);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(worker.distanceTo(owner) < 4,
                        "still " + String.format(java.util.Locale.ROOT, "%.1f", worker.distanceTo(owner)) + " blocks from its owner"))
                .thenExecute(() -> assertStatus(helper, worker, "activity.villagerhuts.following"))
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(owner))
                .thenSucceed();
    }

    /** The worker panel's buttons: take back the hoe (owner only), and orders. */
    private static void toolTakenBackFromThePanel(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = hireFarmer(helper, owner);
        MinecraftServer server = helper.getLevel().getServer();
        WorkerRoster.get(server).update(worker); // normally happens within a second
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!ModNetwork.commandWorker(server, stranger, worker.getUUID(), WorkerCommandPayload.Command.TAKE_TOOL),
                "someone else took the hoe");
        helper.assertTrue(ModNetwork.commandWorker(server, owner, worker.getUUID(), WorkerCommandPayload.Command.TAKE_TOOL), "owner couldn't take the hoe");
        helper.assertTrue(worker.getJob() == WorkerJob.NONE && worker.getMainHandItem().isEmpty(), "worker still has its job or hoe");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.IRON_HOE)), "owner didn't get the hoe");
        helper.assertTrue(!ModNetwork.commandWorker(server, owner, worker.getUUID(), WorkerCommandPayload.Command.TAKE_TOOL), "took a hoe twice");
        helper.assertTrue(ModNetwork.commandWorker(server, owner, worker.getUUID(), WorkerCommandPayload.Command.FOLLOW)
                && worker.getOrder() == WorkerEntity.Order.FOLLOW, "follow order not taken");
        helper.succeed();
    }

    /** New farmers get a look and a name that suits it; one player's farmers get different names. */
    private static void farmersGetALookAndAName(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        WorkerEntity first = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        roster.update(first);
        WorkerEntity second = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT.east()), owner);
        for (WorkerEntity worker : List.of(first, second)) {
            helper.assertTrue(worker.hasCustomName() && WorkerNames.isKnown(worker.getCustomName().getString()),
                    "farmer has no name from the lists: " + worker.getCustomName());
        }
        helper.assertTrue(!first.getCustomName().getString().equals(second.getCustomName().getString()),
                "two farmers of the same player both named " + first.getCustomName().getString());
        helper.succeed();
    }

    /**
     * Sharing from the worker panel: only the owner shares (with players who are online). A player it's shared with
     * can give orders (Follow follows them) and rename it, but not take its hoe. Unsharing takes that away (and it
     * follows its owner again); they can also leave by themselves. Giving it away makes them the owner, and the old
     * owner a shared player.
     */
    @SuppressWarnings("removal") // mock players that are really online, which sharing needs
    private static void sharingAWorker(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        Player offline = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            WorkerEntity worker = hireFarmer(helper, owner);
            UUID id = worker.getUUID();
            WorkerRoster roster = WorkerRoster.get(server);
            roster.update(worker);
            helper.assertTrue(!ModNetwork.commandWorker(server, friend, id, WorkerCommandPayload.Command.FOLLOW), "gave orders before it was shared");
            helper.assertTrue(!ModNetwork.renameWorker(server, friend.getUUID(), id, "Mine"), "renamed before it was shared");

            helper.assertTrue(ModNetwork.shareWorker(server, owner, id, friend.getUUID(), ShareWorkerPayload.Action.SHARE), "owner couldn't share");
            helper.assertTrue(worker.canCommand(friend), "shared, but the worker doesn't know");
            helper.assertTrue(!ModNetwork.shareWorker(server, friend, id, owner.getUUID(), ShareWorkerPayload.Action.UNSHARE), "a shared player unshared the owner");
            helper.assertTrue(!ModNetwork.shareWorker(server, owner, id, offline.getUUID(), ShareWorkerPayload.Action.SHARE), "shared with a player who isn't online");
            List<WorkerInfo> friendsList = ModNetwork.workerList(server, friend.getUUID());
            helper.assertTrue(friendsList.size() == 1 && !friendsList.get(0).yours() && friendsList.get(0).owner().id().equals(owner.getUUID()),
                    "shared worker not in the friend's panel as someone else's");

            helper.assertTrue(ModNetwork.commandWorker(server, friend, id, WorkerCommandPayload.Command.FOLLOW)
                    && friend.getUUID().equals(worker.getFollowTargetId()), "doesn't follow the shared player who said so");
            helper.assertTrue(ModNetwork.renameWorker(server, friend.getUUID(), id, "Ramu"), "shared player couldn't rename");
            helper.assertTrue(!ModNetwork.commandWorker(server, friend, id, WorkerCommandPayload.Command.TAKE_TOOL)
                    && worker.getMainHandItem().is(Items.IRON_HOE), "shared player took the hoe");

            helper.assertTrue(ModNetwork.shareWorker(server, owner, id, friend.getUUID(), ShareWorkerPayload.Action.UNSHARE), "owner couldn't unshare");
            helper.assertTrue(!worker.canCommand(friend) && ModNetwork.workerList(server, friend.getUUID()).isEmpty(), "still shared after unsharing");
            helper.assertTrue(owner.getUUID().equals(worker.getFollowTargetId()), "still following the player it was taken from");

            ModNetwork.shareWorker(server, owner, id, friend.getUUID(), ShareWorkerPayload.Action.SHARE);
            helper.assertTrue(ModNetwork.shareWorker(server, friend, id, friend.getUUID(), ShareWorkerPayload.Action.UNSHARE)
                    && !worker.canCommand(friend), "shared player couldn't leave");

            ModNetwork.shareWorker(server, owner, id, friend.getUUID(), ShareWorkerPayload.Action.SHARE);
            helper.assertTrue(!ModNetwork.shareWorker(server, friend, id, friend.getUUID(), ShareWorkerPayload.Action.GIVE), "a shared player gave it to themselves");
            helper.assertTrue(ModNetwork.shareWorker(server, owner, id, friend.getUUID(), ShareWorkerPayload.Action.GIVE), "owner couldn't give it away");
            helper.assertTrue(worker.isOwner(friend) && !worker.isOwner(owner) && worker.canCommand(owner), "giving it away didn't swap owner and sharer");
            helper.assertTrue(roster.entry(id).orElseThrow().owner().equals(friend.getUUID()), "the worker list still has the old owner");
            helper.succeed();
        } finally {
            server.getPlayerList().remove(owner);
            server.getPlayerList().remove(friend);
        }
    }

    /** Someone it isn't shared with can't lead it away, rename it with a name tag, upgrade it, or hurt it. Its owner can hurt it. */
    private static void strangersCantUseOrHurtWorkers(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = hireFarmer(helper, owner);
        String name = worker.getCustomName().getString();

        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        worker.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(!worker.isLeashed(), "a stranger put a lead on it");
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal("Stolen"));
        stranger.setItemInHand(InteractionHand.MAIN_HAND, tag);
        worker.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(worker.getCustomName().getString().equals(name), "a stranger renamed it to " + worker.getCustomName().getString());
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIAMOND_CARROT.get(), 64));
        worker.interact(stranger, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(worker.getRank() == 1 && stranger.getMainHandItem().getCount() == 64, "a stranger upgraded it");

        float health = worker.getHealth();
        worker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(stranger), 5.0F);
        helper.assertTrue(worker.getHealth() == health, "a stranger hurt it");
        worker.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 1.0F);
        helper.assertTrue(worker.getHealth() < health, "its owner couldn't hurt it");
        helper.succeed();
    }

    /**
     * Diamond carrots: 1 for Apprentice, 2 for Journeyman; flawless ones: 1 for Expert, 2 for Master. Each level adds a
     * day's harvest. Too few, or the wrong kind, does nothing and uses nothing up.
     */
    private static void upgradesRaiseTheDailyLimit(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        int base = worker.getDailyLimit();
        helper.assertTrue(worker.getRank() == 1 && base == 64, "new farmer is rank " + worker.getRank() + " with a limit of " + base);
        Item diamond = ModItems.DIAMOND_CARROT.get();
        Item flawless = ModItems.FLAWLESS_DIAMOND_CARROT.get();

        offer(helper, worker, owner, flawless, 2, 1, 2);  // Novice: flawless ones aren't for Apprentice
        offer(helper, worker, owner, diamond, 1, 2, 0);   // Apprentice
        offer(helper, worker, owner, diamond, 1, 2, 1);   // Journeyman takes 2
        offer(helper, worker, owner, diamond, 3, 3, 1);   // Journeyman
        offer(helper, worker, owner, diamond, 5, 3, 5);   // Expert takes flawless ones
        offer(helper, worker, owner, flawless, 1, 4, 0);  // Expert
        offer(helper, worker, owner, flawless, 1, 4, 1);  // Master takes 2
        offer(helper, worker, owner, flawless, 2, 5, 0);  // Master
        helper.assertTrue(worker.getDailyLimit() == 5 * base, "Master's limit is " + worker.getDailyLimit());
        offer(helper, worker, owner, flawless, 2, 5, 2);  // nothing past Master
        offer(helper, worker, owner, Items.GOLDEN_CARROT, 64, 5, 64); // golden carrots don't upgrade any more
        helper.succeed();
    }

    // The owner right-clicks the worker holding `count` of `item`: it should end up at `rank`, with `left` of them left.
    private static void offer(GameTestHelper helper, WorkerEntity worker, Player owner, Item item, int count, int rank, int left) {
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, count));
        worker.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
        ItemStack held = owner.getMainHandItem();
        helper.assertTrue(worker.getRank() == rank && (left == 0 ? held.isEmpty() : held.is(item) && held.getCount() == left),
                "offered " + count + " " + item + ": rank " + worker.getRank() + " (expected " + rank + "), holding " + held);
    }

    /**
     * Diamond carrots are a carrot in eight diamonds; flawless ones a carrot in eight diamond blocks. A Farmer needs a
     * golden carrot, and so does a Fighter (with a grindstone and a target).
     */
    private static void recipesForFarmersAndUpgrades(GameTestHelper helper) {
        assertCrafts(helper, ring(Items.DIAMOND, Items.CARROT), ModItems.DIAMOND_CARROT.get());
        assertCrafts(helper, ring(Items.DIAMOND_BLOCK, Items.CARROT), ModItems.FLAWLESS_DIAMOND_CARROT.get());
        assertCrafts(helper, ring(Items.DIAMOND, Items.GOLDEN_CARROT), null);
        assertCrafts(helper, ring(Items.GOLD_BLOCK, Items.CARROT), null);
        assertCrafts(helper, grid(Items.IRON_HOE, Items.COMPOSTER, Items.GOLDEN_CARROT), ModItems.FARMER.get());
        assertCrafts(helper, grid(Items.GOLDEN_CARROT, Items.IRON_HOE, Items.COMPOSTER), ModItems.FARMER.get());
        assertCrafts(helper, grid(Items.IRON_HOE, Items.COMPOSTER), null);
        assertCrafts(helper, grid(Items.IRON_HOE, Items.COMPOSTER, Items.CARROT), null);
        assertCrafts(helper, grid(Items.GRINDSTONE, Items.TARGET, Items.GOLDEN_CARROT), ModItems.FIGHTER.get());
        assertCrafts(helper, grid(Items.TARGET, Items.GOLDEN_CARROT, Items.GRINDSTONE), ModItems.FIGHTER.get());
        assertCrafts(helper, grid(Items.GRINDSTONE, Items.TARGET), null);
        helper.succeed();
    }

    // A 3x3 crafting grid: `center` surrounded by eight `around`.
    private static List<ItemStack> ring(Item around, Item center) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(new ItemStack(i == 4 ? center : around));
        }
        return items;
    }

    // A 3x3 crafting grid with these items in its first slots and the rest empty.
    private static List<ItemStack> grid(Item... first) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            items.add(i < first.length ? new ItemStack(first[i]) : ItemStack.EMPTY);
        }
        return items;
    }

    private static void assertCrafts(GameTestHelper helper, List<ItemStack> items, @Nullable Item expected) {
        CraftingInput input = CraftingInput.of(3, 3, items);
        ItemStack result = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(recipe -> recipe.value().assemble(input))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(expected == null ? result.isEmpty() : result.is(expected),
                items.stream().map(stack -> stack.getItem().toString()).toList() + " made " + result + ", expected " + expected);
    }

    /**
     * A whole farm of ripe wheat is more than a day's harvest: the farmer harvests up to its limit and never past it,
     * leaves the rest, puts everything away and rests. The next day it carries on.
     */
    private static void farmerStopsAtItsDailyLimit(GameTestHelper helper) {
        WorkerEntity worker = hireFarmer(helper, null);
        for (int x = FARM_MIN_X; x <= FARM_MAX_X; x++) {
            for (int z = FARM_MIN_Z; z <= FARM_MAX_Z; z++) {
                if (x != FARM_WATER.getX() || z != FARM_WATER.getZ()) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
                }
            }
        }
        Container chest = container(helper, CHEST);
        int limit = worker.getDailyLimit();
        int[] ripeLeft = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(worker.isDoneForToday(), "not done for the day yet (" + worker.getHarvestedToday() + " harvested)");
                    helper.assertTrue(worker.getInventory().isEmpty(), "hasn't put its harvest away");
                    assertStatus(helper, worker, "activity.villagerhuts.resting");
                })
                .thenExecute(() -> {
                    int harvested = worker.getHarvestedToday();
                    // A wheat harvest is at most 4 items, so it stops within 4 of the limit, and never past it.
                    helper.assertTrue(harvested <= limit && harvested > limit - 4, "harvested " + harvested + " with a limit of " + limit);
                    ripeLeft[0] = countBlocks(helper, Blocks.WHEAT);
                    helper.assertTrue(ripeLeft[0] > 0 && count(chest, Items.WHEAT) > 0, "nothing left for tomorrow, or nothing stored");
                    runCommand(helper, "time add 24000");
                })
                .thenWaitUntil(() -> helper.assertTrue(!worker.isDoneForToday() && worker.getHarvestedToday() > 0,
                        "not back at work the next day"))
                .thenSucceed();
    }

    private static void runCommand(GameTestHelper helper, String command) {
        MinecraftServer server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Done for the day, a farmer stays by its bed. A bed can only be one worker's. */
    private static void doneForTheDayRestsByItsBed(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity worker = hireFarmer(helper, owner);
        placeBed(helper);
        assertOutcome(helper, assign(helper, worker, BED_FOOT), true, "message.villagerhuts.bed_assigned");
        BlockPos head = helper.absolutePos(BED_HEAD);
        helper.assertTrue(head.equals(worker.getBedPos()), "bed is " + worker.getBedPos() + ", expected its head at " + head);
        WorkerRoster.get(helper.getLevel().getServer()).update(worker);
        WorkerEntity other = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        assertOutcome(helper, assign(helper, other, BED_HEAD), false, "message.villagerhuts.bed_taken");

        helper.assertTrue(worker.takeHarvest(worker.getDailyLimit()) && worker.isDoneForToday(), "a full day's harvest didn't finish its day");
        BlockPos start = helper.absolutePos(FARM_CORNER);
        worker.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertTrue(worker.position().distanceTo(Vec3.atBottomCenterOf(head)) < 4.5, "not by its bed");
            assertStatus(helper, worker, "activity.villagerhuts.resting");
        });
    }

    /** At night (this test's batch runs at midnight) a farmer goes to its bed and sleeps in it. */
    private static void farmerSleepsInItsBedAtNight(GameTestHelper helper) {
        // Midnight again: the batch sets it when it starts, but the test can start thousands of ticks later (while its
        // spot loads), by when it can be morning.
        runCommand(helper, "time set 18000");
        WorkerEntity worker = hireFarmer(helper, null);
        placeBed(helper);
        assertOutcome(helper, assign(helper, worker, BED_HEAD), true, "message.villagerhuts.bed_assigned");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().isDarkOutside(), "it isn't night");
            helper.assertTrue(worker.isSleeping(), "not asleep");
            helper.assertTrue(helper.getBlockState(BED_HEAD).getValue(AbstractBedBlock.OCCUPIED), "bed not taken");
            assertStatus(helper, worker, "activity.villagerhuts.sleeping");
        });
    }

    /**
     * The worker panel's messages come through being written out and read back unchanged, as they are on a server.
     * (In single player they're handed over without that, so a mistake here would only show in multiplayer.)
     */
    @SuppressWarnings("removal") // a mock player that's really online, which sharing needs
    private static void workerPanelMessagesSurviveTheNetwork(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerPlayer friend = helper.makeMockServerPlayerInLevel();
        try {
            WorkerEntity worker = hireFarmer(helper, owner);
            placeBed(helper);
            assign(helper, worker, BED_HEAD);
            worker.recordCollected(new ItemStack(Items.WHEAT, 12));
            WorkerRoster.get(server).update(worker);
            WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.CROSSBOW));
            ranger.getInventory().addItem(new ItemStack(Items.ARROW, 20));
            ranger.recordDefeated(helper.spawn(EntityTypes.HUSK, FOE_SPOT));
            WorkerRoster.get(server).update(ranger);
            ModNetwork.shareWorker(server, owner, worker.getUUID(), friend.getUUID(), ShareWorkerPayload.Action.SHARE);
            for (UUID player : List.of(owner.getUUID(), friend.getUUID())) {
                WorkerListPayload list = new WorkerListPayload(ModNetwork.workerList(server, player));
                helper.assertTrue(!list.workers().isEmpty(), "nothing to send");
                assertRoundTrip(helper, server, WorkerListPayload.STREAM_CODEC, list);
            }
            assertRoundTrip(helper, server, ShareWorkerPayload.STREAM_CODEC,
                    new ShareWorkerPayload(worker.getUUID(), friend.getUUID(), ShareWorkerPayload.Action.GIVE));
            assertRoundTrip(helper, server, WorkerCommandPayload.STREAM_CODEC,
                    new WorkerCommandPayload(worker.getUUID(), WorkerCommandPayload.Command.STAY));
            helper.succeed();
        } finally {
            server.getPlayerList().remove(friend);
        }
    }

    // Sent and read back, it reads all of what was sent, and sends the same again (item stacks have no equals()).
    private static <T> void assertRoundTrip(GameTestHelper helper, MinecraftServer server, StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        codec.encode(buf, value);
        byte[] sent = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), sent);
        T read = codec.decode(buf);
        helper.assertTrue(buf.readableBytes() == 0, "left " + buf.readableBytes() + " bytes unread of " + value);
        RegistryFriendlyByteBuf again = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        codec.encode(again, read);
        byte[] resent = new byte[again.readableBytes()];
        again.getBytes(again.readerIndex(), resent);
        helper.assertTrue(java.util.Arrays.equals(sent, resent), "came back as " + read + ", sent " + value);
    }

    // ================================================================ helpers

    /** A red bed at BED_FOOT / BED_HEAD. */
    // ================================================================ fighters

    /** The Fighter item hires an unarmed fighter: a Sikh warrior with a Singh or Kaur name, owned by the player, following them. */
    private static void fighterItemHiresAnUnarmedSikhFighter(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerRoster roster = WorkerRoster.get(helper.getLevel().getServer());
        WorkerEntity first = FighterItem.spawnFighter(helper.getLevel(), helper.absolutePos(FIGHTER_SPOT), owner);
        roster.update(first);
        WorkerEntity second = FighterItem.spawnFighter(helper.getLevel(), helper.absolutePos(FIGHTER_SPOT.east()), owner);
        for (WorkerEntity fighter : List.of(first, second)) {
            helper.assertTrue(fighter.getJob() == WorkerJob.FIGHTER && fighter.getMainHandItem().isEmpty(), "not an unarmed fighter: " + fighter.getJob());
            helper.assertTrue(fighter.getLook().kind() == WorkerLook.Kind.FIGHTER, "dressed as a farmer: " + fighter.getLook());
            helper.assertTrue(fighter.hasCustomName() && WorkerNames.isSikhFighterName(fighter.getCustomName().getString()),
                    "not a Sikh fighter's name: " + fighter.getCustomName());
            helper.assertTrue(fighter.isOwner(owner) && fighter.getOrder() == WorkerEntity.Order.FOLLOW, "not following its owner");
        }
        helper.assertTrue(!first.getCustomName().getString().equals(second.getCustomName().getString()),
                "two fighters of the same player both named " + first.getCustomName().getString());
        assertStatus(helper, first, "activity.villagerhuts.following_waiting"); // the mock owner isn't in the world
        helper.succeed();
    }

    /**
     * Any melee weapon makes a warrior and any ranged weapon a ranger, checked against every item in the game; nothing else
     * arms a fighter, and farmers don't take weapons. The owner can swap weapons (and gets the old one back); a player it's
     * shared with can't. Taking the weapon back leaves it unarmed.
     */
    private static void weaponsMakeWarriorsAndRangers(GameTestHelper helper) {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            String name = BuiltInRegistries.ITEM.getKey(item).toString();
            boolean melee = stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || item == Items.MACE;
            boolean ranged = item instanceof ProjectileWeaponItem || item == Items.TRIDENT;
            boolean neither = stack.is(ItemTags.HOES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                    || item == Items.SHIELD || item == Items.STICK || item == Items.SHEARS || item == Items.FISHING_ROD || item == Items.ARROW;
            helper.assertTrue(!melee || (Weapons.isMelee(stack) && WorkerJob.FIGHTER.withTool(stack) == WorkerJob.WARRIOR), name + " doesn't make a warrior");
            helper.assertTrue(!ranged || (Weapons.isRanged(stack) && WorkerJob.FIGHTER.withTool(stack) == WorkerJob.RANGER), name + " doesn't make a ranger");
            helper.assertTrue(!neither || WorkerJob.FIGHTER.withTool(stack) == null, name + " arms a fighter");
            helper.assertTrue(!(melee || ranged) || WorkerJob.NONE.withTool(stack) == null, name + " gives a farmer work");
        }

        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player friend = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity fighter = FighterItem.spawnFighter(helper.getLevel(), helper.absolutePos(FIGHTER_SPOT), owner);
        fighter.setOwnership(owner.getUUID(), List.of(friend.getUUID()));
        give(fighter, owner, new ItemStack(Items.IRON_HOE));
        helper.assertTrue(fighter.getJob() == WorkerJob.FIGHTER && owner.getMainHandItem().is(Items.IRON_HOE), "a fighter took a hoe");
        give(fighter, friend, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(fighter.getJob() == WorkerJob.WARRIOR && fighter.getMainHandItem().is(Items.IRON_SWORD)
                && friend.getMainHandItem().isEmpty(), "a sword didn't make it a warrior: " + fighter.getJob());
        give(fighter, friend, new ItemStack(Items.BOW));
        helper.assertTrue(fighter.getJob() == WorkerJob.WARRIOR && friend.getMainHandItem().is(Items.BOW), "a shared player swapped its weapon");
        give(fighter, owner, new ItemStack(Items.BOW));
        helper.assertTrue(fighter.getJob() == WorkerJob.RANGER && fighter.getMainHandItem().is(Items.BOW)
                && owner.getInventory().contains(new ItemStack(Items.IRON_SWORD)), "the owner couldn't swap the sword for a bow");

        MinecraftServer server = helper.getLevel().getServer();
        WorkerRoster.get(server).update(fighter);
        helper.assertTrue(ModNetwork.commandWorker(server, owner, fighter.getUUID(), WorkerCommandPayload.Command.TAKE_TOOL), "couldn't take the bow");
        helper.assertTrue(fighter.getJob() == WorkerJob.FIGHTER && fighter.getMainHandItem().isEmpty()
                && owner.getInventory().contains(new ItemStack(Items.BOW)), "taking the bow didn't leave it unarmed");

        WorkerEntity farmer = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        farmer.returnTool(owner);
        give(farmer, owner, new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(farmer.getJob() == WorkerJob.NONE && owner.getMainHandItem().is(Items.DIAMOND_SWORD), "a farmer took a sword");
        helper.succeed();
    }

    /** A warrior at its post fights a monster that comes near, keeps the drops (into its chest later), and its sword doesn't wear. */
    private static void warriorDefendsItsPost(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        assertOutcome(helper, assign(helper, warrior, POST_GROUND), true, "message.villagerhuts.post_assigned");
        helper.assertTrue(helper.absolutePos(POST_GROUND.above()).equals(warrior.getGuardPost()), "post is " + warrior.getGuardPost());
        Husk husk = foe(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(warrior.getTarget() == husk, "not fighting the husk: " + warrior.getTarget());
                    assertStatus(helper, warrior, "activity.villagerhuts.fighting");
                })
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "the husk is still alive"))
                .thenWaitUntil(() -> helper.assertTrue(count(warrior.getInventory(), Items.EMERALD) == 1, "its emerald isn't in the warrior's pockets"))
                .thenExecute(() -> {
                    helper.assertTrue(warrior.getDefeated().getOrDefault(Identifier.withDefaultNamespace("husk"), 0) == 1,
                            "defeated: " + warrior.getDefeated());
                    helper.assertTrue(warrior.getMainHandItem().is(Items.IRON_SWORD) && warrior.getMainHandItem().getDamageValue() == 0,
                            "the sword wore down: " + warrior.getMainHandItem().getDamageValue());
                })
                .thenSucceed();
    }

    /** A ranger shoots a monster from a distance with arrows from its pockets, and its bow doesn't wear. */
    private static void rangerShootsFromADistance(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.BOW));
        ranger.getInventory().addItem(new ItemStack(Items.ARROW, 32));
        assign(helper, ranger, POST_GROUND);
        Husk husk = foe(helper);
        double[] firstShotFrom = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(ranger.getAmmoCount() < 32, "hasn't shot yet");
                    firstShotFrom[0] = ranger.distanceTo(husk);
                })
                .thenExecute(() -> helper.assertTrue(firstShotFrom[0] > 4, "shot from only " + firstShotFrom[0] + " blocks"))
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "the husk is still alive"))
                .thenExecute(() -> {
                    helper.assertTrue(ranger.getDefeated().getOrDefault(Identifier.withDefaultNamespace("husk"), 0) == 1,
                            "defeated: " + ranger.getDefeated());
                    helper.assertTrue(ranger.getMainHandItem().getDamageValue() == 0, "the bow wore down");
                })
                .thenSucceed();
    }

    /** A crossbow is loaded (taking arrows from its pockets), then fired; it doesn't wear either. */
    private static void rangerFiresACrossbow(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.CROSSBOW));
        ranger.getInventory().addItem(new ItemStack(Items.ARROW, 32));
        assign(helper, ranger, POST_GROUND);
        Husk husk = foe(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "the husk is still alive"))
                .thenExecute(() -> helper.assertTrue(ranger.getAmmoCount() < 32 && ranger.getMainHandItem().getDamageValue() == 0,
                        "arrows left " + ranger.getAmmoCount() + ", crossbow damage " + ranger.getMainHandItem().getDamageValue()))
                .thenSucceed();
    }

    /** A trident is thrown (a copy: the ranger keeps its own), with no ammo needed. */
    private static void rangerThrowsATrident(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.TRIDENT));
        assign(helper, ranger, POST_GROUND);
        Husk husk = foe(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "the husk is still alive"))
                .thenExecute(() -> helper.assertTrue(ranger.getMainHandItem().is(Items.TRIDENT), "lost its trident"))
                .thenSucceed();
    }

    /** A ranger with no arrows says so, then fetches arrows from its chest (and won't take the chest's food unless hurt). */
    private static void rangerFetchesArrowsFromItsChest(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.CROSSBOW));
        assign(helper, ranger, POST_GROUND);
        helper.setBlock(SPARE_CHEST_SPOT, Blocks.CHEST);
        assertOutcome(helper, assign(helper, ranger, SPARE_CHEST_SPOT), true, "message.villagerhuts.fighter_chest_assigned");
        assertStatus(helper, ranger, "activity.villagerhuts.out_of_ammo");
        Container chest = container(helper, SPARE_CHEST_SPOT);
        chest.setItem(0, new ItemStack(Items.BREAD, 4));
        chest.setItem(1, new ItemStack(Items.ARROW, 40));
        helper.succeedWhen(() -> {
            helper.assertTrue(ranger.getAmmoCount() == 40 && count(chest, Items.ARROW) == 0, "has " + ranger.getAmmoCount() + " arrows");
            helper.assertTrue(count(chest, Items.BREAD) == 4, "took food it didn't need");
        });
    }

    /** A hurt fighter fetches food from its chest (not rotten flesh) and eats it to heal. */
    private static void fightersEatToHeal(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.STONE_AXE));
        assign(helper, warrior, POST_GROUND);
        helper.setBlock(SPARE_CHEST_SPOT, Blocks.CHEST);
        assign(helper, warrior, SPARE_CHEST_SPOT);
        Container chest = container(helper, SPARE_CHEST_SPOT);
        chest.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 16));
        chest.setItem(1, new ItemStack(Items.BREAD, 3));
        warrior.setHealth(8.0F);
        helper.succeedWhen(() -> {
            helper.assertTrue(warrior.getHealth() >= 18.0F, "health " + warrior.getHealth());
            helper.assertTrue(count(chest, Items.BREAD) < 3, "didn't take any bread");
            helper.assertTrue(count(chest, Items.ROTTEN_FLESH) == 16 && count(warrior.getInventory(), Items.ROTTEN_FLESH) == 0,
                    "took rotten flesh");
        });
    }

    /** Fighters leave villagers, workers, animals and neutral mobs alone, until one of them goes for a friend. */
    private static void fightersLeaveFriendsAlone(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        assign(helper, warrior, POST_GROUND);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.TRIDENT));
        assign(helper, ranger, POST_GROUND);
        WorkerEntity farmer = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 16)), null);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 16));
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 20));
        Wolf wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(12, 1, 20));
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    for (WorkerEntity fighter : List.of(warrior, ranger)) {
                        helper.assertTrue(fighter.getTarget() == null, fighter.getJob() + " went for " + fighter.getTarget());
                    }
                    for (LivingEntity friend : List.of(farmer, villager, cow, wolf)) {
                        helper.assertTrue(friend.isAlive() && friend.getHealth() == friend.getMaxHealth(), friend.getName().getString() + " got hurt");
                    }
                    wolf.setTarget(farmer); // the wolf goes for the farmer: now it's a foe
                })
                .thenWaitUntil(() -> helper.assertTrue(warrior.getTarget() == wolf || !wolf.isAlive(), "the warrior didn't defend the farmer"))
                .thenSucceed();
    }

    /** Warriors keep away from creepers; rangers shoot them. */
    private static void warriorsLeaveCreepersToRangers(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        assign(helper, warrior, POST_GROUND);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.BOW));
        ranger.getInventory().addItem(new ItemStack(Items.ARROW, 16));
        assign(helper, ranger, POST_GROUND);
        Creeper creeper = helper.spawn(EntityTypes.CREEPER, FOE_SPOT);
        creeper.setNoAi(true); // stands still (and never blows up)
        helper.succeedWhen(() -> {
            helper.assertTrue(warrior.getTarget() == null, "the warrior went for the creeper");
            helper.assertTrue(ranger.getTarget() == creeper || !creeper.isAlive(), "the ranger isn't shooting the creeper");
        });
    }

    /** A warrior following its player fights monsters that come near them, and stays on Follow. */
    private static void bodyguardFightsNearItsPlayer(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(FIGHTER_SPOT);
        owner.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(warrior.getOrder() == WorkerEntity.Order.FOLLOW, "not following");
        assertStatus(helper, warrior, "activity.villagerhuts.guarding_player");
        Husk husk = foe(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!husk.isAlive(), "the husk is still alive"))
                .thenExecute(() -> helper.assertTrue(warrior.getOrder() == WorkerEntity.Order.FOLLOW, "stopped following"))
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(owner))
                .thenSucceed();
    }

    /** Levels give a fighter 5 more health and 1 more damage each: 40 health and +4 at Master. */
    private static void fighterUpgradesAddHealthAndDamage(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        helper.startSequence()
                .thenIdle(2) // a held weapon's damage counts from the holder's next tick
                .thenExecute(() -> {
                    double damage = warrior.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    helper.assertTrue(warrior.getMaxHealth() == 20 && damage == 6.0,
                            "a new warrior has " + warrior.getMaxHealth() + " health and " + damage + " damage");
                    offer(helper, warrior, owner, ModItems.DIAMOND_CARROT.get(), 1, 2, 0);
                    offer(helper, warrior, owner, ModItems.DIAMOND_CARROT.get(), 2, 3, 0);
                    offer(helper, warrior, owner, ModItems.FLAWLESS_DIAMOND_CARROT.get(), 1, 4, 0);
                    offer(helper, warrior, owner, ModItems.FLAWLESS_DIAMOND_CARROT.get(), 2, 5, 0);
                    helper.assertTrue(warrior.getMaxHealth() == 40 && warrior.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage + 4,
                            "a Master has " + warrior.getMaxHealth() + " health and " + warrior.getAttributeValue(Attributes.ATTACK_DAMAGE) + " damage");
                    WorkerEntity farmer = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
                    offer(helper, farmer, owner, ModItems.DIAMOND_CARROT.get(), 1, 2, 0);
                    helper.assertTrue(farmer.getMaxHealth() == 20, "a farmer's health went up with its level");
                })
                .thenSucceed();
    }

    /** A fighter's blows (and so its arrows) never hurt players, workers or villagers; they do hurt monsters. */
    private static void fightersNeverHurtFriends(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity ranger = hireFighter(helper, owner, new ItemStack(Items.BOW));
        WorkerEntity farmer = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        Villager villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(12, 1, 16));
        Husk husk = helper.spawn(EntityTypes.HUSK, FOE_SPOT);
        for (LivingEntity friend : List.of(farmer, villager)) {
            friend.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(ranger), 5.0F);
            helper.assertTrue(friend.getHealth() == friend.getMaxHealth(), "a fighter hurt " + friend.getName().getString());
        }
        husk.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(ranger), 5.0F);
        helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "a fighter couldn't hurt a husk");
        helper.succeed();
    }

    /** At night a fighter with a bed doesn't go to bed: it keeps watch at its post. */
    private static void fightersKeepWatchAtNight(GameTestHelper helper) {
        runCommand(helper, "time set 18000");
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        assign(helper, warrior, POST_GROUND);
        placeBed(helper);
        assertOutcome(helper, assign(helper, warrior, BED_HEAD), true, "message.villagerhuts.bed_assigned");
        helper.startSequence()
                .thenIdle(200)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getLevel().isDarkOutside(), "it isn't night");
                    helper.assertTrue(!warrior.isSleeping(), "went to sleep on watch");
                    assertStatus(helper, warrior, "activity.villagerhuts.keeping_watch");
                })
                .thenSucceed();
    }

    /** In the morning a fighter sleeps in its bed, and a monster coming near wakes it to fight. */
    private static void fightersSleepInTheMorning(GameTestHelper helper) {
        runCommand(helper, "time set 1000");
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        WorkerEntity warrior = hireFighter(helper, owner, new ItemStack(Items.IRON_SWORD));
        assign(helper, warrior, POST_GROUND);
        placeBed(helper);
        assign(helper, warrior, BED_HEAD);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(warrior.isSleeping(), "not asleep");
                    helper.assertTrue(helper.getBlockState(BED_HEAD).getValue(AbstractBedBlock.OCCUPIED), "bed not taken");
                })
                .thenExecute(() -> assertStatus(helper, warrior, "activity.villagerhuts.sleeping_after_watch"))
                .thenExecute(() -> foe(helper))
                .thenWaitUntil(() -> helper.assertTrue(!warrior.isSleeping() && warrior.getTarget() instanceof Husk, "slept through a husk"))
                .thenSucceed();
    }

    // A fighter hired at FIGHTER_SPOT and handed this weapon.
    private static WorkerEntity hireFighter(GameTestHelper helper, Player owner, ItemStack weapon) {
        WorkerEntity fighter = FighterItem.spawnFighter(helper.getLevel(), helper.absolutePos(FIGHTER_SPOT), owner);
        helper.assertTrue(fighter != null, "no fighter spawned");
        give(fighter, owner, weapon);
        helper.assertTrue(fighter.getJob().isArmed(), "not armed with " + weapon);
        return fighter;
    }

    // The player right-clicks the worker holding this.
    private static void give(WorkerEntity worker, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        worker.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
    }

    // A husk (a zombie that doesn't burn in daylight) in a fighter's guard zone, carrying an emerald it always drops.
    private static Husk foe(GameTestHelper helper) {
        Husk husk = helper.spawn(EntityTypes.HUSK, FOE_SPOT);
        husk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.EMERALD));
        husk.setGuaranteedDrop(EquipmentSlot.OFFHAND);
        return husk;
    }

    private static void placeBed(GameTestHelper helper) {
        BlockState bed = Blocks.BED.red().defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.SOUTH);
        helper.setBlock(BED_FOOT, bed.setValue(AbstractBedBlock.PART, BedPart.FOOT));
        helper.setBlock(BED_HEAD, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD));
    }

    private static void assertName(GameTestHelper helper, WorkerEntity worker, WorkerRoster roster, @Nullable String expected) {
        String actual = worker.hasCustomName() ? worker.getCustomName().getString() : null;
        helper.assertTrue(Objects.equals(actual, expected), "worker is named " + actual + ", expected " + expected);
        Optional<String> listed = roster.entry(worker.getUUID()).orElseThrow().customName();
        helper.assertTrue(listed.equals(Optional.ofNullable(expected)), "worker list shows " + listed + ", expected " + expected);
    }

    /** The fenced farm and its chest (see the positions at the top). */
    private static void buildFarm(GameTestHelper helper) {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE);
        for (int x = FARM_MIN_X; x <= FARM_MAX_X; x++) {
            for (int z = FARM_MIN_Z; z <= FARM_MAX_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), farmland);
            }
        }
        helper.setBlock(FARM_WATER, Blocks.WATER);
        List<BlockPos> fence = new ArrayList<>();
        for (int x = FARM_MIN_X - 1; x <= FARM_MAX_X + 1; x++) {
            for (int z = FARM_MIN_Z - 1; z <= FARM_MAX_Z + 1; z++) {
                boolean edge = x == FARM_MIN_X - 1 || x == FARM_MAX_X + 1 || z == FARM_MIN_Z - 1 || z == FARM_MAX_Z + 1;
                if (edge && !(x == FENCE_GAP.getX() && z == FENCE_GAP.getZ())) {
                    fence.add(new BlockPos(x, 1, z));
                }
            }
        }
        fence.forEach(pos -> helper.setBlock(pos, Blocks.OAK_FENCE));
        for (BlockPos pos : fence) { // all placed: now join each post to its neighbors
            BlockPos at = helper.absolutePos(pos);
            helper.getLevel().setBlockAndUpdate(at, Block.updateFromNeighbourShapes(Blocks.OAK_FENCE.defaultBlockState(), helper.getLevel(), at));
        }
        helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
    }

    /** buildFarm, plus a farmer hired next to it with the farm and chest assigned. */
    private static WorkerEntity hireFarmer(GameTestHelper helper, @Nullable Player owner) {
        buildFarm(helper);
        WorkerEntity worker = FarmerItem.spawnFarmer(helper.getLevel(), helper.absolutePos(WORKER_SPOT), owner);
        helper.assertTrue(worker != null, "no farmer spawned");
        assertOutcome(helper, assign(helper, worker, FARM_WATER), true, "message.villagerhuts.farm_assigned");
        assertOutcome(helper, assign(helper, worker, CHEST), true, "message.villagerhuts.chest_assigned");
        return worker;
    }

    private static WorkerAssigning.Outcome assign(GameTestHelper helper, WorkerEntity worker, BlockPos relative) {
        return WorkerAssigning.assign(helper.getLevel(), worker, helper.absolutePos(relative));
    }

    private static void assertOutcome(GameTestHelper helper, WorkerAssigning.Outcome outcome, boolean assigned, String expectedKey) {
        String key = outcome.message().getContents() instanceof TranslatableContents translatable ? translatable.getKey() : outcome.message().getString();
        helper.assertTrue(outcome.assigned() == assigned && key.equals(expectedKey),
                "assigning gave " + key + " (assigned: " + outcome.assigned() + "), expected " + expectedKey);
    }

    private static void fillPockets(WorkerEntity worker, Item item) {
        for (int i = 0; i < worker.getInventory().getContainerSize(); i++) {
            worker.getInventory().setItem(i, new ItemStack(item, 64));
        }
    }

    private static Container container(GameTestHelper helper, BlockPos relative) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof Container container) {
            return container;
        }
        throw helper.assertionException(Component.literal("no container at " + relative));
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countCrops(GameTestHelper helper) {
        int crops = 0;
        for (int x = FARM_MIN_X; x <= FARM_MAX_X; x++) {
            for (int z = FARM_MIN_Z; z <= FARM_MAX_Z; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).getBlock() instanceof CropBlock) {
                    crops++;
                }
            }
        }
        return crops;
    }

    private static int countBlocks(GameTestHelper helper, Block block) {
        int found = 0;
        for (int x = FARM_MIN_X; x <= FARM_MAX_X; x++) {
            for (int z = FARM_MIN_Z; z <= FARM_MAX_Z; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(block)) {
                    found++;
                }
            }
        }
        return found;
    }

    private static void assertStatus(GameTestHelper helper, WorkerEntity worker, String expectedKey) {
        Component status = worker.currentStatus();
        String key = status.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : status.getString();
        helper.assertTrue(key.equals(expectedKey), "status was " + key + ", expected " + expectedKey);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VillagerHuts.MODID, path);
    }

    private VillagerHutsTests() {}
}

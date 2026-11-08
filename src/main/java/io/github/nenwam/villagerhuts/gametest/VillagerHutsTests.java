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
import io.github.nenwam.villagerhuts.farm.FarmPlan;
import io.github.nenwam.villagerhuts.item.FarmerItem;
import io.github.nenwam.villagerhuts.job.WorkerJob;
import io.github.nenwam.villagerhuts.network.ModNetwork;
import io.github.nenwam.villagerhuts.network.WorkerCommandPayload;
import io.github.nenwam.villagerhuts.roster.WorkerRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> body) {}

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
            new Test("tool_taken_back_from_the_panel", 40, VillagerHutsTests::toolTakenBackFromThePanel));

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
        Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test test : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(test.name()));
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
        helper.assertTrue(worker.isOwnedBy(owner) && owner.getUUID().equals(worker.getOwnerId()), "not owned by the player");
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

    // ================================================================ helpers

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

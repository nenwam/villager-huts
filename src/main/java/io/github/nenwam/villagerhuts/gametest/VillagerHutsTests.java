package io.github.nenwam.villagerhuts.gametest;

import java.util.List;
import java.util.function.Consumer;

import io.github.nenwam.villagerhuts.VillagerHuts;
import io.github.nenwam.villagerhuts.entity.WorkerEntity;
import io.github.nenwam.villagerhuts.hut.HutBuilder;
import io.github.nenwam.villagerhuts.job.WorkerJob;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Automated in-game tests ("chaos tests") for workers and huts. Each test builds a Farmer Hut on a
 * grass platform, then breaks things on purpose and checks the mod copes.
 *
 * Run them all with: ./gradlew runGameTestServer
 * Only registered in development and test runs, never in a normal game.
 */
public final class VillagerHutsTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, VillagerHuts.MODID);
    private static final Identifier PLATFORM = id("test/platform");
    private static final Identifier FARMER_HUT = id("farmer_hut");

    // Positions on the test platform (relative), for a Farmer Hut built facing south at HUT_GROUND.
    private static final BlockPos HUT_GROUND = new BlockPos(7, 0, 2);
    private static final BlockPos CHEST = new BlockPos(9, 1, 17);
    private static final BlockPos CHEST_SPOT_WEST = new BlockPos(8, 1, 17);  // floor spots beside the chest
    private static final BlockPos CHEST_SPOT_NORTH = new BlockPos(9, 1, 16);
    private static final BlockPos SPARE_CHEST_SPOT = new BlockPos(7, 1, 17);  // free floor by the back wall
    private static final BlockPos FENCE_GAP = new BlockPos(7, 1, 10);
    private static final BlockPos FARM_CORNER = new BlockPos(4, 1, 3);       // crop level, inside the fence
    private static final int FARM_MIN_X = 4, FARM_MAX_X = 10, FARM_MIN_Z = 3, FARM_MAX_Z = 9;

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> body) {}

    private static final List<Test> TESTS = List.of(
            new Test("farmer_hut_starts_unplanted", 40, VillagerHutsTests::farmerHutStartsUnplanted),
            new Test("farmer_fetches_seeds_and_plants", 2400, VillagerHutsTests::farmerFetchesSeedsAndPlants),
            new Test("farmer_harvests_replants_and_stores", 2400, VillagerHutsTests::farmerHarvestsReplantsAndStores),
            new Test("pitcher_pods_are_ignored", 400, VillagerHutsTests::pitcherPodsAreIgnored),
            new Test("chest_broken_then_replaced", 1600, VillagerHutsTests::chestBrokenThenReplaced),
            new Test("chest_broken_mid_trip", 800, VillagerHutsTests::chestBrokenMidTrip),
            new Test("fenced_off_farm_is_left_alone", 800, VillagerHutsTests::fencedOffFarmIsLeftAlone),
            new Test("walled_in_chest_is_reported", 500, VillagerHutsTests::walledInChestIsReported),
            new Test("death_removes_worker_from_roster", 100, VillagerHutsTests::deathRemovesWorkerFromRoster),
            new Test("zombies_hunt_workers", 200, VillagerHutsTests::zombiesHuntWorkers),
            new Test("hut_placement_refusals", 40, VillagerHutsTests::hutPlacementRefusals));

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

    /** A new Farmer Hut: 48 bare farmland, nothing planted, a chest, and a worker with no job or seeds. */
    private static void farmerHutStartsUnplanted(GameTestHelper helper) {
        WorkerEntity worker = buildFarmerHut(helper, null);
        int farmland = 0;
        for (int x = FARM_MIN_X; x <= FARM_MAX_X; x++) {
            for (int z = FARM_MIN_Z; z <= FARM_MAX_Z; z++) {
                if (helper.getBlockState(new BlockPos(x, 0, z)).getBlock() instanceof FarmlandBlock) {
                    farmland++;
                }
            }
        }
        helper.assertTrue(farmland == 48, "expected 48 farmland, found " + farmland);
        helper.assertTrue(countCrops(helper) == 0, "expected nothing planted, found " + countCrops(helper) + " crops");
        helper.assertTrue(helper.getBlockState(CHEST).is(Blocks.CHEST), "no chest in the hut");
        helper.assertTrue(!worker.hasSeeds(), "worker started with seeds");
        helper.assertTrue(worker.getJob() == WorkerJob.NONE, "worker started with a job");
        helper.succeed();
    }

    /** Seeds put in the chest get fetched and planted: exactly as many crops as seeds, none left over. */
    private static void farmerFetchesSeedsAndPlants(GameTestHelper helper) {
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
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
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
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
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
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

    /** Chest broken while the worker's pockets are full: nothing lost or duplicated; a new chest gets used. */
    private static void chestBrokenThenReplaced(GameTestHelper helper) {
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
        fillPockets(worker, Items.WHEAT);
        helper.setBlock(CHEST, Blocks.AIR);
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(count(worker.getInventory(), Items.WHEAT) == 9 * 64, "worker's wheat changed with no chest");
                    assertStatus(helper, worker, "activity.villagerhuts.no_storage");
                    helper.setBlock(SPARE_CHEST_SPOT, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
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
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
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
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
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
        WorkerEntity worker = buildFarmerHut(helper, null);
        makeFarmer(worker);
        fillPockets(worker, Items.WHEAT);
        helper.setBlock(CHEST_SPOT_WEST, Blocks.STONE);
        helper.setBlock(CHEST_SPOT_NORTH, Blocks.STONE);
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
        WorkerEntity worker = buildFarmerHut(helper, owner);
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
     * Zombies go after workers like they go after villagers. Like villagers, a worker must be in sight
     * to be noticed, so the zombie starts inside the hut (the worker stays in there; the door is shut).
     * The helmet stops it burning in the daylight the tests run in.
     */
    private static void zombiesHuntWorkers(GameTestHelper helper) {
        WorkerEntity worker = buildFarmerHut(helper, null);
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(7, 1, 17)); // floor by the hut's back wall
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.succeedWhen(() -> helper.assertTrue(zombie.getTarget() == worker, "zombie's target is " + zombie.getTarget()));
    }

    /** Building where it can't fit: on top of another hut, past the top of the world, or a missing design. */
    private static void hutPlacementRefusals(GameTestHelper helper) {
        buildFarmerHut(helper, null);
        HutBuilder.Result overlap = HutBuilder.build(helper.getLevel(), FARMER_HUT, helper.absolutePos(HUT_GROUND), Direction.SOUTH, 0, null);
        helper.assertTrue(overlap == HutBuilder.Result.NO_SPACE, "second hut on top of the first: " + overlap);

        BlockPos ground = helper.absolutePos(HUT_GROUND);
        BlockPos nearTop = new BlockPos(ground.getX(), helper.getLevel().getMaxY() - 2, ground.getZ());
        HutBuilder.Result tooHigh = HutBuilder.build(helper.getLevel(), FARMER_HUT, nearTop, Direction.SOUTH, 0, null);
        helper.assertTrue(tooHigh == HutBuilder.Result.NO_SPACE, "hut past the top of the world: " + tooHigh);
        helper.assertTrue(helper.getLevel().getBlockState(nearTop).isAir(), "blocks placed for a refused hut");

        HutBuilder.Result missing = HutBuilder.build(helper.getLevel(), id("no_such_hut"), helper.absolutePos(new BlockPos(1, 0, 1)), Direction.SOUTH, 0, null);
        helper.assertTrue(missing == HutBuilder.Result.MISSING_STRUCTURE, "missing design: " + missing);
        helper.succeed();
    }

    // ================================================================ helpers

    private static WorkerEntity buildFarmerHut(GameTestHelper helper, @Nullable Player owner) {
        HutBuilder.Result result = HutBuilder.build(helper.getLevel(), FARMER_HUT, helper.absolutePos(HUT_GROUND), Direction.SOUTH, 0, owner);
        helper.assertTrue(result == HutBuilder.Result.BUILT, "hut not built: " + result);
        List<WorkerEntity> workers = helper.getLevel().getEntitiesOfClass(WorkerEntity.class, helper.getBounds());
        helper.assertTrue(workers.size() == 1, "expected 1 worker, found " + workers.size());
        return workers.get(0);
    }

    private static void makeFarmer(WorkerEntity worker) {
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        worker.setJob(WorkerJob.FARMER);
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

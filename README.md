# Villager Huts

A NeoForge mod for Minecraft 26.3: place a hut item to build a hut with a villager worker,
then assign the villager a job (starting with farming).

## Commands

| Command | What it does |
|---|---|
| `./gradlew runClient` | Launch Minecraft with the mod loaded |
| `./gradlew build` | Build the mod jar into `build/libs/` |
| `./gradlew runClient --debug-jvm` | Launch and wait for a debugger on port 5005 |
| `./gradlew runData` | Run data generators into `src/generated/resources` |
| `./gradlew runGameTestServer` | Run the automated in-game tests (fails if any test fails) |

Requires JDK 25.

## How to play

- **Hut items:** the **Farmer Hut** builds a hut with its own farm; the **Worker Hut** builds just the hut.
  Hold one and look at the ground to see a wireframe preview (yellow = fits, red = blocked).
  Sneak + right-click to rotate it 90°. Right-click to build.
- **Worker:** right-click with a hoe to make them a farmer. Right-click with an empty hand for status;
  sneak + right-click with an empty hand to take the tool back.
- **Work Area Marker:** assigns a farm you built to a worker. Right-click the worker to link the marker,
  look at the farm to preview the area (yellow box, farmland covered in green), right-click to assign.
  Sneak + right-click the worker to clear it. Limits (Config screen): 16×16 blocks, within 32 blocks of the hut.
- **Worker panel:** the **Workers** button in the survival inventory opens a panel beside it (the **J** key
  opens the inventory with the panel, also in creative). It lists all your workers: live status, days worked,
  what they're carrying and have collected; click a worker's book for its details page. **Show** highlights a worker's farm
  in its color, with its number above the farm and above the worker's head; **Show all** does every worker.
- **Seeds:** farms start unplanted and workers start with no seeds. Put seeds (wheat seeds, carrots,
  potatoes, beetroot seeds...) in the hut chest; the farmer fetches them and plants the empty farmland.
- Farmers harvest and replant their farm (assigned, or else the one at their hut), store the harvest in
  the hut chest, go inside at night, and run from hostile mobs.
- Mobs that hunt villagers (zombies, drowned, raiders, and modded villager-hunters) hunt workers the same way.

## Automated tests

`gametest/VillagerHutsTests.java` builds a Farmer Hut on a test platform and checks the mod copes when
things go wrong: seeds fetched and planted exactly, harvests stored, pitcher pods ignored, the chest broken
(with full pockets, and mid-trip) and replaced, the farm fenced off, the chest walled in, a worker dying,
zombies hunting workers, and huts refused when they don't fit. Run them after changes with
`./gradlew runGameTestServer`. They're only registered in development, never in a normal game.

## Hut designs

Huts are structure files in `src/main/resources/data/villagerhuts/structure/`.
To design your own, build it in-game and save it with a Structure Block, then copy the `.nbt`
from the world's `generated/villagerhuts/structure/` folder over the existing file.
A design must follow the rules at the top of `hut/HutBuilder.java`:

- The front edge is the north side (low Z). It goes on the block you click, centered.
- The bottom layer replaces the ground.
- A Structure Block in **Data** mode with the text `worker` marks where the worker spawns.

`tools/make_hut_designs.py` generates the starter Farmer Hut and Worker Hut, and `tools/make_icons.py` draws the item icons.

Started from the NeoForge MDK (see `TEMPLATE_LICENSE.txt`).

# Villager Huts

A NeoForge mod for Minecraft 26.3: hire villager workers (starting with farmers), give each one a farm
(or have them make one) and a chest, and they look after the farm for you.

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

- **Farmer:** craft one from an **iron hoe** and a **composter** (any arrangement); picking up an iron hoe
  unlocks it in the recipe book. Use it on the ground to hire a farmer there. They hold the iron hoe, belong
  to you, and **follow you** until you give them a farm or a chest.
- **Giving them a farm or chest:** right-click your farmer with an empty hand, then right-click a farm or a chest.
  While they're selected, a hint shows above the hotbar and looking at blocks previews what they'd get: a chest
  (or barrel) is outlined in yellow; anywhere else shows the farm area (yellow box, farmland covered in green).
  Red means there's no farmland there, or it's too far. Right-click the farmer again to cancel (it also runs out
  after a minute). Do it once for the farm and once for the chest; doing it again replaces them.
- **Making a new farm:** shift + right-click your farmer with an empty hand, then right-click the ground. The
  preview shows a cyan box, the land they'll till outlined in orange (grass, dirt or paths with nothing on top
  but air or short grass), and where they'll put water in blue, so all the farmland stays wet. They put the
  water in, then till the land right away, and plant it once they have seeds.
- Limits (Config screen): farms up to 16×16 blocks (new farms 9×9), within 32 blocks of the farmer; the chest
  within 32 blocks of the farm.
- **Worker panel:** the **Workers** button in the survival inventory opens a panel beside it (the **J** key
  opens the inventory with the panel, also in creative). It lists all your workers: live status, days worked,
  what they're carrying and have collected; click a worker's book for its details page. **Show** highlights a
  worker's farm and chest in its color, with its number above each and above the worker's head; **Show all**
  does every worker. On a details page:
  - the **name tag** renames the worker: type a name and press Enter (Esc cancels; an empty name removes it).
    Workers that are far away take the new name when they're next loaded;
  - beside its slot, its orders: **Work** (wheat), **Follow me** (lead) and **Stay here** (armor stand); the one
    it's on is in its color. Giving it a farm or chest also sets it working;
  - on the right, the **iron hoe** takes the hoe back (it's unemployed until you right-click it with a hoe).
  The orders and the hoe button only work while the worker is near enough to be loaded.
- **Seeds and bone meal:** farmers start with no seeds. Put seeds (wheat seeds, carrots, potatoes, beetroot
  seeds...) in their chest; the farmer fetches them and plants the empty farmland. Put bone meal in the chest
  and they use it on their growing crops.
- Farmers harvest and replant their farm and store the harvest in their chest. When their pockets fill up they
  keep their seeds and bone meal and carry on; once all their work is done (nothing to harvest, plant, till or
  bone-meal, and nothing in the chest that would change that) they put everything away, seeds and bone meal too.
- At night farmers go indoors if their chest is inside a building (somewhere with a roof and walls near the
  chest), and they run from hostile mobs. Mobs that hunt villagers (zombies, drowned, raiders, and modded
  villager-hunters) hunt workers the same way.

## Automated tests

`gametest/VillagerHutsTests.java` builds a fenced farm and a chest on a test platform, hires a farmer for
them, and checks the mod copes when things go wrong: the Farmer item (and following its owner), assigning a farm
and chest (and refusing spots with no farmland or too far away), new farms planned, watered and tilled, seeds
fetched and planted exactly, bone meal used, harvests stored, leftovers put away once work is done, pitcher pods
ignored, the chest broken (with full pockets, and mid-trip) and a new one assigned, the farm fenced off, the chest
walled in, staying put, a worker dying, workers renamed and the hoe taken back from the panel, and zombies
hunting workers. Run them after changes with
`./gradlew runGameTestServer`. They're only registered in development, never in a normal game.

## Tools

`tools/make_icons.py` draws the item icons, and `tools/make_test_platform.py` builds the platform the tests run on.

Started from the NeoForge MDK (see `TEMPLATE_LICENSE.txt`).

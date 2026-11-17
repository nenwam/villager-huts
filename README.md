# Sanjay Gupta's Best Workers!

A NeoForge mod for Minecraft 26.3: hire workers, give each one somewhere to work, a chest and a bed, and they get
on with it. Farmers look after a farm (or make one); fighters, Sikh warriors and rangers, guard a post, or you.
Works in multiplayer: workers belong to the player who hired them, who can share them with friends.

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

- **Farmer:** craft one from an **iron hoe**, a **composter** and a **golden carrot** (any arrangement); picking
  up an iron hoe or a golden carrot unlocks it in the recipe book. Use it on the ground to hire a farmer there.
  They hold the iron hoe, belong to you, and **follow you** until you give them a farm, a chest or a bed.
- **Looks and names:** every farmer is Indian, with one of eight looks: men in a kurta and dhoti with a pagri,
  a vest and lungi with a gamcha, a Sikh farmer with a dastar, an older farmer; women in green, pink or indigo
  sarees or a salwar kameez. Each gets a name that suits their look, from all over India, and different from your
  other farmers' names. Rename them any time (worker panel, or a name tag). `tools/make_worker_looks.py` draws the looks.
- **Giving them a farm, chest or bed:** right-click your farmer with an empty hand, then right-click a farm, a chest
  or a bed. While they're selected, a hint shows above the hotbar and looking at blocks previews what they'd get: a
  chest (or barrel) or bed is outlined in yellow; anywhere else shows the farm area (yellow box, farmland covered in
  green). Red means there's no farmland there, or it's too far. Right-click the farmer again to cancel (it also runs
  out after a minute). Do it once each for the farm, the chest and the bed; doing it again replaces them. A bed can
  only be one worker's.
- **Making a new farm:** shift + right-click your farmer with an empty hand, then right-click the ground. The
  preview shows a cyan box, the land they'll till outlined in orange (grass, dirt or paths with nothing on top
  but air or short grass), and where they'll put water in blue, so all the farmland stays wet. They put the
  water in, then till the land right away, and plant it once they have seeds.
- Limits (Config screen): farms up to 16×16 blocks (new farms 9×9), within 32 blocks of the farmer; the chest
  and bed within 32 blocks of the farm.
- **A day's work:** a farmer can harvest **64 items a day**, and never more: a harvest that would take them over
  is left for tomorrow. Then they put everything in their chest and rest by their bed (by their chest if they
  have no bed) until the next morning. At night they sleep in their bed.
- **Upgrades:** right-click a worker holding **diamond carrots** to raise their level, like a villager's:

  | Level | Takes | Farmer: harvest a day | Fighter: health, damage |
  |---|---|---|---|
  | Novice | (where they start) | 64 | 20, +0 |
  | Apprentice | 1 Diamond Carrot | 128 | 25, +1 |
  | Journeyman | 2 Diamond Carrots | 192 | 30, +2 |
  | Expert | 1 Flawless Diamond Carrot | 256 | 35, +3 |
  | Master | 2 Flawless Diamond Carrots | 320 | 40, +4 |

  A **Diamond Carrot** is a carrot surrounded by eight diamonds; a **Flawless Diamond Carrot** is a carrot
  surrounded by eight diamond blocks. (The 64 a day is the `farmerDailyHarvest` setting; each level adds that
  much again.) Fighters also swing and shoot faster with each level, and rangers' shots go straighter.
- **Worker panel:** the **Workers** button in the survival inventory opens a panel beside it (the **J** key
  opens the inventory with the panel, also in creative). It lists all your workers, then the ones shared with
  you: live status, days worked, what they're carrying and have collected; click a worker's book for its details
  page (level, today's harvest, what the next level costs, farm, chest and bed; for a fighter, its health and
  damage, weapon, ammo, post, and a grid of who it has defeated; scroll if it doesn't all fit).
  **Show** highlights a worker's farm (or guard area), chest and bed in its color, with its number above each and
  above the worker's head; **Show all** does every worker. On a details page:
  - the **name tag** renames the worker: type a name and press Enter (Esc cancels; an empty name removes it).
    Workers that are far away take the new name when they're next loaded;
  - beside its slot, its orders: **Work** (wheat; for a fighter, a shield: guard its post), **Follow me** (lead) and
    **Stay here** (armor stand); the one it's on is in its color. Giving it a farm, post or chest also sets it working;
  - for a farmer, the **chest** clears its field: it pulls up everything planted (ripe or not) and puts it all in its
    chest, seeds, harvest and bone meal, then waits by its bed. The **seeds** button (in its place while it's cleared)
    starts it farming again with what's in the chest. Clearing doesn't count toward the day's harvest;
  - on the right, its **tool** takes it back (a farmer is unemployed until you right-click it with a hoe, a fighter
    unarmed until you hand it a weapon);
  - the **player head** opens its sharing page (below).
  The orders, field and tool buttons only work while the worker is near enough to be loaded. In a narrow panel the
  buttons on the right move to a row of their own. A ranger's ammo shows in the list, and near the top of its page.
- **Multiplayer:** a worker belongs to the player who hired it. Nobody else can give it orders or work, rename
  it, use a lead or name tag on it, or hurt it (the `protectWorkers` setting; turn it off for PvP). On its sharing
  page its owner can **share** it with players who are online (**+**), **stop sharing** (**×**), or **give it**
  to someone they share it with (the golden helmet, clicked twice; the old owner can still use it). Players it's
  shared with can give it orders and work (**Follow me** follows whoever pressed it), rename and upgrade it, and
  can stop using it (**×** on their own row); only the owner can take its hoe, share it or give it away. Sharing
  works even when the worker is far away, and the other player is told.
- **Fighter:** craft one from a **grindstone**, a **target** and a **golden carrot** (any arrangement); picking up a
  grindstone or a golden carrot unlocks it. Use it on the ground to hire a fighter: a Sikh warrior (a Nihang in a
  blue or saffron chola with a dumalla and its steel chakkar, a Singh in a kesri dastar, or a Kaur), with a Sikh
  name ending in Singh or Kaur. They arrive unarmed and follow you; `tools/make_worker_looks.py` draws them too.
  - **Arming:** hand them any **melee weapon** (sword, axe, spear, mace...) and they become a **warrior**; any
    **ranged weapon** (bow, crossbow, trident...) and they become a **ranger**. Modded weapons count too
    (anything that shoots, and anything tagged `c:tools/melee_weapon` or `c:tools/ranged_weapon`; the
    `villagerhuts:warrior_weapons` and `ranger_weapons` tags add more). Their owner can hand them a different
    weapon to swap (getting the old one back); the panel's weapon button takes it back. Weapons never wear out
    in their hands, and their enchantments work. Armed, they unfold their arms and hold the weapon in their hand:
    swinging a blade, drawing a bow, loading and aiming a crossbow, raising a trident to throw.
  - **A post:** right-click your fighter, then any block that isn't a chest or a bed: they guard it, fighting
    monsters within 12 blocks (the `fighterGuardRadius` setting; the preview shows the area in red-orange).
    Following you, they're your bodyguard: they fight what comes near you. Told to stay, they guard that spot.
  - **Who they fight:** monsters, and anything attacking a player, a worker, a villager, a golem or a pet. They
    leave neutral mobs alone unless they're angry, never take on a warden, and warriors keep clear of creepers
    (rangers shoot them). Their blows and arrows never hurt friends: arrows fly through them.
  - **Rangers** shoot from a distance and back off if a monster gets close. Bows and crossbows use the arrows
    (or fireworks) in their pockets, tipped and spectral arrows too, and Infinity works; hand them arrows or put
    some in their chest and they fetch more. Tridents are thrown (a copy: they keep theirs). Out of ammo, they
    run from monsters until they've got more.
  - **Loot:** what their foes drop goes into their pockets, then their chest. Their details page counts who
    they've defeated.
  - **Healing:** hurt, they eat food from their pockets (put bread or cooked meat in their chest; they won't eat
    rotten flesh or anything that does more than fill you up), and heal slowly anyway. Badly hurt (under half
    health), they go to bed to recover, and heal fast there.
  - **Night watch:** fighters keep watch at their post all night, then sleep in their bed from sunrise until
    noon. Anything coming near wakes them.
- **Seeds and bone meal:** farmers start with no seeds. Put seeds (wheat seeds, carrots, potatoes, beetroot
  seeds...) in their chest; the farmer fetches them and plants the empty farmland. Put bone meal in the chest
  and they use it on their growing crops.
- Farmers harvest and replant their farm and store the harvest in their chest. When their pockets fill up they
  keep their seeds and bone meal and carry on; once all their work is done (nothing to harvest, plant, till or
  bone-meal, and nothing in the chest that would change that) they put everything away, seeds and bone meal too.
- At night farmers sleep in their bed. Without one, they go indoors if their chest is inside a building
  (somewhere with a roof and walls near the chest). They run from hostile mobs (that wakes them up too). Mobs that hunt villagers (zombies, drowned, raiders, and modded
  villager-hunters) hunt workers the same way.

## Automated tests

`gametest/VillagerHutsTests.java` builds a fenced farm and a chest on a test platform, hires a farmer for
them, and checks the mod copes when things go wrong: the Farmer item (and following its owner), assigning a farm
and chest (and refusing spots with no farmland or too far away), new farms planned, watered and tilled, seeds
fetched and planted exactly, bone meal used, harvests stored, leftovers put away once work is done, pitcher pods
ignored, the chest broken (with full pockets, and mid-trip) and a new one assigned, the farm fenced off, the chest
walled in, staying put, a worker dying, workers renamed and the hoe taken back from the panel, zombies hunting
workers, looks and names, clearing a field and starting it again, sharing (and giving away) a worker, strangers kept from using or hurting it, upgrades
(and the recipes for diamond carrots, the Farmer and the Fighter), fighters (Sikh looks and names, every weapon in
the game sorted into melee and ranged, swapping weapons, warriors defending their post, rangers shooting bows,
crossbows and tridents and fetching arrows, eating to heal, leaving friends alone and creepers to rangers,
bodyguards, levels, never hurting friends, keeping watch at night and sleeping in the morning, in a batch of its
own),
the daily harvest limit and the next day, resting by the bed, sleeping in it at night (in a batch of its own that
runs at midnight), and the worker panel's messages surviving the trip over the network. Run them after changes with
`./gradlew runGameTestServer`. They're only registered in development, never in a normal game.

## Tools

`tools/make_icons.py` draws the item icons, `tools/make_worker_looks.py` draws the workers' looks, and
`tools/make_test_platform.py` builds the platform the tests run on.

Started from the NeoForge MDK (see `TEMPLATE_LICENSE.txt`).

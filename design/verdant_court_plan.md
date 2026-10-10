# Verdant Court - implementation plan

Faction 3 of `claude/design-factions.md` (elves and druids; living-wood halls, moss, lanterns; green and silver;
mobility and living terrain, BAR role: Legion). This plan maps the design onto the Reign of Nether (RoN)
faction plumbing that the three live factions use (villagers = Sunforged, monsters = Gravebound,
piglins = Ironhide Horde), and cuts it into slices that can each ship and be playtested on their own.

Paths below are relative to `bar/src/main/java/com/solegendary/reignofnether/` unless they start with
`bar/`, `tools/` or `design/`.

---

## 1. How a faction is defined, end to end (every touch point)

A faction is **not an enum**. It is a `Faction` object in a plain mod-local `MappedRegistry`
(`api/ReignOfNetherRegistries.FACTIONS`, not synced by Forge). Packets and saves carry the
`ResourceLocation` key (`reignofnether:villagers`), never an ordinal. That makes adding a faction cheap
and safe at the registry level; the real cost is the ~35 places that branch on "is it MONSTERS / PIGLINS,
else villagers" (section 1.4), where a new faction silently falls into the villager branch.

### 1.1 Core registration
| What | Where | Notes |
|---|---|---|
| Faction data object | `faction/Faction.java` | key, icon, idle-worker icon, worker + scout entity type, capitol building, calm theme `SoundEvent`, survival `spawnWave`, `isBuildableCustomBuilding`, build/unit button lists, `playable`, `hasCubeMap`, (new) `preview`. |
| Faction registry + lists | `faction/Factions.java` | `register(name, Faction)` fills `CLASSIC_FACTIONS` (hasCubeMap: title screen, survival random), `PLAYABLE_FACTIONS` (sandbox / scenario cyclers), `SURVIVAL_FACTIONS` (has spawnWave). `registerUnits()` -> `ENTITY_FACTION` map (entity type -> faction; this is how `Factions.getFaction(unit)` works). `registerBuildings()` sets `Building.setFaction` and the build menu order + hotkey. `registerStartBuilding` sets the capitol. |
| Process notes | `faction/New Faction.md` | 5-step recipe; steps 3-5 must run in `FMLCommonSetupEvent` (`ReignOfNether.java:170`, `Factions.register()` inside `enqueueWork`). |
| Registry ids | `Factions.getFaction(int)` uses `FACTIONS.byId` | Unused today, but **register new factions after NONE** so existing ids never move. |
| Registry definition | `api/ReignOfNetherRegistries.java` | `FACTIONS`, `BUILDING`, `PRODUCTION_ITEM`. |

### 1.2 Units
| What | Where |
|---|---|
| Entity types | `registrars/EntityRegistrar.java` (`ENTITIES.register("x_unit", ...)`, plus the `itemName -> EntityType` switch around line 540-560 used by production) |
| Attributes | `CommonModEvents.java` (`EntityAttributeCreationEvent`: `evt.put(TYPE, XUnit.createAttributes().build())`) |
| Renderers | `ClientModEvents.java` (`registerEntityRenderer`), custom renderers in `unit/modelling/renderers/` |
| Unit classes | `unit/units/<faction>/XUnit.java` (extend a vanilla mob + `implements Unit, AttackerUnit, RangedAttackerUnit, WorkerUnit...`) and `XProd.java` (the production item: cost, build time, button, `itemName`) |
| Production item registry | `building/production/ProductionItems.java` |
| Costs | `resources/ResourceCosts.java` + `config/ReignOfNetherCommonConfigs.java` (`UnitCosts.X.define`) |
| Spawn eggs | `registrars/ItemRegistrar.java`, creative tab in `CommonModEvents.java` |
| Worker logic | `unit/interfaces/WorkerUnit.java`; T2 constructor detection `unit/T2Workers.java` (hard-coded `instanceof` list + `isT3Lab`) |
| Commander | `player/CommanderServerEvents.java` (`makeCommander` tags the first worker; `ensureAbility` adds faction signature: RaiseDead for MONSTERS, WarDrums for PIGLINS), `ability/abilities/CommanderAbility.java` + `CommanderDGun.java` (per-faction branches) |
| Liveries | `unit/UnitDressServerEvents.java` (per-faction colours + Epic Knights kit, villager is the `else`) |
| Portrait / unit card | `hud/PortraitRendererUnit.java`, `hud/UnitInfoCard.java` (faction accent colour, line ~112) |
| Faction for non-unit mobs | `unit/NonUnitServerEvents.getNonUnitFaction` (instanceof IronGolem/Illager -> villagers etc.) |
| Faction mechanics | `unit/MomentumServerEvents.java` (Horde), `unit/FormationServerEvents.java` (Sunforged), `resources/WreckServerEvents.java` (Gravebound wrecks), `unit/interfaces/Unit.java` (~356, ~466 faction branches) |

### 1.3 Buildings
| What | Where |
|---|---|
| Building registry | `building/Buildings.java` (`register(rl, new X())`) |
| Building classes | `building/buildings/<faction>/`, shared bases in `building/buildings/shared/` (`MetalExtractor(String variant)`, `WindGenerator`, `EnergyConverter`, `AbstractFarm`, `AbstractStockpile`, `AbstractBridge`, `AbstractMarket`), `building/production/ProductionBuilding.java` |
| Save mapping | `building/BuildingSaveData.java` (`case X.buildingName -> Buildings.X`) |
| Structures (NBT) | `bar/src/main/resources/assets/reignofnether/structures/` **and** `bar/src/main/resources/data/reignofnether/structures/` (server reads data/, client reads assets/; GameTest `every_building_blueprint_loads_on_the_server` enforces) |
| Structure generator | `tools/gen_structures.py` (`PALETTES` dict: `""` Sunforged, `"_dark"` Gravebound, `"_nether"` Horde; writes extractor T1/T2, wind, converter into both trees). Revert unchanged NBT after regenerating. |
| Faction visuals | `building/BannerRenderClientEvents.java`, `building/ScaffoldRenderClientEvents.java`, `building/BuildingAmbienceClientEvents.java`, `barfx/BarFx.java` (all `MONSTERS / PIGLINS / else`) |
| Placement rules | `building/BuildingValidators.java` (nether-terrain rule for non-piglins), `building/BuildingPlacement.java:848`, `building/PatchQuickBuildClientEvents.java` (per-faction extractor/wind for quick-build) |
| Research / tiers | `research/researchItems/ResearchTier2.java`, `ResearchTier3.java` (unlocks each faction's T3 lab), per-unit research items |

### 1.4 Lobby, match start, bots, saves
| What | Where |
|---|---|
| BAR lobby faction picker | `matchstart/MatchStartScreen.java` `renderSlotRow` (hard-coded tile order) + `renderFactionTile` |
| Skirmish setup | `matchstart/SkirmishSetupScreen.java` (player + bot faction as an int, `3` = random), `matchstart/QuickBattle.java`, `matchstart/SkirmishServerboundPacket.java` (`factionOf(int)`: 0/1/2, anything else = random; `factionName` switch) |
| Start positions | `startpos/StartPosServerboundPacket.java` / `StartPosClientboundPacket.java` (key-based), `startpos/StartPosServerEvents.java` |
| Start RTS | `player/PlayerServerEvents.startRTS` (RANDOM -> one of the three; spawns `workerEntityType`, `scoutEntityType`, capitol foundation) |
| Player save | `player/RTSPlayerSaveData.java` (key-based, with a legacy `"VILLAGERS"` enum-name fallback) |
| Bots | `bot/BotPlayer.kitFor` (record `Kit`: capitol, house, farm, extractor, wind, army building, tower, worker, army, T2 lab, T2 worker, T2 army; villagers is the default branch), more branches at ~583/591/653; `bot/BotServerEvents.java:112` (string -> faction for `/rts bot`) |
| Sandbox / scenario | `sandbox/SandboxClientEvents.java`, `scenario/ScenarioMenu.java`, `scenario/ScenarioRole.java` (enum-name strings) |
| Title / survival | `hud/TitleClientEvents.java` (cube map from `CLASSIC_FACTIONS`), `survival/Wave.java`, `survival/SurvivalClientEvents.java`, `survival/spawners/*` |
| Tutorial | `tutorial/TutorialClientEvents.java`, `TutorialServerEvents.java` (villager/monster only; leave alone) |
| Debug start keys | `unit/UnitClientEvents.java:373` |
| Minimap / fog | `minimap/MinimapClientEvents.java` (no faction branching today; unit icons come from team colour), `fogofwar/` (none) |
| Keybinds | `keybinds/Keybindings.java` (abilitySlot1-10, hotkey2-10: each faction reuses the same slots in `Factions.registerBuildings`) |
| Colours | `player/PlayerPalette.java` (team colours, not faction) |
| Lang | `bar/src/main/resources/assets/reignofnether/lang/en_us.json` (`hud.faction.reignofnether.<name>`, `units.*`, `buildings.*`); other locales fall back to en_us |
| GameTests | `gametest/SkirmishGameTests.java`: `bot_kits_use_their_t2_lab`, T2 constructor test (~830), commander signature (~140), liveries (~179), extractor stamping per faction (~58) all enumerate the three factions explicitly |

---

## 2. Slice 0 - announce (implemented on branch `verdant-court-plan`)

- `Faction.preview` + `setPreview()` (also unplayable); `Factions.VERDANT_COURT` registered **after NONE**
  with `noCubeMap()` and no spawn wave, so it is in none of `PLAYABLE / CLASSIC / SURVIVAL`.
- `Factions.isLive(f)`: playable, not preview, has capitol + worker.
- Lobby: a fifth tile (greyed, never selectable) between Horde and Random, tooltip
  "Verdant Court (preview)" / "Coming soon". Row layout sized from `FACTION_TILES`.
- Server guards: `StartPosServerboundPacket` RESERVE ignores a preview faction; `PlayerServerEvents.startRTS`
  refuses one (a modded client cannot start as a faction with no capitol).
- GameTest `preview_faction_leaves_the_three_live_factions_intact`.
- Icon is vanilla `flowering_azalea_leaves` until a portrait exists.

## 3. Slice 1 - minimum playable Court (the MVP)

Goal: a player can pick Verdant Court in the lobby, start, build an economy and a T1 army, and a bot can
play it. Every body is a vanilla mob (retextured / tinted / dressed) so there is **no Alex's Mobs code
dependency**. Alex's Mobs is installed in the instance, but making it a compile/runtime dependency is a
decision for lovish (it would also need to be in CI's build classpath); its models could later replace the
vanilla bodies via renderer swaps only.

| Design unit | Body | Extend (RoN class) | Mechanic in slice 1 |
|---|---|---|---|
| Commander - Grove Warden | Vindicator body, leaf-and-silver livery | the Seedshaper (commander = tagged first worker, as today) | `CommanderAbility` branch: *Wildstride* (+speed aura, reuse WarDrums plumbing); `CommanderDGun` branch: *Thornburst* cone (reuse D-gun cone/line logic, thorn particles). *Overgrowth* deferred to slice 3. |
| Constructor - Seedshaper | Villager body (`VillagerUnit`), green robe texture | `VillagerUnit` (WorkerUnit plumbing) - or a thin copy if VillagerData profession rendering fights us | builds normally; "grows" is visual (scaffold palette = moss/leaves) |
| Fox Courier (scout) | vanilla Fox | new `FoxCourierUnit extends Fox implements Unit` modelled on `ScoutDogUnit` | fast scout; "invisible in grass" deferred |
| Leafblade (raider) | Vindicator, leaf armour livery | `VindicatorUnit` | short dash ability (reuse an existing leap/charge ability) |
| Thornbow (skirmisher) | Skeleton/Stray body with leaf armour, or Pillager with bow | `StrayUnit` / `SkeletonUnit` (ranged plumbing) | slow-on-hit arrows (Stray already does it) as the "root" stand-in |
| Sentinel Treant (tank) | Iron Golem tinted with moss/oak texture | `IronGolemUnit` | tank; "takes root" (stationary armour toggle) deferred |
| Hive Keeper (anti-swarm) | Witch body + `BeeUnit` summons | `WitchUnit` + reuse `BeeUnit` (already registered under NEUTRAL) | optional for slice 1 |

Buildings for slice 1:

| Building | Extend | Structure |
|---|---|---|
| Capitol - Heartwood Hall | copy of `TownCentre` (ProductionBuilding, trains Seedshaper) | new `heartwood_hall.nbt`: living-wood hall (stripped oak/mangrove roots, moss, azalea, lanterns) |
| T1 lab - Grove (trains T1 army) | copy of `Barracks` | new `grove.nbt` |
| Metal extractor / wind / converter | `MetalExtractor("_verdant")`, `WindGenerator("_verdant")`, `EnergyConverter("_verdant")` | add a `"_verdant"` palette to `tools/gen_structures.py` (moss block pad, mossy stone bricks ring, oak/mangrove timber cap, azalea/flowering leaves, silver = smooth stone/iron bars, lamp = lantern / chipped lantern) |
| Stockpile, bridge | reuse `AbstractStockpile`, `AbstractBridge` with oak/mangrove variants | reuse existing |

Wiring checklist for slice 1 (every item from section 1):
1. `Factions.java`: drop `setPreview()`, give it `setSound`, `setCustomBuildingCondition` (new
   `CustomBuilding.buildableByVerdant` flag or reuse villagers'), worker/scout/start building, unit + building
   registration with the same hotkey slots as the others; keep it registered after NONE. Add to
   `CLASSIC_FACTIONS` only once a title-screen cube map exists (otherwise keep `noCubeMap()`).
2. `MatchStartScreen`: preview tile becomes a normal tile (it already sits in the order array).
3. `PlayerServerEvents.startRTS`: RANDOM pool - use `Factions.isLive` over `PLAYABLE_FACTIONS` instead of the
   hard-coded list (also `SkirmishServerboundPacket` default branch).
4. Skirmish: `SkirmishSetupScreen` cycler + `SkirmishServerboundPacket.factionOf` / `factionName` (int-coded, `3` is already random - Verdant takes `4`, see risks).
5. `bot/BotPlayer.kitFor` + the three other faction branches; `bot/BotServerEvents` name parse.
6. `T2Workers` (nothing until the Elder Druid), `ResearchTier3` (nothing until a T3 lab).
7. Explicit `VERDANT_COURT` branches in: `UnitDressServerEvents`, `BannerRenderClientEvents`,
   `ScaffoldRenderClientEvents`, `BuildingAmbienceClientEvents`, `BarFx`, `UnitInfoCard`,
   `PatchQuickBuildClientEvents`, `CommanderServerEvents`, `CommanderAbility`, `CommanderDGun`,
   `WreckServerEvents` (wreck look), `BuildingValidators` (nether-terrain rule must not trip).
8. `EntityRegistrar`, `CommonModEvents` attributes, `ClientModEvents` renderers, `ItemRegistrar` spawn eggs,
   `ProductionItems`, `ResourceCosts` + config, `Buildings`, `BuildingSaveData`.
9. Textures: `bar/src/main/resources/assets/reignofnether/textures/entities/...` (retextures via
   `tools/gen_skins.py`), mob heads for icons in `textures/mobheads/`.
10. Lang: units, buildings, abilities, faction name (drop "(preview)").
11. GameTests: add Verdant to the three-faction lists in `bot_kits_use_their_t2_lab` (once a T2 lab exists),
    commander signature test, livery test, extractor stamping test; a new "every live faction's kit builds"
    test that iterates `PLAYABLE_FACTIONS` filtered by `isLive` instead of hard-coded lists.

### Slice 1 status (branch `verdant-slice-1`)

Implemented: Seedshaper (worker; first one = Grove Warden), Fox Courier (scout, weak bite), Leafblade (raider,
extends VindicatorUnit, Leaf Dash), Thornbow (skirmisher, skeleton archer frame, rooting arrows), Sentinel Treant
(tank, extends IronGolemUnit); Heartwood Hall (capitol), Grove (T1 lab), `_verdant` extractor/T2 extractor/wind/
converter (gen_structures.py, both trees); Thornburst (cone D-gun, `CommanderDGun.Kind.THORNBURST`) and Wildstride
(`CommanderAbility.Kind.WILDSTRIDE`) via FactionTraits; quick-build extractor; bot kit (T1 only, null-guarded T2/T3);
lobby tile live, skirmish code 4, "Random" = any live faction (`Factions.randomLive`); lang, icons, skins.
Bodies are vanilla (Alex's Mobs renderers were not needed for slice 1). Still stubbed: stockpile/bridge variants,
calm theme (reuses the Kingdom's), cube map (so not in CLASSIC_FACTIONS), survival wave, death debris (wire field
full), T2/T3 (Heartwood Hall offers Tier 2 only, for the extractor refit), Hive Keeper.

### Slice 4 (T2) status (branch `verdant-t2`)

Implemented: **Circle of Elders** T2 lab (`building/buildings/verdant/CircleOfElders.java`, `circle_of_elders.nbt` from
`tools/gen_structures.py` in both trees, build slot `abilitySlot9` like the Arcane Tower; needs a Grove and Tier 2 on
the button, and every Court T2 production item re-checks Tier 2 on the server via `VerdantT2Prod`). **Elder Druid**
T2 constructor (2x build power, 2x Seedshaper HP, in `T2Workers.isT2Worker`) with **Awaken Thicket** (aimed, 12
blocks: a tagged Sentinel Treant for 30 s, 45 s cooldown, no wreck, discarded if it reloads from a save). **Stag
Lancer** on Alex's Mobs' moose body (`RenderMoose`, always antlered, antler drops off) with **Leaping Charge** (10-block
arc, landing AoE 10 dmg + knockback, 14 s). **Shade Ranger** (Thornbow subclass, 24 range, 16 dmg plain shots): cloaks
after 3 s still with no target - invisible flag, owner sees a ghost, skipped by `MiscUtil.findClosestAttackableEntity`,
hidden on enemy minimaps, attackers lose it - and is revealed by moving, targeting or firing. **Elder Treant** (520 HP,
0.16 speed) with a passive boulder every 8 s (`BoulderToss`: 14-block throw, r3 18 dmg to enemies only, particle arc,
no projectile entity). Bot kit: t2Lab / Elder Druid / T2 army; Awaken Thicket and Leaping Charge join the bot's aimed
powers. Tier 3 stays off the Heartwood Hall and the bot never researches it (null T3 building).
Not done: Wisp Choir, Bloom Priestess, Storm Oak, Great Elk Herd; own portraits for the T2 units (recoloured
slice-1 skins and a drawn stag icon for now).

## 4. Later slices

| Slice | Content | Notes |
|---|---|---|
| 2 - rest of T1 | Moonwell Bearer (healer, stronger at night: reuse Witch healing + `level.isNight()`), Owl Watcher (perching static scout: Parrot/Phantom body, `BatUnit`-like flyer), Vine Snare (trap layer: places a hidden "snare" block that roots the first enemy), Hive Keeper if not in slice 1 | snare needs a new block + server tick rate-limited per chunk |
| 3 - Living Terrain (signature) | Seedshaper plants **Thickets** (real blocks: azalea / leaves variant that hides units standing in it from enemy fog reveal), vines that slow; enemies cut/burn them; Grove Warden *Overgrowth* (instant thicket patch) | fog hook in `fogofwar/` (hide units inside a thicket unless an enemy is within N blocks); burnable by fire; strict caps (thicket count per player) for 8v8 perf |
| 4 - T2 lab + Elder Druid | T2 lab (Moonwell Sanctum), Elder Druid T2 constructor (add to `T2Workers`), *awaken* thicket -> temporary treant; Elder Treant, Stag Lancer (horse/goat body, leap), Wisp Choir (AA: Allay/Vex bodies), Shade Ranger (cloaked sniper: reuse invisibility handling), Bloom Priestess, Storm Oak (static artillery building), Great Elk Herd (squad of 3) | bot kit gets t2Lab/t2Worker/t2Army; `bot_kits_use_their_t2_lab` must include Verdant |
| 5 - T3 | World Tree Walker (scaled tinted Iron Golem or custom model; plants thickets as it walks, heals in shade; burns for double), T3 lab, `ResearchTier3` + `T2Workers.isT3Lab` | obey the T3 rules in design-factions.md |
| 6 - polish | own portraits/mob heads, cube map for title screen (then add to `CLASSIC_FACTIONS`), calm theme, survival wave spawner, Alex's Mobs bodies if lovish approves | |

## 5. Risks

- **Fall-through to villagers.** About 35 call sites use `MONSTERS ? : PIGLINS ? : else (villagers)`. A Court
  unit would wear Sunforged liveries, get Formation's bonus, villager banners, Sunforged D-gun, etc. Slice 1
  must add an explicit branch to every file in item 7 above; grep `Factions\.(VILLAGERS|MONSTERS|PIGLINS)`
  before merging. Long-term: move per-faction look/mechanic data into fields on `Faction`.
- **Enum ordinals / int ids.** Factions themselves are key-based (safe), but `SkirmishServerboundPacket` sends
  the faction as an int 0/1/2 and `SkirmishSetupScreen` uses `3` for random (`default` branch). Verdant must
  take `4` (or switch the packet to the faction key); never reuse `3`. Registry ids:
  always register new factions after NONE. `RTSPlayerSaveData` / `ScenarioRole` legacy enum-name strings
  need no change (new saves use the key).
- **Packets.** No new packet needed for slices 0-2. Thickets (slice 3) are blocks, so they sync for free; the
  "hidden in thicket" state must be computed server side and ride the existing fog/visibility updates,
  rate-limited (no per-tick per-unit packets at 8v8).
- **Saves.** New buildings need `BuildingSaveData` cases or they vanish on reload; renaming a structure later
  breaks old saves. Units are vanilla-entity-NBT, so new entity types are fine.
- **Bot.** `kitFor` falls back to the villager kit for any unknown faction - a Verdant bot without its own kit
  would build Sunforged buildings. Add the kit in the same slice as the faction goes live; until then the
  preview flag keeps bots from ever getting it (bots start via `startRTSBot` with explicit factions).
- **Instanceof inheritance.** Extending `IronGolemUnit`, `VillagerUnit`, `VindicatorUnit` inherits code that
  checks `instanceof` those classes (e.g. `NonUnitServerEvents`, `T2Workers`, villager profession rendering,
  research that buffs "Iron Golems"). Grep for `instanceof <Base>` before extending; prefer extending the
  vanilla mob + copying the small Unit boilerplate when the base class carries faction-specific behaviour.
- **Structures in both trees.** Forgetting `data/` makes the server unable to place a building (CI test
  catches it).
- **Lobby width.** Five tiles narrow the name column by one tile (22 px). Fine for 16-char names at GUI
  scale 3 in testing assumptions; re-check when factions 4-7 arrive (seven tiles will need a dropdown or a
  second row).
- **Alex's Mobs.** Installed, not a dependency. Using it in code needs a `compileOnly`/runtime dep in
  `bar/build.gradle` and CI, and a soft-dependency guard; decision for lovish.

## 6. Size estimates

| Slice | Size | Rough scope |
|---|---|---|
| 0 announce | XS (done) | 6 files, ~120 lines |
| 1 MVP | L | ~15 new classes (6 units x Unit+Prod, 3 buildings), ~25 touched files, 3-5 structures, retextures, 3-4 GameTests; 2-3 sessions |
| 2 rest of T1 | M | 3 units, 1 trap block |
| 3 Living Terrain | L | new blocks, fog hook, Seedshaper/Warden abilities, perf caps; needs a playtest |
| 4 T2 | L-XL | lab, T2 worker, 7 units incl. a static building and a squad |
| 5 T3 | M | one big unit + lab + research hook |
| 6 polish | M | art, music, survival spawner |

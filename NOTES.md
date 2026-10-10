# Beyond and Tabs - working notes

A BAR-style RTS in Minecraft, built as a GPL-3 fork of Reign of Nether, with TABS-era faction themes.
This file is the session-proof status log: what's shipped, how to install, what's next.

## How things work
- Live game: `bar/` (Forge 1.20.1, Forge 47.4.0, modid stays `reignofnether`).
- CI compiles on every push (local JDK has no Minecraft classpath); jars publish to release tags `dev` / `bar-dev`.
- Before every push: `python3 tools/check_java.py` (structural gate). CI also runs `tools/balance_audit.py`.
- Install route to the user's PC: slim the jar (strip the oggs in `bigsounds.txt`), `SendUserFile` → `device_commit_files`
  into `BeyondAndTabs-Test` Prism instance's mods folder (instance pinned to Forge 47.4.0 in mmc-pack.json).
- Old from-scratch prototype (`engine/` + `mod/`) stays as reference only.

## Shipped (fork)
- BAR flow economy: metal/energy, storage, income/expense HUD bar with stall warning, build power.
- Metal patches: plus-shaped raw-iron stamps (base ring, lanes, flanks, contested middle); extractors only on patches.
- Extractor T1→T2 (copper drill tower, deniable mid-upgrade), wind generators, Tier 2 research.
- Commanders (first worker, +HP/dmg/build power, gold star icon; commanderDefeat gamerule).
- Skirmish bots all 3 factions (`/bot add <faction> [easy|medium|hard|far]`): eco, army, waves, towers, T2.
- Quick Battle title-screen button (Battlefield world preset); patrol (Y); repeat queue (persists saves);
  area mex (shift+right-click); guard (P) - fighters defend ward, workers assist/repair.
- TABS-era factions: The Kingdom / The Fallen / The Gilded Legion, 25 themed unit names.
- Camera: WASD pan + edge pan; Shift at edge or Shift+A/D swings, Shift+W/S tilts, Ctrl+wheel rotates;
  one-time controls hint; strategic zoom icons (commander star, building plates).
- Minimap intel: metal patches as silver diamonds (synced + saved; taken ones dim). No enemy reveals -
  the arena wall bounds the search instead.
- Battlefield auto-forms for Quick Battle / bot matches (BattlefieldSetup): bases + lanes cleared, patches
  stamped, Great-Wall ring raised. Per-match rolls: wall radius/towers/weathering, wall material (stone
  brick/sandstone/deepslate/blackstone/mud brick), gatehouses, mex richness 0.6-1.6.
- War banners on production buildings + capitols (client-rendered, faction colours).
- Role-silhouette liveries (UnitDressServerEvents): workers boots, melee full kit, ranged hood, commander
  gold crown - all ZERO armour (explicit modifier).
- Bots: territory patrol sweeps between waves, skip patches deep in enemy territory.
- Windmills + extractors now faction-skinned (Kingdom plaster/spruce, Fallen deepslate/dark oak + grey rag sails, Gilded Legion blackstone/gold + gold sails); spinning sails client-rendered; producing buildings
  puff chimney smoke.

- BAR wrecks + reclaim (WreckServerEvents): units worth >=30 metal leave a faction-styled wreck (block display)
  holding half their metal; workers within 3 blocks reclaim it (5 metal/s x build power). 5 min decay, cap 150.
  Bots send an idle worker to wrecks near home. Chipped lamps/friezes on economy buildings.

- Commander D-gun per faction (aimed line, 150 energy); Gravebound commander Raise Dead (wrecks rise as Ghouls,
  paid from the wreck). Faction signatures: Horde Momentum (straight charges +50% dmg), Sunforged Formation
  (ranged with 2 melee guards +25% projectile dmg). Reclaim chains; Gravebound reclaim x2, Horde x1.5.

## Decor mods (installed in lovish's instance; structures may reference them)
Macaw's Roofs 2.3.2, Macaw's Windows 2.4.2, Macaw's Fences & Walls 1.2.1, Supplementaries 1.20-3.1.43,
Moonlight Lib 1.20-2.16.35 (SHA-512 verified vs Modrinth). Modded blocks in a structure load as AIR if the mod
is missing - never a crash. Before pushing structures that use them: stage the jars from his mods folder and run
`python3 tools/validate_modded_structures.py <jar-dir>` (catches typos/invalid states, e.g. Macaw's windows
only accept facing=north|east). Houses (villager_house, haunted_house) now use them.

## Known issues / watchlist
- Upstream Reign of Nether bug: central_portal.nbt references `reignofnether:decayable_netherwart_block`
  (registered name is `decayable_nether_wart_block`) - 1 of 1584 blocks loads as air. Cosmetic; left alone.
- Awaiting playtest of: wall materials/gatehouses, banners, liveries, bot sweeps, round-3 fixes.
- (fixed) /rts-reset now digs out patches, clears the registry/save and empties every minimap.
- Faction-distinct versions of RoN's core buildings not started (tools/preview_structure.py renders NBTs
  for eyeballing before CI).

## Next steps
See project doc claude/work-orders.md for the prioritised backlog.

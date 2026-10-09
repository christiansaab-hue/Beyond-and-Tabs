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
- Minimap intel: metal patches as silver diamonds (synced + saved), enemy capitols always visible,
  bot base ping on `/bot add`.
- Windmill rebuilt (plastered tower + wooden cap) with client-rendered spinning sails; producing buildings
  puff chimney smoke.

## Known issues / watchlist
- No human playtest yet of: guard, piglin bots, repeat queue, minimap patches, windmill sails.
- (fixed) /rts-reset now digs out patches, clears the registry/save and empties every minimap.
- Deeper faction theming (skins/building variants per faction) not started - awaiting user verdict.
- Units "simplistic" per user - acceptable for now, revisit after theming pass.

## Next steps
1. Install latest jar to user's instance after CI green.
2. More building character/animations if user likes the windmill direction.
3. Possible: area reclaim, bot defense via patrol, hotkey polish.

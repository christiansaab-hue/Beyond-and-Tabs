# Beyond and Tabs

A Minecraft (Forge 1.20.1) RTS mashup: Beyond All Reason-style economy, tech tiers and controls, commanding real
Totally Accurate Battle Simulator units as physics ragdolls. TABS content is read from the player's own install and is never
included in this repository or in releases.

- `design/` JSON design sheets (source of truth). `python3 tools/preflight.py` checks them.
- `tools/extract/` reads unit blueprints and meshes from a TABS install (local use only).

Status: design + extraction proven; mod code not started. Credits: Beyond All Reason and Reign of Nether (GPL-3.0) as references; licenses to be confirmed before any code is reused.

## Third-party data
- `mod/src/main/resources/assets/beyondtabs/tabs/unity-2019.4.json`: Unity 2019.4 type trees for built-in engine classes
  (GameObject, Transform, Mesh, Material, renderers...), exported with UnityPy (MIT, K0lb3) from its bundled type-tree
  package. They describe Unity's file format, not any game's content.

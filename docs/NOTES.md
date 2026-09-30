# Aethermon Server — Running Notes Log

> Read this at the start of every task. Add entries after every problem solved.

---

## 2026-09-30 — Initial Project Setup (Task 1)

### What was found on first inspection
- Fabric server already installed at `C:\AetherMon` with `fabric-server-launch.jar`
- Cobblemon 1.7.3+1.21.1 confirmed — correct and latest stable
- EULA already accepted by owner (`eula=true`)
- LuckPerms already installed in mods folder
- 48 mods total found — includes both server and client mods mixed together

### ⚠️ Known Issues / Gotchas

#### CRITICAL: `spawn-animals=false` and `spawn-monsters=false`
- Found in `server.properties`
- **This will break Cobblemon!** Pokémon are spawned as entities. If animal spawning is off, no Pokémon will appear in the world.
- **Fix:** Must set both to `true` before testing Cobblemon spawns.

#### Client-only mods in server mods folder (to audit)
- Some mods in `/mods` are client-side only and should NOT run on a server.
- Running client mods on a server can cause crashes or errors in logs.
- Full audit to be done in Task 2.

#### Git not installed
- `git` command not found on this Windows machine.
- **Fix:** Owner needs to install Git from https://git-scm.com/download/win
- Until then, folder structure and files are created manually.

#### PvP setting
- Owner confirmed: PvP zone-based (global OFF, zones later)
- `server.properties` currently has `pvp=true` — will be changed in Task 3.

### Decisions Made
- `C:\AetherMon` is the project root
- `/server`, `/docs`, `/scripts`, `/mods-backup` folders created alongside existing files
- `world/` and `lobby/` folders are NEVER touched or deleted
- Whitelist will be turned ON in Task 3

---

---

## 2026-09-30 — Mod Audit + Start Scripts (Task 2)

### Client-only mods removed (moved to mods-backup, NOT deleted)
17 mods moved to `mods-backup/client-only-removed-2026-09-30/`:
- sodium, indium, ImmediatelyFast, entityculling, moreculling
- visuality, sound-physics-remastered, InvMove, durabilitytooltip
- morechathistory, chatsigninghider, RoughlyEnoughItems, Jade
- betterstats, Iceberg, SmoothServerCosmetics, craftingtweaks

**Gotcha:** These are fine on the CLIENT (player's PC) but must never be in the server's mods folder. They can cause startup crashes or confusing errors.

### authme-fabric kept intentionally
- Owner wants password login even with `online-mode=true`
- This is unusual — monitor for conflicts with LuckPerms login flow

### Start scripts created
- `scripts/start.bat` — Windows (double-click to run)
- `scripts/start.sh` — Linux/Mac (for future paid server)
- RAM: `-Xms2G -Xmx5G` (5GB to Java, 1GB left for Windows)
- GC: G1GC with Aikar flags (standard for Minecraft servers)

### ⚠️ EULA note
- `eula.txt` already has `eula=true` — owner accepted it themselves

---

## Template for future entries

### YYYY-MM-DD — [Task Name]
**Problem:** What went wrong or what was unclear  
**Fix:** What was done to resolve it  
**Gotcha:** Anything to watch out for next time

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

## 2026-09-30 — server.properties Configuration (Task 3)

### Critical fixes applied
- `spawn-animals=true` — was FALSE, would have broken all Cobblemon spawns
- `spawn-monsters=true` — was FALSE, needed for hostile Pokémon encounters

### Settings changed from default
- `pvp=false` — global off; zone PvP to be added later
- `white-list=true` + `enforce-whitelist=true` — locked to approved players
- `difficulty=normal` — was easy
- `view-distance=8` — was 10 (performance, 6GB RAM)
- `simulation-distance=6` — was 10 (big CPU saving)
- `max-players=10` — was 20 (small community start)
- `spawn-protection=0` — was 16 (Multiworld/Lobby handles spawn protection)
- `entity-broadcast-range-percentage=80` — was 100 (network saving)
- `player-idle-timeout=30` — was 0 (kick AFK after 30 min)
- `motd` — set to colored Aethermon server name

### Gotcha: Minecraft rewrites server.properties on startup
- When the server starts, it strips our comments and reorders lines!
- The comments only exist in our Git history — that's fine, Git is our source of truth.

---

## 2026-09-30 — Java Version Error Fix (Task 2 follow-up)

### Problem
Server crashed on first launch with:
`UnsupportedClassVersionError: class file version 65.0 ... recognizes up to 52.0`

### Plain English
- `class file version 65.0` = Java 21 (what Minecraft 1.21.1 needs)
- `class file version 52.0` = Java 8 (what was installed as system default)
- The system `java` command pointed to Java 8 at `C:\Program Files (x86)\Common Files\Oracle\Java`

### Fix
- Installed Eclipse Temurin JDK 21 from adoptium.net
- Installed at: `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`
- Updated `start.bat` and `start.sh` to hardcode the Java 21 path
- System default `java` is still Java 8 — that's fine, the scripts bypass it

### Gotcha: whitelist was empty
- `whitelist.json` was `[]` even after setting `white-list=true` in server.properties
- This is because the server rewrites properties on startup — need to do `/whitelist add <name>` in console OR restart server fresh with the new properties and then add players
- **Action needed:** Stop server → start with `start.bat` → run `/whitelist add YourName` in console

---

## 2026-09-30 — Lobby Mob Spawning Fix

### Problem
Pokémon (and mobs) were spawning inside the lobby world. Owner wants lobby to be entity-free.

### World setup discovered
- Lobby = `minecraft:overworld` (default world, `level-name=lobby` in server.properties)
- Survival = `multiworld:survival` (separate Multiworld dimension — spawn works fine here)
- These are DIFFERENT worlds so fixing one does NOT affect the other

### Fix
Edited `config/multiworld/worlds/minecraft/overworld.yml` to add gamerules:
- `doMobSpawning: false` — no Pokémon/mobs in lobby
- `doWeatherCycle: false` — lobby stays sunny
- `doDaylightCycle: false` — lobby time frozen
- `keepInventory: true` — players keep items if they die in lobby

### Gotcha: Multiworld gamerule format
- Gamerule values MUST be quoted strings ("false" not false) in this yml
- Changes take effect after server restart OR `/multiworld reload`

---

## Template for future entries

### YYYY-MM-DD — [Task Name]
**Problem:** What went wrong or what was unclear  
**Fix:** What was done to resolve it  
**Gotcha:** Anything to watch out for next time

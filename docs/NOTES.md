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

## 2026-09-30 — Cobblemon Lobby Spawn Fix (datapack)

### Problem
`doMobSpawning false` stopped vanilla mobs but NOT Pokémon.
Cobblemon has its own spawner that completely ignores the vanilla gamerule.

### Fix
Created a Cobblemon `spawn_rules` datapack at:
`lobby/datapacks/no-lobby-spawns/`

Key file: `data/cobblemon/spawn_rules/no_overworld_spawns.json`
```json
{ "type": "location", "allow": "!v.world.is_of('minecraft:overworld')" }
```
- `type: location` = checked FIRST before any species lookup (most efficient)
- `!v.world.is_of('minecraft:overworld')` = deny if in overworld

This blocks ALL Pokémon spawning in the lobby without touching individual species files.

### Gotcha
- Datapack lives in `lobby/datapacks/` NOT in the root `datapacks/` folder
- This is because each world has its own datapacks folder in Fabric/Vanilla
- Must run `/datapack enable "file/no-lobby-spawns"` OR restart server

---

## ⚠️ OPEN ISSUE — Lobby Pokémon Spawning (unresolved)

**Status:** Not fixed yet — deferred to come back to later.

**What was tried:**
1. `doMobSpawning false` via console — stopped vanilla mobs, not Cobblemon ❌
2. `spawn_rules` datapack with `!v.world.is_of('minecraft:overworld')` — no effect ❌
3. Same datapack with pack_format 48 + positive allow logic — still spawning ❌

**Next things to try when we return:**
- Run `datapack list` in console to confirm datapack is actually loading
- Check server log on startup for any datapack errors
- Try the `spawn_pool_world` override approach (per-species anticondition)
- Ask in Cobblemon Discord with exact 1.7.3 version — `spawn_rules` may be 1.8+ only
- Nuclear option: disable `enableSpawning` in Cobblemon config globally and only re-enable it in survival via a different mechanism

**Workaround for now:** Pokémon in lobby are cosmetic annoyance, not game-breaking. Server is playable.

---

## 2026-09-30 — LuckPerms Groups (Task 4)

### Groups created
- `default` (weight 0) → auto-assigned to everyone
- `member` (weight 10) → inherits default
- `staff` (weight 50) → inherits member
- `admin` (weight 100) → inherits staff, full permissions

### Admin assigned
- `Antoinekub` → admin group

### Missing: /home /tpa /back
- No mod installed for these yet
- Recommended: Essential Commands (modrinth.com/mod/essential-commands)
- Deferred — add when ready

---

## 2026-09-30 — Phase 2 Decisions & Audit (AethermonCore)

### Language & tooling
- Language: **Java** (Java 21, matches server JDK)
- Build: **Gradle wrapper** (no Gradle install needed, bundled in project)
- Mod loader: **Fabric Loom**
- Package root: `com.aethermon.core`
- Mod ID: `aethermoncore`

### What to install (mods, not custom code)
- **Essential Commands** → /home /sethome /tpa /back /warp
- **Flan** → land claims

### What to build in AethermonCore (Java mod)
Economy → Sidebar/Menu → Homes → Server Shop → Player Shop →
Login/Online Rewards → Quests → Lucky Draw → World Boss → Keys/Crates

### Economy decisions
- Two currencies from day 1: Coins 🪙 and Gems 💎
- Starting balance: 50,000 Coins, 25 Gems
- AethermonCore implements Impactor's economy API so Cobblemon hooks in automatically
- All balance changes go through one EconomyService — never direct DB writes

### Claims & homes decisions
- Homes: default=1, member=2, staff=5, admin=unlimited
- Claims (Flan): other players CAN battle wild Pokémon inside any claim
- Claims: players CAN build inside their own claim, NOT others
- PvP battles inside claims: DISABLED — players use /duel command instead

### Other decisions
- No GitHub yet — local only for now
- No real-money code at all in this phase

### Impactor removal
- Owner requested removing Impactor's economy ($500 starting dollars).
- `Impactor-Fabric-5.3.5+1.21.1.jar` moved to `mods-backup/removed-2026-09-30/`.
- AethermonCore now owns `/balance`, `/bal`, `/money`, `/coins`, and `/gems` exclusively.

---

## 2026-09-30 — Sidebar & Main Menu Module (AethermonCore)

### Sidebar (Scoreboard)
- Registered `%aethermon:coins%` and `%aethermon:gems%` placeholders with PlaceholderAPI (`eu.pb4:placeholder-api`).
- Configured `config/styled-sidebars/styles/default.json` with Aethermon branding, player rank, coins, gems, online count, and ping.

### Main Menu GUI (/menu, /help, /gui)
- Implemented 100% server-side chest GUI (`MenuScreenHandler`). Zero client mod required.
- Provides interactive menu slots for Wallet, Homes, Spawn, RTP, Land Claims, Shop, Market, and Daily Rewards.
- Clicking menu items safely executes the underlying server command.

---

## Template for future entries

### YYYY-MM-DD — [Task Name]
**Problem:** What went wrong or what was unclear  
**Fix:** What was done to resolve it  
**Gotcha:** Anything to watch out for next time

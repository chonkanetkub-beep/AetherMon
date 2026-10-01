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

## 2026-10-01 — Homes & Land Claims Module (Task 3)

### Homes (Built into AethermonCore)
- Implemented `HomesModule` storing homes in `aethermon.db` SQLite (`player_homes` table).
- Full cross-world teleportation (`/home [name]`, `/sethome [name]`, `/delhome <name>`, `/homes`).
- Permission-based limits via LuckPerms:
  - Default: 1 home
  - Member: 2 homes (`aethermon.homes.2`)
  - Staff: 5 homes (`aethermon.homes.5`)
  - Admin: Unlimited (`aethermon.homes.unlimited`)
- Full test coverage with JUnit 5 (`HomeServiceTest`).

### Land Claims (Flan 1.21.1)
- Installed `flan-1.21.1-1.12.8-fabric.jar` (zero client mod required).
- Configured claim policies per owner specification:
  - Visitors CAN battle wild Pokémon inside claims (`flan:hurt_animal: true`, `flan:animal_interact: true`).
  - Visitors CANNOT place or break blocks inside other claims (`flan:place: false`, `flan:break: false`).
  - PvP inside claims is globally DISABLED (`flan:hurt_player: ALLFALSE`). Players use `/duel`.

---

## 2026-10-01 — Server Shop Module (Task 4)

### Server Shop (/shop, /sell)
- Implemented `ShopModule` in `AethermonCore`:
  - 100% server-side chest GUI (`ShopGui.java`) with zero client mod required.
  - Category selector with live wallet indicators (Coins 🪙 & Gems 💎).
  - 5 default categories configured in `config/aethermoncore/shop.json`:
    - Poké Balls (Poké Ball, Great Ball, Ultra Ball, Dusk, Quick, Timer, Heal)
    - Medicine (Potion, Super, Hyper, Max, Full Restore, Revive, Max Revive)
    - Evolution Stones (Fire, Water, Thunder, Leaf, Moon, Sun, Shiny, Dusk, Dawn)
    - Berries & Food (Oran, Sitrus, Lum, Leppa, Golden Apple)
    - Minerals & Ores (Iron, Gold, Diamond, Emerald, Copper, Coal, Torches)
  - Interactive shopping controls:
    - **Left-Click**: Buy 1
    - **Shift-Left-Click**: Buy 16 (bulk)
    - **Right-Click**: Sell 1
    - **Shift-Right-Click**: Sell All of that item from player's inventory
- Quick sell commands:
  - `/sell hand`: sells the currently held item if defined in shop.
  - `/sell all`: scans inventory and auto-sells all sellable shop items, depositing total coins/gems.
  - `/shop reload`: reloads `shop.json` without server restart (admin permission level 2).
- Integrated with `/menu` slot 15.

---

## 2026-10-01 — Player Market / Auction House Module (Task 5)

### Player Market (/ah, /market)
- Implemented `MarketModule` in `AethermonCore`:
  - 100% server-side 54-slot chest GUI (`MarketGui.java`) with zero client mod required.
  - Backed by SQLite tables `market_listings` and `market_deliveries`.
  - Full item preservation using `ItemStack.CODEC` with `RegistryOps<NbtElement>` — preserves all NBT, custom names, Cobblemon Pokémon data, lore, and component metadata.
  - Features:
    - 45 listings per page with pagination controls.
    - Search filtering (`/ah search <query>` or interactive).
    - Player listing limits (default 10 active listings per player).
    - Configurable tax rate (default 2% sink) and listing expiration (default 48 hours).
    - Auto-expiration system: expired items automatically convert into claimable items in the player's delivery box.
    - Offline earnings delivery: offline sales deposit earnings into `market_deliveries` so players can claim them with `/ah claim` or the GUI.
    - Self-listing management: "Your Active Listings" view allows cancelling any listing and instantly retrieving items.
- Commands:
  - `/ah` or `/market`: opens main Market GUI.
  - `/ah sell <price> [coins|gems]`: lists held item.
  - `/ah search <query>`: opens market filtered by keyword.
  - `/ah listings`: opens active listings manager.
  - `/ah mail` or `/ah claim` or `/ah deliveries`: opens delivery collection box.
- Integrated with `/menu` slot 16.

---

## 2026-10-01 — Login & Online Rewards Module (Task 6)

### Login & Playtime Rewards (/rewards, /daily, /playtime)
- Implemented `RewardsModule` in `AethermonCore`:
  - 100% server-side 54-slot chest GUI (`RewardsGui.java`) with zero client mod required.
  - Backed by SQLite tables `player_daily_rewards` and `player_playtime`.
  - Configurable via `config/aethermoncore/rewards.json`.
  - **Daily Login Streak (30-day cycle)**:
    - 30 customizable reward tiers configured with Coins, Gems, Cobblemon balls/potions/candies, and command execution.
    - **Streak Freezing**: As chosen by owner, missing a day preserves player streak progress without resetting back to Day 1.
    - Day 7, Day 14, Day 21 milestones, and Day 30 Grand Master tier with Master Ball & Rare Candies.
  - **Online Playtime Rewards**:
    - 4 daily milestones (15m, 30m, 60m, 120m).
    - **AFK Detection**: Tracks position and rotation delta; pauses playtime timer if inactive for > 5 minutes (`afkThresholdSeconds`).
    - Midnight reset for daily playtime counters and claimed tiers.
- Commands:
  - `/rewards` or `/daily`: opens the interactive 54-slot Rewards GUI.
  - `/playtime`: opens playtime rewards tab or `/playtime check` for chat status.
  - `/rewards reload`: reloads `rewards.json` hot (permission level 2).
  - `/rewards reset <player> [daily|playtime]`: resets data for testing (permission level 2).
- Integrated with `/menu` slot 22.

---

## 2026-10-01 — Mystery Crates & Keys Module (Task 8)

### Mystery Crates (/crates, /crate, /keys)
- Implemented `CratesModule` in `AethermonCore`:
  - 100% server-side 54-slot chest GUIs (`CratesMenuGui`, `CratePreviewGui`, `CrateAnimationGui`) with zero client mod required.
  - Backed by SQLite tables `player_crate_keys` and `crate_blocks`.
  - Configurable via `config/aethermoncore/crates.json`.
  - **4 Default Crates**:
    - **Poké Crate** (Common): starter balls, potions, berries, stones, coins & gems.
    - **Great Crate** (Rare): ultra balls, competitive held items (Leftovers, Choice Band, Focus Sash, Life Orb), rare candies.
    - **Ultra Crate** (Epic): Master Balls, Ability Capsules, Exp Candy XL, high coin/gem drops.
    - **Legendary Crate** (Mythic): Ability Patches, 2x Master Balls, Netherite, rare candies, massive jackpots.
  - **Dual-Key Architecture**:
    - **Virtual Keys**: Stored in SQLite per player per crate. Checked automatically when opening in GUI.
    - **Physical Keys**: Tagged `ItemStack` keys (NBT marker `aethermon_crate_key` + lore). Tradeable in `/ah` or right-clickable on physical crate blocks.
  - **Physical In-World Crate Blocks**:
    - Admins can link any block (chest/ender chest/beacon/etc.) to a crate using `/crates setblock <crate_id>`.
    - Right-click block opens crate opening animation (consumes physical or virtual key).
    - Left-click block opens reward preview with exact drop odds.
  - **Synchronized Opening Animation**:
    - 26-frame continuous roulette tape engine with realistic deceleration curve.
    - Pre-calculated winner positioning at `FINAL_STEP + 4` landing directly on slot 31.
    - Climax delivery with levelup fanfare and server-wide announcement for rare prizes.
- Commands:
  - `/crates`, `/crate`, `/keys`: opens interactive Crates Menu GUI.
  - `/crates preview <crate_id>`: opens odds inspection view.
  - `/crates key <player> <crate_id> [amount] [physical|virtual]`: gives keys to player (admin perm level 2).
  - `/crates keyall <crate_id> [amount] [physical|virtual]`: server-wide key drop event.
  - `/crates setblock <crate_id>`: links target block to crate.
  - `/crates delblock`: unlinks target block.
  - `/crates reload`: reloads `crates.json` hot.
- Integrated with `/menu` slot 20.

---

## 2026-10-01 — Battle Pass / Season Pass Module (Task 9)

### Battle Pass (/bp, /pass, /battlepass)
- Implemented `BattlePassModule` in `AethermonCore`:
  - 100% server-side 54-slot chest GUI (`BattlePassGui.java`) with zero client mod required.
  - Backed by SQLite table `player_battlepass` storing exp, tier, premium status, and claimed tier history per season.
  - Configurable via `config/aethermoncore/battlepass.json`.
  - **Season 1: Aether Ascension (30 Tiers)**:
    - **Dual Tracks**: Free Track (available to everyone) & Premium Track (unlocked with 25 Gems).
    - **Free Track Highlights**: Over 200,000 Coins, Poké/Great/Ultra Balls, Potions, evolutionary stones, Rare Candies.
    - **Premium Track Highlights**: Additional Coins, Gems, Master Balls, Ability Capsules, Ability Patches, Held items (Choice Scarf, Leftovers, Focus Sash, Life Orb), Netherite Ingots, and Crate Keys.
    - **Tier 30 Grand Champion**:
      - Free: 100,000 Coins + 10 Gems.
      - Premium: Master Ball + Ability Patch + 50 Gems!
  - **Automated Progression Hooks**:
    - Hooked into Cobblemon `POKEMON_CAPTURED` event (+50 Pass EXP per capture).
    - Hooked into Cobblemon `BATTLE_VICTORY` event (+40 Pass EXP per victory).
    - Levelup toast and broadcast when reaching Tier 30.
  - **Interactive 54-Slot GUI**:
    - Displays Free track row, Tier status indicator row, and Premium track row with 7 tiers per page.
    - Live visual cues: green glint on claimable rewards, checkmarks on claimed, locked indicators on unreached tiers.
    - Real-time ASCII progress bar and total EXP counter.
    - "Claim All Available Rewards" button (one-click mass claiming).
    - In-GUI "Unlock Premium Pass" button directly deducting Gems and activating the track in real-time.
- Commands:
  - `/bp`, `/pass`, `/battlepass`: opens the Battle Pass GUI.
  - `/bp claim`: claims all unlocked rewards for both tracks.
  - `/bp buy`: unlocks the Premium Pass for the season using Gems.
  - `/bp addexp <player> <amount>`: grants Pass EXP (admin perm level 2).
  - `/bp settier <player> <tier>`: sets player tier directly (admin perm level 2).
  - `/bp unlockpremium <player>`: grants Premium Pass (admin perm level 2).
  - `/bp reload`: reloads `battlepass.json` hot.
- Integrated with `/menu` slot 19.

---

## Template for future entries

### YYYY-MM-DD — [Task Name]
**Problem:** What went wrong or what was unclear  
**Fix:** What was done to resolve it  
**Gotcha:** Anything to watch out for next time




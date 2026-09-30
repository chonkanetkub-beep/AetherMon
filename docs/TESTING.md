# Aethermon — Phase 1 Testing Checklist

> Run through this with just you and your friend before opening to anyone else.
> Tick off each item. If something fails, note it below the item.

---

## 🔧 BEFORE YOU START
- [ ] Server is running (`scripts/start.bat`)
- [ ] You can see `Done! (X.Xs)` in the server console
- [ ] No red `ERROR` lines in the startup log (yellow WARN is usually OK)

---

## 1️⃣ JOINING & WHITELIST

| # | Test | How to test | Expected result |
|---|------|-------------|-----------------|
| 1.1 | You can join | Open Minecraft, add server IP `localhost`, connect | You join successfully |
| 1.2 | Friend can join | Friend connects to your IP address | Friend joins successfully |
| 1.3 | Whitelist blocks strangers | Try joining with an account NOT on the whitelist | Kicked with "not whitelisted" message |
| 1.4 | You spawn in lobby | On join, you appear in the lobby world | Lobby area loads correctly |

**Your external IP** (share this with your friend):
Run this in PowerShell: `(Invoke-WebRequest ifconfig.me/ip).Content`

---

## 2️⃣ COBBLEMON — SPAWNING & BATTLES

| # | Test | How to test | Expected result |
|---|------|-------------|-----------------|
| 2.1 | Pokémon spawn in survival | Go to the survival world, walk around 1-2 min | Pokémon appear in the world |
| 2.2 | Wild battle works | Walk into a wild Pokémon | Battle screen opens |
| 2.3 | You can catch a Pokémon | Weaken it, throw a Pokéball | Pokémon is caught and appears in your party |
| 2.4 | PC works | Open your PC (`/pc` or right-click a PC block) | PC opens, Pokémon can be stored |
| 2.5 | PvP battle works | You and your friend challenge each other | Battle starts, no damage to health bars |
| 2.6 | Starter select | New player joins for first time | Starter selection screen appears |

---

## 3️⃣ PERMISSIONS (LUCKPERMS)

| # | Test | How to test | Expected result |
|---|------|-------------|-----------------|
| 3.1 | You have admin | In-game, run `/lp user Antoinekub info` in console | Shows group: admin |
| 3.2 | Regular player can't op themselves | As a non-admin, try `/op username` | "You don't have permission" |
| 3.3 | Warps work for players | Player runs `/warp` | Shows warp list (or "no warps set" if none created yet) |
| 3.4 | RTP works | Player runs `/rtp` | Teleports to a random location in survival world |

---

## 4️⃣ SERVER RESTART

| # | Test | How to test | Expected result |
|---|------|-------------|-----------------|
| 4.1 | Clean stop | Type `stop` in console | Server shuts down gracefully, no errors |
| 4.2 | Restarts cleanly | Double-click `scripts/start.bat` | Server comes back up, `Done!` message appears |
| 4.3 | World data persists | Build something, restart, check it's still there | Your builds are saved |
| 4.4 | Player data persists | Catch a Pokémon, restart, rejoin | Your Pokémon are still in your party |
| 4.5 | LuckPerms persists | After restart, check your rank still works | Still admin after restart |

---

## 5️⃣ BACKUP & RESTORE TEST

| # | Test | How to test | Expected result |
|---|------|-------------|-----------------|
| 5.1 | Manual backup runs | Double-click `scripts/backup.bat` | Folder created in `backups/` with today's date |
| 5.2 | Backup has world data | Open the backup folder, check for `lobby/` and `world/` | Both world folders are inside |
| 5.3 | Restore script lists backups | Double-click `scripts/restore.bat` (server stopped) | Shows a list of available backups |
| 5.4 | Restore test (optional) | Make a test change, backup, undo change, restore | World goes back to backup state |

---

## 6️⃣ CRASH RECOVERY

| # | Test | How to test | Expected result |
|---|------|-------------|-----------------|
| 6.1 | Crash reports exist | Check `crash-reports/` folder after any crash | Crash report file is there |
| 6.2 | Server restarts after crash | Start server after a crash | Server comes back normally |
| 6.3 | No corrupted chunks | Walk around after restart | No missing/glitched terrain |

---

## 📋 HOW TO READ THE SERVER LOG

Open `logs/latest.log` in any text editor. Here's what the colours/prefixes mean:

| Prefix | Meaning |
|--------|---------|
| `[INFO]` | Normal — just information |
| `[WARN]` | Warning — usually harmless, worth noting |
| `[ERROR]` | Problem — something failed, tell me what it says |
| `[FATAL]` | Critical crash — server can't continue |

**Common harmless warnings you can ignore:**
- `Unable to resolve BlockEntity` — Cobblemon texture thing, cosmetic only
- `Skipping bad option: lastServer` — client option in server log, harmless
- `Session lock` — can appear if server was force-closed, OK if server still starts

---

## ✅ SIGN-OFF

When all items above pass, sign off here:

- [ ] **Phase 1 testing complete**
- Date: ___________
- Testers: Antoinekub + ___________
- Notes: ___________

**Next phase:** Open to a small group of friends → then public launch!

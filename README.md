# 🌌 Aethermon — Cobblemon Community Server

A Minecraft 1.21.1 Fabric + Cobblemon survival server.

## Quick Info
| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **Loader** | Fabric |
| **Cobblemon** | 1.7.3+1.21.1 |
| **Status** | 🔧 Local Dev |

## Project Structure
```
C:\AetherMon\
├── mods/           ← Server mod JARs (not in Git)
├── mods-backup/    ← Safe copies of working mod sets
├── config/         ← Mod configs (committed to Git)
├── docs/
│   ├── RULES.md    ← Project rules & versions
│   └── NOTES.md    ← Running log of fixes & gotchas
├── scripts/        ← Start scripts, backup scripts
├── server/         ← Reserved for test server copies
├── world/          ← Live world data (NOT in Git)
├── lobby/          ← Lobby world data (NOT in Git)
├── server.properties
└── .gitignore
```

## Starting the Server
See `scripts/start.bat` (Windows) or `scripts/start.sh` (Linux/Mac).

## Rules
See [docs/RULES.md](docs/RULES.md).

## Changelogs & Notes
See [docs/NOTES.md](docs/NOTES.md).

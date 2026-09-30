# Aethermon Server — Project Rules

## Versions
| Item | Version |
|---|---|
| Minecraft | 1.21.1 |
| Mod Loader | Fabric |
| Cobblemon | 1.7.3+1.21.1 (latest) |
| RAM | 6 GB |
| Hosting | Local (dev) → Paid server (launch) |

## Coding & Config Style
- Change one thing at a time. Test before moving on.
- Comment every non-default setting in config files.
- Never delete or overwrite the `world` or `lobby` folders without explicit permission.
- Never deploy to a live/paid server without explicit go-ahead.
- Commit to Git after each working step with a clear message.
- If unsure about a mod's API or config — **ask for the docs, don't guess**.
- Keep explanations beginner-friendly.

## Safety Rules
> **Never modify payment, permission, or ban-related code without asking me first.**

- Whitelist stays ON until launch is explicitly approved.
- `online-mode=true` must never be disabled on the live server.
- World data (`world/`, `lobby/`) is never in Git — see `.gitignore`.
- Secrets (RCON passwords, API keys) are never committed to Git.

## PvP Policy
- Global PvP is OFF.
- Designated PvP zones will be configured separately when ready.

## Deployment Policy
- All changes are tested locally first.
- A test copy of the server is used before any live changes.
- Backups are verified before any major update.

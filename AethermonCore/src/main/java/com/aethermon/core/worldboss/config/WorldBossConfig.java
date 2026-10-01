package com.aethermon.core.worldboss.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.worldboss.model.BossDefinition;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads World Boss definitions from:
 *   config/aethermoncore/worldboss.json
 *
 * Writes default boss definitions on first run.
 */
public class WorldBossConfig {

    /** All configured boss definitions. */
    public List<BossDefinition> bosses = new ArrayList<>();

    /** Cooldown in seconds between boss spawns (default 30 min). */
    public int spawnCooldownSeconds = 1800;

    /** World/dimension key where bosses are allowed to spawn, e.g. "minecraft:overworld". */
    public String allowedDimension = "minecraft:overworld";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static WorldBossConfig load() {
        Path path = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("aethermoncore")
                .resolve("worldboss.json");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                WorldBossConfig defaults = buildDefaults();
                Files.writeString(path, GSON.toJson(defaults));
                AethermonCore.LOGGER.info("[WorldBoss] Wrote default worldboss.json");
                return defaults;
            }
            WorldBossConfig loaded = GSON.fromJson(Files.readString(path), WorldBossConfig.class);
            if (loaded == null || loaded.bosses == null || loaded.bosses.isEmpty()) {
                AethermonCore.LOGGER.warn("[WorldBoss] worldboss.json has no bosses — loading defaults.");
                loaded = buildDefaults();
            }
            return loaded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load worldboss.json", e);
        }
    }

    public BossDefinition getById(String id) {
        return bosses.stream().filter(b -> b.id.equals(id)).findFirst().orElse(null);
    }

    // ── Defaults ──────────────────────────────────────────────────────────────

    private static WorldBossConfig buildDefaults() {
        WorldBossConfig cfg = new WorldBossConfig();

        // ── Wither King ────────────────────────────────────────────────────────
        BossDefinition wither = new BossDefinition();
        wither.id               = "wither_king";
        wither.displayName      = "§4§lWither King";
        wither.entityType       = "minecraft:wither";
        wither.healthMultiplier = 3.0;   // 3× vanilla Wither HP = ~900 HP
        wither.damageMultiplier = 1.5;
        wither.movementSpeed    = 0.3;
        wither.prizeCoins       = 100_000;
        wither.prizeGems        = 50;
        wither.prizeTopN        = 10;
        wither.bossBarColor     = "PURPLE";
        wither.spawnAnnouncement =
            "§4§l[⚡ WORLD BOSS ⚡] §cThe §4§lWither King §chas emerged! " +
            "Fight it for §6Coins §cand §bGems §crewards!";
        wither.deathAnnouncement =
            "§6§l[⚡ WORLD BOSS ⚡] §eThe §4§lWither King §ehas been slain! " +
            "Prizes have been distributed to the top damage dealers!";

        // ── Elder Titan ────────────────────────────────────────────────────────
        BossDefinition titan = new BossDefinition();
        titan.id               = "elder_titan";
        titan.displayName      = "§9§lElder Titan";
        titan.entityType       = "minecraft:elder_guardian";
        titan.healthMultiplier = 5.0;   // 5× Elder Guardian HP
        titan.damageMultiplier = 2.0;
        titan.movementSpeed    = 0.35;
        titan.prizeCoins       = 200_000;
        titan.prizeGems        = 100;
        titan.prizeTopN        = 10;
        titan.bossBarColor     = "BLUE";
        titan.spawnAnnouncement =
            "§9§l[⚡ WORLD BOSS ⚡] §bThe §9§lElder Titan §bhas awakened from the deep! " +
            "Gather your forces and claim the rewards!";
        titan.deathAnnouncement =
            "§6§l[⚡ WORLD BOSS ⚡] §eThe §9§lElder Titan §ehas fallen! " +
            "Prizes distributed to the top §b" + titan.prizeTopN + " §edamage dealers!";

        cfg.bosses.add(wither);
        cfg.bosses.add(titan);
        return cfg;
    }
}

package com.aethermon.core.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

/**
 * Root config for AethermonCore, stored at:
 * config/aethermoncore/config.json
 *
 * Each module has a simple enabled flag.
 * Add module-specific settings as nested objects here later.
 */
public class CoreConfig {

    // ── Module toggles ────────────────────────────────────────
    public boolean economyEnabled   = true;
    public boolean shopEnabled      = true;
    public boolean marketEnabled    = true;
    public boolean homesEnabled     = true;
    public boolean rewardsEnabled   = true;
    public boolean questsEnabled    = true;
    public boolean luckyDrawEnabled = true;
    public boolean worldBossEnabled = true;

    // ── Economy ───────────────────────────────────────────────
    public long startingCoins = 50_000;
    public long startingGems  = 25;

    // ── Homes (per permission group) ──────────────────────────
    public int homeLimitDefault = 1;
    public int homeLimitMember  = 2;
    public int homeLimitStaff   = 5;
    public int homeLimitAdmin   = Integer.MAX_VALUE; // unlimited

    // ── Economy /pay ──────────────────────────────────────────
    public double payTaxPercent = 0.0;   // 0 = no tax on /pay for now

    // ── /shop ─────────────────────────────────────────────────
    public double shopTaxPercent  = 5.0;   // 5% tax on player market sales

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Loads config from disk, creating defaults if missing. */
    public static CoreConfig load() {
        Path path = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("aethermoncore")
                .resolve("config.json");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                CoreConfig defaults = new CoreConfig();
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }
            CoreConfig loaded = GSON.fromJson(Files.readString(path), CoreConfig.class);
            // Re-save to add any new fields introduced in updates
            Files.writeString(path, GSON.toJson(loaded));
            return loaded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load AethermonCore config: " + path, e);
        }
    }
}

package com.aethermon.core.battlepass.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.battlepass.model.BattlePassTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BattlePassConfig {
    public int season = 1;
    public String seasonName = "§6§lSeason 1: Aether Ascension";
    public String seasonDescription = "Level up your Battle Pass by catching Pokémon, battling, and completing quests!";
    public int expPerTier = 1000;
    public int maxTier = 30;
    public int premiumCostGems = 300;

    public int catchPokemonExp = 50;
    public int winBattleExp = 40;
    public int questCompletedExp = 150;
    public int dailyLoginExp = 100;

    public List<BattlePassTier> tiers = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static BattlePassConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir()
                .resolve("aethermoncore")
                .resolve("battlepass.json");
        try {
            if (!Files.exists(path)) {
                AethermonCore.LOGGER.warn("[BattlePass] battlepass.json not found at {}, using empty defaults", path);
                return new BattlePassConfig();
            }
            return GSON.fromJson(Files.readString(path), BattlePassConfig.class);
        } catch (IOException e) {
            AethermonCore.LOGGER.error("[BattlePass] Failed to load battlepass.json: {}", e.getMessage());
            return new BattlePassConfig();
        }
    }

    public Optional<BattlePassTier> getTier(int tierNumber) {
        if (tiers == null)
            return Optional.empty();
        return tiers.stream().filter(t -> t.tier == tierNumber).findFirst();
    }
}

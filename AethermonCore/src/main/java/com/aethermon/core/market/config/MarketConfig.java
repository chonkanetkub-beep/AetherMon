package com.aethermon.core.market.config;

import com.aethermon.core.AethermonCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class MarketConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int listingDurationHours = 48;
    private double taxRatePercent = 2.0; // 2% tax on sold items (economy sink)
    private int maxListingsPerPlayer = 10;
    private double minPrice = 1.0;
    private double maxPrice = 10000000.0;

    public int getListingDurationHours() {
        return listingDurationHours;
    }

    public double getTaxRatePercent() {
        return taxRatePercent;
    }

    public int getMaxListingsPerPlayer() {
        return maxListingsPerPlayer;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public static MarketConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("aethermoncore");
        Path file = configDir.resolve("market.json");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                MarketConfig cfg = GSON.fromJson(reader, MarketConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException e) {
                AethermonCore.LOGGER.error("[Market] Failed to load market.json, using defaults", e);
            }
        }

        MarketConfig cfg = new MarketConfig();
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(cfg, writer);
            }
        } catch (IOException e) {
            AethermonCore.LOGGER.error("[Market] Failed to save market.json", e);
        }
        return cfg;
    }
}

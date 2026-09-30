package com.aethermon.core.shop.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.shop.model.ShopCategory;
import com.aethermon.core.shop.model.ShopItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves the server shop configuration at config/aethermoncore/shop.json.
 */
public class ShopConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private List<ShopCategory> categories = new ArrayList<>();

    public List<ShopCategory> getCategories() {
        return categories;
    }

    public ShopCategory getCategory(String id) {
        for (ShopCategory cat : categories) {
            if (cat.id().equalsIgnoreCase(id)) return cat;
        }
        return null;
    }

    public ShopItem findShopItem(String itemId) {
        for (ShopCategory cat : categories) {
            for (ShopItem item : cat.items()) {
                if (item.itemId().equalsIgnoreCase(itemId)) {
                    return item;
                }
            }
        }
        return null;
    }

    public void reload() {
        ShopConfig fresh = load();
        this.categories = fresh.categories;
    }

    public static ShopConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("aethermoncore");
        Path file = configDir.resolve("shop.json");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                ShopConfig config = GSON.fromJson(reader, ShopConfig.class);
                if (config != null && config.categories != null && !config.categories.isEmpty()) {
                    return config;
                }
            } catch (IOException e) {
                AethermonCore.LOGGER.error("[Shop] Failed to load shop.json, creating defaults", e);
            }
        }

        ShopConfig config = createDefaults();
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            AethermonCore.LOGGER.error("[Shop] Failed to save default shop.json", e);
        }
        return config;
    }

    private static ShopConfig createDefaults() {
        ShopConfig cfg = new ShopConfig();

        // 1. Poké Balls
        List<ShopItem> balls = List.of(
            new ShopItem("cobblemon:poke_ball", "Poké Ball", Currency.COINS, BigDecimal.valueOf(200), BigDecimal.valueOf(50), 10),
            new ShopItem("cobblemon:great_ball", "Great Ball", Currency.COINS, BigDecimal.valueOf(600), BigDecimal.valueOf(150), 11),
            new ShopItem("cobblemon:ultra_ball", "Ultra Ball", Currency.COINS, BigDecimal.valueOf(1200), BigDecimal.valueOf(300), 12),
            new ShopItem("cobblemon:dusk_ball", "Dusk Ball", Currency.COINS, BigDecimal.valueOf(1000), BigDecimal.valueOf(250), 13),
            new ShopItem("cobblemon:quick_ball", "Quick Ball", Currency.COINS, BigDecimal.valueOf(1000), BigDecimal.valueOf(250), 14),
            new ShopItem("cobblemon:timer_ball", "Timer Ball", Currency.COINS, BigDecimal.valueOf(1000), BigDecimal.valueOf(250), 15),
            new ShopItem("cobblemon:heal_ball", "Heal Ball", Currency.COINS, BigDecimal.valueOf(500), BigDecimal.valueOf(125), 16)
        );
        cfg.categories.add(new ShopCategory("balls", "§c§lPoké Balls", "cobblemon:poke_ball", 11, balls));

        // 2. Medicine
        List<ShopItem> med = List.of(
            new ShopItem("cobblemon:potion", "Potion", Currency.COINS, BigDecimal.valueOf(300), BigDecimal.valueOf(75), 10),
            new ShopItem("cobblemon:super_potion", "Super Potion", Currency.COINS, BigDecimal.valueOf(700), BigDecimal.valueOf(175), 11),
            new ShopItem("cobblemon:hyper_potion", "Hyper Potion", Currency.COINS, BigDecimal.valueOf(1500), BigDecimal.valueOf(375), 12),
            new ShopItem("cobblemon:max_potion", "Max Potion", Currency.COINS, BigDecimal.valueOf(2500), BigDecimal.valueOf(600), 13),
            new ShopItem("cobblemon:full_restore", "Full Restore", Currency.COINS, BigDecimal.valueOf(3000), BigDecimal.valueOf(750), 14),
            new ShopItem("cobblemon:revive", "Revive", Currency.COINS, BigDecimal.valueOf(2000), BigDecimal.valueOf(500), 15),
            new ShopItem("cobblemon:max_revive", "Max Revive", Currency.COINS, BigDecimal.valueOf(4000), BigDecimal.valueOf(1000), 16)
        );
        cfg.categories.add(new ShopCategory("medicine", "§d§lMedicine", "cobblemon:potion", 12, med));

        // 3. Evolution Stones
        List<ShopItem> stones = List.of(
            new ShopItem("cobblemon:fire_stone", "Fire Stone", Currency.COINS, BigDecimal.valueOf(5000), BigDecimal.valueOf(1250), 9),
            new ShopItem("cobblemon:water_stone", "Water Stone", Currency.COINS, BigDecimal.valueOf(5000), BigDecimal.valueOf(1250), 10),
            new ShopItem("cobblemon:thunder_stone", "Thunder Stone", Currency.COINS, BigDecimal.valueOf(5000), BigDecimal.valueOf(1250), 11),
            new ShopItem("cobblemon:leaf_stone", "Leaf Stone", Currency.COINS, BigDecimal.valueOf(5000), BigDecimal.valueOf(1250), 12),
            new ShopItem("cobblemon:moon_stone", "Moon Stone", Currency.COINS, BigDecimal.valueOf(5000), BigDecimal.valueOf(1250), 13),
            new ShopItem("cobblemon:sun_stone", "Sun Stone", Currency.COINS, BigDecimal.valueOf(5000), BigDecimal.valueOf(1250), 14),
            new ShopItem("cobblemon:shiny_stone", "Shiny Stone", Currency.COINS, BigDecimal.valueOf(6000), BigDecimal.valueOf(1500), 15),
            new ShopItem("cobblemon:dusk_stone", "Dusk Stone", Currency.COINS, BigDecimal.valueOf(6000), BigDecimal.valueOf(1500), 16),
            new ShopItem("cobblemon:dawn_stone", "Dawn Stone", Currency.COINS, BigDecimal.valueOf(6000), BigDecimal.valueOf(1500), 17)
        );
        cfg.categories.add(new ShopCategory("evolution", "§6§lEvolution Stones", "cobblemon:fire_stone", 13, stones));

        // 4. Farming & Berries
        List<ShopItem> berries = List.of(
            new ShopItem("cobblemon:oran_berry", "Oran Berry", Currency.COINS, BigDecimal.valueOf(100), BigDecimal.valueOf(25), 11),
            new ShopItem("cobblemon:sitrus_berry", "Sitrus Berry", Currency.COINS, BigDecimal.valueOf(300), BigDecimal.valueOf(75), 12),
            new ShopItem("cobblemon:lum_berry", "Lum Berry", Currency.COINS, BigDecimal.valueOf(400), BigDecimal.valueOf(100), 13),
            new ShopItem("cobblemon:leppa_berry", "Leppa Berry", Currency.COINS, BigDecimal.valueOf(500), BigDecimal.valueOf(125), 14),
            new ShopItem("minecraft:golden_apple", "Golden Apple", Currency.COINS, BigDecimal.valueOf(2500), BigDecimal.valueOf(500), 15)
        );
        cfg.categories.add(new ShopCategory("farming", "§a§lBerries & Food", "cobblemon:oran_berry", 14, berries));

        // 5. Mining & Ores
        List<ShopItem> ores = List.of(
            new ShopItem("minecraft:iron_ingot", "Iron Ingot", Currency.COINS, BigDecimal.valueOf(100), BigDecimal.valueOf(25), 10),
            new ShopItem("minecraft:gold_ingot", "Gold Ingot", Currency.COINS, BigDecimal.valueOf(250), BigDecimal.valueOf(60), 11),
            new ShopItem("minecraft:diamond", "Diamond", Currency.COINS, BigDecimal.valueOf(1000), BigDecimal.valueOf(250), 12),
            new ShopItem("minecraft:emerald", "Emerald", Currency.COINS, BigDecimal.valueOf(500), BigDecimal.valueOf(125), 13),
            new ShopItem("minecraft:copper_ingot", "Copper Ingot", Currency.COINS, BigDecimal.valueOf(50), BigDecimal.valueOf(10), 14),
            new ShopItem("minecraft:coal", "Coal", Currency.COINS, BigDecimal.valueOf(40), BigDecimal.valueOf(10), 15),
            new ShopItem("minecraft:torch", "Torches (x16)", Currency.COINS, BigDecimal.valueOf(50), BigDecimal.valueOf(10), 16)
        );
        cfg.categories.add(new ShopCategory("mining", "§b§lMinerals & Ores", "minecraft:diamond", 15, ores));

        return cfg;
    }
}

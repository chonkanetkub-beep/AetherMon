package com.aethermon.core.quests.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.quests.model.QuestDefinition;
import com.aethermon.core.quests.model.QuestType;
import com.aethermon.core.rewards.model.RewardItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and manages quest definitions from config/aethermoncore/quests.json.
 */
public class QuestsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private List<QuestDefinition> dailyPool = new ArrayList<>();
    private List<QuestDefinition> weeklyPool = new ArrayList<>();

    public List<QuestDefinition> getDailyPool() { return dailyPool; }
    public List<QuestDefinition> getWeeklyPool() { return weeklyPool; }

    public QuestDefinition getQuest(String id) {
        for (QuestDefinition q : dailyPool) {
            if (q.id().equalsIgnoreCase(id)) return q;
        }
        for (QuestDefinition q : weeklyPool) {
            if (q.id().equalsIgnoreCase(id)) return q;
        }
        return null;
    }

    public void reload() {
        QuestsConfig fresh = load();
        this.dailyPool = fresh.dailyPool;
        this.weeklyPool = fresh.weeklyPool;
    }

    public static QuestsConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("aethermoncore");
        Path file = configDir.resolve("quests.json");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                QuestsConfig config = GSON.fromJson(reader, QuestsConfig.class);
                if (config != null && config.dailyPool != null && !config.dailyPool.isEmpty()) {
                    return config;
                }
            } catch (Exception e) {
                AethermonCore.LOGGER.error("Failed to load quests.json, regenerating defaults: {}", e.getMessage());
            }
        }

        QuestsConfig defaults = createDefaultConfig();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults));
        } catch (IOException e) {
            AethermonCore.LOGGER.error("Failed to save default quests.json: {}", e.getMessage());
        }
        return defaults;
    }

    public static QuestsConfig createDefaultConfig() {
        QuestsConfig cfg = new QuestsConfig();

        // ── Daily Pool ───────────────────────────────────────────────
        List<QuestDefinition> daily = new ArrayList<>();
        daily.add(new QuestDefinition("daily_catch_1", "DAILY", QuestType.CATCH_POKEMON, 3,
            "Wild Catcher I", "Catch 3 wild Pokémon in the wild", "cobblemon:poke_ball",
            BigDecimal.valueOf(1000), BigDecimal.valueOf(2), List.of(new RewardItem("cobblemon:poke_ball", 5, "5x Poké Balls")), List.of()));

        daily.add(new QuestDefinition("daily_catch_2", "DAILY", QuestType.CATCH_POKEMON, 5,
            "Wild Catcher II", "Catch 5 wild Pokémon in the wild", "cobblemon:great_ball",
            BigDecimal.valueOf(1800), BigDecimal.valueOf(5), List.of(new RewardItem("cobblemon:great_ball", 3, "3x Great Balls")), List.of()));

        daily.add(new QuestDefinition("daily_battle_1", "DAILY", QuestType.WIN_BATTLE, 2,
            "Battle Champion I", "Win 2 Pokémon battles against trainers or wild Pokémon", "minecraft:iron_sword",
            BigDecimal.valueOf(1200), BigDecimal.valueOf(2), List.of(new RewardItem("cobblemon:revive", 2, "2x Revives")), List.of()));

        daily.add(new QuestDefinition("daily_battle_2", "DAILY", QuestType.WIN_BATTLE, 3,
            "Battle Champion II", "Win 3 Pokémon battles against trainers or wild Pokémon", "minecraft:diamond_sword",
            BigDecimal.valueOf(2000), BigDecimal.valueOf(5), List.of(new RewardItem("cobblemon:hyper_potion", 2, "2x Hyper Potions")), List.of()));

        daily.add(new QuestDefinition("daily_mine_1", "DAILY", QuestType.MINE_BLOCKS, 48,
            "Resource Gathering I", "Mine 48 blocks of stone, minerals, or logs", "minecraft:iron_pickaxe",
            BigDecimal.valueOf(1000), BigDecimal.ZERO, List.of(new RewardItem("minecraft:iron_ingot", 6, "6x Iron Ingots")), List.of()));

        daily.add(new QuestDefinition("daily_mine_2", "DAILY", QuestType.MINE_BLOCKS, 64,
            "Resource Gathering II", "Mine 64 blocks of stone, minerals, or logs", "minecraft:diamond_pickaxe",
            BigDecimal.valueOf(1500), BigDecimal.valueOf(3), List.of(new RewardItem("minecraft:gold_ingot", 4, "4x Gold Ingots")), List.of()));

        daily.add(new QuestDefinition("daily_walk_1", "DAILY", QuestType.WALK_BLOCKS, 500,
            "Explorer's Trail I", "Travel 500 blocks across the world", "minecraft:compass",
            BigDecimal.valueOf(800), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:oran_berry", 5, "5x Oran Berries")), List.of()));

        daily.add(new QuestDefinition("daily_walk_2", "DAILY", QuestType.WALK_BLOCKS, 1000,
            "Explorer's Trail II", "Travel 1,000 blocks across the world", "minecraft:map",
            BigDecimal.valueOf(1800), BigDecimal.valueOf(5), List.of(new RewardItem("cobblemon:rare_candy", 1, "1x Rare Candy")), List.of()));

        daily.add(new QuestDefinition("daily_sell_1", "DAILY", QuestType.SHOP_SELL, 5,
            "Merchant's Day", "Sell 5 items to the server shop or player market", "minecraft:emerald",
            BigDecimal.valueOf(1500), BigDecimal.valueOf(5), List.of(), List.of()));

        cfg.dailyPool = daily;

        // ── Weekly Pool ──────────────────────────────────────────────
        List<QuestDefinition> weekly = new ArrayList<>();
        weekly.add(new QuestDefinition("weekly_catch_1", "WEEKLY", QuestType.CATCH_POKEMON, 20,
            "Master Collector", "Catch 20 wild Pokémon this week", "cobblemon:ultra_ball",
            BigDecimal.valueOf(10000), BigDecimal.valueOf(25), List.of(new RewardItem("cobblemon:ultra_ball", 5, "5x Ultra Balls"), new RewardItem("cobblemon:rare_candy", 1, "1x Rare Candy")), List.of()));

        weekly.add(new QuestDefinition("weekly_battle_1", "WEEKLY", QuestType.WIN_BATTLE, 15,
            "Elite Competitor", "Win 15 Pokémon battles this week", "minecraft:netherite_sword",
            BigDecimal.valueOf(12000), BigDecimal.valueOf(30), List.of(new RewardItem("cobblemon:max_revive", 3, "3x Max Revives"), new RewardItem("cobblemon:rare_candy", 2, "2x Rare Candies")), List.of()));

        weekly.add(new QuestDefinition("weekly_mine_1", "WEEKLY", QuestType.MINE_BLOCKS, 300,
            "Industrial Excavation", "Mine 300 blocks of any type this week", "minecraft:netherite_pickaxe",
            BigDecimal.valueOf(8000), BigDecimal.valueOf(20), List.of(new RewardItem("minecraft:diamond", 5, "5x Diamonds")), List.of()));

        weekly.add(new QuestDefinition("weekly_walk_1", "WEEKLY", QuestType.WALK_BLOCKS, 5000,
            "Grand Expedition", "Travel 5,000 blocks across the continents", "minecraft:recovery_compass",
            BigDecimal.valueOf(10000), BigDecimal.valueOf(25), List.of(new RewardItem("cobblemon:water_stone", 1, "1x Water Stone")), List.of()));

        weekly.add(new QuestDefinition("weekly_sell_1", "WEEKLY", QuestType.SHOP_SELL, 25,
            "Market Tycoon", "Sell 25 items across shop and auction house", "minecraft:chest",
            BigDecimal.valueOf(15000), BigDecimal.valueOf(35), List.of(new RewardItem("cobblemon:rare_candy", 2, "2x Rare Candies")), List.of()));

        cfg.weeklyPool = weekly;
        return cfg;
    }
}

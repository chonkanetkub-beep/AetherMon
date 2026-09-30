package com.aethermon.core.rewards.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.rewards.model.DailyRewardTier;
import com.aethermon.core.rewards.model.PlaytimeTier;
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
 * Configuration for Login & Playtime Rewards.
 * Stored at config/aethermoncore/rewards.json.
 */
public class RewardsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private int afkThresholdSeconds = 300; // 5 minutes without movement
    private List<DailyRewardTier> dailyRewards = new ArrayList<>();
    private List<PlaytimeTier> playtimeRewards = new ArrayList<>();

    public int getAfkThresholdSeconds() {
        return afkThresholdSeconds > 0 ? afkThresholdSeconds : 300;
    }

    public List<DailyRewardTier> getDailyRewards() {
        return dailyRewards;
    }

    public List<PlaytimeTier> getPlaytimeRewards() {
        return playtimeRewards;
    }

    public DailyRewardTier getDailyTier(int day) {
        for (DailyRewardTier tier : dailyRewards) {
            if (tier.day() == day) return tier;
        }
        return null;
    }

    public PlaytimeTier getPlaytimeTier(String id) {
        for (PlaytimeTier tier : playtimeRewards) {
            if (tier.id().equalsIgnoreCase(id)) return tier;
        }
        return null;
    }

    public void reload() {
        RewardsConfig fresh = load();
        this.afkThresholdSeconds = fresh.afkThresholdSeconds;
        this.dailyRewards = fresh.dailyRewards;
        this.playtimeRewards = fresh.playtimeRewards;
    }

    public static RewardsConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("aethermoncore");
        Path file = configDir.resolve("rewards.json");

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                RewardsConfig config = GSON.fromJson(reader, RewardsConfig.class);
                if (config != null && config.dailyRewards != null && !config.dailyRewards.isEmpty()) {
                    return config;
                }
            } catch (Exception e) {
                AethermonCore.LOGGER.error("Failed to load rewards.json, regenerating defaults: {}", e.getMessage());
            }
        }

        RewardsConfig defaults = createDefaultConfig();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(defaults));
        } catch (IOException e) {
            AethermonCore.LOGGER.error("Failed to save default rewards.json: {}", e.getMessage());
        }
        return defaults;
    }

    public static RewardsConfig createDefaultConfig() {
        RewardsConfig cfg = new RewardsConfig();
        cfg.afkThresholdSeconds = 300;

        // 30 Days of Daily Rewards
        List<DailyRewardTier> days = new ArrayList<>();
        days.add(new DailyRewardTier(1, "Day 1 Starter", "cobblemon:poke_ball", BigDecimal.valueOf(500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:poke_ball", 5, "5x Poké Balls")), List.of()));
        days.add(new DailyRewardTier(2, "Day 2 Medicine", "cobblemon:potion", BigDecimal.valueOf(750), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:potion", 2, "2x Potions")), List.of()));
        days.add(new DailyRewardTier(3, "Day 3 Great Catch", "cobblemon:great_ball", BigDecimal.valueOf(1000), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:great_ball", 3, "3x Great Balls")), List.of()));
        days.add(new DailyRewardTier(4, "Day 4 Healing Boost", "cobblemon:super_potion", BigDecimal.valueOf(1250), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:super_potion", 2, "2x Super Potions")), List.of()));
        days.add(new DailyRewardTier(5, "Day 5 Gem Sparks", "minecraft:diamond", BigDecimal.valueOf(1500), BigDecimal.valueOf(5), List.of(), List.of()));
        days.add(new DailyRewardTier(6, "Day 6 Ultra Gear", "cobblemon:ultra_ball", BigDecimal.valueOf(2000), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:ultra_ball", 2, "2x Ultra Balls")), List.of()));
        days.add(new DailyRewardTier(7, "Day 7 ★ Week 1 Milestone", "cobblemon:rare_candy", BigDecimal.valueOf(5000), BigDecimal.valueOf(15), List.of(new RewardItem("cobblemon:rare_candy", 1, "1x Rare Candy")), List.of()));

        days.add(new DailyRewardTier(8, "Day 8 Berries & Coins", "cobblemon:oran_berry", BigDecimal.valueOf(2500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:oran_berry", 5, "5x Oran Berries")), List.of()));
        days.add(new DailyRewardTier(9, "Day 9 Night Hunter", "cobblemon:dusk_ball", BigDecimal.valueOf(2750), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:dusk_ball", 2, "2x Dusk Balls")), List.of()));
        days.add(new DailyRewardTier(10, "Day 10 Hyper Aid", "cobblemon:hyper_potion", BigDecimal.valueOf(3000), BigDecimal.valueOf(5), List.of(new RewardItem("cobblemon:hyper_potion", 2, "2x Hyper Potions")), List.of()));
        days.add(new DailyRewardTier(11, "Day 11 Quick Catcher", "cobblemon:quick_ball", BigDecimal.valueOf(3250), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:quick_ball", 2, "2x Quick Balls")), List.of()));
        days.add(new DailyRewardTier(12, "Day 12 Revival Pack", "cobblemon:revive", BigDecimal.valueOf(3500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:revive", 2, "2x Revives")), List.of()));
        days.add(new DailyRewardTier(13, "Day 13 Miner's Touch", "minecraft:iron_ingot", BigDecimal.valueOf(4000), BigDecimal.valueOf(5), List.of(new RewardItem("minecraft:iron_ingot", 8, "8x Iron Ingots")), List.of()));
        days.add(new DailyRewardTier(14, "Day 14 ★ Week 2 Milestone", "cobblemon:fire_stone", BigDecimal.valueOf(10000), BigDecimal.valueOf(25), List.of(new RewardItem("cobblemon:fire_stone", 1, "1x Fire Stone")), List.of()));

        days.add(new DailyRewardTier(15, "Day 15 Trainer Stash", "cobblemon:ultra_ball", BigDecimal.valueOf(4500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:ultra_ball", 3, "3x Ultra Balls")), List.of()));
        days.add(new DailyRewardTier(16, "Day 16 Full Restore", "cobblemon:full_restore", BigDecimal.valueOf(5000), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:full_restore", 1, "1x Full Restore")), List.of()));
        days.add(new DailyRewardTier(17, "Day 17 Lum Boost", "cobblemon:lum_berry", BigDecimal.valueOf(5500), BigDecimal.valueOf(5), List.of(new RewardItem("cobblemon:lum_berry", 5, "5x Lum Berries")), List.of()));
        days.add(new DailyRewardTier(18, "Day 18 Timer Specialist", "cobblemon:timer_ball", BigDecimal.valueOf(6000), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:timer_ball", 3, "3x Timer Balls")), List.of()));
        days.add(new DailyRewardTier(19, "Day 19 Max Revive", "cobblemon:max_revive", BigDecimal.valueOf(6500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:max_revive", 1, "1x Max Revive")), List.of()));
        days.add(new DailyRewardTier(20, "Day 20 Golden Riches", "minecraft:gold_ingot", BigDecimal.valueOf(7000), BigDecimal.valueOf(10), List.of(new RewardItem("minecraft:gold_ingot", 8, "8x Gold Ingots")), List.of()));
        days.add(new DailyRewardTier(21, "Day 21 ★ Week 3 Milestone", "cobblemon:rare_candy", BigDecimal.valueOf(15000), BigDecimal.valueOf(35), List.of(new RewardItem("cobblemon:rare_candy", 2, "2x Rare Candies")), List.of()));

        days.add(new DailyRewardTier(22, "Day 22 Elite Balls", "cobblemon:ultra_ball", BigDecimal.valueOf(8000), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:ultra_ball", 5, "5x Ultra Balls")), List.of()));
        days.add(new DailyRewardTier(23, "Day 23 Full Recovery", "cobblemon:full_restore", BigDecimal.valueOf(8500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:full_restore", 2, "2x Full Restores")), List.of()));
        days.add(new DailyRewardTier(24, "Day 24 Water Energy", "cobblemon:water_stone", BigDecimal.valueOf(9000), BigDecimal.valueOf(10), List.of(new RewardItem("cobblemon:water_stone", 1, "1x Water Stone")), List.of()));
        days.add(new DailyRewardTier(25, "Day 25 Diamond Trove", "minecraft:diamond", BigDecimal.valueOf(10000), BigDecimal.valueOf(15), List.of(new RewardItem("minecraft:diamond", 3, "3x Diamonds")), List.of()));
        days.add(new DailyRewardTier(26, "Day 26 Thunder Charge", "cobblemon:thunder_stone", BigDecimal.valueOf(12000), BigDecimal.valueOf(15), List.of(new RewardItem("cobblemon:thunder_stone", 1, "1x Thunder Stone")), List.of()));
        days.add(new DailyRewardTier(27, "Day 27 Sweet Sitrus", "cobblemon:sitrus_berry", BigDecimal.valueOf(14000), BigDecimal.valueOf(15), List.of(new RewardItem("cobblemon:sitrus_berry", 8, "8x Sitrus Berries")), List.of()));
        days.add(new DailyRewardTier(28, "Day 28 ★ Month Finish Line", "cobblemon:rare_candy", BigDecimal.valueOf(20000), BigDecimal.valueOf(40), List.of(new RewardItem("cobblemon:rare_candy", 2, "2x Rare Candies")), List.of()));
        days.add(new DailyRewardTier(29, "Day 29 Eve of Champions", "minecraft:emerald", BigDecimal.valueOf(25000), BigDecimal.valueOf(50), List.of(new RewardItem("minecraft:emerald", 10, "10x Emeralds")), List.of()));
        days.add(new DailyRewardTier(30, "Day 30 ★★★ GRAND MASTER", "cobblemon:master_ball", BigDecimal.valueOf(50000), BigDecimal.valueOf(100), List.of(new RewardItem("cobblemon:master_ball", 1, "1x Master Ball"), new RewardItem("cobblemon:rare_candy", 3, "3x Rare Candies")), List.of()));

        cfg.dailyRewards = days;

        // 4 Daily Playtime Tiers
        List<PlaytimeTier> playtime = new ArrayList<>();
        playtime.add(new PlaytimeTier("15", 15, "15 Min Playtime", "minecraft:iron_ingot", BigDecimal.valueOf(500), BigDecimal.ZERO, List.of(new RewardItem("cobblemon:poke_ball", 2, "2x Poké Balls")), List.of()));
        playtime.add(new PlaytimeTier("30", 30, "30 Min Playtime", "minecraft:gold_ingot", BigDecimal.valueOf(1000), BigDecimal.valueOf(2), List.of(new RewardItem("cobblemon:great_ball", 2, "2x Great Balls"), new RewardItem("cobblemon:revive", 1, "1x Revive")), List.of()));
        playtime.add(new PlaytimeTier("60", 60, "1 Hour Playtime", "minecraft:diamond", BigDecimal.valueOf(2500), BigDecimal.valueOf(5), List.of(new RewardItem("cobblemon:ultra_ball", 2, "2x Ultra Balls")), List.of()));
        playtime.add(new PlaytimeTier("120", 120, "2 Hours Playtime", "minecraft:nether_star", BigDecimal.valueOf(5000), BigDecimal.valueOf(10), List.of(new RewardItem("cobblemon:rare_candy", 1, "1x Rare Candy")), List.of()));

        cfg.playtimeRewards = playtime;
        return cfg;
    }
}

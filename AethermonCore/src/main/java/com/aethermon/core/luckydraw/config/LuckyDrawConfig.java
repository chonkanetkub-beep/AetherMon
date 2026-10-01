package com.aethermon.core.luckydraw.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.luckydraw.model.DrawPool;
import com.aethermon.core.luckydraw.model.DrawPrize;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads Lucky Draw pool definitions from:
 *   config/aethermoncore/luckydraw.json
 *
 * Writes default pools on first run.
 */
public class LuckyDrawConfig {

    /** All configured pools. */
    public List<DrawPool> pools = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static LuckyDrawConfig load() {
        Path path = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("aethermoncore")
                .resolve("luckydraw.json");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                LuckyDrawConfig defaults = buildDefaults();
                Files.writeString(path, GSON.toJson(defaults));
                AethermonCore.LOGGER.info("[LuckyDraw] Wrote default luckydraw.json");
                return defaults;
            }
            LuckyDrawConfig loaded = GSON.fromJson(Files.readString(path), LuckyDrawConfig.class);
            // Validate & sanitise
            if (loaded == null || loaded.pools == null || loaded.pools.isEmpty()) {
                AethermonCore.LOGGER.warn("[LuckyDraw] luckydraw.json has no pools — loading defaults.");
                loaded = buildDefaults();
            }
            return loaded;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load luckydraw.json", e);
        }
    }

    // ── Default pools ──────────────────────────────────────────────────────────

    private static LuckyDrawConfig buildDefaults() {
        LuckyDrawConfig cfg = new LuckyDrawConfig();

        // ── Standard Pool (1 Gem) ──────────────────────────────────────────────
        DrawPool standard = new DrawPool();
        standard.id          = "standard";
        standard.displayName = "§b§lStandard Draw";
        standard.description = new String[]{
            "§7Spin for a chance at Coins,",
            "§7Gems, and rare items!",
            "",
            "§aCost: §b1 Gem 💎"
        };
        standard.costGems    = 1;
        standard.iconItemId  = "minecraft:chest";
        standard.prizes      = new ArrayList<>(List.of(
            DrawPrize.coins("std_coins_sm",  "§6500 Coins",  "minecraft:gold_ingot",   500,  30, new String[]{"§7A small handful of Coins!"}),
            DrawPrize.coins("std_coins_md",  "§61,000 Coins","minecraft:gold_block",  1000,  20, new String[]{"§7A decent pile of Coins!"}),
            DrawPrize.coins("std_coins_lg",  "§65,000 Coins","minecraft:gold_block",  5000,   8, new String[]{"§eA nice stack of Coins!"}),
            DrawPrize.gems ("std_gems_1",    "§b1 Gem",      "minecraft:amethyst_shard",  1, 15, new String[]{"§7A Gem! Back to the pool!"}),
            DrawPrize.gems ("std_gems_3",    "§b3 Gems",     "minecraft:amethyst_shard",  3,  7, new String[]{"§bA few shiny Gems!"}),
            DrawPrize.item ("std_diamond",   "§bDiamond",    "minecraft:diamond",          10, new String[]{"§7A rare mineral!"}),
            DrawPrize.item ("std_nether_star","§dNether Star","minecraft:nether_star",       2, new String[]{"§d§lUltra rare!"}),
            DrawPrize.none ("std_empty",     8)
        ));

        // ── Premium Pool (5 Gems) ──────────────────────────────────────────────
        DrawPool premium = new DrawPool();
        premium.id          = "premium";
        premium.displayName = "§d§lPremium Draw";
        premium.description = new String[]{
            "§7Higher-tier rewards await",
            "§7the bold Aethermon trainer!",
            "",
            "§aCost: §b5 Gems 💎"
        };
        premium.costGems    = 5;
        premium.iconItemId  = "minecraft:ender_chest";
        premium.prizes      = new ArrayList<>(List.of(
            DrawPrize.coins("pre_coins_md",  "§65,000 Coins",  "minecraft:gold_block",   5000, 25, new String[]{"§7A generous pile of Coins!"}),
            DrawPrize.coins("pre_coins_lg",  "§615,000 Coins", "minecraft:gold_block",  15000, 15, new String[]{"§eA huge stack of Coins!"}),
            DrawPrize.coins("pre_coins_xl",  "§650,000 Coins", "minecraft:gold_block",  50000,  5, new String[]{"§6§lJACKPOT Coins!"}),
            DrawPrize.gems ("pre_gems_5",    "§b5 Gems",       "minecraft:amethyst_shard",  5, 20, new String[]{"§bYour Gems returned plus extra!"}),
            DrawPrize.gems ("pre_gems_15",   "§b15 Gems",      "minecraft:amethyst_shard", 15, 10, new String[]{"§b§lA sparkling haul!"}),
            DrawPrize.item ("pre_netherite",  "§cNetherite Ingot","minecraft:netherite_ingot", 15, new String[]{"§cA precious material!"}),
            DrawPrize.item ("pre_totem",      "§eTotem of Undying","minecraft:totem_of_undying", 8, new String[]{"§eWard off death once!"}),
            DrawPrize.item ("pre_nether_star","§dNether Star",  "minecraft:nether_star",          2, new String[]{"§d§lIncredibly rare!"}),
            DrawPrize.none ("pre_empty",      0)  // no duds on premium
        ));

        cfg.pools.add(standard);
        cfg.pools.add(premium);
        return cfg;
    }
}

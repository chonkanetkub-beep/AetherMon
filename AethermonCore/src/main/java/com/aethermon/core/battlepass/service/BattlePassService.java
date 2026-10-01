package com.aethermon.core.battlepass.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.battlepass.config.BattlePassConfig;
import com.aethermon.core.battlepass.model.BattlePassReward;
import com.aethermon.core.battlepass.model.BattlePassTier;
import com.aethermon.core.battlepass.model.PlayerBattlePass;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class BattlePassService {

    private final DatabaseManager db;
    private final EconomyService economy;
    private BattlePassConfig config;

    // Cache: UUID -> PlayerBattlePass
    private final Map<UUID, PlayerBattlePass> cache = new ConcurrentHashMap<>();

    public BattlePassService(DatabaseManager db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
        this.config = BattlePassConfig.load();
    }

    public void reload() {
        this.config = BattlePassConfig.load();
        cache.clear();
    }

    public BattlePassConfig getConfig() {
        return config;
    }

    // ── Data Access ───────────────────────────────────────────────────────────

    public CompletableFuture<PlayerBattlePass> getPlayerPass(UUID uuid) {
        PlayerBattlePass cached = cache.get(uuid);
        if (cached != null && cached.season == config.season) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_battlepass WHERE player_uuid = ? AND season = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, config.season);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlayerBattlePass pass = new PlayerBattlePass();
                        pass.playerUuid = uuid;
                        pass.season = rs.getInt("season");
                        pass.exp = rs.getInt("exp");
                        pass.tier = rs.getInt("tier");
                        pass.isPremium = rs.getInt("is_premium") == 1;
                        pass.claimedFreeTiers = parseTiers(rs.getString("claimed_free_tiers"));
                        pass.claimedPremiumTiers = parseTiers(rs.getString("claimed_premium_tiers"));
                        cache.put(uuid, pass);
                        return pass;
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[BattlePass] Error loading pass for {}: {}", uuid, e.getMessage());
            }

            // Create new record for this season
            PlayerBattlePass pass = new PlayerBattlePass();
            pass.playerUuid = uuid;
            pass.season = config.season;
            pass.exp = 0;
            pass.tier = 1;
            pass.isPremium = false;
            savePlayerPass(pass);
            cache.put(uuid, pass);
            return pass;
        });
    }

    public void savePlayerPass(PlayerBattlePass pass) {
        CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO player_battlepass
                (player_uuid, season, exp, tier, is_premium, claimed_free_tiers, claimed_premium_tiers, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
                ON CONFLICT(player_uuid, season) DO UPDATE SET
                    exp = excluded.exp,
                    tier = excluded.tier,
                    is_premium = excluded.is_premium,
                    claimed_free_tiers = excluded.claimed_free_tiers,
                    claimed_premium_tiers = excluded.claimed_premium_tiers,
                    updated_at = datetime('now')
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, pass.playerUuid.toString());
                ps.setInt(2, pass.season);
                ps.setInt(3, pass.exp);
                ps.setInt(4, pass.tier);
                ps.setInt(5, pass.isPremium ? 1 : 0);
                ps.setString(6, serializeTiers(pass.claimedFreeTiers));
                ps.setString(7, serializeTiers(pass.claimedPremiumTiers));
                ps.executeUpdate();
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[BattlePass] Error saving pass for {}: {}", pass.playerUuid, e.getMessage());
            }
        });
    }

    private Set<Integer> parseTiers(String str) {
        Set<Integer> set = new HashSet<>();
        if (str == null || str.isBlank()) return set;
        for (String part : str.split(",")) {
            try {
                if (!part.isBlank()) set.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return set;
    }

    private String serializeTiers(Set<Integer> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Integer t : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(t);
        }
        return sb.toString();
    }

    // ── Experience & Progression ──────────────────────────────────────────────

    public void addExp(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return;
        getPlayerPass(player.getUuid()).thenAccept(pass -> {
            int oldTier = pass.tier;
            pass.exp += amount;

            // Calculate new tier
            // Each tier requires config.expPerTier (e.g. tier 1 is 0..999, tier 2 is 1000..1999)
            int calculatedTier = Math.min(config.maxTier, 1 + (pass.exp / config.expPerTier));
            pass.tier = calculatedTier;
            savePlayerPass(pass);

            if (player.getServer() != null) {
                player.getServer().execute(() -> {
                    player.sendMessage(Text.literal("§6§l[Battle Pass] §r§e+" + amount + " Pass EXP §7(" + pass.exp + " / " + (pass.tier * config.expPerTier) + ")"), false);

                    if (pass.tier > oldTier) {
                        player.sendMessage(Text.literal("§6§l★ BATTLE PASS LEVEL UP! ★ §r§eYou reached §a§lTier " + pass.tier + "§e! Use §b/bp §eto claim rewards!"), false);
                        player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.8f, 1f);

                        if (pass.tier >= config.maxTier) {
                            String broadcast = "§6§l[Battle Pass] §e" + player.getName().getString()
                                + " §7has reached the MAX TIER (Tier " + config.maxTier + ") of the Battle Pass!";
                            player.getServer().getPlayerManager().broadcast(Text.literal(broadcast), false);
                        }
                    }
                });
            }
        });
    }

    public void setTier(ServerPlayerEntity player, int newTier) {
        int tier = Math.max(1, Math.min(config.maxTier, newTier));
        getPlayerPass(player.getUuid()).thenAccept(pass -> {
            pass.tier = tier;
            pass.exp = (tier - 1) * config.expPerTier;
            savePlayerPass(pass);
            if (player.getServer() != null) {
                player.getServer().execute(() -> {
                    player.sendMessage(Text.literal("§a[Battle Pass] Your Battle Pass tier was set to Tier " + tier + "!"), false);
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 1f);
                });
            }
        });
    }

    // ── Premium Unlock ────────────────────────────────────────────────────────

    public CompletableFuture<Boolean> buyPremium(ServerPlayerEntity player) {
        return getPlayerPass(player.getUuid()).thenCompose(pass -> {
            if (pass.isPremium) {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        player.sendMessage(Text.literal("§cYou already have the Premium Battle Pass for this season!"), false);
                        player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                    });
                }
                return CompletableFuture.completedFuture(false);
            }

            BigDecimal cost = BigDecimal.valueOf(config.premiumCostGems);
            return economy.withdraw(player.getUuid(), Currency.GEMS, cost, "battlepass_premium_season_" + config.season)
                .thenApply(res -> {
                    if (!res.isSuccess()) {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> {
                                player.sendMessage(Text.literal("§cYou need §b" + config.premiumCostGems + " 💎 Gems §cto unlock the Premium Battle Pass!"), false);
                                player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                            });
                        }
                        return false;
                    }

                    pass.isPremium = true;
                    savePlayerPass(pass);

                    if (player.getServer() != null) {
                        player.getServer().execute(() -> {
                            player.sendMessage(Text.literal("§6§l★ PREMIUM BATTLE PASS UNLOCKED! ★"), false);
                            player.sendMessage(Text.literal("§eYou can now claim all premium tier rewards in §b/bp§e!"), false);
                            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1f, 1f);

                            String broadcast = "§6§l[Battle Pass] §e" + player.getName().getString()
                                + " §7has unlocked the §6§lPremium Battle Pass§7!";
                            player.getServer().getPlayerManager().broadcast(Text.literal(broadcast), false);
                        });
                    }
                    return true;
                });
        });
    }

    // ── Reward Claiming ───────────────────────────────────────────────────────

    public boolean claimReward(ServerPlayerEntity player, int tierNumber, boolean isPremiumTrack) {
        PlayerBattlePass pass = cache.get(player.getUuid());
        if (pass == null) return false;

        if (pass.tier < tierNumber) {
            player.sendMessage(Text.literal("§cYou have not reached Tier " + tierNumber + " yet!"), false);
            player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
            return false;
        }

        if (isPremiumTrack && !pass.isPremium) {
            player.sendMessage(Text.literal("§cYou need to unlock the Premium Battle Pass to claim this!"), false);
            player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
            return false;
        }

        if (isPremiumTrack && pass.isPremiumClaimed(tierNumber)) {
            player.sendMessage(Text.literal("§cYou have already claimed this premium reward!"), false);
            return false;
        }
        if (!isPremiumTrack && pass.isFreeClaimed(tierNumber)) {
            player.sendMessage(Text.literal("§cYou have already claimed this reward!"), false);
            return false;
        }

        Optional<BattlePassTier> tierOpt = config.getTier(tierNumber);
        if (tierOpt.isEmpty()) return false;

        BattlePassReward reward = isPremiumTrack ? tierOpt.get().premiumReward : tierOpt.get().freeReward;
        if (reward == null) return false;

        // Deliver reward
        deliverReward(player, reward);

        if (isPremiumTrack) {
            pass.claimedPremiumTiers.add(tierNumber);
        } else {
            pass.claimedFreeTiers.add(tierNumber);
        }
        savePlayerPass(pass);

        player.sendMessage(Text.literal("§a§l[Battle Pass] §eClaimed Tier " + tierNumber + " (" + (isPremiumTrack ? "Premium" : "Free") + "): §f" + reward.displayName), false);
        player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.MASTER, 1f, 1.2f);
        return true;
    }

    public int claimAllAvailable(ServerPlayerEntity player) {
        PlayerBattlePass pass = cache.get(player.getUuid());
        if (pass == null) return 0;

        int claimedCount = 0;
        for (int t = 1; t <= pass.tier; t++) {
            Optional<BattlePassTier> tierOpt = config.getTier(t);
            if (tierOpt.isEmpty()) continue;

            BattlePassTier tier = tierOpt.get();

            // Free track
            if (tier.freeReward != null && !pass.isFreeClaimed(t)) {
                deliverReward(player, tier.freeReward);
                pass.claimedFreeTiers.add(t);
                claimedCount++;
            }

            // Premium track
            if (pass.isPremium && tier.premiumReward != null && !pass.isPremiumClaimed(t)) {
                deliverReward(player, tier.premiumReward);
                pass.claimedPremiumTiers.add(t);
                claimedCount++;
            }
        }

        if (claimedCount > 0) {
            savePlayerPass(pass);
            player.sendMessage(Text.literal("§a§l[Battle Pass] §eSuccessfully claimed §a" + claimedCount + " §eavailable rewards!"), false);
            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.8f, 1.2f);
        } else {
            player.sendMessage(Text.literal("§7No unclaimed rewards available for your current tier."), false);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.MASTER, 1f, 0.8f);
        }

        return claimedCount;
    }

    private void deliverReward(ServerPlayerEntity player, BattlePassReward reward) {
        switch (reward.type) {
            case COINS, GEMS -> {
                Currency currency = reward.asCurrency();
                economy.deposit(player.getUuid(), currency, BigDecimal.valueOf(reward.amount), "battlepass_reward:" + reward.id);
            }
            case ITEM -> {
                ItemStack stack = resolveItem(reward.itemId);
                stack.setCount(Math.max(1, reward.amount));
                if (!player.getInventory().insertStack(stack)) {
                    player.dropItem(stack, false);
                }
            }
            case COMMAND -> {
                if (reward.command != null && !reward.command.isBlank() && player.getServer() != null) {
                    String cmd = reward.command.replace("{player}", player.getName().getString());
                    player.getServer().getCommandManager().executeWithPrefix(
                        player.getServer().getCommandSource(), cmd);
                }
            }
        }
    }

    public ItemStack resolveItem(String itemId) {
        try {
            Identifier id = Identifier.of(itemId);
            var item = Registries.ITEM.getOrEmpty(id);
            if (item.isPresent()) return new ItemStack(item.get());
        } catch (Exception ignored) {}
        return new ItemStack(Items.CHEST);
    }
}

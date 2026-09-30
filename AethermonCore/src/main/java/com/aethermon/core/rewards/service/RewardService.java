package com.aethermon.core.rewards.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.rewards.config.RewardsConfig;
import com.aethermon.core.rewards.model.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
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
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service managing daily login streaks, active playtime tracking,
 * AFK detection, and reward claiming.
 */
public class RewardService {

    private final DatabaseManager db;
    private final EconomyService economy;
    private final RewardsConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Aethermon-Rewards-Tracker");
        t.setDaemon(true);
        return t;
    });

    // In-memory caches for active online players
    private final Map<UUID, PlayerDailyData> dailyCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPlaytimeData> playtimeCache = new ConcurrentHashMap<>();
    private final Map<UUID, PositionRecord> lastPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActiveTimes = new ConcurrentHashMap<>();

    private MinecraftServer server;

    private record PositionRecord(double x, double y, double z, float yaw, float pitch) {}

    public RewardService(DatabaseManager db, EconomyService economy, RewardsConfig config) {
        this.db = db;
        this.economy = economy;
        this.config = config;
    }

    public RewardsConfig getConfig() {
        return config;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        // Schedule playtime tracking every 5 seconds
        scheduler.scheduleAtFixedRate(this::tickPlaytime, 5, 5, TimeUnit.SECONDS);
        // Schedule database periodic flush every 60 seconds
        scheduler.scheduleAtFixedRate(this::flushAllPlaytime, 60, 60, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {}
        flushAllPlaytime();
    }

    public String getTodayDate() {
        return LocalDate.now().toString();
    }

    // ── Player Join / Quit Handlers ──────────────────────────────

    public void onPlayerJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        lastPositions.put(uuid, new PositionRecord(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
        lastActiveTimes.put(uuid, System.currentTimeMillis());

        // Load daily & playtime data async
        CompletableFuture.runAsync(() -> {
            loadDailyData(uuid);
            loadPlaytimeData(uuid);
        });
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        savePlaytimeData(uuid);
        dailyCache.remove(uuid);
        playtimeCache.remove(uuid);
        lastPositions.remove(uuid);
        lastActiveTimes.remove(uuid);
    }

    // ── AFK & Playtime Tick ──────────────────────────────────────

    private void tickPlaytime() {
        if (server == null) return;

        String today = getTodayDate();
        long now = System.currentTimeMillis();
        long afkThresholdMillis = config.getAfkThresholdSeconds() * 1000L;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            PositionRecord prevPos = lastPositions.get(uuid);
            PositionRecord currentPos = new PositionRecord(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());

            if (prevPos != null) {
                double dx = Math.abs(currentPos.x - prevPos.x);
                double dy = Math.abs(currentPos.y - prevPos.y);
                double dz = Math.abs(currentPos.z - prevPos.z);
                float dYaw = Math.abs(currentPos.yaw - prevPos.yaw);
                float dPitch = Math.abs(currentPos.pitch - prevPos.pitch);

                if (dx > 0.05 || dy > 0.05 || dz > 0.05 || dYaw > 1.0f || dPitch > 1.0f) {
                    lastActiveTimes.put(uuid, now);
                }
            }
            lastPositions.put(uuid, currentPos);

            long lastActive = lastActiveTimes.getOrDefault(uuid, now);
            boolean isAfk = (now - lastActive) >= afkThresholdMillis;

            if (!isAfk) {
                PlayerPlaytimeData data = getOrLoadPlaytimeDataSync(uuid);
                // Check if date changed (midnight reset)
                if (!today.equals(data.getTrackingDate())) {
                    data.setTrackingDate(today);
                    data.setActiveSeconds(0);
                    data.clearClaimedTiers();
                }
                data.addActiveSeconds(5);
            }
        }
    }

    public boolean isPlayerAfk(UUID uuid) {
        long lastActive = lastActiveTimes.getOrDefault(uuid, System.currentTimeMillis());
        return (System.currentTimeMillis() - lastActive) >= (config.getAfkThresholdSeconds() * 1000L);
    }

    // ── Daily Reward Claims ──────────────────────────────────────

    public PlayerDailyData getDailyData(UUID uuid) {
        return dailyCache.computeIfAbsent(uuid, this::loadDailyData);
    }

    public CompletableFuture<ClaimResult> claimDailyReward(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        String today = getTodayDate();

        return CompletableFuture.supplyAsync(() -> {
            PlayerDailyData data = getDailyData(uuid);
            if (data.hasClaimedToday(today)) {
                return new ClaimResult(false, "§cYou have already claimed today's login reward! Come back tomorrow.", null);
            }

            // Streak freezing: increment streak from last value without resetting
            int nextStreak = (data.getCurrentStreak() % 30) + 1;
            DailyRewardTier tier = config.getDailyTier(nextStreak);
            if (tier == null) {
                tier = new DailyRewardTier(nextStreak, "Day " + nextStreak, "minecraft:chest", BigDecimal.valueOf(1000), BigDecimal.ZERO, List.of(), List.of());
            }

            data.setCurrentStreak(nextStreak);
            data.setLastClaimDate(today);
            data.setTotalClaims(data.getTotalClaims() + 1);
            saveDailyData(data);

            // Deliver rewards on server thread
            final DailyRewardTier finalTier = tier;
            if (server != null) {
                server.execute(() -> deliverDailyRewards(player, finalTier));
            }

            return new ClaimResult(true, "§aSuccessfully claimed Daily Reward for Day " + nextStreak + "!", finalTier);
        });
    }

    private void deliverDailyRewards(ServerPlayerEntity player, DailyRewardTier tier) {
        UUID uuid = player.getUuid();

        // 1. Coins
        if (tier.coins() != null && tier.coins().compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(uuid, Currency.COINS, tier.coins(), "Daily Reward Day " + tier.day());
            player.sendMessage(Text.literal(" §6+ " + Currency.COINS.format(tier.coins()) + " Coins"));
        }

        // 2. Gems
        if (tier.gems() != null && tier.gems().compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(uuid, Currency.GEMS, tier.gems(), "Daily Reward Day " + tier.day());
            player.sendMessage(Text.literal(" §b+ " + Currency.GEMS.format(tier.gems()) + " Gems"));
        }

        // 3. Items
        for (RewardItem itemDef : tier.items()) {
            giveRewardItem(player, itemDef);
        }

        // 4. Commands
        for (String cmd : tier.commands()) {
            executeRewardCommand(player, cmd);
        }

        // Sound effect
        player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // ── Playtime Reward Claims ───────────────────────────────────

    public PlayerPlaytimeData getPlaytimeData(UUID uuid) {
        return playtimeCache.computeIfAbsent(uuid, this::loadPlaytimeData);
    }

    public CompletableFuture<ClaimResult> claimPlaytimeReward(ServerPlayerEntity player, String tierId) {
        UUID uuid = player.getUuid();
        String today = getTodayDate();

        return CompletableFuture.supplyAsync(() -> {
            PlayerPlaytimeData data = getPlaytimeData(uuid);
            if (!today.equals(data.getTrackingDate())) {
                data.setTrackingDate(today);
                data.setActiveSeconds(0);
                data.clearClaimedTiers();
            }

            PlaytimeTier tier = config.getPlaytimeTier(tierId);
            if (tier == null) {
                return new ClaimResult(false, "§cUnknown playtime reward tier: " + tierId, null);
            }

            if (data.isTierClaimed(tierId)) {
                return new ClaimResult(false, "§cYou have already claimed the " + tier.title() + " reward today!", null);
            }

            int playedMinutes = data.getActiveSeconds() / 60;
            if (playedMinutes < tier.requiredMinutes()) {
                int minsLeft = tier.requiredMinutes() - playedMinutes;
                return new ClaimResult(false, "§cYou need " + minsLeft + " more minutes of active playtime to claim this reward!", null);
            }

            data.markTierClaimed(tierId);
            savePlaytimeData(uuid);

            if (server != null) {
                server.execute(() -> deliverPlaytimeRewards(player, tier));
            }

            return new ClaimResult(true, "§aSuccessfully claimed playtime reward: " + tier.title() + "!", tier);
        });
    }

    private void deliverPlaytimeRewards(ServerPlayerEntity player, PlaytimeTier tier) {
        UUID uuid = player.getUuid();

        // 1. Coins
        if (tier.coins() != null && tier.coins().compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(uuid, Currency.COINS, tier.coins(), "Playtime Reward " + tier.title());
            player.sendMessage(Text.literal(" §6+ " + Currency.COINS.format(tier.coins()) + " Coins"));
        }

        // 2. Gems
        if (tier.gems() != null && tier.gems().compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(uuid, Currency.GEMS, tier.gems(), "Playtime Reward " + tier.title());
            player.sendMessage(Text.literal(" §b+ " + Currency.GEMS.format(tier.gems()) + " Gems"));
        }

        // 3. Items
        for (RewardItem itemDef : tier.items()) {
            giveRewardItem(player, itemDef);
        }

        // 4. Commands
        for (String cmd : tier.commands()) {
            executeRewardCommand(player, cmd);
        }

        // Sound effect
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    // ── Delivery Helpers ─────────────────────────────────────────

    private void giveRewardItem(ServerPlayerEntity player, RewardItem itemDef) {
        Item item = Registries.ITEM.get(Identifier.tryParse(itemDef.itemId()));
        if (item == null || item == Items.AIR) {
            AethermonCore.LOGGER.warn("[Rewards] Could not resolve item: {}", itemDef.itemId());
            return;
        }

        ItemStack stack = new ItemStack(item, itemDef.count());
        if (itemDef.displayName() != null && !itemDef.displayName().isBlank()) {
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(itemDef.displayName()));
        }

        player.getInventory().offerOrDrop(stack);
        String name = itemDef.displayName() != null ? itemDef.displayName() : (itemDef.count() + "x " + stack.getName().getString());
        player.sendMessage(Text.literal(" §e+ " + name));
    }

    private void executeRewardCommand(ServerPlayerEntity player, String cmd) {
        if (server == null || cmd == null || cmd.isBlank()) return;
        String formatted = cmd.replace("%player%", player.getName().getString());
        try {
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), formatted);
        } catch (Exception e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to execute reward command '{}': {}", formatted, e.getMessage());
        }
    }

    // ── SQLite Persistence ───────────────────────────────────────

    private PlayerDailyData loadDailyData(UUID uuid) {
        String sql = "SELECT last_claim_date, current_streak, total_claims FROM player_daily_rewards WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerDailyData(
                        uuid,
                        rs.getString("last_claim_date"),
                        rs.getInt("current_streak"),
                        rs.getInt("total_claims")
                    );
                }
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to load daily data for {}: {}", uuid, e.getMessage());
        }
        return new PlayerDailyData(uuid, "", 0, 0);
    }

    private void saveDailyData(PlayerDailyData data) {
        String sql = """
            INSERT INTO player_daily_rewards (player_uuid, last_claim_date, current_streak, total_claims, updated_at)
            VALUES (?, ?, ?, ?, datetime('now'))
            ON CONFLICT(player_uuid) DO UPDATE SET
                last_claim_date = excluded.last_claim_date,
                current_streak = excluded.current_streak,
                total_claims = excluded.total_claims,
                updated_at = datetime('now')
            """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getPlayerUuid().toString());
            ps.setString(2, data.getLastClaimDate());
            ps.setInt(3, data.getCurrentStreak());
            ps.setInt(4, data.getTotalClaims());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to save daily data for {}: {}", data.getPlayerUuid(), e.getMessage());
        }
    }

    private PlayerPlaytimeData loadPlaytimeData(UUID uuid) {
        String sql = "SELECT tracking_date, active_seconds, claimed_tiers, total_playtime_secs FROM player_playtime WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String date = rs.getString("tracking_date");
                    int secs = rs.getInt("active_seconds");
                    Set<String> claimed = PlayerPlaytimeData.deserializeClaimedTiers(rs.getString("claimed_tiers"));
                    int totalSecs = rs.getInt("total_playtime_secs");
                    return new PlayerPlaytimeData(uuid, date, secs, claimed, totalSecs);
                }
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to load playtime data for {}: {}", uuid, e.getMessage());
        }
        return new PlayerPlaytimeData(uuid, getTodayDate(), 0, Set.of(), 0);
    }

    private PlayerPlaytimeData getOrLoadPlaytimeDataSync(UUID uuid) {
        return playtimeCache.computeIfAbsent(uuid, this::loadPlaytimeData);
    }

    private void savePlaytimeData(UUID uuid) {
        PlayerPlaytimeData data = playtimeCache.get(uuid);
        if (data == null) return;

        String sql = """
            INSERT INTO player_playtime (player_uuid, tracking_date, active_seconds, claimed_tiers, total_playtime_secs, updated_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(player_uuid) DO UPDATE SET
                tracking_date = excluded.tracking_date,
                active_seconds = excluded.active_seconds,
                claimed_tiers = excluded.claimed_tiers,
                total_playtime_secs = excluded.total_playtime_secs,
                updated_at = datetime('now')
            """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getPlayerUuid().toString());
            ps.setString(2, data.getTrackingDate());
            ps.setInt(3, data.getActiveSeconds());
            ps.setString(4, data.serializeClaimedTiers());
            ps.setInt(5, data.getTotalPlaytimeSecs());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to save playtime data for {}: {}", uuid, e.getMessage());
        }
    }

    private void flushAllPlaytime() {
        for (UUID uuid : playtimeCache.keySet()) {
            savePlaytimeData(uuid);
        }
    }

    public void resetDaily(UUID uuid) {
        dailyCache.remove(uuid);
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM player_daily_rewards WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to reset daily for {}: {}", uuid, e.getMessage());
        }
    }

    public void resetPlaytime(UUID uuid) {
        playtimeCache.remove(uuid);
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM player_playtime WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Rewards] Failed to reset playtime for {}: {}", uuid, e.getMessage());
        }
    }

    public record ClaimResult(boolean success, String message, Object tier) {}
}

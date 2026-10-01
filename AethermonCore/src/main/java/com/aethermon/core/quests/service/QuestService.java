package com.aethermon.core.quests.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.quests.config.QuestsConfig;
import com.aethermon.core.quests.model.PlayerQuestInstance;
import com.aethermon.core.quests.model.QuestDefinition;
import com.aethermon.core.quests.model.QuestType;
import com.aethermon.core.rewards.model.RewardItem;
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
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing daily & weekly quest assignment, progress tracking, rerolls, and reward delivery.
 */
public class QuestService {

    private final DatabaseManager db;
    private final EconomyService economy;
    private final QuestsConfig config;
    private MinecraftServer server;

    // Cache of active quests: UUID -> List of active quests (daily + weekly)
    private final Map<UUID, List<PlayerQuestInstance>> activeQuestsCache = new ConcurrentHashMap<>();

    public QuestService(DatabaseManager db, EconomyService economy, QuestsConfig config) {
        this.db = db;
        this.economy = economy;
        this.config = config;
    }

    public void init(MinecraftServer server) {
        this.server = server;
    }

    public QuestsConfig getConfig() {
        return config;
    }

    public String getTodayKey() {
        return LocalDate.now().toString();
    }

    public String getWeeklyKey() {
        LocalDate now = LocalDate.now();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int week = now.get(weekFields.weekOfWeekBasedYear());
        int year = now.get(weekFields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    // ── Player Join / Quit ────────────────────────────────────────

    public void onPlayerJoin(ServerPlayerEntity player) {
        CompletableFuture.runAsync(() -> {
            loadAllPlayerQuests(player.getUuid());
        });
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        activeQuestsCache.remove(player.getUuid());
    }

    // ── Quest Retrieval & Assignment ─────────────────────────────

    public List<PlayerQuestInstance> getDailyQuests(UUID uuid) {
        String today = getTodayKey();
        List<PlayerQuestInstance> all = getOrLoadQuests(uuid);
        List<PlayerQuestInstance> daily = new ArrayList<>();
        for (PlayerQuestInstance q : all) {
            if ("DAILY".equals(q.getPeriodType()) && today.equals(q.getPeriodKey())) {
                daily.add(q);
            }
        }

        if (daily.size() < 3) {
            daily = assignNewQuests(uuid, "DAILY", today, 3, config.getDailyPool());
            refreshCache(uuid);
        }
        return daily;
    }

    public List<PlayerQuestInstance> getWeeklyQuests(UUID uuid) {
        String week = getWeeklyKey();
        List<PlayerQuestInstance> all = getOrLoadQuests(uuid);
        List<PlayerQuestInstance> weekly = new ArrayList<>();
        for (PlayerQuestInstance q : all) {
            if ("WEEKLY".equals(q.getPeriodType()) && week.equals(q.getPeriodKey())) {
                weekly.add(q);
            }
        }

        if (weekly.size() < 3) {
            weekly = assignNewQuests(uuid, "WEEKLY", week, 3, config.getWeeklyPool());
            refreshCache(uuid);
        }
        return weekly;
    }

    private List<PlayerQuestInstance> assignNewQuests(UUID uuid, String periodType, String periodKey, int count, List<QuestDefinition> pool) {
        List<QuestDefinition> available = new ArrayList<>(pool);
        Collections.shuffle(available);

        List<PlayerQuestInstance> assigned = new ArrayList<>();
        int toAssign = Math.min(count, available.size());

        for (int slot = 0; slot < toAssign; slot++) {
            QuestDefinition def = available.get(slot);
            PlayerQuestInstance instance = new PlayerQuestInstance(
                0, uuid, periodType, periodKey, def.id(), slot, 0, def.target(), false, false, def
            );
            saveNewQuest(instance);
            assigned.add(instance);
        }
        return assigned;
    }

    // ── Progress Tracking ────────────────────────────────────────

    public void addProgress(ServerPlayerEntity player, QuestType type, int amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUuid();
        String today = getTodayKey();
        String week = getWeeklyKey();

        List<PlayerQuestInstance> quests = getOrLoadQuests(uuid);
        for (PlayerQuestInstance quest : quests) {
            if (quest.isCompleted()) continue;

            // Ensure quest belongs to current period
            if ("DAILY".equals(quest.getPeriodType()) && !today.equals(quest.getPeriodKey())) continue;
            if ("WEEKLY".equals(quest.getPeriodType()) && !week.equals(quest.getPeriodKey())) continue;

            QuestDefinition def = quest.getDefinition();
            if (def != null && def.type() == type) {
                int newProgress = Math.min(quest.getTargetAmount(), quest.getCurrentProgress() + amount);
                quest.setCurrentProgress(newProgress);

                if (newProgress >= quest.getTargetAmount()) {
                    quest.setCompleted(true);
                    onQuestComplete(player, quest);
                }
                saveQuestProgressAsync(quest);
            }
        }
    }

    private void onQuestComplete(ServerPlayerEntity player, PlayerQuestInstance quest) {
        QuestDefinition def = quest.getDefinition();
        String title = def != null ? def.title() : quest.getQuestId();

        player.sendMessage(Text.literal("§8§m───────────────────────────────"));
        player.sendMessage(Text.literal(" §a§l✔ QUEST COMPLETED!"));
        player.sendMessage(Text.literal("  §f" + title));
        player.sendMessage(Text.literal("  §eType §6/quests §eto claim your reward!"));
        player.sendMessage(Text.literal("§8§m───────────────────────────────"));

        player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    // ── Claiming Rewards ─────────────────────────────────────────

    public CompletableFuture<ClaimResult> claimQuest(ServerPlayerEntity player, PlayerQuestInstance quest) {
        return CompletableFuture.supplyAsync(() -> {
            if (!quest.isCompleted()) {
                return new ClaimResult(false, "§cThis quest is not completed yet!", null);
            }
            if (quest.isClaimed()) {
                return new ClaimResult(false, "§cYou have already claimed this quest reward!", null);
            }

            quest.setClaimed(true);
            saveQuestClaimed(quest);

            QuestDefinition def = quest.getDefinition();
            if (def != null && server != null) {
                server.execute(() -> deliverQuestRewards(player, def));
            }

            return new ClaimResult(true, "§aSuccessfully claimed rewards for: " + (def != null ? def.title() : quest.getQuestId()), def);
        });
    }

    private void deliverQuestRewards(ServerPlayerEntity player, QuestDefinition def) {
        UUID uuid = player.getUuid();

        // 1. Coins
        if (def.coins().compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(uuid, Currency.COINS, def.coins(), "Quest Reward: " + def.title());
            player.sendMessage(Text.literal(" §6+ " + Currency.COINS.format(def.coins()) + " Coins"));
        }

        // 2. Gems
        if (def.gems().compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(uuid, Currency.GEMS, def.gems(), "Quest Reward: " + def.title());
            player.sendMessage(Text.literal(" §b+ " + Currency.GEMS.format(def.gems()) + " Gems"));
        }

        // 3. Items
        for (RewardItem itemDef : def.items()) {
            Item item = Registries.ITEM.get(Identifier.tryParse(itemDef.itemId()));
            if (item != null && item != Items.AIR) {
                ItemStack stack = new ItemStack(item, itemDef.count());
                if (itemDef.displayName() != null) {
                    stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(itemDef.displayName()));
                }
                player.getInventory().offerOrDrop(stack);
                player.sendMessage(Text.literal(" §e+ " + (itemDef.displayName() != null ? itemDef.displayName() : (itemDef.count() + "x " + stack.getName().getString()))));
            }
        }

        // 4. Commands
        for (String cmd : def.commands()) {
            String formatted = cmd.replace("%player%", player.getName().getString());
            try {
                server.getCommandManager().executeWithPrefix(server.getCommandSource(), formatted);
            } catch (Exception e) {
                AethermonCore.LOGGER.error("[Quests] Failed to execute reward command '{}': {}", formatted, e.getMessage());
            }
        }

        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    // ── Rerolls ──────────────────────────────────────────────────

    public boolean hasFreeReroll(UUID uuid) {
        String today = getTodayKey();
        String sql = "SELECT reroll_date, rerolls_used FROM player_quest_meta WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String date = rs.getString("reroll_date");
                    int used = rs.getInt("rerolls_used");
                    if (today.equals(date)) {
                        return used < 1;
                    }
                }
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to check rerolls for {}: {}", uuid, e.getMessage());
        }
        return true; // No record today means free reroll is available
    }

    public CompletableFuture<ClaimResult> rerollDailyQuest(ServerPlayerEntity player, int slotIndex) {
        UUID uuid = player.getUuid();
        String today = getTodayKey();

        return CompletableFuture.supplyAsync(() -> {
            if (!hasFreeReroll(uuid)) {
                return new ClaimResult(false, "§cYou have already used your free daily quest reroll today!", null);
            }

            List<PlayerQuestInstance> dailyQuests = getDailyQuests(uuid);
            PlayerQuestInstance target = null;
            for (PlayerQuestInstance q : dailyQuests) {
                if (q.getSlotIndex() == slotIndex) {
                    target = q;
                    break;
                }
            }

            if (target == null) {
                return new ClaimResult(false, "§cQuest not found in slot " + (slotIndex + 1), null);
            }

            if (target.isCompleted()) {
                return new ClaimResult(false, "§cYou cannot reroll an already completed quest!", null);
            }

            // Find a quest from daily pool not already assigned
            Set<String> activeIds = new HashSet<>();
            for (PlayerQuestInstance q : dailyQuests) activeIds.add(q.getQuestId());

            List<QuestDefinition> candidates = new ArrayList<>();
            for (QuestDefinition def : config.getDailyPool()) {
                if (!activeIds.contains(def.id())) {
                    candidates.add(def);
                }
            }

            if (candidates.isEmpty()) {
                return new ClaimResult(false, "§cNo alternative quests available to reroll into!", null);
            }

            Collections.shuffle(candidates);
            QuestDefinition newDef = candidates.get(0);

            // Replace in DB
            replaceQuestInDb(target, newDef);
            target.setCurrentProgress(0);
            target.setCompleted(false);
            target.setClaimed(false);
            target.setDefinition(newDef);

            // Record reroll used
            recordRerollUsed(uuid, today);
            refreshCache(uuid);

            return new ClaimResult(true, "§aQuest rerolled! New quest: §f" + newDef.title(), newDef);
        });
    }

    private void recordRerollUsed(UUID uuid, String date) {
        String sql = """
            INSERT INTO player_quest_meta (player_uuid, reroll_date, rerolls_used, updated_at)
            VALUES (?, ?, 1, datetime('now'))
            ON CONFLICT(player_uuid) DO UPDATE SET
                reroll_date = excluded.reroll_date,
                rerolls_used = player_quest_meta.rerolls_used + 1,
                updated_at = datetime('now')
            """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, date);
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to record reroll for {}: {}", uuid, e.getMessage());
        }
    }

    // ── SQLite Persistence ───────────────────────────────────────

    private List<PlayerQuestInstance> getOrLoadQuests(UUID uuid) {
        return activeQuestsCache.computeIfAbsent(uuid, this::loadAllPlayerQuests);
    }

    private void refreshCache(UUID uuid) {
        activeQuestsCache.put(uuid, loadAllPlayerQuests(uuid));
    }

    private List<PlayerQuestInstance> loadAllPlayerQuests(UUID uuid) {
        List<PlayerQuestInstance> list = new ArrayList<>();
        String sql = "SELECT id, period_type, period_key, quest_id, slot_index, current_progress, target_amount, completed, claimed FROM player_quests WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String qId = rs.getString("quest_id");
                    QuestDefinition def = config.getQuest(qId);
                    PlayerQuestInstance inst = new PlayerQuestInstance(
                        rs.getLong("id"),
                        uuid,
                        rs.getString("period_type"),
                        rs.getString("period_key"),
                        qId,
                        rs.getInt("slot_index"),
                        rs.getInt("current_progress"),
                        rs.getInt("target_amount"),
                        rs.getInt("completed") == 1,
                        rs.getInt("claimed") == 1,
                        def
                    );
                    list.add(inst);
                }
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to load quests for {}: {}", uuid, e.getMessage());
        }
        return list;
    }

    private void saveNewQuest(PlayerQuestInstance q) {
        String sql = """
            INSERT INTO player_quests (player_uuid, period_type, period_key, quest_id, slot_index, current_progress, target_amount, completed, claimed, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, q.getPlayerUuid().toString());
            ps.setString(2, q.getPeriodType());
            ps.setString(3, q.getPeriodKey());
            ps.setString(4, q.getQuestId());
            ps.setInt(5, q.getSlotIndex());
            ps.setInt(6, q.getCurrentProgress());
            ps.setInt(7, q.getTargetAmount());
            ps.setInt(8, q.isCompleted() ? 1 : 0);
            ps.setInt(9, q.isClaimed() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    q.setDbId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to save new quest for {}: {}", q.getPlayerUuid(), e.getMessage());
        }
    }

    private void saveQuestProgressAsync(PlayerQuestInstance q) {
        CompletableFuture.runAsync(() -> {
            String sql = "UPDATE player_quests SET current_progress = ?, completed = ? WHERE id = ?";
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, q.getCurrentProgress());
                ps.setInt(2, q.isCompleted() ? 1 : 0);
                ps.setLong(3, q.getDbId());
                ps.executeUpdate();
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Quests] Failed to update progress for quest id {}: {}", q.getDbId(), e.getMessage());
            }
        });
    }

    private void saveQuestClaimed(PlayerQuestInstance q) {
        String sql = "UPDATE player_quests SET claimed = 1 WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, q.getDbId());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to update claimed for quest id {}: {}", q.getDbId(), e.getMessage());
        }
    }

    private void replaceQuestInDb(PlayerQuestInstance oldQuest, QuestDefinition newDef) {
        String sql = "UPDATE player_quests SET quest_id = ?, current_progress = 0, target_amount = ?, completed = 0, claimed = 0 WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newDef.id());
            ps.setInt(2, newDef.target());
            ps.setLong(3, oldQuest.getDbId());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to replace quest id {}: {}", oldQuest.getDbId(), e.getMessage());
        }
    }

    public void resetPlayerQuests(UUID uuid) {
        activeQuestsCache.remove(uuid);
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM player_quests WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to delete quests for {}: {}", uuid, e.getMessage());
        }
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM player_quest_meta WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Quests] Failed to delete quest meta for {}: {}", uuid, e.getMessage());
        }
    }

    public record ClaimResult(boolean success, String message, QuestDefinition def) {}
}

package com.aethermon.core.crates.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.crates.config.CratesConfig;
import com.aethermon.core.crates.model.Crate;
import com.aethermon.core.crates.model.CrateReward;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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

public class CrateService {

    private final DatabaseManager db;
    private final EconomyService economy;
    private CratesConfig config;

    // Cache: "world:x:y:z" -> crateId
    private final Map<String, String> crateBlocks = new ConcurrentHashMap<>();

    public CrateService(DatabaseManager db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
        this.config = CratesConfig.load();
        loadCrateBlocks();
    }

    public void reload() {
        this.config = CratesConfig.load();
        loadCrateBlocks();
    }

    public CratesConfig getConfig() {
        return config;
    }

    public List<Crate> getCrates() {
        return config.crates;
    }

    public Optional<Crate> getCrate(String crateId) {
        return config.getCrate(crateId);
    }

    // ── Physical Keys ─────────────────────────────────────────────────────────

    public ItemStack createPhysicalKey(Crate crate, int amount) {
        ItemStack key = resolveItem(crate.keyItemId);
        key.setCount(Math.max(1, amount));
        key.set(DataComponentTypes.CUSTOM_NAME, Text.literal(crate.keyDisplayName));

        List<Text> lore = new ArrayList<>();
        if (crate.keyLore != null) {
            for (String line : crate.keyLore) lore.add(Text.literal(line));
        }
        lore.add(Text.literal("§8[CrateKey:" + crate.id + "]"));
        key.set(DataComponentTypes.LORE, new LoreComponent(lore));
        key.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        NbtCompound tag = new NbtCompound();
        tag.putString("aethermon_crate_key", crate.id);
        key.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));

        return key;
    }

    public boolean isPhysicalKey(ItemStack stack, String crateId) {
        if (stack == null || stack.isEmpty()) return false;

        // 1. Check custom NBT tag
        NbtComponent nbtComp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbtComp != null) {
            NbtCompound tag = nbtComp.copyNbt();
            if (tag.contains("aethermon_crate_key")) {
                return tag.getString("aethermon_crate_key").equalsIgnoreCase(crateId);
            }
        }

        // 2. Fallback check lore tag
        LoreComponent loreComp = stack.get(DataComponentTypes.LORE);
        if (loreComp != null) {
            String marker = "[CrateKey:" + crateId + "]";
            for (Text line : loreComp.lines()) {
                if (line.getString().contains(marker)) return true;
            }
        }

        return false;
    }

    public int countPhysicalKeys(ServerPlayerEntity player, Crate crate) {
        int count = 0;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (isPhysicalKey(stack, crate.id)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean consumePhysicalKey(ServerPlayerEntity player, Crate crate) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (isPhysicalKey(stack, crate.id)) {
                stack.decrement(1);
                if (stack.isEmpty()) {
                    inv.setStack(i, ItemStack.EMPTY);
                }
                player.playerScreenHandler.sendContentUpdates();
                return true;
            }
        }
        return false;
    }

    // ── Virtual Keys ──────────────────────────────────────────────────────────

    public CompletableFuture<Integer> getVirtualKeys(UUID playerUuid, String crateId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT amount FROM player_crate_keys WHERE player_uuid = ? AND crate_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, crateId.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("amount");
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Crates] Error reading virtual keys for {}: {}", playerUuid, e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Void> addVirtualKeys(UUID playerUuid, String crateId, int amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO player_crate_keys (player_uuid, crate_id, amount, updated_at)
                VALUES (?, ?, ?, datetime('now'))
                ON CONFLICT(player_uuid, crate_id) DO UPDATE SET
                    amount = amount + excluded.amount,
                    updated_at = datetime('now')
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, crateId.toLowerCase());
                ps.setInt(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Crates] Error adding virtual keys for {}: {}", playerUuid, e.getMessage());
            }
        });
    }

    public CompletableFuture<Boolean> consumeVirtualKey(UUID playerUuid, String crateId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE player_crate_keys
                SET amount = amount - 1, updated_at = datetime('now')
                WHERE player_uuid = ? AND crate_id = ? AND amount > 0
                """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, crateId.toLowerCase());
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Crates] Error consuming virtual key for {}: {}", playerUuid, e.getMessage());
                return false;
            }
        });
    }

    // ── Unified Key Handling ──────────────────────────────────────────────────

    public CompletableFuture<Boolean> consumeKey(ServerPlayerEntity player, Crate crate) {
        // Physical key preferred first
        if (consumePhysicalKey(player, crate)) {
            return CompletableFuture.completedFuture(true);
        }
        // Fallback to virtual key
        return consumeVirtualKey(player.getUuid(), crate.id);
    }

    // ── Crate Blocks ──────────────────────────────────────────────────────────

    private void loadCrateBlocks() {
        crateBlocks.clear();
        String sql = "SELECT world, x, y, z, crate_id FROM crate_blocks";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("world") + ":" + rs.getInt("x") + ":" + rs.getInt("y") + ":" + rs.getInt("z");
                crateBlocks.put(key, rs.getString("crate_id"));
            }
            AethermonCore.LOGGER.info("[Crates] Loaded {} physical crate blocks.", crateBlocks.size());
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Crates] Error loading crate blocks: {}", e.getMessage());
        }
    }

    public Optional<Crate> getCrateAt(String world, int x, int y, int z) {
        String key = world + ":" + x + ":" + y + ":" + z;
        String crateId = crateBlocks.get(key);
        if (crateId == null) return Optional.empty();
        return getCrate(crateId);
    }

    public CompletableFuture<Void> setCrateBlock(String world, int x, int y, int z, String crateId) {
        String key = world + ":" + x + ":" + y + ":" + z;
        crateBlocks.put(key, crateId);
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO crate_blocks (world, x, y, z, crate_id) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setString(5, crateId);
                ps.executeUpdate();
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Crates] Error saving crate block: {}", e.getMessage());
            }
        });
    }

    public CompletableFuture<Boolean> removeCrateBlock(String world, int x, int y, int z) {
        String key = world + ":" + x + ":" + y + ":" + z;
        crateBlocks.remove(key);
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM crate_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Crates] Error removing crate block: {}", e.getMessage());
                return false;
            }
        });
    }

    // ── Reward Delivery ───────────────────────────────────────────────────────

    public CompletableFuture<Void> awardReward(ServerPlayerEntity player, Crate crate, CrateReward reward) {
        return switch (reward.type) {
            case COINS, GEMS -> {
                Currency currency = reward.asCurrency();
                yield economy.deposit(player.getUuid(), currency,
                        BigDecimal.valueOf(reward.amount), "crate_reward:" + crate.id + ":" + reward.id)
                    .thenAccept(res -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> notifyWin(player, crate, reward));
                        }
                    });
            }
            case ITEM -> {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        ItemStack stack = resolveItem(reward.itemId);
                        stack.setCount(Math.max(1, reward.amount));
                        if (!player.getInventory().insertStack(stack.copy())) {
                            player.dropItem(stack, false);
                        }
                        notifyWin(player, crate, reward);
                    });
                }
                yield CompletableFuture.completedFuture(null);
            }
            case COMMAND -> {
                if (player.getServer() != null && reward.command != null && !reward.command.isBlank()) {
                    player.getServer().execute(() -> {
                        String cmd = reward.command.replace("{player}", player.getName().getString());
                        player.getServer().getCommandManager().executeWithPrefix(
                            player.getServer().getCommandSource(), cmd);
                        notifyWin(player, crate, reward);
                    });
                }
                yield CompletableFuture.completedFuture(null);
            }
        };
    }

    private void notifyWin(ServerPlayerEntity player, Crate crate, CrateReward reward) {
        String msg = switch (reward.type) {
            case COINS -> "§6§l✦ Crate Reward! §r§eYou won §6" + reward.amount + " 🪙 Coins§e!";
            case GEMS  -> "§b§l✦ Crate Reward! §r§eYou won §b" + reward.amount + " 💎 Gems§e!";
            case ITEM, COMMAND -> "§d§l✦ Crate Reward! §r§eYou won §d" + reward.displayName + "§e!";
        };
        player.sendMessage(Text.literal(msg), false);
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.6f, 1.2f);

        boolean isRare = reward.weight <= 6;
        if (isRare && player.getServer() != null) {
            String broadcast = "§6§l[Crates] §e" + player.getName().getString()
                + " §7just opened " + crate.displayName + " §7and won §d" + reward.displayName + "§7!";
            player.getServer().getPlayerManager().broadcast(Text.literal(broadcast), false);
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.7f, 1f);
        }

        AethermonCore.LOGGER.info("[Crates] {} opened '{}' and won '{}' (type={})",
            player.getName().getString(), crate.id, reward.id, reward.type);
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

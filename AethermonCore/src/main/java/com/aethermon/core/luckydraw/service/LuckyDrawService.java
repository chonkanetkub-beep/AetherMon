package com.aethermon.core.luckydraw.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.luckydraw.config.LuckyDrawConfig;
import com.aethermon.core.luckydraw.model.DrawPool;
import com.aethermon.core.luckydraw.model.DrawPrize;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service for the Lucky Draw system.
 *
 * Responsibilities:
 *  - Charge the Gem cost via EconomyService
 *  - Roll the prize using the pool's weighted random
 *  - Award currency or items
 *  - Notify the player with chat + sound
 *
 * No direct balance manipulation — all flows through EconomyService.
 */
public class LuckyDrawService {

    private final EconomyService economy;
    private final LuckyDrawConfig config;

    /** Tracks players who currently have a spin in flight — prevents double-spend. */
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    public LuckyDrawService(EconomyService economy, LuckyDrawConfig config) {
        this.economy = economy;
        this.config  = config;
    }

    /** Returns all configured draw pools. */
    public List<DrawPool> getPools() {
        return config.pools;
    }

    /** Returns a pool by ID, or empty if not found. */
    public Optional<DrawPool> getPool(String poolId) {
        return config.pools.stream().filter(p -> p.id.equals(poolId)).findFirst();
    }

    /**
     * Charges costGems and rolls the prize, but does NOT award it yet.
     * Keeps player in `spinning` set until awardPrize() is called.
     * Returns the determined prize on success, or empty on failure (insufficient gems, etc.).
     */
    public CompletableFuture<Optional<DrawPrize>> prepareSpin(ServerPlayerEntity player, DrawPool pool) {
        UUID uuid = player.getUuid();

        // Prevent concurrent spins for the same player
        if (!spinning.add(uuid)) {
            AethermonCore.LOGGER.warn("[LuckyDraw] {} tried to spin while already spinning — blocked.", player.getName().getString());
            return CompletableFuture.completedFuture(Optional.empty());
        }

        BigDecimal cost = BigDecimal.valueOf(pool.costGems);

        return economy
            .withdraw(uuid, Currency.GEMS, cost, "luckydraw_spin:" + pool.id)
            .thenApply(result -> {
                if (!result.isSuccess()) {
                    spinning.remove(uuid);
                    if (player.getServer() != null) {
                        player.getServer().execute(() -> {
                            player.sendMessage(Text.literal(
                                "§cYou need §b" + pool.costGems + " 💎 Gems §cto spin the " + pool.displayName + "§c."
                            ), false);
                            player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                        });
                    }
                    return Optional.<DrawPrize>empty();
                }

                DrawPrize prize = pool.rollPrize();
                return Optional.of(prize);
            })
            .exceptionally(ex -> {
                spinning.remove(uuid);
                AethermonCore.LOGGER.error("[LuckyDraw] Spin error for {}: {}", player.getName().getString(), ex.getMessage());
                return Optional.empty();
            });
    }

    /**
     * Awards the prize to the player and clears the spinning state.
     * Currency prizes go through EconomyService.
     * Item prizes are given directly.
     */
    public CompletableFuture<Void> awardPrize(ServerPlayerEntity player, DrawPrize prize) {
        UUID uuid = player.getUuid();
        CompletableFuture<Void> future = switch (prize.type) {
            case COINS, GEMS -> {
                Currency currency = prize.asCurrency();
                yield economy.deposit(uuid, currency,
                        BigDecimal.valueOf(prize.amount), "luckydraw_prize:" + prize.id)
                    .thenAccept(result -> {
                        if (player.getServer() != null) {
                            player.getServer().execute(() -> notifyWin(player, prize));
                        }
                    });
            }
            case ITEM -> {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        ItemStack stack = resolveItem(prize.itemId);
                        if (!player.getInventory().insertStack(stack.copy())) {
                            // Drop at player feet if inventory full
                            player.dropItem(stack, false);
                        }
                        notifyWin(player, prize);
                    });
                }
                yield CompletableFuture.completedFuture(null);
            }
            case NONE -> {
                if (player.getServer() != null) {
                    player.getServer().execute(() -> {
                        player.sendMessage(Text.literal("§7You spun the " + prize.displayName + "§7. Better luck next time!"), false);
                        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.MASTER, 1f, 0.8f);
                    });
                }
                yield CompletableFuture.completedFuture(null);
            }
        };

        return future.whenComplete((v, ex) -> spinning.remove(uuid));
    }

    /**
     * Backward-compatible direct spin (rolls & immediately awards).
     */
    public CompletableFuture<Optional<DrawPrize>> spin(ServerPlayerEntity player, DrawPool pool) {
        return prepareSpin(player, pool).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
            DrawPrize prize = opt.get();
            return awardPrize(player, prize).thenApply(v -> Optional.of(prize));
        });
    }

    private void notifyWin(ServerPlayerEntity player, DrawPrize prize) {
        String msg = switch (prize.type) {
            case COINS -> "§6§l✦ Lucky Draw! §r§eYou won §6" + prize.amount + " 🪙 Coins§e!";
            case GEMS  -> "§b§l✦ Lucky Draw! §r§eYou won §b" + prize.amount + " 💎 Gems§e!";
            case ITEM  -> "§d§l✦ Lucky Draw! §r§eYou won §d" + prize.displayName + "§e!";
            case NONE  -> "§8Better luck next time...";
        };
        player.sendMessage(Text.literal(msg), false);

        // Broadcast rare wins to all players
        boolean isRare = prize.weight <= 5;
        if (isRare && player.getServer() != null) {
            String broadcast = "§6§l[Lucky Draw] §e" + player.getName().getString()
                + " §7just won §d" + prize.displayName + " §7from the Lucky Draw!";
            player.getServer().getPlayerManager().broadcast(Text.literal(broadcast), false);
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.7f, 1f);
        }

        AethermonCore.LOGGER.info("[LuckyDraw] {} won prize '{}' (type={})", 
            player.getName().getString(), prize.id, prize.type);
    }

    /** Resolves an item registry ID to an ItemStack; falls back to barrier if unknown. */
    public ItemStack resolveItem(String itemId) {
        try {
            Identifier id = Identifier.of(itemId);
            var item = Registries.ITEM.getOrEmpty(id);
            if (item.isPresent()) return new ItemStack(item.get());
        } catch (Exception ignored) {}
        AethermonCore.LOGGER.warn("[LuckyDraw] Unknown item ID '{}', using barrier as fallback.", itemId);
        return new ItemStack(Items.BARRIER);
    }
}

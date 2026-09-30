package com.aethermon.core.market.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.market.config.MarketConfig;
import com.aethermon.core.market.model.MarketDelivery;
import com.aethermon.core.market.model.MarketListing;
import com.aethermon.core.market.util.ItemSerializer;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MarketService {

    private final DatabaseManager db;
    private final EconomyService economy;
    private final MarketConfig config;

    public MarketService(DatabaseManager db, EconomyService economy, MarketConfig config) {
        this.db = db;
        this.economy = economy;
        this.config = config;
    }

    public MarketConfig getConfig() {
        return config;
    }

    public CompletableFuture<MarketListing> createListing(ServerPlayerEntity seller, ItemStack stack, BigDecimal price, Currency currency) {
        return CompletableFuture.supplyAsync(() -> {
            if (stack == null || stack.isEmpty()) {
                seller.sendMessage(Text.literal("§cYou cannot list an empty item."));
                return null;
            }

            if (price.compareTo(BigDecimal.valueOf(config.getMinPrice())) < 0 ||
                price.compareTo(BigDecimal.valueOf(config.getMaxPrice())) > 0) {
                seller.sendMessage(Text.literal("§cPrice must be between " +
                    currency.format(BigDecimal.valueOf(config.getMinPrice())) + " and " +
                    currency.format(BigDecimal.valueOf(config.getMaxPrice())) + "."));
                return null;
            }

            try (Connection conn = db.getConnection()) {
                // Check active listings limit
                try (PreparedStatement check = conn.prepareStatement(
                    "SELECT COUNT(*) FROM market_listings WHERE seller_uuid = ? AND status = 'ACTIVE'")) {
                    check.setString(1, seller.getUuid().toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt(1) >= config.getMaxListingsPerPlayer()) {
                            seller.sendMessage(Text.literal("§cYou have reached the maximum active listings limit (" + config.getMaxListingsPerPlayer() + ")."));
                            return null;
                        }
                    }
                }

                String nbt = ItemSerializer.serialize(stack, seller.getServer().getRegistryManager());
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                String displayName = stack.getName().getString();
                int count = stack.getCount();
                Instant now = Instant.now();
                Instant expiresAt = now.plus(Duration.ofHours(config.getListingDurationHours()));

                try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO market_listings (
                        seller_uuid, seller_name, item_nbt, item_id, item_count,
                        item_display_name, currency, price, created_at, expires_at, status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, seller.getUuid().toString());
                    ps.setString(2, seller.getGameProfile().getName());
                    ps.setString(3, nbt);
                    ps.setString(4, itemId);
                    ps.setInt(5, count);
                    ps.setString(6, displayName);
                    ps.setString(7, currency.name());
                    ps.setBigDecimal(8, price);
                    ps.setString(9, now.toString());
                    ps.setString(10, expiresAt.toString());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            int id = keys.getInt(1);
                            MarketListing listing = new MarketListing(
                                id, seller.getUuid(), seller.getGameProfile().getName(),
                                nbt, itemId, count, displayName, currency, price, now, expiresAt, "ACTIVE"
                            );

                            seller.getServer().execute(() -> {
                                stack.setCount(0); // Take item from player
                                seller.sendMessage(Text.literal("§a[Market] Listed §e" + count + "x " + displayName +
                                    " §afor §e" + currency.format(price) + " " + currency.symbol + "§a (ID: #" + id + ")"));
                            });

                            return listing;
                        }
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Failed to create listing", e);
                seller.sendMessage(Text.literal("§cAn error occurred while creating your listing."));
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> buyListing(ServerPlayerEntity buyer, int listingId) {
        return CompletableFuture.supplyAsync(() -> {
            MarketListing listing = getListingById(listingId);
            if (listing == null || !listing.isActive()) {
                buyer.sendMessage(Text.literal("§cThis listing is no longer available."));
                return false;
            }

            if (listing.sellerUuid().equals(buyer.getUuid())) {
                buyer.sendMessage(Text.literal("§cYou cannot buy your own listing! Use 'Your Listings' to cancel and retrieve it."));
                return false;
            }

            // Withdraw funds from buyer
            var withdrawResult = economy.withdraw(
                buyer.getUuid(), listing.currency(), listing.price(), "market_buy:#" + listing.id()
            ).join();

            if (!withdrawResult.isSuccess()) {
                buyer.sendMessage(Text.literal("§cInsufficient funds! You need §e" +
                    listing.currency().format(listing.price()) + " " + listing.currency().symbol));
                return false;
            }

            // Mark as SOLD
            try (Connection conn = db.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE market_listings SET status = 'SOLD' WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, listing.id());
                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        // Already sold to someone else in a race condition — refund buyer
                        economy.deposit(buyer.getUuid(), listing.currency(), listing.price(), "market_refund:#" + listing.id());
                        buyer.sendMessage(Text.literal("§cThis item was just bought by another player. You were refunded."));
                        return false;
                    }
                }

                // Calculate seller earnings after tax
                BigDecimal taxMultiplier = BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(config.getTaxRatePercent() / 100.0)
                );
                BigDecimal earnings = listing.price().multiply(taxMultiplier).setScale(2, RoundingMode.HALF_UP);

                MinecraftServer server = buyer.getServer();
                ServerPlayerEntity onlineSeller = server != null ? server.getPlayerManager().getPlayer(listing.sellerUuid()) : null;

                if (onlineSeller != null) {
                    economy.deposit(listing.sellerUuid(), listing.currency(), earnings, "market_sold:#" + listing.id());
                    onlineSeller.sendMessage(Text.literal("§6[Market] §aYour §e" + listing.itemDisplayName() +
                        " §awas bought by §e" + buyer.getGameProfile().getName() + " §afor §e" +
                        listing.currency().format(earnings) + " " + listing.currency().symbol + "§a (after " + config.getTaxRatePercent() + "% tax)!"));
                } else {
                    // Deliver to offline mailbox
                    try (PreparedStatement mail = conn.prepareStatement("""
                        INSERT INTO market_deliveries (player_uuid, type, currency, amount, claimed, created_at)
                        VALUES (?, 'MONEY', ?, ?, 0, ?)
                        """)) {
                        mail.setString(1, listing.sellerUuid().toString());
                        mail.setString(2, listing.currency().name());
                        mail.setBigDecimal(3, earnings);
                        mail.setString(4, Instant.now().toString());
                        mail.executeUpdate();
                    }
                }

                // Give item to buyer on main server thread
                if (server != null) {
                    server.execute(() -> {
                        ItemStack itemStack = ItemSerializer.deserialize(listing.itemNbt(), server.getRegistryManager());
                        boolean added = buyer.getInventory().insertStack(itemStack);
                        if (!added) {
                            buyer.dropItem(itemStack, false);
                            buyer.sendMessage(Text.literal("§6[Market] §eInventory was full! The item was dropped at your feet."));
                        }
                        buyer.sendMessage(Text.literal("§a[Market] Purchased §e" + listing.itemCount() + "x " +
                            listing.itemDisplayName() + " §afor §e" + listing.currency().format(listing.price()) + " " + listing.currency().symbol + "§a!"));
                    });
                }

                return true;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Error completing purchase", e);
                buyer.sendMessage(Text.literal("§cAn error occurred during transaction."));
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> cancelListing(ServerPlayerEntity seller, int listingId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                MarketListing listing = null;
                try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM market_listings WHERE id = ? AND seller_uuid = ? AND status = 'ACTIVE'")) {
                    ps.setInt(1, listingId);
                    ps.setString(2, seller.getUuid().toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            listing = mapListing(rs);
                        }
                    }
                }

                if (listing == null) {
                    seller.sendMessage(Text.literal("§cListing not found or already cancelled."));
                    return false;
                }

                try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE market_listings SET status = 'CANCELLED' WHERE id = ?")) {
                    update.setInt(1, listingId);
                    update.executeUpdate();
                }

                final MarketListing cancelled = listing;
                seller.getServer().execute(() -> {
                    ItemStack stack = ItemSerializer.deserialize(cancelled.itemNbt(), seller.getServer().getRegistryManager());
                    boolean added = seller.getInventory().insertStack(stack);
                    if (!added) {
                        seller.dropItem(stack, false);
                        seller.sendMessage(Text.literal("§6[Market] §eInventory full! Returned item dropped at your feet."));
                    }
                    seller.sendMessage(Text.literal("§a[Market] Cancelled listing #" + cancelled.id() +
                        " and retrieved §e" + cancelled.itemDisplayName() + "§a."));
                });

                return true;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Failed to cancel listing", e);
                return false;
            }
        });
    }

    public CompletableFuture<List<MarketListing>> getActiveListings(int page, int pageSize, String searchFilter) {
        return CompletableFuture.supplyAsync(() -> {
            expireOldListings();

            List<MarketListing> list = new ArrayList<>();
            String sql = "SELECT * FROM market_listings WHERE status = 'ACTIVE' ";
            if (searchFilter != null && !searchFilter.isBlank()) {
                sql += "AND (LOWER(item_display_name) LIKE ? OR LOWER(item_id) LIKE ?) ";
            }
            sql += "ORDER BY created_at DESC LIMIT ? OFFSET ?";

            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                int paramIdx = 1;
                if (searchFilter != null && !searchFilter.isBlank()) {
                    String pattern = "%" + searchFilter.toLowerCase().trim() + "%";
                    ps.setString(paramIdx++, pattern);
                    ps.setString(paramIdx++, pattern);
                }
                ps.setInt(paramIdx++, pageSize);
                ps.setInt(paramIdx, Math.max(0, page * pageSize));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapListing(rs));
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Failed to fetch active listings", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<MarketListing>> getPlayerListings(UUID sellerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            expireOldListings();
            List<MarketListing> list = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM market_listings WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY created_at DESC")) {
                ps.setString(1, sellerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapListing(rs));
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Failed to fetch player listings", e);
            }
            return list;
        });
    }

    public CompletableFuture<List<MarketDelivery>> getDeliveries(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<MarketDelivery> list = new ArrayList<>();
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM market_deliveries WHERE player_uuid = ? AND claimed = 0 ORDER BY created_at ASC")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapDelivery(rs));
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Failed to fetch deliveries", e);
            }
            return list;
        });
    }

    public CompletableFuture<Integer> claimDeliveries(ServerPlayerEntity player) {
        return CompletableFuture.supplyAsync(() -> {
            List<MarketDelivery> deliveries = getDeliveries(player.getUuid()).join();
            if (deliveries.isEmpty()) {
                player.sendMessage(Text.literal("§7You have no unclaimed items or profits in your delivery box."));
                return 0;
            }

            int claimedCount = 0;
            BigDecimal totalCoins = BigDecimal.ZERO;
            BigDecimal totalGems = BigDecimal.ZERO;

            try (Connection conn = db.getConnection()) {
                for (MarketDelivery del : deliveries) {
                    if (del.isMoney()) {
                        if (del.currency() == Currency.COINS) {
                            totalCoins = totalCoins.add(del.amount());
                        } else {
                            totalGems = totalGems.add(del.amount());
                        }
                    } else if (del.isItem()) {
                        player.getServer().execute(() -> {
                            ItemStack stack = ItemSerializer.deserialize(del.itemNbt(), player.getServer().getRegistryManager());
                            boolean added = player.getInventory().insertStack(stack);
                            if (!added) {
                                player.dropItem(stack, false);
                            }
                        });
                    }

                    try (PreparedStatement mark = conn.prepareStatement(
                        "UPDATE market_deliveries SET claimed = 1 WHERE id = ?")) {
                        mark.setInt(1, del.id());
                        mark.executeUpdate();
                    }
                    claimedCount++;
                }

                if (totalCoins.compareTo(BigDecimal.ZERO) > 0) {
                    economy.deposit(player.getUuid(), Currency.COINS, totalCoins, "market_claim");
                }
                if (totalGems.compareTo(BigDecimal.ZERO) > 0) {
                    economy.deposit(player.getUuid(), Currency.GEMS, totalGems, "market_claim");
                }

                final int finalCount = claimedCount;
                final BigDecimal finalCoins = totalCoins;
                final BigDecimal finalGems = totalGems;

                player.sendMessage(Text.literal("§a[Market] Claimed §e" + finalCount + " deliveries" +
                    (finalCoins.compareTo(BigDecimal.ZERO) > 0 ? " (+§e" + Currency.COINS.format(finalCoins) + " 🪙§a)" : "") +
                    (finalGems.compareTo(BigDecimal.ZERO) > 0 ? " (+§b" + Currency.GEMS.format(finalGems) + " 💎§a)" : "") + "!"));

                return claimedCount;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Market] Failed to claim deliveries", e);
                return 0;
            }
        });
    }

    private void expireOldListings() {
        try (Connection conn = db.getConnection();
             PreparedStatement check = conn.prepareStatement(
                 "SELECT * FROM market_listings WHERE status = 'ACTIVE' AND expires_at <= datetime('now')");
             ResultSet rs = check.executeQuery()) {

            while (rs.next()) {
                MarketListing expired = mapListing(rs);
                // Mark EXPIRED
                try (PreparedStatement up = conn.prepareStatement("UPDATE market_listings SET status = 'EXPIRED' WHERE id = ?")) {
                    up.setInt(1, expired.id());
                    up.executeUpdate();
                }

                // Add item to deliveries
                try (PreparedStatement del = conn.prepareStatement("""
                    INSERT INTO market_deliveries (player_uuid, type, item_nbt, item_display_name, claimed, created_at)
                    VALUES (?, 'ITEM', ?, ?, 0, ?)
                    """)) {
                    del.setString(1, expired.sellerUuid().toString());
                    del.setString(2, expired.itemNbt());
                    del.setString(3, expired.itemDisplayName());
                    del.setString(4, Instant.now().toString());
                    del.executeUpdate();
                }
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Market] Error expiring old listings", e);
        }
    }

    private MarketListing getListingById(int id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM market_listings WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapListing(rs);
            }
        } catch (SQLException e) {
            AethermonCore.LOGGER.error("[Market] Error getting listing by id", e);
        }
        return null;
    }

    private MarketListing mapListing(ResultSet rs) throws SQLException {
        return new MarketListing(
            rs.getInt("id"),
            UUID.fromString(rs.getString("seller_uuid")),
            rs.getString("seller_name"),
            rs.getString("item_nbt"),
            rs.getString("item_id"),
            rs.getInt("item_count"),
            rs.getString("item_display_name"),
            Currency.valueOf(rs.getString("currency").toUpperCase()),
            rs.getBigDecimal("price"),
            parseInstant(rs.getString("created_at")),
            parseInstant(rs.getString("expires_at")),
            rs.getString("status")
        );
    }

    private MarketDelivery mapDelivery(ResultSet rs) throws SQLException {
        String curr = rs.getString("currency");
        Currency currency = curr != null ? Currency.valueOf(curr.toUpperCase()) : null;
        return new MarketDelivery(
            rs.getInt("id"),
            UUID.fromString(rs.getString("player_uuid")),
            rs.getString("type"),
            currency,
            rs.getBigDecimal("amount"),
            rs.getString("item_nbt"),
            rs.getString("item_display_name"),
            rs.getInt("claimed") == 1,
            parseInstant(rs.getString("created_at"))
        );
    }

    private static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) return Instant.now();
        try {
            if (text.endsWith("Z")) {
                return Instant.parse(text);
            }
            return Instant.parse(text.replace(" ", "T") + "Z");
        } catch (Exception e) {
            return Instant.now();
        }
    }
}

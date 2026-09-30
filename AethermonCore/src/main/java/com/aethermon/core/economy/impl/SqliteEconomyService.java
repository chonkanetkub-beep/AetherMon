package com.aethermon.core.economy.impl;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyResult;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SQLite-backed implementation of {@link EconomyService}.
 *
 * All public methods run on a dedicated single-thread executor so they
 * never block Minecraft's main server thread.
 *
 * Switching to MySQL later:
 *  - Swap out DatabaseManager's JDBC URL
 *  - Replace AUTOINCREMENT with AUTO_INCREMENT in schema
 *  - Everything else stays the same
 */
public class SqliteEconomyService implements EconomyService {

    // Single thread keeps SQLite happy (it doesn't love concurrency)
    private static final Executor DB_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "aethermon-economy-db");
                t.setDaemon(true);
                return t;
            });

    private final DatabaseManager db;

    // Starting balances for new accounts (from config — injected here)
    private final BigDecimal startingCoins;
    private final BigDecimal startingGems;

    public SqliteEconomyService(DatabaseManager db,
                                 BigDecimal startingCoins,
                                 BigDecimal startingGems) {
        this.db            = db;
        this.startingCoins = startingCoins;
        this.startingGems  = startingGems;
    }

    // ── EconomyService API ────────────────────────────────────

    @Override
    public CompletableFuture<BigDecimal> getBalance(UUID playerId, Currency currency) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT balance FROM economy_accounts WHERE player_uuid = ? AND currency = ?");
                ps.setString(1, playerId.toString());
                ps.setString(2, currency.name());
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getBigDecimal("balance") : BigDecimal.ZERO;
            } catch (SQLException e) {
                throw new RuntimeException("getBalance failed", e);
            }
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> has(UUID playerId, Currency currency, BigDecimal amount) {
        return getBalance(playerId, currency)
                .thenApply(bal -> bal.compareTo(amount) >= 0);
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(UUID playerId, Currency currency,
                                                     BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            return CompletableFuture.completedFuture(EconomyResult.invalidAmount(amount));

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    BigDecimal current = queryBalance(conn, playerId, currency);
                    if (current == null) {
                        conn.rollback();
                        return EconomyResult.accountNotFound();
                    }
                    BigDecimal newBal = current.add(amount);
                    updateBalance(conn, playerId, currency, newBal);
                    logTransaction(conn, playerId, currency, "DEPOSIT", amount, newBal, reason, null);
                    conn.commit();
                    return EconomyResult.success(newBal);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("deposit failed", e);
            }
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(UUID playerId, Currency currency,
                                                      BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            return CompletableFuture.completedFuture(EconomyResult.invalidAmount(amount));

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    BigDecimal current = queryBalance(conn, playerId, currency);
                    if (current == null) {
                        conn.rollback();
                        return EconomyResult.accountNotFound();
                    }
                    if (current.compareTo(amount) < 0) {
                        conn.rollback();
                        return EconomyResult.insufficientFunds(current);
                    }
                    BigDecimal newBal = current.subtract(amount);
                    updateBalance(conn, playerId, currency, newBal);
                    logTransaction(conn, playerId, currency, "WITHDRAW", amount, newBal, reason, null);
                    conn.commit();
                    return EconomyResult.success(newBal);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("withdraw failed", e);
            }
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<EconomyResult> transfer(UUID fromId, UUID toId, Currency currency,
                                                      BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            return CompletableFuture.completedFuture(EconomyResult.invalidAmount(amount));

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    BigDecimal fromBal = queryBalance(conn, fromId, currency);
                    BigDecimal toBal   = queryBalance(conn, toId,   currency);
                    if (fromBal == null) { conn.rollback(); return EconomyResult.accountNotFound(); }
                    if (toBal   == null) { conn.rollback(); return EconomyResult.accountNotFound(); }
                    if (fromBal.compareTo(amount) < 0) {
                        conn.rollback();
                        return EconomyResult.insufficientFunds(fromBal);
                    }
                    BigDecimal newFrom = fromBal.subtract(amount);
                    BigDecimal newTo   = toBal.add(amount);
                    updateBalance(conn, fromId, currency, newFrom);
                    updateBalance(conn, toId,   currency, newTo);
                    logTransaction(conn, fromId, currency, "TRANSFER_OUT", amount, newFrom, reason, toId.toString());
                    logTransaction(conn, toId,   currency, "TRANSFER_IN",  amount, newTo,   reason, fromId.toString());
                    conn.commit();
                    return EconomyResult.success(newFrom);  // from sender's perspective
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("transfer failed", e);
            }
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<EconomyResult> setBalance(UUID playerId, Currency currency,
                                                        BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            return CompletableFuture.completedFuture(EconomyResult.invalidAmount(amount));

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    BigDecimal current = queryBalance(conn, playerId, currency);
                    if (current == null) { conn.rollback(); return EconomyResult.accountNotFound(); }
                    updateBalance(conn, playerId, currency, amount);
                    logTransaction(conn, playerId, currency, "SET", amount, amount, reason, null);
                    conn.commit();
                    return EconomyResult.success(amount);
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("setBalance failed", e);
            }
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<Void> createAccount(UUID playerId, String playerName) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = db.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    for (Currency cur : Currency.values()) {
                        BigDecimal start = cur == Currency.COINS ? startingCoins : startingGems;
                        // INSERT OR IGNORE — safe to call multiple times
                        PreparedStatement ps = conn.prepareStatement("""
                            INSERT OR IGNORE INTO economy_accounts
                              (player_uuid, player_name, currency, balance)
                            VALUES (?, ?, ?, ?)
                            """);
                        ps.setString(1, playerId.toString());
                        ps.setString(2, playerName);
                        ps.setString(3, cur.name());
                        ps.setBigDecimal(4, start);
                        ps.executeUpdate();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("createAccount failed", e);
            }
        }, DB_EXECUTOR);
    }

    @Override
    public CompletableFuture<Boolean> hasAccount(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = db.getConnection()) {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM economy_accounts WHERE player_uuid = ? LIMIT 1");
                ps.setString(1, playerId.toString());
                return ps.executeQuery().next();
            } catch (SQLException e) {
                throw new RuntimeException("hasAccount failed", e);
            }
        }, DB_EXECUTOR);
    }

    // ── Private helpers ───────────────────────────────────────

    /** Returns current balance or null if account row missing. */
    private BigDecimal queryBalance(Connection conn, UUID playerId, Currency currency)
            throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT balance FROM economy_accounts WHERE player_uuid = ? AND currency = ?");
        ps.setString(1, playerId.toString());
        ps.setString(2, currency.name());
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getBigDecimal("balance") : null;
    }

    private void updateBalance(Connection conn, UUID playerId, Currency currency,
                                BigDecimal newBalance) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("""
            UPDATE economy_accounts
            SET balance = ?, updated_at = datetime('now')
            WHERE player_uuid = ? AND currency = ?
            """);
        ps.setBigDecimal(1, newBalance);
        ps.setString(2, playerId.toString());
        ps.setString(3, currency.name());
        ps.executeUpdate();
    }

    private void logTransaction(Connection conn, UUID playerId, Currency currency,
                                 String type, BigDecimal amount, BigDecimal balanceAfter,
                                 String reason, String counterpart) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO economy_transactions
              (player_uuid, currency, type, amount, balance_after, reason, counterpart)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """);
        ps.setString(1, playerId.toString());
        ps.setString(2, currency.name());
        ps.setString(3, type);
        ps.setBigDecimal(4, amount);
        ps.setBigDecimal(5, balanceAfter);
        ps.setString(6, reason);
        ps.setString(7, counterpart);
        ps.executeUpdate();
    }
}

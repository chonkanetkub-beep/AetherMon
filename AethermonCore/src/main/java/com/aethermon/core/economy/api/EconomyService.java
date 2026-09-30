package com.aethermon.core.economy.api;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The single source of truth for all currency operations.
 *
 * Rules:
 *  - All methods are async (return CompletableFuture) — never block the main thread.
 *  - Balances can never go below zero.
 *  - Every mutation is logged with a reason string.
 *  - Transactions are atomic — partial failures roll back fully.
 */
public interface EconomyService {

    // ── Balance queries ───────────────────────────────────────

    /** Returns the player's current balance for the given currency. */
    CompletableFuture<BigDecimal> getBalance(UUID playerId, Currency currency);

    /** Returns true if the player has at least {@code amount} of the currency. */
    CompletableFuture<Boolean> has(UUID playerId, Currency currency, BigDecimal amount);

    // ── Mutations (all logged) ────────────────────────────────

    /**
     * Adds {@code amount} to the player's balance.
     * @param reason Short description logged to the transaction table (e.g. "quest_reward").
     */
    CompletableFuture<EconomyResult> deposit(UUID playerId, Currency currency,
                                              BigDecimal amount, String reason);

    /**
     * Removes {@code amount} from the player's balance.
     * Fails with {@link EconomyResult.Status#INSUFFICIENT_FUNDS} if balance would go negative.
     */
    CompletableFuture<EconomyResult> withdraw(UUID playerId, Currency currency,
                                               BigDecimal amount, String reason);

    /**
     * Transfers {@code amount} from one player to another atomically.
     * Both deposit and withdraw succeed or both roll back.
     */
    CompletableFuture<EconomyResult> transfer(UUID fromId, UUID toId, Currency currency,
                                               BigDecimal amount, String reason);

    // ── Admin mutations ───────────────────────────────────────

    /** Sets a player's balance to exactly {@code amount}. Admin use only. */
    CompletableFuture<EconomyResult> setBalance(UUID playerId, Currency currency,
                                                 BigDecimal amount, String reason);

    // ── Account management ────────────────────────────────────

    /**
     * Creates an account for a new player with starting balances.
     * Called on first join. Safe to call multiple times (idempotent).
     */
    CompletableFuture<Void> createAccount(UUID playerId, String playerName);

    /** Returns true if the player has an account in the database. */
    CompletableFuture<Boolean> hasAccount(UUID playerId);
}

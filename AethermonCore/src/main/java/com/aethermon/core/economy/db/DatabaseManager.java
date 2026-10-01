package com.aethermon.core.economy.db;

import com.aethermon.core.AethermonCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite connection and schema creation.
 *
 * One connection pool for the entire mod.
 * Written so switching to MySQL later only requires:
 *   1. Changing the JDBC URL in config
 *   2. Replacing "INTEGER PRIMARY KEY AUTOINCREMENT" with "BIGINT AUTO_INCREMENT"
 *   3. Removing the SQLite JDBC bundled dep and adding MySQL connector
 */
public class DatabaseManager {

    private static final String SCHEMA_VERSION = "1";
    private final String jdbcUrl;

    public DatabaseManager(Path dataDir) {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directory: " + dataDir, e);
        }
        this.jdbcUrl = "jdbc:sqlite:" + dataDir.resolve("aethermon.db");
    }

    /** Opens a connection. Callers must close it (use try-with-resources). */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    /**
     * Creates all tables if they don't exist.
     * Safe to call multiple times (idempotent).
     */
    public void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // ── Economy: account balances ──────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS economy_accounts (
                    player_uuid   TEXT    NOT NULL,
                    player_name   TEXT    NOT NULL,
                    currency      TEXT    NOT NULL,
                    balance       NUMERIC NOT NULL DEFAULT 0,
                    created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
                    updated_at    TEXT    NOT NULL DEFAULT (datetime('now')),
                    PRIMARY KEY (player_uuid, currency)
                )
                """);

            // ── Economy: transaction log ───────────────────────────────
            // Every deposit/withdraw/transfer writes a row here.
            // Never deleted — audit trail forever.
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS economy_transactions (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid   TEXT    NOT NULL,
                    currency      TEXT    NOT NULL,
                    type          TEXT    NOT NULL,  -- DEPOSIT | WITHDRAW | TRANSFER_IN | TRANSFER_OUT | SET
                    amount        NUMERIC NOT NULL,
                    balance_after NUMERIC NOT NULL,
                    reason        TEXT    NOT NULL,
                    counterpart   TEXT,              -- other player UUID for transfers
                    timestamp     TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // Index for fast per-player balance lookups
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_accounts_uuid
                ON economy_accounts (player_uuid)
                """);

            // Index for fast transaction history per player
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_transactions_uuid
                ON economy_transactions (player_uuid, timestamp DESC)
                """);

            // ── Homes: player saved homes ──────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_homes (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT    NOT NULL,
                    home_name   TEXT    NOT NULL,
                    world       TEXT    NOT NULL,
                    x           REAL    NOT NULL,
                    y           REAL    NOT NULL,
                    z           REAL    NOT NULL,
                    yaw         REAL    NOT NULL,
                    pitch       REAL    NOT NULL,
                    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
                    UNIQUE(player_uuid, home_name)
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_player_homes_uuid
                ON player_homes (player_uuid)
                """);

            // ── Market: player-to-player listings ───────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS market_listings (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid       TEXT    NOT NULL,
                    seller_name       TEXT    NOT NULL,
                    item_nbt          TEXT    NOT NULL,
                    item_id           TEXT    NOT NULL,
                    item_count        INTEGER NOT NULL,
                    item_display_name TEXT    NOT NULL,
                    currency          TEXT    NOT NULL DEFAULT 'COINS',
                    price             NUMERIC NOT NULL,
                    created_at        TEXT    NOT NULL DEFAULT (datetime('now')),
                    expires_at        TEXT    NOT NULL,
                    status            TEXT    NOT NULL DEFAULT 'ACTIVE'
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_status
                ON market_listings (status, created_at DESC)
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_seller
                ON market_listings (seller_uuid, status)
                """);

            // ── Market: delivery box for offline sales & returned items ──
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS market_deliveries (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid       TEXT    NOT NULL,
                    type              TEXT    NOT NULL,
                    currency          TEXT,
                    amount            NUMERIC,
                    item_nbt          TEXT,
                    item_display_name TEXT,
                    claimed           INTEGER NOT NULL DEFAULT 0,
                    created_at        TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_deliveries_player
                ON market_deliveries (player_uuid, claimed)
                """);

            // ── Rewards: Daily claims & streaks ────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_daily_rewards (
                    player_uuid     TEXT PRIMARY KEY,
                    last_claim_date TEXT NOT NULL,
                    current_streak  INTEGER NOT NULL DEFAULT 0,
                    total_claims    INTEGER NOT NULL DEFAULT 0,
                    updated_at      TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // ── Rewards: Playtime tracking & claimed daily tiers ────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_playtime (
                    player_uuid         TEXT PRIMARY KEY,
                    tracking_date       TEXT NOT NULL,
                    active_seconds      INTEGER NOT NULL DEFAULT 0,
                    claimed_tiers       TEXT NOT NULL DEFAULT '',
                    total_playtime_secs INTEGER NOT NULL DEFAULT 0,
                    updated_at          TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // ── Quests: Daily and Weekly Quests ─────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_quests (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid      TEXT    NOT NULL,
                    period_type      TEXT    NOT NULL,
                    period_key       TEXT    NOT NULL,
                    quest_id         TEXT    NOT NULL,
                    slot_index       INTEGER NOT NULL DEFAULT 0,
                    current_progress INTEGER NOT NULL DEFAULT 0,
                    target_amount    INTEGER NOT NULL,
                    completed        INTEGER NOT NULL DEFAULT 0,
                    claimed          INTEGER NOT NULL DEFAULT 0,
                    created_at       TEXT    NOT NULL DEFAULT (datetime('now')),
                    UNIQUE(player_uuid, period_type, period_key, slot_index)
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_player_quests_lookup
                ON player_quests (player_uuid, period_type, period_key)
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_quest_meta (
                    player_uuid  TEXT PRIMARY KEY,
                    reroll_date  TEXT NOT NULL,
                    rerolls_used INTEGER NOT NULL DEFAULT 0,
                    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )
                """);

            // ── Lucky Draw: spin history ────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS lucky_draw_history (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT    NOT NULL,
                    player_name TEXT    NOT NULL,
                    pool_id     TEXT    NOT NULL,
                    prize_id    TEXT    NOT NULL,
                    prize_type  TEXT    NOT NULL,
                    prize_amount NUMERIC NOT NULL DEFAULT 0,
                    cost_gems   INTEGER NOT NULL,
                    spun_at     TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_lucky_draw_player
                ON lucky_draw_history (player_uuid, spun_at DESC)
                """);

            // ── Crates: virtual keys & physical crate blocks ─────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_crate_keys (
                    player_uuid TEXT NOT NULL,
                    crate_id    TEXT NOT NULL,
                    amount      INTEGER NOT NULL DEFAULT 0,
                    updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
                    PRIMARY KEY (player_uuid, crate_id)
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS crate_blocks (
                    world    TEXT NOT NULL,
                    x        INTEGER NOT NULL,
                    y        INTEGER NOT NULL,
                    z        INTEGER NOT NULL,
                    crate_id TEXT NOT NULL,
                    PRIMARY KEY (world, x, y, z)
                )
                """);

            // ── Battle Pass: seasonal player progression ────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_battlepass (
                    player_uuid           TEXT NOT NULL,
                    season                INTEGER NOT NULL DEFAULT 1,
                    exp                   INTEGER NOT NULL DEFAULT 0,
                    tier                  INTEGER NOT NULL DEFAULT 1,
                    is_premium            INTEGER NOT NULL DEFAULT 0,
                    claimed_free_tiers    TEXT NOT NULL DEFAULT '',
                    claimed_premium_tiers TEXT NOT NULL DEFAULT '',
                    updated_at            TEXT NOT NULL DEFAULT (datetime('now')),
                    PRIMARY KEY (player_uuid, season)
                )
                """);

            // ── Duel: fight history ─────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS duel_history (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    challenger_uuid  TEXT    NOT NULL,
                    challenger_name  TEXT    NOT NULL,
                    target_uuid      TEXT    NOT NULL,
                    target_name      TEXT    NOT NULL,
                    winner_uuid      TEXT,
                    wager_coins      NUMERIC NOT NULL DEFAULT 0,
                    fought_at        TEXT    NOT NULL DEFAULT (datetime('now'))
                )
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_duel_history_challenger
                ON duel_history (challenger_uuid, fought_at DESC)
                """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_duel_history_target
                ON duel_history (target_uuid, fought_at DESC)
                """);

            AethermonCore.LOGGER.info("Database schema ready (economy, homes, market, rewards, quests, luckydraw, crates, battlepass & duels).");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise database schema", e);
        }
    }
}

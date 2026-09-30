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

            AethermonCore.LOGGER.info("Database schema ready (economy & homes).");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialise database schema", e);
        }
    }
}

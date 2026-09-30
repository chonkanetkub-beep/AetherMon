package com.aethermon.core.homes.impl;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.homes.api.HomeService;
import com.aethermon.core.homes.model.Home;
import net.minecraft.server.network.ServerPlayerEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQLite-backed implementation of HomeService.
 */
public class SqliteHomeService implements HomeService {

    private final DatabaseManager db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aethermon-homes-db");
        t.setDaemon(true);
        return t;
    });

    public SqliteHomeService(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            List<Home> list = new ArrayList<>();
            String sql = "SELECT home_name, world, x, y, z, yaw, pitch FROM player_homes WHERE player_uuid = ? ORDER BY home_name ASC";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new Home(
                            playerId,
                            rs.getString("home_name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                        ));
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Homes] Failed to get homes for {}", playerId, e);
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Home>> getHome(UUID playerId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT world, x, y, z, yaw, pitch FROM player_homes WHERE player_uuid = ? AND home_name = ?";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, name.toLowerCase());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Home(
                            playerId,
                            name.toLowerCase(),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                        ));
                    }
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Homes] Failed to get home '{}' for {}", name, playerId, e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> setHome(Home home) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO player_homes (player_uuid, home_name, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, home_name) DO UPDATE SET
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    yaw = excluded.yaw,
                    pitch = excluded.pitch,
                    created_at = datetime('now')
            """;
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, home.playerId().toString());
                stmt.setString(2, home.name().toLowerCase());
                stmt.setString(3, home.world());
                stmt.setDouble(4, home.x());
                stmt.setDouble(5, home.y());
                stmt.setDouble(6, home.z());
                stmt.setFloat(7, home.yaw());
                stmt.setFloat(8, home.pitch());
                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Homes] Failed to save home '{}' for {}", home.name(), home.playerId(), e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteHome(UUID playerId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM player_homes WHERE player_uuid = ? AND home_name = ?";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, name.toLowerCase());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Homes] Failed to delete home '{}' for {}", name, playerId, e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getHomeCount(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM player_homes WHERE player_uuid = ?";
            try (Connection conn = db.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                AethermonCore.LOGGER.error("[Homes] Failed to count homes for {}", playerId, e);
            }
            return 0;
        }, executor);
    }

    @Override
    public int getMaxHomes(ServerPlayerEntity player) {
        // Admin / OP: unlimited (100)
        if (player.hasPermissionLevel(3)) {
            return 100;
        }
        // Staff: 5
        if (player.hasPermissionLevel(2)) {
            return 5;
        }

        // Custom permission check via Fabric permissions / LuckPerms if available
        try {
            Class<?> permsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            java.lang.reflect.Method check = permsClass.getMethod("check", net.minecraft.entity.Entity.class, String.class);
            if ((boolean) check.invoke(null, player, "aethermon.homes.unlimited")) return 100;
            if ((boolean) check.invoke(null, player, "aethermon.homes.5")) return 5;
            if ((boolean) check.invoke(null, player, "aethermon.homes.2")) return 2;
        } catch (Throwable ignored) {}

        // Member group by default on this server gets 2 homes
        // Default guest gets 1
        return 2;
    }
}

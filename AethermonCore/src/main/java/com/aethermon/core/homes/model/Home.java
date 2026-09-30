package com.aethermon.core.homes.model;

import java.util.UUID;

/**
 * Represents a saved player home location.
 */
public record Home(
    UUID playerId,
    String name,
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
    public Home withName(String newName) {
        return new Home(playerId, newName, world, x, y, z, yaw, pitch);
    }
}

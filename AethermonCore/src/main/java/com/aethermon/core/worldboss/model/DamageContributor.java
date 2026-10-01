package com.aethermon.core.worldboss.model;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Tracks a single player's contribution to the current boss fight.
 *
 * Used to rank players for proportional prize distribution.
 */
public class DamageContributor implements Comparable<DamageContributor> {

    public final UUID   playerUuid;
    public final String playerName;
    /** Cumulative damage dealt to the current boss. */
    public double damageDealt;

    public DamageContributor(ServerPlayerEntity player) {
        this.playerUuid  = player.getUuid();
        this.playerName  = player.getName().getString();
        this.damageDealt = 0.0;
    }

    public void addDamage(double amount) {
        damageDealt += amount;
    }

    /** Natural order: highest damage first. */
    @Override
    public int compareTo(DamageContributor other) {
        return Double.compare(other.damageDealt, this.damageDealt);
    }
}

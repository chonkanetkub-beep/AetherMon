package com.aethermon.core.worldboss.model;

/**
 * Defines a World Boss configuration entry.
 *
 * Each boss is a named, buffed vanilla mob. Admins spawn one
 * with /worldboss start <bossId>.
 */
public class BossDefinition {

    /** Machine ID used in commands, e.g. "wither_king". */
    public String id;

    /** Display name shown in boss bar and chat (supports § colour codes). */
    public String displayName;

    /** Vanilla entity type ID, e.g. "minecraft:wither" or "minecraft:elder_guardian". */
    public String entityType;

    /** Health multiplier applied on top of the mob's base max health. */
    public double healthMultiplier;

    /** Extra damage multiplier for the boss's attacks. */
    public double damageMultiplier;

    /** Movement speed attribute (default vanilla = 0.25). */
    public double movementSpeed;

    /**
     * Prize Coins shared among top-N damage dealers when the boss dies.
     * Distributed proportionally by damage dealt.
     */
    public long prizeCoins;

    /**
     * Prize Gems shared among top-N damage dealers.
     */
    public long prizeGems;

    /** How many top damage dealers share the prize pool (default 10). */
    public int prizeTopN;

    /** Colour of the boss bar. Vanilla values: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE. */
    public String bossBarColor;

    /** Announcement message broadcast when the boss spawns (§ colour codes allowed). */
    public String spawnAnnouncement;

    /** Announcement message broadcast when the boss dies. */
    public String deathAnnouncement;
}

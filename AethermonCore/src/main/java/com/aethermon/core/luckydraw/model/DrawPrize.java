package com.aethermon.core.luckydraw.model;

import com.aethermon.core.economy.api.Currency;

/**
 * A single prize entry in a Lucky Draw pool.
 *
 * Prize types:
 *  COINS  — award coins from EconomyService
 *  GEMS   — award gems from EconomyService
 *  ITEM   — give a named Minecraft item (by registry ID)
 *  NONE   — "no prize" / dud entry (rare filler for weighting)
 */
public class DrawPrize {

    public enum PrizeType { COINS, GEMS, ITEM, NONE }

    /** Unique ID within the pool (used for logging). */
    public String id;

    /** Display name shown in the GUI slot. */
    public String displayName;

    /** Item shown in the spinning GUI and award message (Minecraft item ID, e.g. "minecraft:diamond"). */
    public String itemId;

    /** Type of prize awarded on win. */
    public PrizeType type;

    /** Amount of COINS or GEMS if type is COINS/GEMS. */
    public long amount;

    /** Relative weight — higher = more common. Sum of all weights = 100% chance. */
    public int weight;

    /** Lines of lore shown in the GUI entry. */
    public String[] lore;

    // ── Convenience constructors for defaults ────────────────────────────

    public static DrawPrize coins(String id, String displayName, String itemId, long amount, int weight, String[] lore) {
        DrawPrize p = new DrawPrize();
        p.id = id; p.displayName = displayName; p.itemId = itemId;
        p.type = PrizeType.COINS; p.amount = amount; p.weight = weight; p.lore = lore;
        return p;
    }

    public static DrawPrize gems(String id, String displayName, String itemId, long amount, int weight, String[] lore) {
        DrawPrize p = new DrawPrize();
        p.id = id; p.displayName = displayName; p.itemId = itemId;
        p.type = PrizeType.GEMS; p.amount = amount; p.weight = weight; p.lore = lore;
        return p;
    }

    public static DrawPrize item(String id, String displayName, String itemId, int weight, String[] lore) {
        DrawPrize p = new DrawPrize();
        p.id = id; p.displayName = displayName; p.itemId = itemId;
        p.type = PrizeType.ITEM; p.amount = 1; p.weight = weight; p.lore = lore;
        return p;
    }

    public static DrawPrize none(String id, int weight) {
        DrawPrize p = new DrawPrize();
        p.id = id; p.displayName = "§8Empty Slot"; p.itemId = "minecraft:barrier";
        p.type = PrizeType.NONE; p.amount = 0; p.weight = weight;
        p.lore = new String[]{"§7Better luck next time!"};
        return p;
    }

    /** Currency this prize corresponds to (null if not COINS/GEMS). */
    public Currency asCurrency() {
        return switch (type) {
            case COINS -> Currency.COINS;
            case GEMS  -> Currency.GEMS;
            default    -> null;
        };
    }
}

package com.aethermon.core.luckydraw.model;

import java.util.List;

/**
 * A named collection of DrawPrize entries.
 *
 * Example pool: "Standard Draw" — costs 1 Gem, 10 prizes.
 * Multiple pools can exist (e.g. Standard, Premium, Event).
 */
public class DrawPool {

    /** Unique machine-readable ID, e.g. "standard". */
    public String id;

    /** Display name shown in the GUI header, e.g. "§b§lStandard Draw". */
    public String displayName;

    /** Short description lines for the pool picker GUI. */
    public String[] description;

    /** Cost in Gems to perform one spin. */
    public int costGems;

    /** Minecraft item shown as the pool icon in the picker GUI. */
    public String iconItemId;

    /** Ordered list of prizes in this pool. */
    public List<DrawPrize> prizes;

    /**
     * Picks a weighted random prize from this pool.
     * Uses a simple cumulative-weight walk.
     */
    public DrawPrize rollPrize() {
        int totalWeight = prizes.stream().mapToInt(p -> p.weight).sum();
        int roll = (int) (Math.random() * totalWeight);
        int cumulative = 0;
        for (DrawPrize prize : prizes) {
            cumulative += prize.weight;
            if (roll < cumulative) return prize;
        }
        return prizes.get(prizes.size() - 1); // fallback
    }
}

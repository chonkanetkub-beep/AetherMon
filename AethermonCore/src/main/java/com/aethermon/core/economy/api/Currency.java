package com.aethermon.core.economy.api;

/**
 * The two currencies on Aethermon.
 *
 * COINS  — earned in-game (quests, battles, shops, events). Main currency.
 * GEMS   — earned via quests and special events. Premium feel, never sold for real money.
 *
 * To add a third currency later, add it here and update DatabaseSchema.
 */
public enum Currency {

    COINS("Coins", "🪙", "coin", "coins"),
    GEMS("Gems",   "💎", "gem",  "gems");

    /** Display name shown to players. */
    public final String displayName;
    /** Unicode symbol shown in sidebar/GUI. */
    public final String symbol;
    /** Singular label for config/commands. */
    public final String singular;
    /** Plural label for config/commands. */
    public final String plural;

    Currency(String displayName, String symbol, String singular, String plural) {
        this.displayName = displayName;
        this.symbol      = symbol;
        this.singular    = singular;
        this.plural      = plural;
    }

    /** Returns e.g. "🪙 50,000 Coins" */
    public String format(java.math.BigDecimal amount) {
        return symbol + " " + String.format("%,.0f", amount) + " " + displayName;
    }
}

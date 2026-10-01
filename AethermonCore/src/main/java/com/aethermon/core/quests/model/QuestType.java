package com.aethermon.core.quests.model;

/**
 * Types of quest objectives.
 */
public enum QuestType {
    CATCH_POKEMON("Catch Pokémon"),
    WIN_BATTLE("Win Battles"),
    MINE_BLOCKS("Mine Blocks"),
    WALK_BLOCKS("Walk Blocks"),
    SHOP_SELL("Sell Items");

    public final String displayName;

    QuestType(String displayName) {
        this.displayName = displayName;
    }
}

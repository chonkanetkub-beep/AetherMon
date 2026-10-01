package com.aethermon.core.crates.model;

import com.aethermon.core.economy.api.Currency;
import java.util.List;

public class CrateReward {
    public enum Type {
        COINS, GEMS, ITEM, COMMAND
    }

    public String id;
    public String displayName;
    public String itemId;
    public Type type;
    public int amount = 1;
    public int weight = 10;
    public String command;
    public List<String> lore;

    public Currency asCurrency() {
        return type == Type.GEMS ? Currency.GEMS : Currency.COINS;
    }
}

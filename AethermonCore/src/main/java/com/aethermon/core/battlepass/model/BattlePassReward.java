package com.aethermon.core.battlepass.model;

import com.aethermon.core.economy.api.Currency;
import java.util.List;

public class BattlePassReward {
    public enum Type {
        COINS, GEMS, ITEM, COMMAND
    }

    public String id;
    public String displayName;
    public String itemId;
    public Type type;
    public int amount = 1;
    public String command;
    public List<String> lore;

    public Currency asCurrency() {
        return type == Type.GEMS ? Currency.GEMS : Currency.COINS;
    }
}

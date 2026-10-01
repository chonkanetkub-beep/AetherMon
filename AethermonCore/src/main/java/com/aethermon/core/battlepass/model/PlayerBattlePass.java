package com.aethermon.core.battlepass.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerBattlePass {
    public UUID playerUuid;
    public int season = 1;
    public int exp = 0;
    public int tier = 1;
    public boolean isPremium = false;
    public Set<Integer> claimedFreeTiers = new HashSet<>();
    public Set<Integer> claimedPremiumTiers = new HashSet<>();

    public boolean isFreeClaimed(int t) {
        return claimedFreeTiers.contains(t);
    }

    public boolean isPremiumClaimed(int t) {
        return claimedPremiumTiers.contains(t);
    }
}

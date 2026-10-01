package com.aethermon.core.crates.model;

import java.util.List;

public class Crate {
    public String id;
    public String displayName;
    public List<String> description;
    public String iconItemId;
    public String keyItemId;
    public String keyDisplayName;
    public List<String> keyLore;
    public List<CrateReward> rewards;

    public CrateReward rollReward() {
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }
        int totalWeight = rewards.stream().mapToInt(r -> r.weight).sum();
        int roll = (int) (Math.random() * totalWeight);
        int cumulative = 0;
        for (CrateReward reward : rewards) {
            cumulative += reward.weight;
            if (roll < cumulative) return reward;
        }
        return rewards.get(rewards.size() - 1);
    }
}

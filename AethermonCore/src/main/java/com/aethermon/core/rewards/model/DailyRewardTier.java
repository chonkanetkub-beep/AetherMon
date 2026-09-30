package com.aethermon.core.rewards.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Definition for a daily login reward day (1-30).
 */
public record DailyRewardTier(
    int day,
    String title,
    String iconItem,
    BigDecimal coins,
    BigDecimal gems,
    List<RewardItem> items,
    List<String> commands
) {
    public DailyRewardTier {
        if (coins == null) coins = BigDecimal.ZERO;
        if (gems == null) gems = BigDecimal.ZERO;
        if (items == null) items = List.of();
        if (commands == null) commands = List.of();
        if (title == null || title.isBlank()) title = "Day " + day;
        if (iconItem == null || iconItem.isBlank()) iconItem = "minecraft:chest";
    }
}

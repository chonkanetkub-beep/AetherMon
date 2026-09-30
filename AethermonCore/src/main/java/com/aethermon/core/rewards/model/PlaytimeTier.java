package com.aethermon.core.rewards.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Definition for an online playtime milestone reward.
 */
public record PlaytimeTier(
    String id,
    int requiredMinutes,
    String title,
    String iconItem,
    BigDecimal coins,
    BigDecimal gems,
    List<RewardItem> items,
    List<String> commands
) {
    public PlaytimeTier {
        if (id == null || id.isBlank()) id = String.valueOf(requiredMinutes);
        if (coins == null) coins = BigDecimal.ZERO;
        if (gems == null) gems = BigDecimal.ZERO;
        if (items == null) items = List.of();
        if (commands == null) commands = List.of();
        if (title == null || title.isBlank()) title = requiredMinutes + " Minutes Online";
        if (iconItem == null || iconItem.isBlank()) iconItem = "minecraft:clock";
    }
}

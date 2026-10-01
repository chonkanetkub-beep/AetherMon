package com.aethermon.core.quests.model;

import com.aethermon.core.rewards.model.RewardItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * Definition of a quest in quests.json pool.
 */
public record QuestDefinition(
    String id,
    String periodType, // "DAILY" or "WEEKLY"
    QuestType type,
    int target,
    String title,
    String description,
    String iconItem,
    BigDecimal coins,
    BigDecimal gems,
    List<RewardItem> items,
    List<String> commands
) {
    public QuestDefinition {
        if (coins == null) coins = BigDecimal.ZERO;
        if (gems == null) gems = BigDecimal.ZERO;
        if (items == null) items = List.of();
        if (commands == null) commands = List.of();
        if (title == null || title.isBlank()) title = "Quest " + id;
        if (description == null) description = "";
        if (iconItem == null || iconItem.isBlank()) iconItem = "minecraft:book";
        if (periodType == null || periodType.isBlank()) periodType = "DAILY";
    }
}

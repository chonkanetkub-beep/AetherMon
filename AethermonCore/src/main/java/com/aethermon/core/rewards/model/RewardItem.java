package com.aethermon.core.rewards.model;

/**
 * An item reward definition.
 */
public record RewardItem(
    String itemId,
    int count,
    String displayName
) {
    public RewardItem(String itemId, int count) {
        this(itemId, count, null);
    }
}

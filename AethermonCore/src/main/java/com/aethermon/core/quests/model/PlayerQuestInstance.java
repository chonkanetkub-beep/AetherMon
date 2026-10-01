package com.aethermon.core.quests.model;

import java.util.UUID;

/**
 * An assigned quest for an individual player.
 */
public class PlayerQuestInstance {

    private long dbId;
    private final UUID playerUuid;
    private final String periodType; // "DAILY" or "WEEKLY"
    private final String periodKey;  // "2026-10-01" or "2026-W40"
    private final String questId;
    private final int slotIndex;
    private int currentProgress;
    private final int targetAmount;
    private boolean completed;
    private boolean claimed;
    private QuestDefinition definition;

    public PlayerQuestInstance(long dbId, UUID playerUuid, String periodType, String periodKey,
                               String questId, int slotIndex, int currentProgress, int targetAmount,
                               boolean completed, boolean claimed, QuestDefinition definition) {
        this.dbId = dbId;
        this.playerUuid = playerUuid;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.questId = questId;
        this.slotIndex = slotIndex;
        this.currentProgress = currentProgress;
        this.targetAmount = targetAmount;
        this.completed = completed;
        this.claimed = claimed;
        this.definition = definition;
    }

    public long getDbId() { return dbId; }
    public void setDbId(long dbId) { this.dbId = dbId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPeriodType() { return periodType; }
    public String getPeriodKey() { return periodKey; }
    public String getQuestId() { return questId; }
    public int getSlotIndex() { return slotIndex; }
    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }
    public int getTargetAmount() { return targetAmount; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }
    public QuestDefinition getDefinition() { return definition; }
    public void setDefinition(QuestDefinition definition) { this.definition = definition; }

    public int getPercent() {
        if (targetAmount <= 0) return 100;
        return Math.min(100, (int) Math.round((currentProgress * 100.0) / targetAmount));
    }
}

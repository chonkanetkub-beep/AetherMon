package com.aethermon.core.rewards.model;

import java.util.UUID;

/**
 * Tracks a player's daily reward claims and streak.
 */
public class PlayerDailyData {

    private final UUID playerUuid;
    private String lastClaimDate; // YYYY-MM-DD
    private int currentStreak;    // 0 to 30
    private int totalClaims;

    public PlayerDailyData(UUID playerUuid, String lastClaimDate, int currentStreak, int totalClaims) {
        this.playerUuid = playerUuid;
        this.lastClaimDate = lastClaimDate != null ? lastClaimDate : "";
        this.currentStreak = currentStreak;
        this.totalClaims = totalClaims;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getLastClaimDate() { return lastClaimDate; }
    public void setLastClaimDate(String lastClaimDate) { this.lastClaimDate = lastClaimDate; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public int getTotalClaims() { return totalClaims; }
    public void setTotalClaims(int totalClaims) { this.totalClaims = totalClaims; }

    public boolean hasClaimedToday(String todayDate) {
        return lastClaimDate != null && lastClaimDate.equals(todayDate);
    }
}

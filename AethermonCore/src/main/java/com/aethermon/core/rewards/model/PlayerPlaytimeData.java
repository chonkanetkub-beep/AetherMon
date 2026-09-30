package com.aethermon.core.rewards.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks a player's active online playtime and claimed tiers for the current day.
 */
public class PlayerPlaytimeData {

    private final UUID playerUuid;
    private String trackingDate; // YYYY-MM-DD
    private int activeSeconds;
    private final Set<String> claimedTiers = new HashSet<>();
    private int totalPlaytimeSecs;

    public PlayerPlaytimeData(UUID playerUuid, String trackingDate, int activeSeconds, Set<String> claimedTiers, int totalPlaytimeSecs) {
        this.playerUuid = playerUuid;
        this.trackingDate = trackingDate != null ? trackingDate : "";
        this.activeSeconds = activeSeconds;
        if (claimedTiers != null) {
            this.claimedTiers.addAll(claimedTiers);
        }
        this.totalPlaytimeSecs = totalPlaytimeSecs;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getTrackingDate() { return trackingDate; }
    public void setTrackingDate(String trackingDate) { this.trackingDate = trackingDate; }
    public int getActiveSeconds() { return activeSeconds; }
    public void setActiveSeconds(int activeSeconds) { this.activeSeconds = activeSeconds; }
    public void addActiveSeconds(int seconds) {
        this.activeSeconds += seconds;
        this.totalPlaytimeSecs += seconds;
    }
    public Set<String> getClaimedTiers() { return Collections.unmodifiableSet(claimedTiers); }
    public boolean isTierClaimed(String tierId) { return claimedTiers.contains(tierId); }
    public void markTierClaimed(String tierId) { claimedTiers.add(tierId); }
    public void clearClaimedTiers() { claimedTiers.clear(); }
    public int getTotalPlaytimeSecs() { return totalPlaytimeSecs; }
    public void setTotalPlaytimeSecs(int totalPlaytimeSecs) { this.totalPlaytimeSecs = totalPlaytimeSecs; }

    public String serializeClaimedTiers() {
        return String.join(",", claimedTiers);
    }

    public static Set<String> deserializeClaimedTiers(String str) {
        Set<String> set = new HashSet<>();
        if (str != null && !str.isBlank()) {
            for (String part : str.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
        }
        return set;
    }
}

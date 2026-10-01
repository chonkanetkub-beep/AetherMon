package com.aethermon.core.duel.model;

import java.util.UUID;

/**
 * Represents an active or pending duel session between two players.
 *
 * Lifecycle:
 *   PENDING  → challenger sent /duel, waiting for target to accept
 *   ACTIVE   → both players accepted; Cobblemon battle is in progress
 *   DONE     → battle concluded; winner determined and wager settled
 *   CANCELLED→ timed out, denied, or cancelled manually
 */
public class DuelRecord {

    public enum State { PENDING, ACTIVE, DONE, CANCELLED }

    private final UUID challengerId;
    private final String challengerName;
    private final UUID targetId;
    private final String targetName;

    /** Wagered coin amount (0 = friendly duel, no money on the line). */
    private final long wagerCoins;

    private State state;
    private UUID winnerId;
    private long createdAt;  // System.currentTimeMillis() when challenge was sent

    public DuelRecord(UUID challengerId, String challengerName,
                      UUID targetId, String targetName,
                      long wagerCoins) {
        this.challengerId   = challengerId;
        this.challengerName = challengerName;
        this.targetId       = targetId;
        this.targetName     = targetName;
        this.wagerCoins     = wagerCoins;
        this.state          = State.PENDING;
        this.createdAt      = System.currentTimeMillis();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID   getChallengerId()   { return challengerId; }
    public String getChallengerName() { return challengerName; }
    public UUID   getTargetId()       { return targetId; }
    public String getTargetName()     { return targetName; }
    public long   getWagerCoins()     { return wagerCoins; }
    public State  getState()          { return state; }
    public UUID   getWinnerId()       { return winnerId; }
    public long   getCreatedAt()      { return createdAt; }

    // ── State transitions ─────────────────────────────────────────────────────

    public void activate()            { this.state = State.ACTIVE; }
    public void cancel()              { this.state = State.CANCELLED; }

    public void finish(UUID winner) {
        this.winnerId = winner;
        this.state    = State.DONE;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean involves(UUID playerId) {
        return challengerId.equals(playerId) || targetId.equals(playerId);
    }

    public UUID opponentOf(UUID playerId) {
        return playerId.equals(challengerId) ? targetId : challengerId;
    }

    public String opponentNameOf(UUID playerId) {
        return playerId.equals(challengerId) ? targetName : challengerName;
    }

    public boolean hasWager() { return wagerCoins > 0; }

    /** Returns true if the pending request has timed out (60 seconds). */
    public boolean isExpired() {
        return state == State.PENDING &&
               (System.currentTimeMillis() - createdAt) > 60_000L;
    }
}

package com.aethermon.core.duel.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.duel.model.DuelRecord;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyResult;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.economy.db.DatabaseManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core logic for the Duel system.
 *
 * Responsibilities:
 *  - Track PENDING challenges (challenger → target)
 *  - Track ACTIVE duels (both players keyed separately for O(1) lookup)
 *  - Handle wager escrow: withdraw on challenge accept, pay winner on resolve
 *  - Resolve duel outcome when Cobblemon fires BATTLE_VICTORY
 *  - Expire stale challenges automatically (60-second timeout)
 *  - Persist duel history to SQLite
 */
public class DuelService {

    // ── In-memory state ───────────────────────────────────────────────────────

    /** Pending challenges keyed by TARGET UUID (one pending challenge per target). */
    private final Map<UUID, DuelRecord> pendingByTarget = new ConcurrentHashMap<>();

    /**
     * Active duels keyed by BOTH participant UUIDs.
     * Both keys point to the same DuelRecord instance.
     */
    private final Map<UUID, DuelRecord> activeByPlayer = new ConcurrentHashMap<>();

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final EconomyService  economy;
    private final DatabaseManager db;
    private final MinecraftServer server;

    /** Background task that sweeps expired PENDING challenges every 10 s. */
    private final ScheduledExecutorService expireScheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aethermon-duel-expire");
            t.setDaemon(true);
            return t;
        });

    public DuelService(EconomyService economy, DatabaseManager db, MinecraftServer server) {
        this.economy = economy;
        this.db      = db;
        this.server  = server;
        expireScheduler.scheduleAtFixedRate(this::expireStale, 10, 10, TimeUnit.SECONDS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Challenger sends a duel request to target with optional coin wager.
     * All economy checks are async; result is communicated via player messages.
     */
    public void challenge(ServerPlayerEntity challenger, ServerPlayerEntity target, long wagerCoins) {

        UUID cId = challenger.getUuid();
        UUID tId = target.getUuid();

        // ── Synchronous validation (no I/O) ──────────────────────────────────

        if (cId.equals(tId)) {
            send(challenger, Formatting.RED, "You cannot duel yourself.");
            return;
        }
        if (isInDuel(cId)) {
            send(challenger, Formatting.RED, "You are already in a duel.");
            return;
        }
        if (isInDuel(tId)) {
            send(challenger, Formatting.RED, target.getName().getString() + " is already in a duel.");
            return;
        }
        // Prevent duplicate challenge spam to the same target
        DuelRecord existing = pendingByTarget.get(tId);
        if (existing != null && existing.getChallengerId().equals(cId)) {
            send(challenger, Formatting.YELLOW,
                 "You already sent a duel request to " + target.getName().getString() + ".");
            return;
        }

        // ── Wager escrow (async) ──────────────────────────────────────────────

        if (wagerCoins > 0) {
            BigDecimal amount = BigDecimal.valueOf(wagerCoins);
            economy.withdraw(cId, Currency.COINS, amount,
                             "duel_wager_escrow_vs_" + target.getName().getString())
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        createAndSendChallenge(challenger, target, wagerCoins);
                    } else {
                        economy.getBalance(cId, Currency.COINS).thenAccept(bal ->
                            send(challenger, Formatting.RED,
                                 "You need " + wagerCoins + " coins to wager but only have " +
                                 bal.longValue() + ".")
                        );
                    }
                });
        } else {
            createAndSendChallenge(challenger, target, 0L);
        }
    }

    /**
     * Target accepts a pending duel challenge.
     *
     * @return CompletableFuture&lt;DuelRecord&gt; — the accepted record (non-null on success),
     *         or null if validation fails.
     */
    public CompletableFuture<DuelRecord> accept(ServerPlayerEntity target) {
        UUID tId = target.getUuid();
        DuelRecord record = pendingByTarget.remove(tId);

        if (record == null) {
            send(target, Formatting.RED, "You have no pending duel request.");
            return CompletableFuture.completedFuture(null);
        }
        if (record.isExpired()) {
            refundChallenger(record, "Duel request expired.");
            send(target, Formatting.RED, "That duel request already expired.");
            return CompletableFuture.completedFuture(null);
        }

        ServerPlayerEntity challenger = server.getPlayerManager().getPlayer(record.getChallengerId());
        if (challenger == null) {
            refundChallenger(record, "Challenger went offline.");
            send(target, Formatting.RED, "The challenger is no longer online.");
            return CompletableFuture.completedFuture(null);
        }
        if (isInDuel(tId) || isInDuel(record.getChallengerId())) {
            refundChallenger(record, "Player already in a duel.");
            send(target, Formatting.RED, "One of the players is already in a duel.");
            return CompletableFuture.completedFuture(null);
        }

        // Escrow target's wager (async if needed)
        if (record.hasWager()) {
            BigDecimal amount = BigDecimal.valueOf(record.getWagerCoins());
            return economy.withdraw(tId, Currency.COINS, amount,
                                    "duel_wager_escrow_vs_" + record.getChallengerName())
                .thenApply(result -> {
                    if (!result.isSuccess()) {
                        // Refund challenger and abort
                        refundChallenger(record, "Target could not afford wager.");
                        economy.getBalance(tId, Currency.COINS).thenAccept(bal ->
                            send(target, Formatting.RED,
                                 "You need " + record.getWagerCoins() + " coins to accept this wager but only have " +
                                 bal.longValue() + ".")
                        );
                        return null;
                    }
                    return activateRecord(record);
                });
        } else {
            return CompletableFuture.completedFuture(activateRecord(record));
        }
    }

    /** Target denies a pending challenge — refunds challenger. */
    public boolean deny(ServerPlayerEntity target) {
        UUID tId = target.getUuid();
        DuelRecord record = pendingByTarget.remove(tId);
        if (record == null) {
            send(target, Formatting.RED, "You have no pending duel request.");
            return false;
        }
        record.cancel();
        refundChallenger(record, "Challenge denied.");
        send(target, Formatting.YELLOW, "Duel request from §b" + record.getChallengerName() + "§r declined.");
        ServerPlayerEntity ch = server.getPlayerManager().getPlayer(record.getChallengerId());
        if (ch != null) send(ch, Formatting.YELLOW, "§b" + target.getName().getString() + "§r denied your duel.");
        return true;
    }

    /** Challenger cancels their own outgoing pending request — refunds them. */
    public boolean cancel(ServerPlayerEntity challenger) {
        UUID cId = challenger.getUuid();
        Optional<Map.Entry<UUID, DuelRecord>> entry = pendingByTarget.entrySet().stream()
            .filter(e -> e.getValue().getChallengerId().equals(cId))
            .findFirst();

        if (entry.isEmpty()) {
            send(challenger, Formatting.RED, "You have no pending duel request to cancel.");
            return false;
        }

        pendingByTarget.remove(entry.get().getKey());
        DuelRecord record = entry.get().getValue();
        record.cancel();
        refundChallenger(record, "Challenge cancelled by challenger.");
        send(challenger, Formatting.YELLOW, "Duel request to §b" + record.getTargetName() + "§r cancelled.");
        ServerPlayerEntity tgt = server.getPlayerManager().getPlayer(record.getTargetId());
        if (tgt != null) send(tgt, Formatting.YELLOW, "§b" + challenger.getName().getString() + "§r cancelled the duel.");
        return true;
    }

    /**
     * Called by DuelHook when Cobblemon fires BATTLE_VICTORY.
     * Pays out the wager, notifies players, and persists history.
     */
    public void resolveBattle(UUID winnerUuid, UUID loserUuid) {
        DuelRecord record = activeByPlayer.get(winnerUuid);
        if (record == null) record = activeByPlayer.get(loserUuid);
        if (record == null) return;
        if (!record.involves(winnerUuid) || !record.involves(loserUuid)) return;
        if (record.getState() != DuelRecord.State.ACTIVE) return;

        record.finish(winnerUuid);
        activeByPlayer.remove(record.getChallengerId());
        activeByPlayer.remove(record.getTargetId());

        // Payout: winner receives both escrowed amounts
        if (record.hasWager()) {
            BigDecimal pot = BigDecimal.valueOf(record.getWagerCoins() * 2L);
            economy.deposit(winnerUuid, Currency.COINS, pot,
                            "duel_wager_win_vs_" + record.opponentNameOf(winnerUuid));
        }

        // Notify
        String wagerMsg = record.hasWager()
            ? " §e(+" + (record.getWagerCoins() * 2) + " coins)§r" : "";
        ServerPlayerEntity winner = server.getPlayerManager().getPlayer(winnerUuid);
        ServerPlayerEntity loser  = server.getPlayerManager().getPlayer(loserUuid);
        if (winner != null) send(winner, Formatting.GOLD,
            "🏆 You won the duel vs §b" + record.opponentNameOf(winnerUuid) + "§r!" + wagerMsg);
        if (loser != null) send(loser, Formatting.RED,
            "You lost the duel to §b" + record.opponentNameOf(loserUuid) + "§r.");

        persistHistory(record);
        AethermonCore.LOGGER.info("[Duel] Resolved: {} beat {} (wager: {} coins)",
            record.opponentNameOf(loserUuid), record.opponentNameOf(winnerUuid), record.getWagerCoins());
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    public boolean    isInDuel(UUID id)          { return activeByPlayer.containsKey(id); }
    public boolean    hasPending(UUID targetId)  { return pendingByTarget.containsKey(targetId); }
    public DuelRecord getActiveRecord(UUID id)   { return activeByPlayer.get(id); }
    public DuelRecord getPendingRecord(UUID id)  { return pendingByTarget.get(id); }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    private void createAndSendChallenge(ServerPlayerEntity challenger,
                                        ServerPlayerEntity target, long wagerCoins) {
        DuelRecord record = new DuelRecord(
            challenger.getUuid(), challenger.getName().getString(),
            target.getUuid(), target.getName().getString(),
            wagerCoins
        );
        pendingByTarget.put(target.getUuid(), record);

        String wagerStr = wagerCoins > 0 ? " for §e" + wagerCoins + " coins§r" : "";
        send(challenger, Formatting.GREEN,
             "Duel request sent to §b" + target.getName().getString() + "§r" + wagerStr + ". (60 s to accept)");
        target.sendMessage(Text.literal(
            "§6[Duel] §b" + challenger.getName().getString() +
            "§r challenges you to a Pokémon battle" + wagerStr + "!\n" +
            "  §a/duel accept §7to accept  |  §c/duel deny §7to decline"
        ));
        AethermonCore.LOGGER.info("[Duel] {} challenged {} (wager: {} coins)",
            challenger.getName().getString(), target.getName().getString(), wagerCoins);
    }

    private DuelRecord activateRecord(DuelRecord record) {
        record.activate();
        activeByPlayer.put(record.getChallengerId(), record);
        activeByPlayer.put(record.getTargetId(), record);
        AethermonCore.LOGGER.info("[Duel] {} accepted {}'s challenge",
            record.getTargetName(), record.getChallengerName());
        return record;
    }

    private void refundChallenger(DuelRecord record, String reason) {
        if (record.hasWager()) {
            BigDecimal amount = BigDecimal.valueOf(record.getWagerCoins());
            economy.deposit(record.getChallengerId(), Currency.COINS, amount,
                            "duel_wager_refund_" + reason.replace(" ", "_").toLowerCase());
            ServerPlayerEntity ch = server.getPlayerManager().getPlayer(record.getChallengerId());
            if (ch != null) send(ch, Formatting.YELLOW,
                "Your wager of §e" + record.getWagerCoins() + " coins§r has been refunded.");
        }
    }

    private void expireStale() {
        List<UUID> expired = pendingByTarget.entrySet().stream()
            .filter(e -> e.getValue().isExpired())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        for (UUID tId : expired) {
            DuelRecord record = pendingByTarget.remove(tId);
            if (record == null) continue;
            record.cancel();
            refundChallenger(record, "timeout");

            ServerPlayerEntity ch  = server.getPlayerManager().getPlayer(record.getChallengerId());
            ServerPlayerEntity tgt = server.getPlayerManager().getPlayer(tId);
            if (ch  != null) send(ch,  Formatting.YELLOW, "Your duel request to §b"   + record.getTargetName()     + "§r expired.");
            if (tgt != null) send(tgt, Formatting.YELLOW, "The duel request from §b" + record.getChallengerName() + "§r expired.");
        }
    }

    private void persistHistory(DuelRecord record) {
        String sql = """
            INSERT INTO duel_history
              (challenger_uuid, challenger_name, target_uuid, target_name,
               winner_uuid, wager_coins, fought_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getChallengerId().toString());
            ps.setString(2, record.getChallengerName());
            ps.setString(3, record.getTargetId().toString());
            ps.setString(4, record.getTargetName());
            ps.setString(5, record.getWinnerId() != null ? record.getWinnerId().toString() : null);
            ps.setLong(6, record.getWagerCoins());
            ps.executeUpdate();
        } catch (SQLException e) {
            AethermonCore.LOGGER.warn("[Duel] Failed to persist duel history: {}", e.getMessage());
        }
    }

    private void send(ServerPlayerEntity player, Formatting colour, String msg) {
        player.sendMessage(Text.literal("§6[Duel] " + colour + msg + "§r"));
    }

    public void shutdown() { expireScheduler.shutdown(); }
}

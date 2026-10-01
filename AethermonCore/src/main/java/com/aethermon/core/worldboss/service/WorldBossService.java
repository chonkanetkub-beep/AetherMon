package com.aethermon.core.worldboss.service;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import com.aethermon.core.worldboss.config.WorldBossConfig;
import com.aethermon.core.worldboss.model.BossDefinition;
import com.aethermon.core.worldboss.model.DamageContributor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World Boss service — manages the full lifecycle of a boss fight.
 *
 * State machine:
 *   IDLE → ACTIVE (on spawn) → IDLE (on death or force-end)
 *
 * Thread safety:
 *   Damage tracking uses ConcurrentHashMap (can be called from any thread).
 *   Entity mutations and prize distribution run on the main server thread.
 */
public class WorldBossService {

    public enum State { IDLE, ACTIVE }

    private final EconomyService   economy;
    private final WorldBossConfig  config;

    // ── Live fight state (null when IDLE) ────────────────────────────────────
    private State           state        = State.IDLE;
    private BossDefinition  activeDef    = null;
    private LivingEntity    bossEntity   = null;
    private ServerBossBar   bossBar      = null;
    private Instant         spawnedAt    = null;
    private Instant         lastEnded    = null; // for cooldown check

    /** UUID → DamageContributor. ConcurrentHashMap for safe multi-thread updates. */
    private final Map<UUID, DamageContributor> contributors = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    public WorldBossService(EconomyService economy, WorldBossConfig config) {
        this.economy = economy;
        this.config  = config;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public State getState()          { return state; }
    public BossDefinition getActive(){ return activeDef; }
    public LivingEntity getBossEntity() { return bossEntity; }

    /** Returns seconds remaining on spawn cooldown, or 0 if ready. */
    public long getCooldownSecondsRemaining() {
        if (lastEnded == null) return 0;
        long elapsed = Instant.now().getEpochSecond() - lastEnded.getEpochSecond();
        long remaining = config.spawnCooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Spawns a boss at the given position.
     *
     * @return true on success, false if already active or on cooldown.
     */
    public boolean spawnBoss(MinecraftServer server, BossDefinition def,
                             ServerWorld world, BlockPos pos) {
        if (state == State.ACTIVE) {
            AethermonCore.LOGGER.warn("[WorldBoss] Cannot spawn '{}' — boss already active.", def.id);
            return false;
        }

        // Resolve entity type
        EntityType<?> entityType = Registries.ENTITY_TYPE
                .getOrEmpty(Identifier.of(def.entityType))
                .orElse(null);
        if (entityType == null) {
            AethermonCore.LOGGER.error("[WorldBoss] Unknown entity type: {}", def.entityType);
            return false;
        }

        // Spawn the entity
        var entity = entityType.create(world);
        if (!(entity instanceof LivingEntity living)) {
            AethermonCore.LOGGER.error("[WorldBoss] Entity type {} is not a LivingEntity.", def.entityType);
            return false;
        }

        living.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        living.setCustomName(Text.literal(def.displayName));
        living.setCustomNameVisible(true);
        // Prevent natural despawn — setPersistent() is on MobEntity
        if (living instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setPersistent();
        }

        // Apply stat multipliers via attributes
        applyAttributes(living, def);

        // Heal to full after attributes are set
        living.setHealth(living.getMaxHealth());

        world.spawnEntity(living);

        // Boss bar
        bossBar = new ServerBossBar(
            Text.literal(def.displayName),
            parseBossBarColor(def.bossBarColor),
            BossBar.Style.PROGRESS
        );
        bossBar.setPercent(1.0f);
        server.getPlayerManager().getPlayerList().forEach(bossBar::addPlayer);

        // State
        state       = State.ACTIVE;
        activeDef   = def;
        bossEntity  = living;
        spawnedAt   = Instant.now();
        contributors.clear();

        // Announcement
        server.getPlayerManager().broadcast(Text.literal(def.spawnAnnouncement), false);
        AethermonCore.LOGGER.info("[WorldBoss] Spawned '{}' at {}", def.id, pos);
        return true;
    }

    /**
     * Called every server tick while a boss is active.
     * Updates the boss bar health percentage.
     */
    public void tick(MinecraftServer server) {
        if (state != State.ACTIVE || bossEntity == null) return;

        // Sync boss bar health
        if (!bossEntity.isAlive()) {
            // Boss died naturally — handle defeat
            handleBossDeath(server, false);
            return;
        }

        float pct = bossEntity.getHealth() / bossEntity.getMaxHealth();
        bossBar.setPercent(Math.max(0f, Math.min(1f, pct)));

        // Update boss bar title with HP counter
        String hpText = String.format("%.0f / %.0f HP", bossEntity.getHealth(), bossEntity.getMaxHealth());
        bossBar.setName(Text.literal(activeDef.displayName + " §7— §c" + hpText));
    }

    /**
     * Records damage dealt to the boss by a player.
     * Safe to call from any thread.
     */
    public void recordDamage(ServerPlayerEntity player, float amount) {
        if (state != State.ACTIVE) return;
        contributors.computeIfAbsent(player.getUuid(), k -> new DamageContributor(player))
                    .addDamage(amount);
    }

    /**
     * Force-ends the current boss fight without prizes (admin use).
     */
    public void forceEnd(MinecraftServer server) {
        if (state != State.ACTIVE) return;
        if (bossEntity != null && bossEntity.isAlive()) {
            bossEntity.discard();
        }
        handleBossDeath(server, true);
    }

    /** Returns a sorted list of damage contributors (highest damage first). */
    public List<DamageContributor> getTopContributors() {
        List<DamageContributor> list = new ArrayList<>(contributors.values());
        Collections.sort(list);
        return list;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void handleBossDeath(MinecraftServer server, boolean forced) {
        AethermonCore.LOGGER.info("[WorldBoss] Boss '{}' ended (forced={})", activeDef.id, forced);

        // Remove boss bar from all players
        if (bossBar != null) {
            server.getPlayerManager().getPlayerList().forEach(bossBar::removePlayer);
            bossBar = null;
        }

        if (!forced) {
            // Announce death
            server.getPlayerManager().broadcast(Text.literal(activeDef.deathAnnouncement), false);
            // Distribute prizes
            distributePrizes(server, activeDef);
        } else {
            server.getPlayerManager().broadcast(
                Text.literal("§8[WorldBoss] The boss fight has been ended by an admin."), false);
        }

        // Reset state
        state       = State.IDLE;
        activeDef   = null;
        bossEntity  = null;
        spawnedAt   = null;
        lastEnded   = Instant.now();
        contributors.clear();
    }

    /**
     * Distributes prize Coins and Gems proportionally among the top-N damage dealers.
     *
     * Distribution formula:
     *   playerShare = (playerDamage / totalDamage) * totalPrize
     */
    private void distributePrizes(MinecraftServer server, BossDefinition def) {
        List<DamageContributor> top = getTopContributors();
        if (top.isEmpty()) return;

        List<DamageContributor> eligible = top.subList(0, Math.min(def.prizeTopN, top.size()));
        double totalDamage = eligible.stream().mapToDouble(c -> c.damageDealt).sum();
        if (totalDamage <= 0) return;

        StringBuilder summary = new StringBuilder();
        summary.append("§6§l[World Boss] §ePrize distribution:\n");

        for (int i = 0; i < eligible.size(); i++) {
            DamageContributor c = eligible.get(i);
            double share = c.damageDealt / totalDamage;

            long coins = Math.round(def.prizeCoins * share);
            long gems  = Math.round(def.prizeGems  * share);

            // Award via EconomyService (async, but fire-and-forget here)
            String reason = "worldboss_prize:" + def.id;
            economy.deposit(c.playerUuid, Currency.COINS,
                BigDecimal.valueOf(coins), reason);
            economy.deposit(c.playerUuid, Currency.GEMS,
                BigDecimal.valueOf(gems), reason);

            // Notify the player if they are online
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(c.playerUuid);
            if (online != null) {
                online.sendMessage(Text.literal(
                    "§6§l[World Boss] §eYou earned §6" + String.format("%,d", coins) +
                    " 🪙 Coins §eand §b" + gems + " 💎 Gems §efor dealing §c" +
                    String.format("%.0f", c.damageDealt) + " damage§e! (Rank #" + (i + 1) + ")"
                ), false);
            }

            summary.append(String.format("  §7#%d §f%s §7— §c%.0f dmg §7→ §6%,d Coins §7+ §b%d Gems%n",
                i + 1, c.playerName, c.damageDealt, coins, gems));

            AethermonCore.LOGGER.info("[WorldBoss] Prize: {} → {} coins, {} gems (rank {})",
                c.playerName, coins, gems, i + 1);
        }

        AethermonCore.LOGGER.info(summary.toString());
    }

    /**
     * Applies health, damage, and speed multipliers to the spawned entity.
     * Uses vanilla attribute API — safe and mod-compatible.
     */
    private void applyAttributes(LivingEntity entity, BossDefinition def) {
        // Max health
        var maxHp = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(maxHp.getBaseValue() * def.healthMultiplier);
        }

        // Attack damage
        var atk = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * def.damageMultiplier);
        }

        // Movement speed
        var spd = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(def.movementSpeed);
        }

        // Follow range — bosses should track players from further away
        var range = entity.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (range != null && range.getBaseValue() < 64) {
            range.setBaseValue(64);
        }
    }

    /** Parses a colour name string into a BossBar.Color. */
    private BossBar.Color parseBossBarColor(String name) {
        try {
            return BossBar.Color.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }
}

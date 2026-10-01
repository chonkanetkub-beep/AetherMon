package com.aethermon.core.worldboss.hook;

import com.aethermon.core.worldboss.service.WorldBossService;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Hooks into damage events to track player damage dealt to the boss entity.
 *
 * Uses ServerLivingEntityEvents.ALLOW_DAMAGE — fires just before damage is applied,
 * giving us the exact amount to record.
 */
public class BossDamageHook {

    public static void register(WorldBossService service) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Only care when a player is the attacker and the entity is the active boss
            if (service.getState() != WorldBossService.State.ACTIVE) return true;
            if (service.getBossEntity() == null) return true;
            if (!entity.getUuid().equals(service.getBossEntity().getUuid())) return true;

            if (source.getAttacker() instanceof ServerPlayerEntity player) {
                // Clamp to remaining HP so we don't over-record on killing blow
                float clamped = Math.min(amount, entity.getHealth());
                service.recordDamage(player, clamped);
            }
            return true; // always allow — we just observe
        });
    }
}

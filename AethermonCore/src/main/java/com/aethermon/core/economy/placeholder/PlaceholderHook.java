package com.aethermon.core.economy.placeholder;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyService;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;

/**
 * Hooks into PlaceholderAPI (eu.pb4:placeholder-api) if present.
 * Exposes placeholders for styled-sidebars, styled-chat, tablist, etc.
 *
 * Placeholders:
 *   %aethermon:coins%      — e.g. "50,000"
 *   %aethermon:gems%       — e.g. "25"
 *   %aethermon:coins_raw%  — e.g. "50000"
 *   %aethermon:gems_raw%   — e.g. "25"
 */
public class PlaceholderHook {

    public static void register(EconomyService service) {
        if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            AethermonCore.LOGGER.info("[PlaceholderHook] PlaceholderAPI not present, skipping placeholder registration.");
            return;
        }

        try {
            AethermonCore.LOGGER.info("[PlaceholderHook] Registering Aethermon placeholders...");

            // %aethermon:coins%
            Placeholders.register(Identifier.of("aethermon", "coins"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player context");
                BigDecimal bal = service.getBalance(ctx.player().getUuid(), Currency.COINS).getNow(BigDecimal.ZERO);
                return PlaceholderResult.value(Currency.COINS.format(bal));
            });

            // %aethermon:gems%
            Placeholders.register(Identifier.of("aethermon", "gems"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player context");
                BigDecimal bal = service.getBalance(ctx.player().getUuid(), Currency.GEMS).getNow(BigDecimal.ZERO);
                return PlaceholderResult.value(Currency.GEMS.format(bal));
            });

            // %aethermon:coins_raw%
            Placeholders.register(Identifier.of("aethermon", "coins_raw"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player context");
                BigDecimal bal = service.getBalance(ctx.player().getUuid(), Currency.COINS).getNow(BigDecimal.ZERO);
                return PlaceholderResult.value(bal.toPlainString());
            });

            // %aethermon:gems_raw%
            Placeholders.register(Identifier.of("aethermon", "gems_raw"), (ctx, arg) -> {
                if (!ctx.hasPlayer()) return PlaceholderResult.invalid("No player context");
                BigDecimal bal = service.getBalance(ctx.player().getUuid(), Currency.GEMS).getNow(BigDecimal.ZERO);
                return PlaceholderResult.value(bal.toPlainString());
            });

            AethermonCore.LOGGER.info("[PlaceholderHook] Registered %aethermon:coins% and %aethermon:gems% successfully!");
        } catch (Throwable t) {
            AethermonCore.LOGGER.warn("[PlaceholderHook] Failed to register placeholders: {}", t.getMessage());
        }
    }
}

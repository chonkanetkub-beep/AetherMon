package com.aethermon.core.market.model;

import com.aethermon.core.economy.api.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketDelivery(
    int id,
    UUID playerUuid,
    String type, // ITEM, MONEY
    Currency currency,
    BigDecimal amount,
    String itemNbt,
    String itemDisplayName,
    boolean claimed,
    Instant createdAt
) {
    public boolean isMoney() {
        return "MONEY".equalsIgnoreCase(type);
    }

    public boolean isItem() {
        return "ITEM".equalsIgnoreCase(type);
    }
}

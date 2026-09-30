package com.aethermon.core.market.model;

import com.aethermon.core.economy.api.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketListing(
    int id,
    UUID sellerUuid,
    String sellerName,
    String itemNbt,
    String itemId,
    int itemCount,
    String itemDisplayName,
    Currency currency,
    BigDecimal price,
    Instant createdAt,
    Instant expiresAt,
    String status // ACTIVE, SOLD, CANCELLED, EXPIRED
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}

package com.aethermon.core.shop.model;

import com.aethermon.core.economy.api.Currency;

import java.math.BigDecimal;

/**
 * Represents a single tradeable item in the server shop.
 */
public record ShopItem(
    String itemId,
    String displayName,
    Currency currency,
    BigDecimal buyPrice,
    BigDecimal sellPrice,
    int slot
) {
    public boolean isBuyable() {
        return buyPrice != null && buyPrice.compareTo(BigDecimal.ZERO) >= 0;
    }

    public boolean isSellable() {
        return sellPrice != null && sellPrice.compareTo(BigDecimal.ZERO) >= 0;
    }
}

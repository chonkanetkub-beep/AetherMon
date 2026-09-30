package com.aethermon.core.shop.model;

import java.util.List;

/**
 * Represents a category tab in the server shop.
 */
public record ShopCategory(
    String id,
    String displayName,
    String iconItem,
    int iconSlot,
    List<ShopItem> items
) {}

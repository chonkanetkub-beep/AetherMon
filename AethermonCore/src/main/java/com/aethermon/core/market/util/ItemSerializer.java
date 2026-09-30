package com.aethermon.core.market.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;

public class ItemSerializer {

    public static String serialize(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        if (stack.isEmpty()) return "";
        try {
            NbtElement element = ItemStack.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), stack).getOrThrow();
            return element.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack deserialize(String nbtString, RegistryWrapper.WrapperLookup registries) {
        if (nbtString == null || nbtString.isBlank()) return ItemStack.EMPTY;
        try {
            NbtElement element = StringNbtReader.parse(nbtString);
            return ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), element).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}

package com.aethermon.core.crates.gui;

import com.aethermon.core.crates.model.Crate;
import com.aethermon.core.crates.model.CrateReward;
import com.aethermon.core.crates.service.CrateService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class CratePreviewGui extends GenericContainerScreenHandler {

    private final ServerPlayerEntity player;
    private final CrateService service;
    private final Crate crate;

    public CratePreviewGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                           ServerPlayerEntity player, CrateService service, Crate crate) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.service = service;
        this.crate = crate;
    }

    public static void open(ServerPlayerEntity player, CrateService service, Crate crate) {
        SimpleInventory inv = new SimpleInventory(54);
        populate(inv, service, crate);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.literal("§6Preview: " + crate.displayName); }
            @Override public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory pi, PlayerEntity p) {
                return new CratePreviewGui(syncId, pi, inv, player, service, crate);
            }
        });
    }

    private static void populate(SimpleInventory inv, CrateService service, Crate crate) {
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 54; i++) inv.setStack(i, border.copy());

        // Header
        ItemStack header = service.resolveItem(crate.iconItemId);
        header.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lRewards: " + crate.displayName));
        header.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Previewing all possible rewards and odds."),
            Text.literal("§8All rewards are weighted fairly.")
        )));
        inv.setStack(4, header);

        // Populate rewards in rows 2-5 (slots 10..16, 19..25, 28..34, 37..43)
        List<CrateReward> rewards = crate.rewards;
        int totalWeight = rewards.stream().mapToInt(r -> r.weight).sum();

        int[] rewardSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < Math.min(rewards.size(), rewardSlots.length); i++) {
            CrateReward reward = rewards.get(i);
            ItemStack item = service.resolveItem(reward.itemId);
            item.setCount(Math.min(64, Math.max(1, reward.amount)));

            boolean isRare = reward.weight <= 6;
            String name = (isRare ? "§6§l★ " : "§f") + reward.displayName;
            item.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));

            double chance = totalWeight > 0 ? (reward.weight * 100.0) / totalWeight : 0.0;
            List<Text> lore = new ArrayList<>();
            if (reward.lore != null) {
                for (String line : reward.lore) lore.add(Text.literal(line));
            }
            lore.add(Text.literal(""));
            lore.add(Text.literal("§7Chance: §e" + String.format("%.1f%%", chance)));
            if (isRare) {
                lore.add(Text.literal("§d§l★ RARE REWARD ★"));
                item.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }

            item.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(rewardSlots[i], item);
        }

        // Back button
        ItemStack back = new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l« Back to Crates"));
        inv.setStack(49, back);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex == 49) {
            CratesMenuGui.open(player, service);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}

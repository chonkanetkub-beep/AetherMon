package com.aethermon.core.battlepass.gui;

import com.aethermon.core.battlepass.config.BattlePassConfig;
import com.aethermon.core.battlepass.model.BattlePassReward;
import com.aethermon.core.battlepass.model.BattlePassTier;
import com.aethermon.core.battlepass.model.PlayerBattlePass;
import com.aethermon.core.battlepass.service.BattlePassService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

public class BattlePassGui extends GenericContainerScreenHandler {

    private final ServerPlayerEntity player;
    private final BattlePassService service;
    private final PlayerBattlePass pass;
    private int page = 0;

    private static final int TIERS_PER_PAGE = 7;
    private static final int[] FREE_ROW_SLOTS    = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] TIER_ROW_SLOTS    = {19, 20, 21, 22, 23, 24, 25};
    private static final int[] PREMIUM_ROW_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    public BattlePassGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                         ServerPlayerEntity player, BattlePassService service, PlayerBattlePass pass, int page) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.service = service;
        this.pass = pass;
        this.page = page;
    }

    public static void open(ServerPlayerEntity player, BattlePassService service) {
        service.getPlayerPass(player.getUuid()).thenAccept(pass -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                int initialPage = Math.min(4, Math.max(0, (pass.tier - 1) / TIERS_PER_PAGE));
                openPage(player, service, pass, initialPage);
            });
        });
    }

    private static void openPage(ServerPlayerEntity player, BattlePassService service, PlayerBattlePass pass, int page) {
        SimpleInventory inv = new SimpleInventory(54);
        populate(inv, service, pass, page);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.literal(service.getConfig().seasonName); }
            @Override public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory pi, PlayerEntity p) {
                return new BattlePassGui(syncId, pi, inv, player, service, pass, page);
            }
        });
    }

    private static void populate(SimpleInventory inv, BattlePassService service, PlayerBattlePass pass, int page) {
        BattlePassConfig config = service.getConfig();

        // Dark Gray Border
        ItemStack border = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 54; i++) inv.setStack(i, border.copy());

        // Header: Nether Star
        ItemStack header = new ItemStack(pass.isPremium ? Items.NETHER_STAR : Items.ENDER_EYE);
        header.set(DataComponentTypes.CUSTOM_NAME, Text.literal(config.seasonName));

        int currentTierProgress = pass.exp % config.expPerTier;
        if (pass.tier >= config.maxTier) {
            currentTierProgress = config.expPerTier;
        }

        String progressBar = makeProgressBar(currentTierProgress, config.expPerTier, 10);
        List<Text> headerLore = new ArrayList<>();
        headerLore.add(Text.literal("§7Your Status: " + (pass.isPremium ? "§6§lPREMIUM ACTIVE" : "§7FREE TRACK")));
        headerLore.add(Text.literal("§7Current Tier: §e§lTier " + pass.tier + " §7/ §e" + config.maxTier));
        headerLore.add(Text.literal("§7EXP Progress: §a" + pass.exp + " §7Total EXP"));
        headerLore.add(Text.literal("§8[" + progressBar + "§8] §a" + currentTierProgress + " §7/ §f" + config.expPerTier));
        headerLore.add(Text.literal(""));
        headerLore.add(Text.literal("§7Catch Pokémon & battle to earn Pass EXP!"));
        header.set(DataComponentTypes.LORE, new LoreComponent(headerLore));
        if (pass.isPremium) {
            header.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        inv.setStack(4, header);

        // Sidebar indicators
        ItemStack freeLabel = new ItemStack(Items.WHITE_STAINED_GLASS_PANE);
        freeLabel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§f§lFREE TRACK"));
        inv.setStack(9, freeLabel);
        inv.setStack(17, freeLabel.copy());

        ItemStack tierLabel = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
        tierLabel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lTIER LEVEL"));
        inv.setStack(18, tierLabel);
        inv.setStack(26, tierLabel.copy());

        ItemStack premLabel = new ItemStack(Items.ORANGE_STAINED_GLASS_PANE);
        premLabel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§lPREMIUM TRACK"));
        inv.setStack(27, premLabel);
        inv.setStack(35, premLabel.copy());

        // Populate Tiers for this page
        int startTier = (page * TIERS_PER_PAGE) + 1;
        for (int col = 0; col < TIERS_PER_PAGE; col++) {
            int t = startTier + col;
            if (t > config.maxTier) {
                // Empty slots
                ItemStack blank = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                blank.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
                inv.setStack(FREE_ROW_SLOTS[col], blank.copy());
                inv.setStack(TIER_ROW_SLOTS[col], blank.copy());
                inv.setStack(PREMIUM_ROW_SLOTS[col], blank.copy());
                continue;
            }

            Optional<BattlePassTier> tierOpt = config.getTier(t);
            if (tierOpt.isEmpty()) continue;
            BattlePassTier tier = tierOpt.get();

            boolean isUnlocked = pass.tier >= t;

            // 1. Free Reward Item
            if (tier.freeReward != null) {
                boolean claimed = pass.isFreeClaimed(t);
                boolean claimable = isUnlocked && !claimed;
                inv.setStack(FREE_ROW_SLOTS[col], buildRewardDisplay(service, tier.freeReward, claimable, claimed, isUnlocked, false));
            } else {
                ItemStack noReward = new ItemStack(Items.BARRIER);
                noReward.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§7No Free Reward"));
                inv.setStack(FREE_ROW_SLOTS[col], noReward);
            }

            // 2. Tier Indicator
            ItemStack tierIndicator;
            if (isUnlocked) {
                boolean allClaimed = pass.isFreeClaimed(t) && (!pass.isPremium || pass.isPremiumClaimed(t));
                if (allClaimed) {
                    tierIndicator = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
                    tierIndicator.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lTier " + t + " (Completed)"));
                } else {
                    tierIndicator = new ItemStack(Items.YELLOW_STAINED_GLASS_PANE);
                    tierIndicator.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lTier " + t + " (Rewards Available!)"));
                    tierIndicator.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                }
            } else {
                tierIndicator = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                tierIndicator.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lTier " + t + " §7(Locked)"));
            }
            inv.setStack(TIER_ROW_SLOTS[col], tierIndicator);

            // 3. Premium Reward Item
            if (tier.premiumReward != null) {
                boolean claimed = pass.isPremiumClaimed(t);
                boolean claimable = isUnlocked && pass.isPremium && !claimed;
                inv.setStack(PREMIUM_ROW_SLOTS[col], buildRewardDisplay(service, tier.premiumReward, claimable, claimed, isUnlocked, true));
            }
        }

        // Bottom row actions
        // Previous page
        if (page > 0) {
            ItemStack prev = new ItemStack(Items.ARROW);
            prev.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§l« Previous Tiers (Page " + page + ")"));
            inv.setStack(45, prev);
        }

        // Claim All Available
        ItemStack claimAll = new ItemStack(Items.HOPPER);
        claimAll.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§a§lClaim All Available Rewards"));
        claimAll.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Instantly claim all unlocked rewards"),
            Text.literal("§7for both Free and Premium tracks!"),
            Text.literal(""),
            Text.literal("§eClick to claim all!")
        )));
        claimAll.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        inv.setStack(48, claimAll);

        // Premium Unlock Button
        if (!pass.isPremium) {
            ItemStack unlockPrem = new ItemStack(Items.AMETHYST_SHARD);
            unlockPrem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l★ Unlock Premium Pass ★"));
            unlockPrem.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§7Unlock all 30 tiers of premium rewards!"),
                Text.literal("§8Includes Master Balls, Ability Patches & Keys."),
                Text.literal(""),
                Text.literal("§aCost: §b" + config.premiumCostGems + " 💎 Gems"),
                Text.literal(""),
                Text.literal("§eClick to unlock now!")
            )));
            unlockPrem.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            inv.setStack(49, unlockPrem);
        } else {
            ItemStack unlocked = new ItemStack(Items.NETHER_STAR);
            unlocked.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l★ Premium Pass Active ★"));
            unlocked.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("§aYou have full access to all Premium Rewards!"),
                Text.literal("§7Reach higher tiers to claim them.")
            )));
            inv.setStack(49, unlocked);
        }

        // Close
        ItemStack close = new ItemStack(Items.BARRIER);
        close.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§c§lClose"));
        inv.setStack(50, close);

        // Next page
        int maxPages = (config.maxTier - 1) / TIERS_PER_PAGE;
        if (page < maxPages) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lNext Tiers (Page " + (page + 2) + ") »"));
            inv.setStack(53, next);
        }
    }

    private static ItemStack buildRewardDisplay(BattlePassService service, BattlePassReward reward,
                                                boolean claimable, boolean claimed, boolean isUnlocked, boolean isPremiumTrack) {
        ItemStack item = service.resolveItem(reward.itemId);
        item.setCount(Math.min(64, Math.max(1, reward.amount)));

        String prefix = isPremiumTrack ? "§6[Premium] " : "§f[Free] ";
        item.set(DataComponentTypes.CUSTOM_NAME, Text.literal(prefix + reward.displayName));

        List<Text> lore = new ArrayList<>();
        if (reward.lore != null) {
            for (String line : reward.lore) lore.add(Text.literal(line));
        }
        lore.add(Text.literal(""));
        if (claimed) {
            lore.add(Text.literal("§a✔ Claimed"));
        } else if (claimable) {
            lore.add(Text.literal("§a▶ CLICK TO CLAIM REWARD! ◀"));
            item.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else if (!isUnlocked) {
            lore.add(Text.literal("§c🔒 Locked (Reach this tier to unlock)"));
        } else if (isPremiumTrack) {
            lore.add(Text.literal("§6🔒 Requires Premium Pass (Click slot 49 to unlock)"));
        }

        item.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return item;
    }

    private static String makeProgressBar(int current, int max, int length) {
        float ratio = max > 0 ? (float) current / max : 0f;
        int greenCount = Math.min(length, Math.max(0, (int) (ratio * length)));
        return "§a" + "■".repeat(greenCount) + "§7" + "■".repeat(length - greenCount);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex < 0 || slotIndex >= 54) return;

        // Navigation
        if (slotIndex == 45 && page > 0) {
            openPage(player, service, pass, page - 1);
            return;
        }

        int maxPages = (service.getConfig().maxTier - 1) / TIERS_PER_PAGE;
        if (slotIndex == 53 && page < maxPages) {
            openPage(player, service, pass, page + 1);
            return;
        }

        // Close
        if (slotIndex == 50) {
            player.closeHandledScreen();
            return;
        }

        // Claim All Available
        if (slotIndex == 48) {
            int claimed = service.claimAllAvailable(player);
            if (claimed > 0) {
                openPage(player, service, pass, page);
            }
            return;
        }

        // Unlock Premium
        if (slotIndex == 49 && !pass.isPremium) {
            service.buyPremium(player).thenAccept(success -> {
                if (player.getServer() == null) return;
                player.getServer().execute(() -> {
                    if (success) {
                        openPage(player, service, pass, page);
                    }
                });
            });
            return;
        }

        // Click on individual free rewards
        int startTier = (page * TIERS_PER_PAGE) + 1;
        for (int col = 0; col < TIERS_PER_PAGE; col++) {
            int t = startTier + col;
            if (slotIndex == FREE_ROW_SLOTS[col]) {
                if (service.claimReward(player, t, false)) {
                    openPage(player, service, pass, page);
                }
                return;
            }
            if (slotIndex == PREMIUM_ROW_SLOTS[col]) {
                if (service.claimReward(player, t, true)) {
                    openPage(player, service, pass, page);
                }
                return;
            }
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

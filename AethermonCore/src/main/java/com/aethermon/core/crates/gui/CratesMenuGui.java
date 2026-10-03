package com.aethermon.core.crates.gui;

import com.aethermon.core.crates.model.Crate;
import com.aethermon.core.crates.service.CrateService;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CratesMenuGui extends GenericContainerScreenHandler {

    private final ServerPlayerEntity player;
    private final CrateService service;
    private final Map<Integer, Crate> slotToCrate = new HashMap<>();

    public CratesMenuGui(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                         ServerPlayerEntity player, CrateService service) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
        this.player = player;
        this.service = service;
    }

    public static void open(ServerPlayerEntity player, CrateService service) {
        SimpleInventory inv = new SimpleInventory(54);
        List<Crate> crates = service.getCrates();

        // Query virtual keys for all crates async
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<String, Integer> virtualKeys = new HashMap<>();

        for (Crate crate : crates) {
            futures.add(service.getVirtualKeys(player.getUuid(), crate.id).thenAccept(amt -> {
                synchronized (virtualKeys) {
                    virtualKeys.put(crate.id, amt);
                }
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                populate(inv, crates, player, service, virtualKeys);
                player.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override public Text getDisplayName() { return Text.literal("§6§l✦ Mystery Crates ✦"); }
                    @Override public GenericContainerScreenHandler createMenu(int syncId, PlayerInventory pi, PlayerEntity p) {
                        CratesMenuGui gui = new CratesMenuGui(syncId, pi, inv, player, service);
                        gui.initSlotMapping(crates);
                        return gui;
                    }
                });
            });
        });
    }

    private void initSlotMapping(List<Crate> crates) {
        int[] slots = getCrateSlots(crates.size());
        for (int i = 0; i < Math.min(crates.size(), slots.length); i++) {
            slotToCrate.put(slots[i], crates.get(i));
        }
    }

    private static int[] getCrateSlots(int count) {
        if (count == 1) return new int[]{22};
        if (count == 2) return new int[]{21, 23};
        if (count == 3) return new int[]{20, 22, 24};
        if (count == 4) return new int[]{20, 22, 24, 31};
        return new int[]{19, 21, 23, 25, 29, 31, 33};
    }

    private static void populate(SimpleInventory inv, List<Crate> crates, ServerPlayerEntity player,
                                 CrateService service, Map<String, Integer> virtualKeys) {
        // Border: Cyan stained glass
        ItemStack border = new ItemStack(Items.CYAN_STAINED_GLASS_PANE);
        border.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 54; i++) inv.setStack(i, border.copy());

        // Header: Ender chest
        ItemStack header = new ItemStack(Items.ENDER_CHEST);
        header.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§6§l★ Aethermon Mystery Crates ★"));
        header.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Open crates using physical or virtual keys!"),
            Text.literal("§7Win rare Pokémon items, currency & consumables."),
            Text.literal(""),
            Text.literal("§eLeft-Click §7to open a crate"),
            Text.literal("§eRight-Click §7to preview rewards")
        )));
        inv.setStack(4, header);

        // Crates
        int[] slots = getCrateSlots(crates.size());
        for (int i = 0; i < Math.min(crates.size(), slots.length); i++) {
            Crate crate = crates.get(i);
            int vKeys = virtualKeys.getOrDefault(crate.id, 0);
            int pKeys = service.countPhysicalKeys(player, crate);
            int total = vKeys + pKeys;

            ItemStack icon = service.resolveItem(crate.iconItemId);
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(crate.displayName));

            List<Text> lore = new ArrayList<>();
            if (crate.description != null) {
                for (String line : crate.description) lore.add(Text.literal(line));
            }
            lore.add(Text.literal(""));
            lore.add(Text.literal("§7Your Keys: " + (total > 0 ? "§a§l" + total : "§c0")));
            lore.add(Text.literal("§8 • Virtual: §f" + vKeys + " §8| Physical: §f" + pKeys));
            lore.add(Text.literal(""));
            if (total > 0) {
                lore.add(Text.literal("§a▶ Left-Click to Open Crate!"));
            } else {
                lore.add(Text.literal("§c✕ You don't have any keys for this!"));
            }
            lore.add(Text.literal("§e▶ Right-Click to Preview Rewards"));

            icon.set(DataComponentTypes.LORE, new LoreComponent(lore));
            if (total > 0) {
                icon.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            inv.setStack(slots[i], icon);
        }

        // Help info
        ItemStack info = new ItemStack(Items.PAPER);
        info.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lHow to get Keys?"));
        info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("§7Keys can be obtained from:"),
            Text.literal("§8 • §6World Boss Raids §7(/worldboss)"),
            Text.literal("§8 • §aDaily & Weekly Quests §7(/quests)"),
            Text.literal("§8 • §dLucky Draw Roulette §7(/luckydraw)"),
            Text.literal("§8 • §bServer Shop §7(/shop)")
        )));
        inv.setStack(49, info);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
        if (slotIndex < 0 || slotIndex >= 54) return;

        Crate crate = slotToCrate.get(slotIndex);
        if (crate == null) return;

        // button == 1 -> Right click -> Preview
        if (button == 1) {
            CratePreviewGui.open(player, service, crate);
            return;
        }

        // Left click -> Open Crate
        service.consumeKey(player, crate).thenAccept(success -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                if (!success) {
                    player.sendMessage(Text.literal("§cYou do not have a " + crate.keyDisplayName + " §cto open this crate!"), false);
                    player.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1f, 1f);
                    return;
                }
                CrateAnimationGui.open(player, service, crate);
            });
        });
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

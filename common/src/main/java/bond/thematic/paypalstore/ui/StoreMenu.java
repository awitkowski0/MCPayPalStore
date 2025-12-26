package bond.thematic.paypalstore.ui;

import bond.thematic.paypalstore.OrderManager;
import bond.thematic.paypalstore.config.StoreConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

public class StoreMenu extends ChestMenu {
    public StoreMenu(int syncId, Inventory playerInventory, Container container) {
        super(MenuType.GENERIC_9x3, syncId, playerInventory, container, 3);
    }

    public static StoreMenu create(int syncId, Inventory playerInventory, Container container) {
        return new StoreMenu(syncId, playerInventory, container);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.getContainer().getContainerSize()) {
            // Clicked in the chest part
            Slot slot = this.slots.get(slotId);
            if (slot.hasItem()) {
                // Handle purchase
                handlePurchase(player, slotId);

                // Cancel the actual pickup
                this.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
                // We typically need to update the client inventory to prevent ghost items,
                // but since we override logic, just returning or doing nothing might leave
                // ghost items if we don't sync.
                // However, 'clicked' is server-side. We should NOT call super.clicked() if we
                // want to cancel?
                // Actually, 'super.clicked' does the logic. If we don't call it, nothing
                // happens (cancelled).
                return;
            }
        }

        // Prevent moving items into the shop
        if (slotId >= 0) {
            if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP) {
                return; // Cancel
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    private void handlePurchase(Player player, int slotId) {
        StoreConfig.StoreItem item = StoreConfig.get().items.get(slotId); // Simple mapping for now
        if (item != null && player instanceof ServerPlayer serverPlayer) {
            player.closeContainer();

            if (item.paymentUrl != null && !item.paymentUrl.isEmpty()) {
                // Use static NCP link
                String finalUrl = item.paymentUrl;
                if (!finalUrl.contains("?")) {
                    finalUrl += "?custom=" + serverPlayer.getStringUUID();
                } else {
                    finalUrl += "&custom=" + serverPlayer.getStringUUID();
                }

                Component link = Component.literal(" [CLICK TO PAY] ")
                        .setStyle(Style.EMPTY
                                .withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, finalUrl)));

                player.sendSystemMessage(Component.literal("Opening payment link for " + item.name + "...")
                        .withStyle(ChatFormatting.YELLOW).append(link));
                player.sendSystemMessage(Component
                        .literal("NOTE: verification for this item is manual or requires server-side IPN setup.")
                        .withStyle(ChatFormatting.RED));
                return;
            }

            OrderManager.createOrder(serverPlayer, item.price, item.currency, () -> {
                // Execute commands
                for (String cmd : item.commands) {
                    serverPlayer.getServer().getCommands().performPrefixedCommand(
                            serverPlayer.getServer().createCommandSourceStack(),
                            cmd.replace("%player%", serverPlayer.getGameProfile().getName()));
                }
            });
        }
    }

    // Override to prevent shift-clicking items out/in if needed.
    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}

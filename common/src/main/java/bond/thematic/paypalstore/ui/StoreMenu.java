package bond.thematic.paypalstore.ui;

import bond.thematic.paypalstore.config.StoreConfig;
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
                // Handle interaction
                handleInteraction(player, slotId, button);

                // Cancel the actual pickup
                this.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
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

    private void handleInteraction(Player player, int slotId, int button) {
        Slot slot = this.slots.get(slotId);
        if (!slot.hasItem())
            return;

        net.minecraft.world.item.ItemStack stack = slot.getItem();
        if (!stack.hasTag() || !stack.getTag().contains("store_item_id"))
            return;

        String itemId = stack.getTag().getString("store_item_id");

        StoreConfig.StoreItem item = StoreConfig.get().items.stream()
                .filter(i -> i.id.equals(itemId))
                .findFirst()
                .orElse(null);
        if (item != null && player instanceof ServerPlayer serverPlayer) {
            // Open Preview for ANY click
            PreviewMenu.open(serverPlayer, item);
            return; // Always open preview and stop further interaction for now.
        }
    }

    // Override to prevent shift-clicking items out/in if needed.
    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}

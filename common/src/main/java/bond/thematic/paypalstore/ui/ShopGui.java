package bond.thematic.paypalstore.ui;

import bond.thematic.paypalstore.config.StoreConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ShopGui {
    public static void open(ServerPlayer player) {
        SimpleContainer inventory = new SimpleContainer(27); // 3 rows

        int slot = 0;
        for (StoreConfig.StoreItem item : StoreConfig.get().items) {
            if (slot >= 27)
                break;

            ItemStack stack = new ItemStack(Items.EMERALD); // Placeholder icon
            stack.setHoverName(Component.literal(item.name).withStyle(s -> s.withColor(0x00FF00)));
            // We could add lore with price

            inventory.setItem(slot, stack);
            slot++;
        }

        player.openMenu(new SimpleMenuProvider((syncId, playerInventory, playerEntity) -> {
            return StoreMenu.create(syncId, playerInventory, inventory);
        }, Component.literal("PayPal Store")));
    }
}
